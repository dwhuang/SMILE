package tabletop2;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
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

import tabletop2.util.MyRigidBodyControl;
import tabletop2.util.MySliderJoint;

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
			processIncludeElements(doc);
			processDefElements(doc);
			processInstanceElements(doc, doc.getDocumentElement(), new HashMap<String, String>());
            writeXmlToFile(doc, "tablesetup/debug.xml");
	        doc = validateXmlTree(doc, true);
			processXmlTree(doc);
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
			String msg = "schema error: " + SCHEMA_DEEP_FNAME;
			logger.log(Level.WARNING, msg, e1);
			showMessageDialog(msg + ": " + e1.getMessage(), 400);
			return doc;
		}
		// validate
		Validator validator = schema.newValidator();
		DOMResult res = new DOMResult();
		try {
			validator.validate(new DOMSource(doc), res);
		} catch (SAXException | IOException e1) {
			String msg = "schema validation error";
			logger.log(Level.WARNING, msg, e1);
			showMessageDialog(msg + ": " + e1.getMessage(), 400);
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
			String msg = "parse error: " + fname;
			logger.log(Level.WARNING, msg, e1);
			showMessageDialog(msg + ": " + e1.getMessage(), 400);
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
			String msg = "cannot parse " + fname;
			logger.log(Level.WARNING, msg, e);
			showMessageDialog(msg + ": " + e.getMessage(), 400);
			return null;
		} catch (IOException e) {
			String msg = "cannot read from " + fname;
			logger.log(Level.WARNING, msg, e);
			showMessageDialog(msg + ": " + e.getMessage(), 400);
			return null;
		} catch (RuntimeException e) {
			String msg = "an error occurs in " + fname;
			logger.log(Level.WARNING, msg, e);
			showMessageDialog(msg + ": " + e.getMessage(), 400);
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
    	            String msg = "Duplicated definition detected: " + name + " (overwrite)";
    	            logger.log(Level.WARNING, msg);
    	            showMessageDialog(msg, 400);
    	            defs.remove(name);
    	            defVars.remove(name);
    	        }
    	        // get default variable values in def
                HashMap<String, String> vars = new HashMap<>();
    	        for (org.w3c.dom.Node defChild = child.getFirstChild(); defChild != null;) {
    	            org.w3c.dom.Node nextDefChild = defChild.getNextSibling();
    	            if (defChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
    	                    && defChild.getNodeName().equals("var")) {
    	                vars.put(((Element) defChild).getAttribute("name"),
    	                        ((Element) defChild).getAttribute("value"));
    	                child.removeChild(defChild);
    	            }
    	            defChild = nextDefChild;
    	        }
    	        defs.put(name, (Element) child);
                defVars.put(name, vars);
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
    private void processInstanceElements(Document doc, Element root, Map<String, String> vars) {
        // substitute variable values
        performVariableSubst(root, vars);
        if (root.getNodeName().equals("instance")) {
            performInstanceExpansion(doc, root, vars);
        } else if (root.getNodeName().equals("def")) {
            String msg = "Element &lt;def&gt; is not allowed here (removed)";
            logger.log(Level.WARNING, msg);
            showMessageDialog(msg, 400);
            org.w3c.dom.Node parent = root.getParentNode();
            if (parent.getNodeType() != org.w3c.dom.Node.DOCUMENT_NODE) {
                parent.removeChild(root);
            }
        } else {
            for (org.w3c.dom.Node child = root.getFirstChild(); child != null;) {
                org.w3c.dom.Node nextChild = child.getNextSibling();
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    processInstanceElements(doc, (Element) child, vars);
                }
                child = nextChild;
            }
        }
    }
    
    private void performInstanceExpansion(Document doc, Element elm, Map<String, String> vars) {
        // look up def
        String defName = elm.getAttribute("def");
        DocumentFragment frag = doc.createDocumentFragment();
        if (!defs.containsKey(defName)) {
            String msg = "Definition not found: " + defName + " (ignored)";
            logger.log(Level.WARNING, msg);
            showMessageDialog(msg, 400);
        } else {
            // make a copy of def
            Element def = defs.get(defName);
            for (org.w3c.dom.Node defChild = def.getFirstChild(); defChild != null;
                    defChild = defChild.getNextSibling()) {
                frag.appendChild(defChild.cloneNode(true));
            }
            // substitute for variable definitions under <instance>
            for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && child.getNodeName().equals("var")) {
                    performVariableSubst((Element) child, vars);
                }
            }
            // add <var> under <def> if the same name does not exist
            Map<String, String> defv = defVars.get(defName);
            for (String k : defv.keySet()) {
                if (!vars.containsKey(k)) {
                    vars.put(k, defv.get(k));
                }
            }
            // get variable values
            for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && child.getNodeName().equals("var")) {
                    vars.put(((Element) child).getAttribute("name"),
                            ((Element) child).getAttribute("value"));
                }
            }
            // recursively process those new cloned nodes in frag
            for (org.w3c.dom.Node dfChild = frag.getFirstChild(); dfChild != null;
                    dfChild = dfChild.getNextSibling()) {
                if (dfChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    processInstanceElements(doc, (Element) dfChild, new HashMap<>(vars));
                }
            }
        }
        elm.getParentNode().insertBefore(frag, elm);
        elm.getParentNode().removeChild(elm);
    }
    
    private void performVariableSubst(Element elm, Map<String, String> vars) {
        NamedNodeMap attrs = elm.getAttributes();
        for (int i = 0; i < attrs.getLength(); ++i) {
            org.w3c.dom.Node attr = attrs.item(i);
            Pattern pat = Pattern.compile("\\$(.*?)\\$");
            Matcher mat = pat.matcher(attr.getNodeValue());
            //System.out.println("process " + attr.getNodeValue());
            StringBuffer buf = new StringBuffer();
            while (mat.find()) {
                try {
                    float ans = performVarArith(new ArithState(mat.group(1)), vars);
                    mat.appendReplacement(buf, "" + ans);
                } catch (RuntimeException e) {
                    if (vars.containsKey(mat.group(1))) {
                        mat.appendReplacement(buf, vars.get(mat.group(1)));
                    } else {
                        String msg = "invalid expression: " + mat.group(1);
                        logger.log(Level.WARNING, msg, e);
                        showMessageDialog(msg, 400);
                        mat.appendReplacement(buf, mat.group());
                    }
                }
            }
            mat.appendTail(buf);
            attr.setNodeValue(buf.toString());
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
                return Float.parseFloat(vars.get(buf.toString()));
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
			} else if (elm.getNodeName().equals("sphere")) {
				processSphereElement(elm, true);
			} else if (elm.getNodeName().equals("box")) {
				processBoxElement(elm, true);
			} else if (elm.getNodeName().equals("custom")) {
				processCustomElement(elm, true);
			} else if (elm.getNodeName().equals("composite")) {
				processCompositeElement(elm, true, null);
			} else if (elm.getNodeName().equals("chain")) {
				processChainElement(elm);
			} else if (elm.getNodeName().equals("dock")) {
				processDockElement(elm);
			} else if (elm.getNodeName().equals("sliderJoint")) {
				processSliderJointElement(elm);
			} else {
				logger.log(Level.WARNING, "skipping unknown element " + elm.getNodeName());
			}
		}
		inventory.resolveStateControlDownstreamIds();
	}

	private void showMessageDialog(String msg, int dialogWidth) {
		JOptionPane.showMessageDialog(null, "<html><body width='" + dialogWidth + "'>" + msg + "</body></html>");
	}

