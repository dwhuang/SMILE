package edu.umd.smile.object;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import edu.umd.smile.MainApp;
import edu.umd.smile.gui.LogMessage;
import edu.umd.smile.util.MyRigidBodyControl;
import edu.umd.smile.util.MySliderJoint;


public class Table implements ActionListener {

	private static final Logger logger = Logger.getLogger(Table.class.getName());
	private static final String SCHEMA_DEEP_FNAME = "tablesetup/schema/deep.xsd";
    private static final String SCHEMA_SHALLOW_FNAME = "tablesetup/schema/shallow.xsd";

    private static Random random = new Random(5566);

    private String name;
    private boolean enabled = true;
	private Node rootNode;
	private Factory factory;
	private BulletAppState bulletAppState;
	private Inventory inventory;
	private Node robotLocationNode;

	private float tableWidth = 20;
	private float tableDepth = 12;
	private static final float TABLE_HEIGHT = 4;
	private Spatial tableSpatial = null;

	private int idSN = 0;
	private HashSet<String> uniqueIds = new HashSet<String>();

	private HashSet<String> xmlFnameLoaded = new HashSet<>();
	private HashMap<String, Element> defs = new HashMap<>();
	private HashMap<String, HashMap<String, String>> defVars = new HashMap<>();
    private HashMap<String, HashMap<String, Boolean>> defVarIsDerived = new HashMap<>();
    
    private boolean shiftKey = false;

	public Table(String name, MainApp app, Node robotLocationNode) {
		this.name = name;
		rootNode = app.getRootNode();
		factory = app.getFactory();
		bulletAppState = app.getBulletAppState();
		inventory = app.getInventory();
		this.robotLocationNode = robotLocationNode;
	}

	public float getWidth() {
		return tableWidth;
	}

	public float getDepth() {
		return tableDepth;
	}

	public ColorRGBA getColor() {
		return ColorRGBA.White;
	}

	public BoundingVolume getWorldBound() {
		return tableSpatial.getWorldBound();
	}

	public void setEnabled(boolean v) {
		enabled = v;
	}

	public void reloadXml(String xmlFname) {
		// remove the table (if exists)
		if (tableSpatial != null) {
			MyRigidBodyControl rbc = tableSpatial.getControl(MyRigidBodyControl.class);
			if (rbc != null) {
				bulletAppState.getPhysicsSpace().remove(rbc);
				tableSpatial.removeControl(rbc);
			}
			rootNode.detachChild(tableSpatial);
		}
		// remove all free items (items not currently being grasped)
		inventory.removeAllFreeItems();
		uniqueIds.clear();
		for (Spatial s : inventory.allItems()) {
			uniqueIds.add(s.getName());
		}
		idSN = 0;
		defs.clear();
		xmlFnameLoaded.clear();

		Document doc = parseXmlFile(xmlFname);
		if (doc != null) {
		    try {
    			processIncludeElements(doc);
    			processDefElements(doc);
    			processInstanceElements(doc, doc.getDocumentElement(), new HashMap<String, String>(),
    			        new Stack<String>());
                writeXmlToFile(doc, "tablesetup/debug.xml");
    	        doc = validateXmlTree(doc, true);
    			processXmlTree(doc);
		    } catch (Exception e) {
		        LogMessage.warn("error processing xml input", logger, e);
		    }
		}
		
		// relocate the robot according to table size
    	robotLocationNode.setLocalTransform(Transform.IDENTITY);
    	robotLocationNode.setLocalTranslation(0, 2, tableDepth / 2 + 3);
    	robotLocationNode.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
	}
	
	/**
	 * Validate an existing DOM tree.
	 * @param doc
	 * @return a document node after validation.
	 */
	private Document validateXmlTree(Document doc, boolean deep) {
		// make schema
		SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		Schema schema = null;
		try {
			schema = sf.newSchema(new File(deep ? SCHEMA_DEEP_FNAME : SCHEMA_SHALLOW_FNAME));
		} catch (SAXException e1) {
			LogMessage.warn("schema error: " + SCHEMA_DEEP_FNAME, logger, e1);
			return doc;
		}
		// validate
		Validator validator = schema.newValidator();
		DOMResult res = new DOMResult();
		try {
			validator.validate(new DOMSource(doc), res);
		} catch (SAXException | IOException e1) {
		    LogMessage.warn("schema validation error", logger, e1);
			return doc;
		}
		return (Document) res.getNode();
	}

	/**
	 * Parse XML file into a DOM tree without validation.
	 * @param fname
	 * @return a document node
	 */
	private Document parseXmlFile(String fname) {
		// make document parser
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setSchema(null);
		DocumentBuilder db = null;
		try {
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e1) {
		    LogMessage.warn("parse error: " + fname, logger, e1);
			return null;
		}
		db.setErrorHandler(new ErrorHandler() {
			@Override
			public void warning(SAXParseException exception) throws SAXException {
			}
			@Override
			public void error(SAXParseException exception) throws SAXException {
				throw exception;
			}
			@Override
			public void fatalError(SAXParseException exception) throws SAXException {
				throw exception;
			}
		});
		// parse document
		Document doc = null;
		try {
			doc = db.parse(new File(fname));
		} catch (SAXException e) {
		    LogMessage.warn("cannot parse " + fname, logger, e);
			return null;
		} catch (IOException e) {
		    LogMessage.warn("cannot read from " + fname, logger, e);
			return null;
		} catch (RuntimeException e) {
		    LogMessage.warn("an error occurs in " + fname, logger, e);
			return null;
		}
		doc = validateXmlTree(doc, false);
		xmlFnameLoaded.add(fname);
		return doc;
	}
	
    private void writeXmlToFile(Document doc, String fname) {
	    Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException
                | TransformerFactoryConfigurationError e) {
            e.printStackTrace();
            return;
        }
	    Result output = new StreamResult(new File(fname));
	    Source input = new DOMSource(doc);

