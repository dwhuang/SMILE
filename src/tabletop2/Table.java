package tabletop2;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import tabletop2.util.MyRigidBodyControl;

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
		
		loadXml(xmlFname);
		
		// relocate the robot according to table size
    	robotLocationNode.setLocalTransform(Transform.IDENTITY);
    	robotLocationNode.setLocalTranslation(0, 2, tableDepth / 2 + 3);
    	robotLocationNode.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));		
	}
	
	private void loadXml(String xmlFname) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setValidating(true);
		dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", 
				"http://www.w3.org/2001/XMLSchema");
		DocumentBuilder db = null;
		try {
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e1) {
			String msg = "parse error: " + xmlFname;
			logger.log(Level.WARNING, msg, e1);
			JOptionPane.showMessageDialog(null, msg + ": " + e1.getMessage());
			makeTable();
			return;
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
		
		Document doc = null;
		try {
			doc = db.parse(new File(xmlFname));
		} catch (SAXException e) {
			String msg = "cannot parse " + xmlFname;
			logger.log(Level.WARNING, msg, e);
			JOptionPane.showMessageDialog(null, msg + ": " + e.getMessage());
			makeTable();
			return;
		} catch (IOException e) {
			String msg = "cannot read from " + xmlFname;
			logger.log(Level.WARNING, msg, e);
			JOptionPane.showMessageDialog(null, msg + ": " + e.getMessage());
			makeTable();
			return;
		} catch (RuntimeException e) {
			String msg = "an error occurs in " + xmlFname;
			logger.log(Level.WARNING, msg, e);
			JOptionPane.showMessageDialog(null, msg + ": " + e.getMessage());
			makeTable();
			return;
		}
		Element docRoot = doc.getDocumentElement();
		// get table size
		tableWidth = Float.parseFloat(docRoot.getAttribute("xspan"));
		tableDepth = Float.parseFloat(docRoot.getAttribute("yspan"));
		makeTable();
		
		NodeList firstLevelNodeList = docRoot.getChildNodes(); 
		for (int i = 0; i < firstLevelNodeList.getLength(); ++i) {
			org.w3c.dom.Node node = docRoot.getChildNodes().item(i);
			if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
				Element elm = (Element) node;
				if (elm.getNodeName().equals("block")) {
					processBlockElement(elm, true);
				} else if (elm.getNodeName().equals("cylinder")) {
					processCylinderElement(elm, true);
				} else if (elm.getNodeName().equals("sphere")) {
					processSphereElement(elm, true);
				} else if (elm.getNodeName().equals("box")) {
					processBoxElement(elm, true);
				} else if (elm.getNodeName().equals("composite")) {
					processCompositeElement(elm, true);
				} else if (elm.getNodeName().equals("chain")) {
					processChainElement(elm);
				} else if (elm.getNodeName().equals("lidbox")) {
					processLidBoxElement(elm);
				}
			}
		}
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
		}
		
		return s;
	}

	private Spatial processCompositeElement(Element elm, boolean isWhole) {
		String id = elm.getAttribute("id");
		if (isWhole) {
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
		NodeList children = elm.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i) {
			if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
				Element child = (Element) children.item(i);
				if (child.getNodeName().equals("block")) {
					node.attachChild(processBlockElement(child, false));
				} else if (child.getNodeName().equals("cylinder")) {
					node.attachChild(processCylinderElement(child, false));
				} else if (child.getNodeName().equals("sphere")) {
					node.attachChild(processSphereElement(child, false));
				} else if (child.getNodeName().equals("box")) {
					node.attachChild(processBoxElement(child, false));
				} else if (child.getNodeName().equals("composite")) {
					node.attachChild(processCompositeElement(child, false));
				}
			}
		}

		if (isWhole) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			inventory.addItem(node, mass);

			node.setUserData("shape", "composite");
		}
		
		return node;
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
		
		// make end node (static)
		id = getUniqueId(groupId + "-end");
		Spatial endNode = factory.makeBlock(id, endNodesSize.x, endNodesSize.y, endNodesSize.z, 
				ColorRGBA.White);
		endNode.setLocalTranslation(end);
		endNode.setLocalRotation(rotStartEndDir);
		inventory.addItem(endNode, 0);
		
		Spatial prevSpatial = startNode;
		Vector3f prevJointPt = new Vector3f(0, 0, -endNodesSize.z);
		for (int i = 0; i < linkCount; ++i) {
			// make a link
			id = getUniqueId(groupId + "-link" + i);
			Spatial link = factory.makeBlock(id, linkXspan, linkYspan, linkZspan + linkPadding * 2, color);
			link.setLocalRotation(rotStartEndDir);
			vec.interpolateLocal(start, end, (i + .5f) / linkCount);
			link.setLocalTranslation(vec);
			inventory.addItem(link, linkMass, new BoxCollisionShape(linkPhysicsSize));
			link.getControl(MyRigidBodyControl.class).setAngularDamping(1);
			
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

	private void processLidBoxElement(Element elm) {
		String groupId = getUniqueId(elm.getAttribute("id"));
		Vector3f location = parseVector3(elm.getAttribute("location"));
		Vector3f rotation = parseVector3(elm.getAttribute("rotation"));
		ColorRGBA color = parseColor(elm.getAttribute("color"));
		float xspan = Float.parseFloat(elm.getAttribute("xspan"));
		float yspan = Float.parseFloat(elm.getAttribute("zspan"));
		float zspan = Float.parseFloat(elm.getAttribute("yspan"));
		float thickness = Float.parseFloat(elm.getAttribute("thickness"));
		ColorRGBA handleColor = parseColor(elm.getAttribute("handleColor"));
		float handleXspan = Float.parseFloat(elm.getAttribute("handleXspan"));
		float handleYspan = Float.parseFloat(elm.getAttribute("handleZspan"));
		float handleZspan = Float.parseFloat(elm.getAttribute("handleYspan"));
		float handleThickness = Math.min(handleXspan, handleYspan) / 3f;

		Transform tf = new Transform();
		tf.setTranslation(location);
		tf.setRotation(new Quaternion().fromAngles(
				rotation.x * FastMath.DEG_TO_RAD, 
				rotation.y * FastMath.DEG_TO_RAD, 
				rotation.z * FastMath.DEG_TO_RAD));
		
		String id;
		id = getUniqueId(groupId + "-box");
		Spatial box = factory.makeBoxContainer(id, xspan, yspan, zspan, thickness, color);		
		box.setLocalTransform(tf);
		float mass = Float.parseFloat(elm.getAttribute("mass"));
		inventory.addItem(box, mass * 0.9f);
		
		id = getUniqueId(groupId + "-lid");
		Node lid = new Node(id); 
		Spatial lidPlate = factory.makeBlock(id + "-lidbody", xspan, thickness, zspan, color);
		lid.attachChild(lidPlate);
		Spatial lidHandle = factory.makeBoxContainer(id + "-lidhandle", handleXspan, handleYspan, handleZspan, 
				handleThickness, handleColor);
		lidHandle.setLocalTranslation(0, thickness / 2 + handleYspan / 2, 0);
		lid.attachChild(lidHandle);		
		lid.setLocalTranslation(0, yspan / 2 + thickness / 2, 0);
		lid.setLocalTransform(lid.getLocalTransform().combineWithParent(tf));
		inventory.addItem(lid, mass * 0.1f);
		
		lid.setUserData("shape", "lid");
		lid.setUserData("width", lidPlate.getUserData("width"));
		lid.setUserData("height", lidPlate.getUserData("height"));
		lid.setUserData("depth", lidPlate.getUserData("depth"));
		lid.setUserData("handleWidth", lidHandle.getUserData("width"));
		lid.setUserData("handleHeight", lidHandle.getUserData("height"));
		lid.setUserData("handleDepth", lidHandle.getUserData("depth"));
		lid.setUserData("handleThickness", lidHandle.getUserData("thickness"));		
		
		inventory.addSliderJoint(box, lid, new Vector3f(0, yspan / 2, 0), new Vector3f(0, -thickness / 2, 0), 
				0, xspan);
//		joint.setCollisionBetweenLinkedBodys(false);
//		joint.setLowerLinLimit(0);
//		joint.setUpperLinLimit(xspan);
		
//		joint.setDampingDirLin(.001f);
//		joint.setRestitutionOrthoLin(.5f);
//		joint.setRestitutionDirLin(0);
//		joint.setPoweredLinMotor(true);
//		joint.setMaxLinMotorForce(1);
//		joint.setTargetLinMotorVelocity(-1);
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
				color.fromIntRGBA((r << 24) + (g << 16) + (b << 8));
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