//	private List<Element> getElementList(NodeList nodeList) {
//		List<Element> ret = new LinkedList<>();
//		for (int i = 0; i < nodeList.getLength(); ++i) {
//			if (nodeList.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
//				ret.add((Element) nodeList.item(i));
//			}
//		}
//		return ret;
//	}
	
	private void makeTable() {
		// make table
		tableSpatial = factory.makeBigBlock(name, tableWidth, TABLE_HEIGHT, tableDepth, ColorRGBA.White, 4);
		tableSpatial.setLocalTranslation(0, -TABLE_HEIGHT / 2, 0);
		MyRigidBodyControl rbc = new MyRigidBodyControl(0);
		tableSpatial.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
		rootNode.attachChild(tableSpatial);
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
			} else if (childElm.getNodeName().equals("sphere")) {
				obj = processSphereElement(childElm, true);
			} else if (childElm.getNodeName().equals("box")) {
				obj = processBoxElement(childElm, true);
			} else if (childElm.getNodeName().equals("custom")) {
				obj = processCustomElement(childElm, true);
			} else if (childElm.getNodeName().equals("composite")) {
				obj = processCompositeElement(childElm, true, null);
			}
			if (obj != null) {
				if (k >= 2) {
					String msg = "sliderJoint " + e.getAttribute("id")
							+ " contains more than two objects: " + obj.getName() + " (ignored)";
					logger.log(Level.WARNING, msg);
					showMessageDialog(msg, 400);
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
			inventory.addSliderJoint(objs[0], objs[1],
					obj1Trans.getTranslation().negate(),
					obj2Trans.getTranslation().negate(),
					obj1Trans.getRotation().toRotationMatrix().transpose(),
					obj2Trans.getRotation().toRotationMatrix().transpose(),
					min, max, collision);
		}
	}

	private Spatial processBlockElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		if (isWhole) {
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

	        s.setUserData("obj_shape", "block");
	        s.setUserData("obj_xspan", xspan);
	        s.setUserData("obj_zspan", yspan);
	        s.setUserData("obj_yspan", zspan);
	        s.setUserData("obj_color", color);
		}

		return s;
	}

	private Spatial processCylinderElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		if (isWhole) {
			id = getUniqueId(id);
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float radius = Float.parseFloat(elm.getAttribute("radius"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));

		Spatial s = factory.makeCylinder(id, radius, zspan, color);
		s.setLocalTranslation(location);
		s.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(s, mass);

			s.setUserData("obj_shape", "cylinder");
	        s.setUserData("obj_radius", radius);
	        s.setUserData("obj_yspan", zspan);
	        s.setUserData("obj_color", color);
		}

		return s;
	}

	private Spatial processSphereElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		if (isWhole) {
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

	        s.setUserData("obj_shape", "cylinder");
	        s.setUserData("obj_radius", radius);
	        s.setUserData("obj_color", color);
		}

		return s;
	}

	private Spatial processBoxElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		if (isWhole) {
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

			s.setUserData("obj_shape", "box");
	        s.setUserData("obj_xspan", xspan);
	        s.setUserData("obj_zspan", yspan);
	        s.setUserData("obj_yspan", zspan);
	        s.setUserData("obj_color", color);
	        s.setUserData("obj_thickness", thickness);
		}

		return s;
	}

	private Spatial processCustomElement(Element elm, boolean isWhole) {
		String id = getUniqueId(elm.getAttribute("id"));
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

			s.setUserData("obj_shape", "custom");
			s.setUserData("obj_color", color);
			s.setUserData("obj_scale", scale);
		}

		return s;
	}

	private Spatial processCompositeElement(Element elm, boolean isWhole,
			Map<Spatial, StateControl> stateControlMap) {
		String id = elm.getAttribute("id");
		if (isWhole) {
			id = getUniqueId(id);
			stateControlMap = new HashMap<>();
		}
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));

		Node node = new Node(id);
		node.setLocalTranslation(location);
		node.setLocalRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));

		for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
			Element childElm = (Element) child;
			if (childElm.getNodeName().equals("block")) {
				node.attachChild(processBlockElement(childElm, false));
			} else if (childElm.getNodeName().equals("cylinder")) {
				node.attachChild(processCylinderElement(childElm, false));
			} else if (childElm.getNodeName().equals("sphere")) {
				node.attachChild(processSphereElement(childElm, false));
			} else if (childElm.getNodeName().equals("box")) {
				node.attachChild(processBoxElement(childElm, false));
			} else if (childElm.getNodeName().equals("custom")) {
				node.attachChild(processCustomElement(childElm, false));
			} else if (childElm.getNodeName().equals("composite")) {
				node.attachChild(processCompositeElement(childElm, false, stateControlMap));
			} else if (childElm.getNodeName().equals("toggleSwitch")) {
				node.attachChild(processToggleSwitchElement(childElm, stateControlMap));
			} else if (childElm.getNodeName().equals("indicatorSet")) {
				node.attachChild(processIndicatorSetElement(childElm, stateControlMap));
			} else {
				logger.log(Level.WARNING, "skipping unknown composite element " + childElm.getNodeName());
			}
		}

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(node, mass);
			// register state controls
			for (Map.Entry<Spatial, StateControl> e : stateControlMap.entrySet()) {
				inventory.registerStateControl(e.getKey(), e.getValue());
			}

			node.setUserData("obj_shape", "composite");
		}

		return node;
	}

	private Spatial processToggleSwitchElement(Element elm, Map<Spatial, StateControl> stateControlMap) {
		String id = getUniqueId(elm.getAttribute("id"));
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

		stateControlMap.put(s, c);

		return s;
	}

	private Spatial processIndicatorSetElement(Element elm, Map<Spatial, StateControl> stateControlMap) {
		String id = getUniqueId(elm.getAttribute("id"));
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
			Spatial light = factory.makeCylinder(id + "-light" + i, lightRadius, lightZspan, ColorRGBA.Black);
			light.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_X));
			light.setLocalTranslation(lightPos);
			lightPos.x += lightIntv;
			s.attachChild(light);
		}

		// get <downstream> and <state> elements
		LinkedList<String> dsIds = new LinkedList<>();
		LinkedList<ColorRGBA[]> lightStates = new LinkedList<>();
		for (org.w3c.dom.Node child = elm.getFirstChild(); child != null; child = child.getNextSibling()) {
		    if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
		        continue;
		    }
			if (child.getNodeName().equals("downstream")) {
				dsIds.add(((Element) child).getAttribute("id"));
			} else if (child.getNodeName().equals("state")) {
				ColorRGBA[] ls = new ColorRGBA[numLights];
				// get <light> elements under <state>
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

		IndicatorSetControl c = new IndicatorSetControl(inventory, s, initState, lightStates);

		for (String dsId : dsIds) {
			c.addDownstreamId(dsId);
		}

		stateControlMap.put(s, c);

		return s;

//		// button 1
//		Spatial b1 = factory.makeBlock(id + "-b1", btxspan, yspan, zspan, color);
//		b1.setLocalTranslation(-btxspan / 2, yspan / 2, 0);
//		s.attachChild(b1);
//		// button 2
//		Spatial b2 = factory.makeBlock(id + "-b2", btxspan, yspan, zspan, color);
//		b2.setLocalRotation(new Quaternion().fromAngles(0, 0, angle));
//		float hypoLen = FastMath.sqr(btxspan * btxspan + yspan * yspan); // hypotenues
//		float cosine = (btxspan / hypoLen) * FastMath.cos(angle) - (yspan / hypoLen) * FastMath.sin(angle);
//		float sine = (yspan / hypoLen) * FastMath.cos(angle) + (btxspan / hypoLen) * FastMath.sin(angle);
//		b2.setLocalTranslation(hypoLen / 2f * cosine, hypoLen / 2f * sine, 0);
//		s.attachChild(b2);
//
//		ToggleSwitchControl c = new ToggleSwitchControl(inventory, s, angle, leftPressed, numStates, initState);
//		stateControls.put(s, c);
//
//		return s;
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
		startNode.setUserData("obj_shape", "chain-endpoint");
		startNode.setUserData("obj_xspan", endNodesSize.x);
		startNode.setUserData("obj_yspan", endNodesSize.z);
		startNode.setUserData("obj_zspan", endNodesSize.y);

		// make end node (static)
		id = getUniqueId(groupId + "-end");
		Spatial endNode = factory.makeBlock(id, endNodesSize.x, endNodesSize.y, endNodesSize.z,
				ColorRGBA.White);
		endNode.setLocalTranslation(end);
		endNode.setLocalRotation(rotStartEndDir);
		inventory.addItem(endNode, 0);
		startNode.setUserData("obj_shape", "chain-endpoint");
		startNode.setUserData("obj_xspan", endNodesSize.x);
		startNode.setUserData("obj_yspan", endNodesSize.z);
		startNode.setUserData("obj_zspan", endNodesSize.y);

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
			link.setUserData("obj_shape", "chain-link");
			link.setUserData("obj_xspan", linkXspan);
			link.setUserData("obj_yspan", linkZspan);
			link.setUserData("obj_zspan", linkYspan);
			link.setUserData("obj_color", color);

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

	private void processDockElement(Element elm) {
		final int NUM_MODULES = 4;
		String groupId = getUniqueId(elm.getAttribute("id"));
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float yspan = Float.parseFloat(elm.getAttribute("zspan"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));
		float xThickness = Float.parseFloat(elm.getAttribute("xthickness"));
		float yThickness = Float.parseFloat(elm.getAttribute("zthickness"));
		float zThickness = Float.parseFloat(elm.getAttribute("ythickness"));
		float handleXspan = Float.parseFloat(elm.getAttribute("handleXspan"));
		float handleYspan = Float.parseFloat(elm.getAttribute("handleZspan"));
		float handleZspan = Float.parseFloat(elm.getAttribute("handleYspan"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		ColorRGBA caseColor = parseColor(elm.getAttribute("caseColor"));
		ColorRGBA handleColor = parseColor(elm.getAttribute("handleColor"));
		float mass = Float.parseFloat(elm.getAttribute("mass"));
		float caseMass = Float.parseFloat(elm.getAttribute("caseMass"));
		int[] switchStates = new int[NUM_MODULES];
		int[] lightStates = new int[NUM_MODULES];
		for (int i = 0; i < NUM_MODULES; ++i) {
			switchStates[i] = Integer.parseInt(elm.getAttribute("switchState" + (i + 1)));
			lightStates[i] = Integer.parseInt(elm.getAttribute("lightState" + (i + 1)));
		}

		Transform tf = new Transform();
		tf.setTranslation(location);
		tf.setRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD,
				rotation.y * FastMath.DEG_TO_RAD,
				rotation.z * FastMath.DEG_TO_RAD));
		String id;
		// case
		id = getUniqueId(groupId + "-case");
		Spatial caseShape = factory.makeBoxContainer(id + "-shape", yspan, xspan - xThickness, zspan,
				yThickness, xThickness, zThickness, caseColor);
		caseShape.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Z));
		caseShape.setLocalTranslation(-xThickness / 2, 0, 0);
		Node caseNode = new Node(id);
		caseNode.setLocalTransform(tf);
		caseNode.attachChild(caseShape);
		inventory.addItem(caseNode, caseMass);

		caseNode.setUserData("obj_shape", "dock-case");
		caseNode.setUserData("obj_width", caseShape.getUserData("obj_width"));
		caseNode.setUserData("obj_height", caseShape.getUserData("obj_height"));
		caseNode.setUserData("obj_depth", caseShape.getUserData("obj_depth"));
		caseNode.setUserData("obj_color", caseShape.getUserData("obj_color"));
		caseNode.setUserData("obj_xthickness", caseShape.getUserData("obj_xthickness"));
		caseNode.setUserData("obj_ythickness", caseShape.getUserData("obj_ythickness"));
		caseNode.setUserData("obj_zthickness", caseShape.getUserData("obj_zthickness"));


		// (component sizes and locations)
		float wBase = xspan - 2 * xThickness;
		float hPanel = 0.15f;
		float hBase = yspan - 2 * yThickness - 0.5f - hPanel;
		float dBase = zspan - 2 * zThickness;
		float thSlot = 0.087f;
		float wHole = 1.375f;
		float hHole = 0.648f;
		float dHole = 0.625f;
		float wHoleToPanel = 0.5f;
		float wPanel = wBase * 0.4048f;
		float wSwitch = 1.15f;
		float hSwitch = 0.05f;
		float dSwitch = 0.45f;
		float wPanelToSwitch = wBase * 0.0071f;
		float wSwitchToIndicator = wBase * 0.0119f;
		float rIndicator = dSwitch / 3;
		float hIndicator = hSwitch;
		// dock
		Node dockNode = new Node(groupId + "-body");
		dockNode.setLocalTransform(tf);
		Node dockOffsetNode = new Node(groupId + "-bodyOffset");
		dockOffsetNode.setLocalTranslation(0, -(yspan - yThickness * 2 - hBase) / 2, 0);
		dockNode.attachChild(dockOffsetNode);

		// dock base
		String baseId = getUniqueId(groupId + "-dock-base");
		Node base = new Node(id);
		// dock base back
		id = getUniqueId(baseId + "-baseB");
		float wBaseB = wBase - (wPanel + wHoleToPanel + wHole);
		Spatial baseB = factory.makeBlock(id, wBaseB, hBase, dBase, color);
		baseB.setLocalTranslation(-wBase / 2 + wBaseB / 2, 0, 0);
		base.attachChild(baseB);
		// dock base front
		id = getUniqueId(baseId + "-baseF");
		float wBaseF = wPanel + wHoleToPanel;
		Spatial baseF = factory.makeBlock(id, wBaseF, hBase, dBase, color);
		baseF.setLocalTranslation(wBase / 2 - wBaseF / 2, 0, 0);
		base.attachChild(baseF);
		// dock base near wall
		id = getUniqueId(baseId + "-baseNW");
		float dBaseNW = (dBase - dHole * 4) * 0.25f;
		Spatial baseNW = factory.makeBlock(id, wHole, hBase, dBaseNW, color);
		baseNW.setLocalTranslation(-wBase / 2 + wBaseB + wHole / 2, 0, -dBase / 2 + dBaseNW / 2);
		base.attachChild(baseNW);
		// dock base far wall
		id = getUniqueId(baseId + "-baseFW");
		Spatial baseFW = factory.makeBlock(id, wHole, hBase, dBaseNW, color);
		baseFW.setLocalTranslation(-wBase / 2 + wBaseB + wHole / 2, 0, dBase / 2 - dBaseNW / 2);
		base.attachChild(baseFW);
		// dock base divider wall 1
		id = getUniqueId(baseId + "-baseDW1");
		float dBaseDW = (dBase - dHole * 4) * 0.5f / 3;
		Spatial baseDW1 = factory.makeBlock(id, wHole, hBase, dBaseDW, color);
		baseDW1.setLocalTranslation(-wBase / 2 + wBaseB + wHole / 2, 0, -dBase / 2 + dBaseNW + dHole + dBaseDW / 2);
		base.attachChild(baseDW1);
		// dock base divider wall 2
		id = getUniqueId(baseId + "-baseDW2");
		Spatial baseDW2 = factory.makeBlock(id, wHole, hBase, dBaseDW, color);
		baseDW2.setLocalTranslation(-wBase / 2 + wBaseB + wHole / 2, 0,
				-dBase / 2 + dBaseNW + dHole * 2 + dBaseDW + dBaseDW / 2);
		base.attachChild(baseDW2);
		// dock base divider wall 3
		id = getUniqueId(baseId + "-baseDW3");
		Spatial baseDW3 = factory.makeBlock(id, wHole, hBase, dBaseDW, color);
		baseDW3.setLocalTranslation(-wBase / 2 + wBaseB + wHole / 2, 0,
				-dBase / 2 + dBaseNW + dHole * 3 + dBaseDW * 2 + dBaseDW / 2);
		base.attachChild(baseDW3);
		// slots
		float slotX = -wBase / 2 + wBaseB + wHole / 2;
		float slotY = hBase / 2 - (hHole + 0.5f * thSlot) / 2;
		float[] slotZ = new float[NUM_MODULES];
		for (int i = 0; i < NUM_MODULES; ++i) {
			id = getUniqueId(baseId + "-slot" + (i + 1));
			Spatial slot = factory.makeBoxContainer(id, wHole, hHole + thSlot, dHole, thSlot, ColorRGBA.DarkGray);
			slotZ[i] = -dBase / 2 + dBaseNW + dHole / 2 + (dHole + dBaseDW) * i;
			slot.setLocalTranslation(slotX, slotY, slotZ[i]);
			base.attachChild(slot);
			id = getUniqueId(groupId + "-assemblyPoint" + (i + 1));
			Node att = new Node(id);
			att.setLocalTranslation(slotX, slotY - hHole / 2, slotZ[i]);
			att.setLocalRotation(new Quaternion().fromAngles(-FastMath.HALF_PI, 0, 0));
			att.setUserData("assembly", "cartridgeSlot");
			att.setUserData("assemblyEnd", 0);
			base.attachChild(att);
		}
		// annotate...
		dockNode.setUserData("obj_shape", "dock-body");
		dockNode.setUserData("obj_color", color);
		dockNode.setUserData("obj_width", xspan);
		dockNode.setUserData("obj_height", yspan);
		dockNode.setUserData("obj_depth", zspan);
		dockNode.setUserData("obj_baseWidth", wBase);
		dockNode.setUserData("obj_baseHeight", hBase);
		dockNode.setUserData("obj_baseDepth", dBase);
		dockNode.setUserData("obj_slotWidth", wHole - thSlot * 2);
		dockNode.setUserData("obj_slotHeight", hHole);
		dockNode.setUserData("obj_slotDepth", dHole - thSlot * 2);
		for (int i = 0; i < NUM_MODULES; ++i) {
			dockNode.setUserData("obj_slot" + (i + 1) + "OffsetX", slotX);
			dockNode.setUserData("obj_slot" + (i + 1) + "OffsetY", slotZ[i]);
			dockNode.setUserData("obj_slot" + (i + 1) + "OffsetZ", slotY);
		}

		// panel
		String panelId = getUniqueId(groupId + "-dock-panel");
		Node panel = new Node(id);
		float panelX = wBase / 2 - wPanel / 2;
		float panelY = hBase / 2 + hPanel / 2;
		panel.setLocalTranslation(panelX, panelY, 0);
		// panel cover
		id = getUniqueId(panelId + "-panelCover");
		Spatial panelCover = factory.makeBlock(id, wPanel, hPanel, dBase, color);
		panel.attachChild(panelCover);
		// switches
		Node[] switchButton = new Node[NUM_MODULES];
		float switchX = -wPanel / 2 + wPanelToSwitch + wSwitch / 2;
		float switchY = hPanel / 2 + hSwitch / 2;
		for (int i = 0; i < NUM_MODULES; ++i) {
			id = getUniqueId(panelId + "-switch" + (i + 1));
			Node switchNode = new Node(id);
			switchNode.setLocalTranslation(switchX, switchY, slotZ[i]);
			panel.attachChild(switchNode);
			// (base)
			id = getUniqueId(panelId + "-switch" + (i + 1) + "-base");
			Spatial switchBase = factory.makeBlock(id, wSwitch, hSwitch, dSwitch, ColorRGBA.White);
			switchNode.attachChild(switchBase);
			// (button)
			switchButton[i] = new Node(groupId + "-switch" + (i + 1));
			switchButton[i].setLocalTranslation(0, -0.1f, 0);
			Spatial b1 = factory.makeBlock("b1", 0.6f, 0.3f, 0.3f, ColorRGBA.DarkGray);
			b1.setLocalTranslation(-0.25f, 0, 0);
			b1.setLocalRotation(new Quaternion().fromAngles(0, 0, -3 * FastMath.DEG_TO_RAD));
			Spatial b2 = factory.makeBlock("b2", 0.6f, 0.3f, 0.3f, ColorRGBA.DarkGray);
			b2.setLocalTranslation(0.25f, 0, 0);
			b2.setLocalRotation(new Quaternion().fromAngles(0, 0, 3 * FastMath.DEG_TO_RAD));
			switchButton[i].attachChild(b1);
			switchButton[i].attachChild(b2);
			switchNode.attachChild(switchButton[i]);
		}
		// annotate...
		dockNode.setUserData("obj_switchWidth", 1.2f);
		dockNode.setUserData("obj_switchDepth", 0.3f);
		for (int i = 0; i < NUM_MODULES; ++i) {
			dockNode.setUserData("obj_switch" + (i + 1) + "OffsetX", panelX + switchX);
			dockNode.setUserData("obj_switch" + (i + 1) + "OffsetY", slotZ[i]);
			dockNode.setUserData("obj_switch" + (i + 1) + "OffsetZ", panelY + switchY);
		}

		// indicator lights
		Node[] indicatorLights = new Node[NUM_MODULES];
		float lightX = -wPanel / 2 + wPanelToSwitch + wSwitch + wSwitchToIndicator + rIndicator;
		float lightY = hPanel / 2 + hIndicator / 2;
		for (int i = 0; i < NUM_MODULES; ++i) {
			id = getUniqueId(panelId + "-indicator" + (i + 1));
			Node indicator = new Node(id);
			indicator.setLocalTranslation(lightX, lightY, slotZ[i]);
			panel.attachChild(indicator);
			// (base)
			id = getUniqueId(panelId + "-indicator" + (i + 1) + "-base");
			Spatial indicatorBase = factory.makeCylinder(id, rIndicator, hIndicator, ColorRGBA.White);
			Quaternion rotX90 = new Quaternion().fromAngles(FastMath.HALF_PI, 0, 0);
			indicatorBase.setLocalRotation(rotX90);
			indicator.attachChild(indicatorBase);
			// (LEDs)
			id = getUniqueId(groupId + "-light" + (i + 1));
			indicatorLights[i] = new Node(id);
			indicator.attachChild(indicatorLights[i]);
			// (green LED)
			Spatial greenLight = factory.makeCylinder("green", rIndicator / 5, 0.005f, ColorRGBA.Green);
			greenLight.setLocalTranslation(rIndicator / 2, hIndicator / 2 + 0.005f / 2, 0);
			greenLight.setLocalRotation(rotX90);
			indicatorLights[i].attachChild(greenLight);
			// (red LED)
			Spatial redLight = factory.makeCylinder("red", rIndicator / 5, 0.01f, ColorRGBA.Red);
			redLight.setLocalTranslation(-rIndicator / 2, hIndicator / 2 + 0.005f / 2, 0);
			redLight.setLocalRotation(rotX90);
			indicatorLights[i].attachChild(redLight);
		}
		// annotate...
		dockNode.setUserData("obj_lightRadius", rIndicator);
		for (int i = 0; i < NUM_MODULES; ++i) {
			dockNode.setUserData("obj_light" + (i + 1) + "OffsetX", panelX + lightX);
			dockNode.setUserData("obj_light" + (i + 1) + "OffsetY", slotZ[i]);
			dockNode.setUserData("obj_light" + (i + 1) + "OffsetZ", panelY + lightY);
		}

		dockOffsetNode.attachChild(base);
		dockOffsetNode.attachChild(panel);

		// dock front
		id = getUniqueId(groupId + "-dock-front");
		Spatial front = factory.makeBlock(id, xThickness, yspan, zspan, color);
		front.setLocalTranslation(wBase / 2 + xThickness / 2, 0, 0);
		dockNode.attachChild(front);
		// dock handle
		id = getUniqueId(groupId + "-dock-handle");
		Spatial handle = factory.makeBlock(id, handleXspan, handleYspan, handleZspan, handleColor);
		float handleY = yspan * 0.3583f;
		float handleX = wBase / 2 + xThickness + handleXspan / 2;
		handle.setLocalTranslation(handleX, handleY, 0);
		dockNode.attachChild(handle);
		dockNode.setUserData("obj_handleWidth", handleXspan);
		dockNode.setUserData("obj_handleHeight", handleYspan);
		dockNode.setUserData("obj_handleDepth", handleZspan);
		dockNode.setUserData("obj_handleOffsetX", handleX);
		dockNode.setUserData("obj_handleOffsetY", 0);
		dockNode.setUserData("obj_handleOffsetZ", handleY);
		dockNode.setUserData("obj_handleColor", handleColor);

		inventory.addItem(dockNode, mass);