	    try {
            transformer.transform(input, output);
        } catch (TransformerException e) {
            e.printStackTrace();
            return;
        }
	}
	
	@SuppressWarnings("unused")
    private void walkXmlTree(org.w3c.dom.Node root, String path) {
	    System.out.println(path + ": " + root);
	    
	    path += "->" + root;
	    for (org.w3c.dom.Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
	        walkXmlTree(child, path);
	    }
	}

	/**
	 * Expands all {@code <include>} elements in the document recursively.
	 * @param doc
	 */
	private void processIncludeElements(Document doc) {
	    Element root = doc.getDocumentElement();
	    for (org.w3c.dom.Node child = root.getFirstChild(); child != null;) {
	        org.w3c.dom.Node nextChild = child.getNextSibling();
	        if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
	                && child.getNodeName().equals("include")) {
	            String fname = ((Element) child).getAttribute("file");
	            Document incDoc = null;
	            if (!xmlFnameLoaded.contains(fname)) {
	                incDoc = parseXmlFile(fname);
	            } else {
	                String msg = "xml file " + fname + " has already been loaded (skip)";
	                logger.log(Level.WARNING, msg);
	            }
	            if (incDoc != null) {
	                processIncludeElements(incDoc);
	                Element incRoot = incDoc.getDocumentElement();
	                for (org.w3c.dom.Node incChild = incRoot.getFirstChild(); incChild != null;) {
	                    org.w3c.dom.Node nextIncChild = incChild.getNextSibling();
	                    incChild = doc.importNode(incChild, true);
	                    if (incChild != null) {
	                        root.insertBefore(incChild, child);
	                    }
	                    incChild = nextIncChild;
	                }
	            }
                root.removeChild(child);
	        }
	        child = nextChild;
	    }
	}

	/**
	 * Store and remove all {@code <def>} elements from {@code doc}.
	 * @param doc
	 */
	private void processDefElements(Document doc) {
	    Element root = doc.getDocumentElement();
	    for (org.w3c.dom.Node child = root.getFirstChild(); child != null;) {
	        org.w3c.dom.Node nextChild = child.getNextSibling();
	        if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
	                && child.getNodeName().equals("def")) {
    	        String name = ((Element) child).getAttribute("name");
    	        if (defs.containsKey(name)) {
    	            LogMessage.warn("Duplicated definition detected: " + name + " (overwrite)", logger);
    	            defs.remove(name);
    	            defVars.remove(name);
    	        }
    	        // get default variable values in def
                HashMap<String, String> vars = new HashMap<>();
                HashMap<String, Boolean> varIsDerived = new HashMap<>();
    	        for (org.w3c.dom.Node defChild = child.getFirstChild(); defChild != null;) {
    	            org.w3c.dom.Node nextDefChild = defChild.getNextSibling();
    	            if (defChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
    	                    && defChild.getNodeName().equals("var")) {
    	                Element e = (Element) defChild;
    	                vars.put(e.getAttribute("name"), e.getAttribute("value"));
    	                varIsDerived.put(e.getAttribute("name"), Boolean.parseBoolean(e.getAttribute("derived")));
    	                child.removeChild(defChild);
    	            }
    	            defChild = nextDefChild;
    	        }
    	        defs.put(name, (Element) child);
                defVars.put(name, vars);
                defVarIsDerived.put(name, varIsDerived);
    	        root.removeChild(child);
	        }
	        child = nextChild;
	    }
    }

	/**
	 * Process the subtree {@code root} for {@code <instance>}-related work:
	 * (1) expand all child {@code <instance>} nodes;
	 * (2) substitute the root's variable values.
	 * @param doc
	 * @param root
	 * @param vars
	 */
    private void processInstanceElements(Document doc, Element root, Map<String, String> vars,
            Stack<String> defCallStack) {
        // substitute variable values
        performVariableSubst(root, vars);
        if (root.getNodeName().equals("instance")) {
            performInstanceExpansion(doc, root, vars, defCallStack);
        } else if (root.getNodeName().equals("def")) {
            LogMessage.warn("Element &lt;def&gt; is not allowed here (removed)", logger);
            org.w3c.dom.Node parent = root.getParentNode();
            if (parent.getNodeType() != org.w3c.dom.Node.DOCUMENT_NODE) {
                parent.removeChild(root);
            }
        } else {
            for (org.w3c.dom.Node child = root.getFirstChild(); child != null;) {
                org.w3c.dom.Node nextChild = child.getNextSibling();
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    processInstanceElements(doc, (Element) child, vars, defCallStack);
                }
                child = nextChild;
            }
        }
    }
    
    private void performInstanceExpansion(Document doc, Element elm, Map<String, String> vars,
            Stack<String> defCallStack) {
        // look up def
        String defName = elm.getAttribute("def");
        if (!defs.containsKey(defName)) {
            LogMessage.warn("Definition not found: " + defName + " (ignored)", logger);
            elm.getParentNode().removeChild(elm);
            return;
        }
        if (defCallStack.contains(defName)) {
            LogMessage.warn("Recursive definition detected in: " + defName + " (recursive instance ignored)",
                    logger);
            elm.getParentNode().removeChild(elm);
            return;
        }
        defCallStack.push(defName);
        
        DocumentFragment frag = doc.createDocumentFragment();
        // make a copy of variables
        vars = new HashMap<>(vars);
        // make a copy of def
        Element def = defs.get(defName);
        for (org.w3c.dom.Node defChild = def.getFirstChild(); defChild != null;
                defChild = defChild.getNextSibling()) {
            frag.appendChild(defChild.cloneNode(true));
        }
        // get <var> elements under <instance>
        HashMap<String, String> myVars = new HashMap<>();
        HashMap<String, Boolean> myVarIsDerived = new HashMap<>();
        for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && child.getNodeName().equals("var")) {
                Element e = (Element) child;
                if (Boolean.parseBoolean(e.getAttribute("derived"))) {
                    myVars.put(e.getAttribute("name"), e.getAttribute("value"));
                    myVarIsDerived.put(e.getAttribute("name"), true);
                } else {
                    myVars.put(e.getAttribute("name"), performVariableSubst(e.getAttribute("value"), vars));
                    myVarIsDerived.put(e.getAttribute("name"), false);
                }
            }
        }
        // merge <var> elements under <instance> and <def>. Variables under <instance> take priority.
        Map<String, String> defv = defVars.get(defName);
        Map<String, Boolean> defvd = defVarIsDerived.get(defName);
        for (String k : defv.keySet()) {
            if (!myVars.containsKey(k)) {
                myVars.put(k, defv.get(k));
                myVarIsDerived.put(k, defvd.get(k));
            }
        }
        // merge non-derived variables with the enclosing scope
        for (String k : myVars.keySet()) {
            if (!myVarIsDerived.get(k)) {
                vars.put(k, myVars.get(k));
            }
        }
        // process derived variables
        for (String k : myVars.keySet()) {
            if (myVarIsDerived.containsKey(k)) {
                myVars.put(k, performVariableSubst(myVars.get(k), vars));
            }
        }
        // merge derived variables with the enclosing scope
        for (String k : myVars.keySet()) {
            if (myVarIsDerived.containsKey(k)) {
                vars.put(k, myVars.get(k));
            }
        }
        // recursively process those new cloned nodes in frag
        for (org.w3c.dom.Node dfChild = frag.getFirstChild(); dfChild != null;) {
            org.w3c.dom.Node dfNextChild = dfChild.getNextSibling();
            if (dfChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                processInstanceElements(doc, (Element) dfChild, vars, defCallStack);
            }
            dfChild = dfNextChild;
        }
        
        elm.getParentNode().insertBefore(frag, elm);
        elm.getParentNode().removeChild(elm);
        
        defCallStack.pop();
    }
    
    private String performVariableSubst(String str, Map<String, String> vars) {
        Pattern pat = Pattern.compile("\\$(.*?)\\$");
        Matcher mat = pat.matcher(str);
        //System.out.println("process " + attr.getNodeValue());
        StringBuffer buf = new StringBuffer();
        while (mat.find()) {
            try {
                float ans = performVarArith(new ArithState(mat.group(1)), vars);
                // if ans is very close to an integer, cast it to int type
                int ansInt = Math.round(ans);
                if (Math.abs(ans - ansInt) < 0.000001) {
                    mat.appendReplacement(buf, "" + ansInt);
                } else {
                    mat.appendReplacement(buf, "" + ans);
                }
            } catch (RuntimeException e) {
                if (vars.containsKey(mat.group(1))) {
                    mat.appendReplacement(buf, vars.get(mat.group(1)));
                } else {
                    LogMessage.warn("invalid expression: " + mat.group(1), logger, e);
                    mat.appendReplacement(buf, mat.group());
                }
            }
        }
        mat.appendTail(buf);
        return buf.toString();
    }
    
    private void performVariableSubst(Element elm, Map<String, String> vars) {
        NamedNodeMap attrs = elm.getAttributes();
        for (int i = 0; i < attrs.getLength(); ++i) {
            org.w3c.dom.Node attr = attrs.item(i);
            attr.setNodeValue(performVariableSubst(attr.getNodeValue(), vars));
        }
    }
    
    private class ArithState {
        final String str;
        int ind;
        ArithState(String str) {
            this.str = str;
        }
        public String toString() {
            return str.substring(ind);
        }
        char getChar() {
            return str.charAt(ind);
        }
        void advance() {
            ++ind;
        }
        boolean isValid() {
            return ind >= 0 && ind < str.length();
        }
    }
    
    private float performVarArith(ArithState st, Map<String, String> vars) {
        //System.out.println("\ta " + st);
        float ans = performVarArithFactor(st, vars);
        while (st.isValid()) {
            char c = st.getChar();
            if (c == ' ' || c == '\t') {
                st.advance();
            } else if (c == '+') {
                st.advance();
                ans += performVarArithFactor(st, vars);
            } else if (c == '-') {
                st.advance();
                ans -= performVarArithFactor(st, vars);
            } else {
                //System.out.println("\ta ret1 " + ans + "; " + st);
                return ans;
            }
        }
        //System.out.println("\ta ret2 " + ans + "; " + st);
        return ans;
    }

    private float performVarArithFactor(ArithState st, Map<String, String> vars) {
        //System.out.println("\tf " + st);
        float ans = performVarArithAtom(st, vars);
        while (st.isValid()) {
            char c = st.getChar();
            if (c == ' ' || c == '\t') {
                st.advance();
            } else if (c == '*') {
                st.advance();
                ans *= performVarArithAtom(st, vars);
            } else if (c == '/') {
                st.advance();
                ans /= performVarArithAtom(st, vars);
            } else {
                //System.out.println("\tf ret1 " + ans + "; " + st);
                return ans;
            }
        }
        //System.out.println("\tf ret2 " + ans + "; " + st);
        return ans;
    }

    private float performVarArithAtom(ArithState st, Map<String, String> vars) {
        //System.out.println("\tt " + st);
        char c;
        while (st.isValid()) {
            c = st.getChar();
            if (c == ' ' || c == '\t') {
                st.advance();
            } else {
                break;
            }
        }
        boolean neg = false;
        c = st.getChar();
        if (c == '-') {
            neg = true;
            st.advance();
        } else if (c == '+') {
            st.advance();
        }
        
        if (st.getChar() == '(') {
            st.advance();
            float ans = performVarArith(st, vars);
            if (!st.isValid() || st.getChar() != ')') {
                throw new IllegalArgumentException("mismatched parentheses");
            }
            st.advance();
            //System.out.println("\tt ret1 " + (neg ? -ans : ans) + "; " + st);
            return neg ? -ans : ans;
        }
        
        StringBuffer buf = new StringBuffer();
        while (st.isValid()) {
            c = st.getChar();
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '_') {
                st.advance();
                buf.append(c);
            } else {
                break;
            }
        }
        try {
            float ans = Float.parseFloat(buf.toString());
            //System.out.println("\tt ret2 " + buf + "; " + st);
            return neg ? -ans : ans;
        } catch (NumberFormatException e) {
            if (vars.containsKey(buf.toString())) {
                //System.out.println("\tt ret3 " + vars.get(buf.toString()) + "; " + st);
                float res = Float.parseFloat(vars.get(buf.toString()));
                return neg ? -res : res;
            }
            //System.out.println(e);
            throw e;
        }
    }
    
    private void processXmlTree(Document doc) {
        Element root = doc.getDocumentElement();
        tableWidth = Float.parseFloat(root.getAttribute("xspan"));
        tableDepth = Float.parseFloat(root.getAttribute("yspan"));
		makeTable();
		for (org.w3c.dom.Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
		    Element elm = (Element) child;
			if (elm.getNodeName().equals("block")) {
				processBlockElement(elm, true);
			} else if (elm.getNodeName().equals("cylinder")) {
				processCylinderElement(elm, true);
            } else if (elm.getNodeName().equals("prism")) {
                processPrismElement(elm, true);
            } else if (elm.getNodeName().equals("ring")) {
                processRingElement(elm, true);
			} else if (elm.getNodeName().equals("sphere")) {
				processSphereElement(elm, true);
			} else if (elm.getNodeName().equals("box")) {
				processBoxElement(elm, true);
			} else if (elm.getNodeName().equals("custom")) {
				processCustomElement(elm, true);
			} else if (elm.getNodeName().equals("composite")) {
				processCompositeElement(elm, true);
            } else if (elm.getNodeName().equals("toggleSwitch")) {
                processToggleSwitchElement(elm, true);
            } else if (elm.getNodeName().equals("indicatorLights")) {
                processIndicatorLightsElement(elm, true);
            } else if (elm.getNodeName().equals("customControl")) {
                processCustomControlElement(elm, true);
			} else if (elm.getNodeName().equals("chain")) {
				processChainElement(elm);
			} else if (elm.getNodeName().equals("sliderJoint")) {
				processSliderJointElement(elm);
			} else {
				logger.log(Level.WARNING, "skipping unknown element " + elm.getNodeName());
			}
		}
		inventory.initControls();
	}

	private void makeTable() {
		// make table
		tableSpatial = factory.makeBigBlock(name, tableWidth, TABLE_HEIGHT, tableDepth, ColorRGBA.White, 4);
		tableSpatial.setLocalTranslation(0, -TABLE_HEIGHT / 2, 0);
		MyRigidBodyControl rbc = new MyRigidBodyControl(0);
		tableSpatial.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
		rootNode.attachChild(tableSpatial);
	}
	
	private static void putDescriptionToSpatial(Spatial s, String name, String value) {
	    int count = 0;
	    try {
	        count = Integer.parseInt((String) s.getUserData("descriptionCount"));
	    } catch (Exception e) {}
	    int i;
	    for (i = 0; i < count; ++i) {
	        String oldName = s.getUserData("description" + i + "Name");
	        if (oldName.equals(name)) {
	            break;
	        }
	    }
	    s.setUserData("description" + i + "Name", name);
        s.setUserData("description" + i + "Value", value);
        if (i == count) {
            s.setUserData("descriptionCount", "" + (count + 1));
        }
	}
	
	private void processDescriptionElements(Element root, Spatial s, String defaultShape) {
	    if (defaultShape != null) {
	        putDescriptionToSpatial(s, "shape", defaultShape);
	    }
	    for (org.w3c.dom.Node child = root.getFirstChild(); child != null; ) {
	        org.w3c.dom.Node nextChild = child.getNextSibling();
	        if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
	                && child.getNodeName().equals("description")) {
	            putDescriptionToSpatial(s, ((Element) child).getAttribute("name"),
	                    ((Element) child).getAttribute("value"));
	            root.removeChild(child);
	        }
	        child = nextChild;
	    }
	}

	private void processSliderJointElement(Element e) {
		// ignore id
		Vector3f location = parseVector3(e.getAttribute("location"));
		Vector3f rotation = parseVector3(e.getAttribute("rotation"));
		float min = Float.parseFloat(e.getAttribute("min"));
		float max = Float.parseFloat(e.getAttribute("max"));
		if (min > max) {
			logger.log(Level.SEVERE, "slider joint has min > max");
			return;
		}
		float init = Float.parseFloat(e.getAttribute("init"));
		init = FastMath.clamp(init, min, max);
		boolean collision = Boolean.parseBoolean(e.getAttribute("collision"));

		Spatial[] objs = new Spatial[2];
		int k = 0;
		for (org.w3c.dom.Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
			Element childElm = (Element) child;
			Spatial obj = null;
			if (childElm.getNodeName().equals("block")) {
				obj = processBlockElement(childElm, true);
			} else if (childElm.getNodeName().equals("cylinder")) {
				obj = processCylinderElement(childElm, true);
            } else if (childElm.getNodeName().equals("prism")) {
                obj = processPrismElement(childElm, true);
            } else if (childElm.getNodeName().equals("ring")) {
                obj = processRingElement(childElm, true);
			} else if (childElm.getNodeName().equals("sphere")) {
				obj = processSphereElement(childElm, true);
			} else if (childElm.getNodeName().equals("box")) {
				obj = processBoxElement(childElm, true);
			} else if (childElm.getNodeName().equals("custom")) {
				obj = processCustomElement(childElm, true);
			} else if (childElm.getNodeName().equals("composite")) {
				obj = processCompositeElement(childElm, true);
            } else if (childElm.getNodeName().equals("toggleSwitch")) {
                obj = processToggleSwitchElement(childElm, true);
            } else if (childElm.getNodeName().equals("indicatorLights")) {
                obj = processIndicatorLightsElement(childElm, true);
            } else if (childElm.getNodeName().equals("customControl")) {
                obj = processCustomControlElement(childElm, true);
			}
			if (obj != null) {
				if (k >= 2) {
					LogMessage.warn("sliderJoint " + e.getAttribute("id")
							+ " contains more than two objects: " + obj.getName() + " (ignored)", logger);
				} else {
					objs[k] = obj;
					++k;
				}
			}
		}
		// make joint between the two objects
		if (objs[0] != null && objs[1] != null) {
			Transform jointTrans = new Transform(location, new Quaternion().fromAngles(
					rotation.x * FastMath.DEG_TO_RAD,
					rotation.y * FastMath.DEG_TO_RAD,
					rotation.z * FastMath.DEG_TO_RAD));
			MyRigidBodyControl c;
			Transform trans;

			// transform obj1 using ((the joint's transform) * (obj1's local transform))
			Transform obj1Trans = objs[0].getLocalTransform();
			trans = obj1Trans.clone();
			trans.combineWithParent(jointTrans);
			c = objs[0].getControl(MyRigidBodyControl.class);
			c.setPhysicsLocation(trans.getTranslation());
			c.setPhysicsRotation(trans.getRotation());

			// transform obj2 using ((the joint's transform) * (slide to init pos) * (obj1's local transform))
			Transform obj2Trans = objs[1].getLocalTransform();
			trans = obj2Trans.clone();
			trans.combineWithParent(new Transform(Vector3f.UNIT_X.mult(-init))); // slide to initial position
			trans.combineWithParent(jointTrans);
			c = objs[1].getControl(MyRigidBodyControl.class);
			c.setPhysicsLocation(trans.getTranslation());
			c.setPhysicsRotation(trans.getRotation());

			// note the negate/transpose: because the objects' local transforms are relative to the pivots,
			// but these parameters take the pivots' transforms relative to the objects.
			MySliderJoint joint = inventory.addSliderJoint(objs[0], objs[1],
					obj1Trans.getTranslation().negate(),
					obj2Trans.getTranslation().negate(),
					obj1Trans.getRotation().toRotationMatrix().transpose(),
					obj2Trans.getRotation().toRotationMatrix().transpose(),
					min, max, collision);
			float damping = Float.parseFloat(e.getAttribute("damping"));
            joint.setDampingDirAng(damping);
            joint.setDampingDirLin(damping);
            float restitution = Float.parseFloat(e.getAttribute("restitution"));
            joint.setRestitutionDirAng(restitution);
            joint.setRestitutionDirLin(restitution);
            float softness = Float.parseFloat(e.getAttribute("softness"));
            joint.setSoftnessDirAng(softness);
            joint.setSoftnessDirLin(softness);
            //System.out.println(joint.toParamString());
		}
	}

	private Spatial processBlockElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		if (isWhole || pointable) {
			id = getUniqueId(id);
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float yspan = Float.parseFloat(elm.getAttribute("zspan"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));

		Spatial s = factory.makeBlock(id, xspan, yspan, zspan, color);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);
			processDescriptionElements(elm, s, "block");
		}
		if (pointable) {
		    s.setUserData("pointable", "" + pointable);
		}
		String ns = elm.getAttribute("nextStateWhenTriggered");
		if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
		}

		return s;
	}

	private Spatial processCylinderElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		if (isWhole || pointable) {
			id = getUniqueId(id);
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float radius = Float.parseFloat(elm.getAttribute("radius"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));
		int sides = Integer.parseInt(elm.getAttribute("sides"));

		Spatial s = factory.makeCylinder(id, radius, zspan, sides, color);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);
			processDescriptionElements(elm, s, "cylinder");
		}
		if (pointable) {
            s.setUserData("pointable", "" + pointable);
		}
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

		return s;
	}

    private Spatial processPrismElement(Element elm, boolean isWhole) {
        String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
        if (isWhole || pointable) {
            id = getUniqueId(id);
        }
        Vector3f location = parseVector3(elm.getAttribute("location"));
        Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
        ColorRGBA color = parseColor(elm.getAttribute("color"));
        float radiusTop = Float.parseFloat(elm.getAttribute("radiusTop"));
        float radiusBottom = Float.parseFloat(elm.getAttribute("radiusBottom"));
        float yspan = Float.parseFloat(elm.getAttribute("zspan"));
        int sides = Integer.parseInt(elm.getAttribute("sides"));

        Spatial s = factory.makePrism(id, radiusTop, radiusBottom, yspan, sides, color);
        s.setLocalTranslation(location);
        s.setLocalRotation(new Quaternion().fromAngles(
                rotation.x * FastMath.DEG_TO_RAD,
                rotation.y * FastMath.DEG_TO_RAD,
                rotation.z * FastMath.DEG_TO_RAD));

        if (isWhole) {
            float mass = Float.parseFloat(elm.getAttribute("mass"));
            inventory.addItem(s, mass);
            processDescriptionElements(elm, s, "prism");
        }
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

        return s;
    }

    private Spatial processRingElement(Element elm, boolean isWhole) {
        String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
        if (isWhole || pointable) {
            id = getUniqueId(id);
        }
        Vector3f location = parseVector3(elm.getAttribute("location"));
        Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
        ColorRGBA color = parseColor(elm.getAttribute("color"));
        float radiusOuter = Float.parseFloat(elm.getAttribute("radiusOuter"));
        float radiusInner = Float.parseFloat(elm.getAttribute("radiusInner"));
        float yspan = Float.parseFloat(elm.getAttribute("zspan"));
        int sides = Integer.parseInt(elm.getAttribute("sides"));

        Spatial s = factory.makeRing(id, radiusOuter, radiusInner, yspan, sides, color);
        s.setLocalTranslation(location);
        s.setLocalRotation(new Quaternion().fromAngles(
                rotation.x * FastMath.DEG_TO_RAD,
                rotation.y * FastMath.DEG_TO_RAD,
                rotation.z * FastMath.DEG_TO_RAD));

        if (isWhole) {
            float mass = Float.parseFloat(elm.getAttribute("mass"));
            inventory.addItem(s, mass);
            processDescriptionElements(elm, s, "ring");
        }
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

        return s;
    }

	private Spatial processSphereElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		if (isWhole || pointable) {
			id = getUniqueId(id);
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float radius = Float.parseFloat(elm.getAttribute("radius"));

		Spatial s = factory.makeSphere(id, radius, color);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);
			processDescriptionElements(elm, s, "sphere");
		}
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

		return s;
	}

	private Spatial processBoxElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		if (isWhole || pointable) {
			id = getUniqueId(id);
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float yspan = Float.parseFloat(elm.getAttribute("zspan"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));
		float thickness = Float.parseFloat(elm.getAttribute("thickness"));

		Spatial s = factory.makeBoxContainer(id, xspan, yspan, zspan, thickness, color);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);
			processDescriptionElements(elm, s, "box");
		}
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

		return s;
	}

	private Spatial processCustomElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
        if (isWhole || pointable) {
            id = getUniqueId(id);
        }
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float scale = Float.parseFloat(elm.getAttribute("scale"));
		String file = elm.getAttribute("file");

		Spatial s = factory.makeCustom(id, file, color, scale);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);
			
			String[] toks = new File(file).getName().split("\\.");
			String shapeName = "custom";
			if (toks.length > 0) {
			    shapeName = toks[0];
			}
			processDescriptionElements(elm, s, shapeName);
		}
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            s.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

		return s;
	}

	private Spatial processCompositeElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
        if (isWhole || pointable) {
            id = getUniqueId(id);
        }
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));

		Node node = new Node(id);
		node.setLocalTranslation(location);
		node.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));
		if (isWhole) {
		    processDescriptionElements(elm, node, "composite");
		}

		for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
			Element childElm = (Element) child;
			Spatial s = null;
			if (childElm.getNodeName().equals("block")) {
				s = processBlockElement(childElm, false);
			} else if (childElm.getNodeName().equals("cylinder")) {
				s = processCylinderElement(childElm, false);
            } else if (childElm.getNodeName().equals("prism")) {
                s = processPrismElement(childElm, false);
            } else if (childElm.getNodeName().equals("ring")) {
                s = processRingElement(childElm, false);
			} else if (childElm.getNodeName().equals("sphere")) {
				s = processSphereElement(childElm, false);
			} else if (childElm.getNodeName().equals("box")) {
				s = processBoxElement(childElm, false);
			} else if (childElm.getNodeName().equals("custom")) {
				s = processCustomElement(childElm, false);
			} else if (childElm.getNodeName().equals("composite")) {
				s = processCompositeElement(childElm, false);
			} else if (childElm.getNodeName().equals("toggleSwitch")) {
				s = processToggleSwitchElement(childElm, false);
			} else if (childElm.getNodeName().equals("indicatorLights")) {
				s = processIndicatorLightsElement(childElm, false);
            } else if (childElm.getNodeName().equals("customControl")) {
                s = processCustomControlElement(childElm, false);
			} else {
				logger.log(Level.WARNING, "skipping unknown composite element " + childElm.getNodeName());
			}
			if (s != null) {
			    node.attachChild(s);
			}
		}

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(node, mass);
			node.setUserData("obj_shape", "composite");
		}
        if (pointable) {
            node.setUserData("pointable", "" + pointable);
        }
        String ns = elm.getAttribute("nextStateWhenTriggered");
        if (ns.length() > 0) {
            node.setUserData("nextStateWhenTriggered", Integer.parseInt(ns));
        }

		return node;
	}

	private Spatial processToggleSwitchElement(Element elm, boolean isWhole) {
		String id = getUniqueId(elm.getAttribute("id"));
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));
		float yspan = Float.parseFloat(elm.getAttribute("zspan"));
		float angle = Float.parseFloat(elm.getAttribute("angle")) * FastMath.DEG_TO_RAD;
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		boolean leftPressed = Boolean.parseBoolean(elm.getAttribute("leftPressed"));
		int numStates = Integer.parseInt(elm.getAttribute("numStates"));
		int initState = Integer.parseInt(elm.getAttribute("initState"));
		if (initState < 0) {
		    initState = 0;
		} else if (initState >= numStates) {
		    initState = numStates - 1;
		}

		float btxspan = xspan / (1.0f + FastMath.cos(angle));

		Node s = new Node(id);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));
		// button 1
		Spatial b1 = factory.makeBlock(id + "-b1", btxspan, yspan, zspan, color);
		b1.setLocalTranslation(-btxspan / 2, yspan / 2, 0);
		s.attachChild(b1);
		// button 2
		Spatial b2 = factory.makeBlock(id + "-b2", btxspan, yspan, zspan, color);
		b2.setLocalRotation(new Quaternion().fromAngles(0, 0, angle));
		float hypoLen = FastMath.sqr(btxspan * btxspan + yspan * yspan); // hypotenues
		float cosine = (btxspan / hypoLen) * FastMath.cos(angle) - (yspan / hypoLen) * FastMath.sin(angle);
		float sine = (yspan / hypoLen) * FastMath.cos(angle) + (btxspan / hypoLen) * FastMath.sin(angle);
		b2.setLocalTranslation(hypoLen / 2f * cosine, hypoLen / 2f * sine, 0);
		s.attachChild(b2);

		// get <downstream> and <state> elements
		LinkedList<String> dsIds = new LinkedList<>();
		for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
			        && child.getNodeName().equals("downstream")) {
				dsIds.add(((Element) child).getAttribute("id"));
			}
		}

		ToggleSwitchControl c = new ToggleSwitchControl(inventory, s, angle, leftPressed, numStates, initState);

		for (String dsId : dsIds) {
			c.addDownstreamId(dsId);
		}
		inventory.registerControl(s, c);
		
		if (isWhole) {
		    float mass = Float.parseFloat(elm.getAttribute("mass"));
		    inventory.addItem(s, mass);
	        processDescriptionElements(elm, s, "toggleSwitch");
		}
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }

		return s;
	}

	private Spatial processIndicatorLightsElement(Element elm, boolean isWhole) {
		String id = getUniqueId(elm.getAttribute("id"));
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float lightZspan = Float.parseFloat(elm.getAttribute("lightZspan"));
		float lightRadius = Float.parseFloat(elm.getAttribute("lightRadius"));
		int numLights = Integer.parseInt(elm.getAttribute("numLights"));
		int initState = Integer.parseInt(elm.getAttribute("initState"));

		Node s = new Node(id);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		float lightIntv = 0;
		Vector3f lightPos = new Vector3f();
		if (numLights > 1) {
			lightIntv = (xspan - lightRadius * 2) / (numLights - 1);
			lightPos.x = -xspan / 2 + lightRadius;
			lightPos.y = lightZspan / 2;
		}
		for (int i = 0; i < numLights; ++i) {
			Spatial light = factory.makeCylinder(id + "-light" + i, lightRadius, lightZspan, 32, ColorRGBA.Black);
			light.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
			light.setLocalTranslation(lightPos);
			lightPos.x += lightIntv;
			s.attachChild(light);
		}

		// get <downstream> and <state> elements
		LinkedList<String> dsIds = new LinkedList<>();
		LinkedList<ColorRGBA[]> lightStates = new LinkedList<>();
		LinkedList<String> lightStateNames = new LinkedList<>();
		for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
			if (child.getNodeName().equals("downstream")) {
				dsIds.add(((Element) child).getAttribute("id"));
			} else if (child.getNodeName().equals("state")) {
			    // try to get the descriptive name of the state
			    String lsName = ((Element) child).getAttribute("descriptionName");
			    if (lsName == null || lsName.length() == 0) {
			        lsName = "" + lightStateNames.size();
			    }
			    lightStateNames.add(lsName);
                // get <light> elements under <state>
				ColorRGBA[] ls = new ColorRGBA[numLights];
				for (org.w3c.dom.Node grandChild = child.getFirstChild(); grandChild != null;
				        grandChild = grandChild.getNextSibling()) {
					if (grandChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
					        && grandChild.getNodeName().equals("light")) {
						int ind = Integer.parseInt(((Element) grandChild).getAttribute("id"));
						if (ind < 0 || ind >= numLights) {
							throw new IllegalArgumentException("invalid light id " + ind);
						}
						ColorRGBA color = parseColor(((Element) grandChild).getAttribute("color"));
						ls[ind] = color;
					}
				}
				lightStates.add(ls);
			}
		}

		IndicatorLightsControl c = new IndicatorLightsControl(inventory, s, initState, lightStates, lightStateNames);

		for (String dsId : dsIds) {
			c.addDownstreamId(dsId);
		}
        inventory.registerControl(s, c);
        
        if (isWhole) {
            float mass = Float.parseFloat(elm.getAttribute("mass"));
            inventory.addItem(s, mass);
            processDescriptionElements(elm, s, "indicatorLights");
        }
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }

		return s;
	}

    private Spatial processCustomControlElement(Element elm, boolean isWhole) {
        String id = getUniqueId(elm.getAttribute("id"));
        boolean pointable = Boolean.parseBoolean(elm.getAttribute("pointable"));
        Vector3f location = parseVector3(elm.getAttribute("location"));
        Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
        int initState = Integer.parseInt(elm.getAttribute("initState"));
        String name = elm.getAttribute("name");

        Node s = new Node(id);
        s.setLocalTranslation(location);
        s.setLocalRotation(new Quaternion().fromAngles(
                rotation.x * FastMath.DEG_TO_RAD,
                rotation.y * FastMath.DEG_TO_RAD,
                rotation.z * FastMath.DEG_TO_RAD));

        // get <downstream> and <state> elements
        LinkedList<String> dsIds = new LinkedList<>();
        LinkedList<List<Spatial>> stateSpatials = new LinkedList<>();
        LinkedList<String> stateNames = new LinkedList<>();
        for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            if (child.getNodeName().equals("downstream")) {
                dsIds.add(((Element) child).getAttribute("id"));
            } else if (child.getNodeName().equals("state")) {
                // try to get the descriptive name of the state
                String stateName = ((Element) child).getAttribute("descriptionName");
                if (stateName == null || stateName.length() == 0) {
                    stateName = "" + stateNames.size();
                }
                stateNames.add(stateName);
                stateSpatials.add(processCustomControlStateElement((Element) child));
            }
        }

        CustomControl c = new CustomControl(inventory, s, initState, stateSpatials, stateNames);

        for (String dsId : dsIds) {
            c.addDownstreamId(dsId);
        }
        inventory.registerControl(s, c);
        
        if (isWhole) {
            float mass = Float.parseFloat(elm.getAttribute("mass"));
            inventory.addItem(s, mass);
            processDescriptionElements(elm, s, name);
        }
        if (pointable) {
            s.setUserData("pointable", "" + pointable);
        }

        return s;
    }
    
    private List<Spatial> processCustomControlStateElement(Element stateElm) {
        List<Spatial> ret = new LinkedList<>();
        for (org.w3c.dom.Node child = stateElm.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElm = (Element) child;
            Spatial s = null;
            if (childElm.getNodeName().equals("block")) {
                s = processBlockElement(childElm, false);
            } else if (childElm.getNodeName().equals("cylinder")) {
                s = processCylinderElement(childElm, false);
            } else if (childElm.getNodeName().equals("prism")) {
                s = processPrismElement(childElm, false);
            } else if (childElm.getNodeName().equals("ring")) {
                s = processRingElement(childElm, false);
            } else if (childElm.getNodeName().equals("sphere")) {
                s = processSphereElement(childElm, false);
            } else if (childElm.getNodeName().equals("box")) {
                s = processBoxElement(childElm, false);
            } else if (childElm.getNodeName().equals("custom")) {
                s = processCustomElement(childElm, false);
            } else if (childElm.getNodeName().equals("composite")) {
                s = processCompositeElement(childElm, false);
            } else {
                logger.log(Level.WARNING, "skipping unknown triggerable element " + childElm.getNodeName());
            }
            if (s != null) {
                ret.add(s);
            }
        }
        return ret;
    }

	private void processChainElement(Element elm) {
		String groupId = getUniqueId(elm.getAttribute("id"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		Vector3f start = parseVector3(elm.getAttribute("start"));
		Vector3f end = parseVector3(elm.getAttribute("end"));
		float linkXspan = Float.parseFloat(elm.getAttribute("linkXspan"));
		float linkYspan = Float.parseFloat(elm.getAttribute("linkZspan"));
		float linkZspan = Float.parseFloat(elm.getAttribute("linkYspan"));
		int linkCount = Integer.parseInt(elm.getAttribute("linkCount"));
		float linkPadding = Float.parseFloat(elm.getAttribute("linkPadding"));
		float linkMass = Float.parseFloat(elm.getAttribute("linkMass"));

		// check if linkCount is enough to connect from start to end locations
		float dist = start.distance(end);
		if (linkCount == 0) {
			linkCount = (int) FastMath.ceil(dist / linkZspan);
			logger.log(Level.INFO, "chain " + groupId + ": linkCount=" + linkCount);
		} else {
			if ((float) linkCount * linkZspan < dist - linkZspan * .5f) {
				throw new IllegalArgumentException("linkCount " + linkCount
						+ " too low to connect the start and end locations");
			}
		}

		// start making a chain
		//
		Vector3f vec = new Vector3f(); // temporary storage
		final Vector3f endNodesSize = new Vector3f(.1f, .1f, .1f);
		final Vector3f linkPhysicsSize = new Vector3f(linkXspan / 2, linkYspan / 2, linkZspan / 2);
		// rotate the z axis to the start->end direction
		// when walking on the links from start to end, z increases in each local model space
		Quaternion rotStartEndDir = new Quaternion();
		rotStartEndDir.lookAt(start.subtract(end), Vector3f.UNIT_Y);

		// make start node (static)
		String id;
		id = getUniqueId(groupId + "-start");
		Spatial startNode = factory.makeBlock(id, endNodesSize.x, endNodesSize.y, endNodesSize.z,
				ColorRGBA.White);
		startNode.setLocalTranslation(start);
		startNode.setLocalRotation(rotStartEndDir);
		inventory.addItem(startNode, 0);
		startNode.setUserData("descriptionCount", 4);
		startNode.setUserData("description0Name", "shape");
		startNode.setUserData("description0Value", "chain-endpoint");
        startNode.setUserData("description1Name", "xspan");
        startNode.setUserData("description1Value", endNodesSize.x);
        startNode.setUserData("description2Name", "yspan");
        startNode.setUserData("description2Value", endNodesSize.z);
        startNode.setUserData("description3Name", "zspan");
        startNode.setUserData("description3Value", endNodesSize.y);

		// make end node (static)
		id = getUniqueId(groupId + "-end");
		Spatial endNode = factory.makeBlock(id, endNodesSize.x, endNodesSize.y, endNodesSize.z,
				ColorRGBA.White);
		endNode.setLocalTranslation(end);
		endNode.setLocalRotation(rotStartEndDir);
		inventory.addItem(endNode, 0);
		endNode.setUserData("descriptionCount", 4);
		endNode.setUserData("description0Name", "shape");
		endNode.setUserData("description0Value", "chain-endpoint");
        endNode.setUserData("description1Name", "xspan");
        endNode.setUserData("description1Value", endNodesSize.x);
        endNode.setUserData("description2Name", "yspan");
        endNode.setUserData("description2Value", endNodesSize.z);
        endNode.setUserData("description3Name", "zspan");
        endNode.setUserData("description3Value", endNodesSize.y);

		Spatial prevSpatial = startNode;
		Vector3f prevJointPt = new Vector3f(0, 0, -endNodesSize.z);
		for (int i = 0; i < linkCount; ++i) {
			// make a link
			id = getUniqueId(groupId + "-link" + i);
			Spatial link = factory.makeBlock(id, linkXspan, linkYspan, linkZspan + linkPadding * 2, color);
			link.setLocalRotation(rotStartEndDir);
			vec.interpolate(start, end, (i + .5f) / linkCount);
			link.setLocalTranslation(vec);
			inventory.addItem(link, linkMass, new BoxCollisionShape(linkPhysicsSize));
			link.getControl(MyRigidBodyControl.class).setAngularDamping(1);
			link.setUserData("descriptionCount", 5);
			link.setUserData("description0Name", "shape");
			link.setUserData("description0Value", "chain-link");
			link.setUserData("description1Name", "xspan");
			link.setUserData("description1Value", linkXspan);
			link.setUserData("description2Name", "yspan");
			link.setUserData("description2Value", linkZspan);
			link.setUserData("description3Name", "zspan");
			link.setUserData("description3Value", linkYspan);
            link.setUserData("description4Name", "color");
            link.setUserData("description4Value", color);

			// connect the link using a joint (or constraint)
			SixDofJoint joint = inventory.addSixDofJoint(prevSpatial, link,
					prevJointPt, new Vector3f(0, 0, linkZspan / 2));
			joint.setCollisionBetweenLinkedBodys(false);

			prevSpatial = link;
			prevJointPt = new Vector3f(0, 0, -linkZspan / 2);
		}
		// connect the last link to the end node
		vec.set(0, 0, endNodesSize.z);
		inventory.addSixDofJoint(prevSpatial, endNode, prevJointPt, vec);
	}

	private Vector3f parseVector3(String str) {
        Pattern pattern = Pattern.compile("^\\s*\\(([\\d\\.\\-E]+)\\s*,\\s*([\\d\\.\\-E]+)\\s*,\\s*([\\d\\.\\-E]+)\\s*\\)\\s*$");
        Matcher m = pattern.matcher(str);
        if (m.find()) {
            float x = Float.parseFloat(m.group(1));
            float y = Float.parseFloat(m.group(3));
            float z = -Float.parseFloat(m.group(2));
            return new Vector3f(x, y, z);
        }
        throw new IllegalArgumentException("could not parse '" + str + "'");
    }
    
    private ColorRGBA parseColor(String str) {
		if (str.equals("black")) {
			return ColorRGBA.Black;
		} else if (str.equals("blue")) {
			return ColorRGBA.Blue;
		} else if (str.equals("brown")) {
			return ColorRGBA.Brown;
		} else if (str.equals("cyan")) {
			return ColorRGBA.Cyan;
		} else if (str.equals("darkgray")) {
			return ColorRGBA.DarkGray;
		} else if (str.equals("gray")) {
			return ColorRGBA.Gray;
		} else if (str.equals("green")) {
			return ColorRGBA.Green;
		} else if (str.equals("lightgray")) {
			return ColorRGBA.LightGray;
		} else if (str.equals("magenta")) {
			return ColorRGBA.Magenta;
		} else if (str.equals("orange")) {
			return ColorRGBA.Orange;
		} else if (str.equals("pink")) {
			return ColorRGBA.Pink;
		} else if (str.equals("red")) {
			return ColorRGBA.Red;
		} else if (str.equals("white")) {
			return ColorRGBA.White;
		} else if (str.equals("yellow")) {
			return ColorRGBA.Yellow;
		} else {
			Pattern pattern = Pattern.compile("^\\s*#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})\\s*$");
			Matcher m = pattern.matcher(str);
			if (m.find()) {
				int r = Integer.parseInt(m.group(1), 16);
				int g = Integer.parseInt(m.group(2), 16);
				int b = Integer.parseInt(m.group(3), 16);
				ColorRGBA color = new ColorRGBA();
				color.fromIntRGBA((r << 24) + (g << 16) + (b << 8) + 0xff);
				return color;
			}
			throw new IllegalArgumentException("could not parse '" + str + "'");
		}
	}

	public void dropRandomBlock() {
		final ColorRGBA[] colors = new ColorRGBA[] { ColorRGBA.Red,
				ColorRGBA.Blue, ColorRGBA.Yellow, ColorRGBA.Green,
				ColorRGBA.Brown, ColorRGBA.Cyan, ColorRGBA.Magenta,
				ColorRGBA.Orange };
		Spatial s = factory.makeBlock(getUniqueId("largeblock"), 1.5f, 1.5f, 1.5f,
				colors[random.nextInt(colors.length)]);
		s.setLocalTranslation(
				(random.nextFloat() * 2 - 1) * (tableWidth / 2),
				10,
				(random.nextFloat() * 2 - 1) * (tableDepth / 2));
		s.setLocalRotation(new Quaternion().fromAngleAxis(
				FastMath.HALF_PI * random.nextFloat(),
				Vector3f.UNIT_XYZ));
		inventory.addItem(s, 1);
	}

	public void dropRandomStackOfBlocks(int blockCount) {
		final Vector3f BOX_SIZE = new Vector3f(1, 1, 1);
        final ColorRGBA[] colors = new ColorRGBA[] {
            ColorRGBA.Red, ColorRGBA.Blue, ColorRGBA.Yellow,
            ColorRGBA.Green, ColorRGBA.Brown, ColorRGBA.Cyan,
            ColorRGBA.Magenta, ColorRGBA.Orange};
        Vector3f pos = new Vector3f(
        		(random.nextFloat() * 2 - 1) * (tableWidth / 2),
        		BOX_SIZE.y / 2,
        		(random.nextFloat() * 2 - 1) * (tableDepth / 2));
        Quaternion rot = new Quaternion().fromAngleAxis(
        		FastMath.HALF_PI * random.nextFloat(), Vector3f.UNIT_Y);
        for (int i = 0; i < blockCount; ++i) {
            Spatial s = factory.makeBlock(getUniqueId("smallblock"),
                    BOX_SIZE.x, BOX_SIZE.y, BOX_SIZE.z,
                    colors[random.nextInt(colors.length)]);
            s.setLocalTranslation(pos);
            s.setLocalRotation(rot);
            inventory.addItem(s, 1);

            pos.y += BOX_SIZE.y;
        }
	}

	public void dropRandomBoxContainer() {
		Spatial boxContainer = factory.makeBoxContainer(getUniqueId("container"), 5, 3, 5,
				0.5f, ColorRGBA.Gray);
		boxContainer.setLocalTranslation((random.nextFloat() * 2 - 1) * (tableWidth / 2),
				10,
				(random.nextFloat() * 2 - 1) * (tableDepth / 2));
		boxContainer.setLocalRotation(new Quaternion().fromAngleAxis(
				FastMath.HALF_PI * random.nextFloat(), Vector3f.UNIT_XYZ));
		inventory.addItem(boxContainer, 3);
	}

	private boolean isValidId(String id) {
		return id != null && !id.isEmpty() && !uniqueIds.contains(id);
	}

	private String getUniqueId(String prefix) {
		if (prefix == null || prefix.isEmpty()) {
			prefix = "obj";
		}
		String id = prefix;
		while (!isValidId(id)) {
			id = prefix + "#" + (idSN++);
		}
		uniqueIds.add(id);
		return id;
	}

	public void initKeys(InputManager inputManager) {
        inputManager.addMapping(name + "MakeBlock", new KeyTrigger(KeyInput.KEY_B));
        inputManager.addMapping(name + "MakeStack", new KeyTrigger(KeyInput.KEY_N));
        inputManager.addMapping(name + "ClearTable", new KeyTrigger(KeyInput.KEY_C));

        inputManager.addListener(this, "shiftKey");
        
        inputManager.addListener(this, name + "MakeBlock");
        inputManager.addListener(this, name + "MakeStack");
        inputManager.addListener(this, name + "ClearTable");
	}

	@Override
	public void onAction(String eName, boolean isPressed, float tpf) {
		if (!enabled) {
			return;
		}
		if (eName.equals("shiftKey")) {
		    shiftKey  = isPressed;
		} else if (eName.equals(name + "MakeBlock")) {
            if (!isPressed) {
            	dropRandomBlock();
            }
        } else if (eName.equals(name + "MakeStack")) {
            if (!isPressed) {
            	dropRandomStackOfBlocks(5);
            }
        } else if (eName.equals(name + "ClearTable")) {
        	if (!isPressed && !shiftKey) {
        		inventory.removeAllFreeItems();
        	}
        }
	}
}