//		for (int i = 0; i < NUM_MODULES; ++i) {
//			// LED function
//			IndicatorSetControl ilFunc = new IndicatorSetControl(inventory, indicatorLights[i]);
//			inventory.registerStateControl(indicatorLights[i], ilFunc);
//			// switch function
//			ToggleSwitchControl sFunc = new ToggleSwitchControl(inventory, switchButton[i], ilFunc);
//			inventory.registerStateControl(switchButton[i], sFunc);
//			// init states
//			ilFunc.setState(lightStates[i]);
//			sFunc.setState(switchStates[i]);
//		}

		// sliding joint
		MySliderJoint joint = inventory.addSliderJoint(dockNode, caseNode,
				Vector3f.ZERO, Vector3f.ZERO, null, null, 0, wBase, false);
		joint.setDampingDirLin(1);
		joint.setDampingDirAng(1);
		joint.setSoftnessOrthoLin(1);
		joint.setSoftnessOrthoAng(1);
	}

	private Vector3f parseVector3(String str) {
		Pattern pattern = Pattern.compile("^\\s*\\((\\-?\\d*(\\.\\d+)?)\\s*,\\s*(\\-?\\d*(\\.\\d+)?)\\s*,\\s*(\\-?\\d*(\\.\\d+)?)\\)\\s*$");
		Matcher m = pattern.matcher(str);
		if (m.find()) {
			float x = Float.parseFloat(m.group(1));
			float y = Float.parseFloat(m.group(5));
			float z = -Float.parseFloat(m.group(3));
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

        inputManager.addListener(this, name + "MakeBlock");
        inputManager.addListener(this, name + "MakeStack");
        inputManager.addListener(this, name + "ClearTable");
	}

	@Override
	public void onAction(String eName, boolean isPressed, float tpf) {
		if (!enabled) {
			return;
		}
    	if (eName.equals(name + "MakeBlock")) {
            if (!isPressed) {
            	dropRandomBlock();
            }
        } else if (eName.equals(name + "MakeStack")) {
            if (!isPressed) {
            	dropRandomStackOfBlocks(5);
            }
        } else if (eName.equals(name + "ClearTable")) {
        	if (!isPressed) {
        		inventory.removeAllFreeItems();
        	}
        }
	}
}
