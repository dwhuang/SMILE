package tabletop2;

import java.io.File;
import java.io.IOException;
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
	
	private int randomItemSN = 0;


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
				Spatial s = null;
				if (elm.getNodeName().equals("block")) {
					s = processBlockElement(elm, true);
				} else if (elm.getNodeName().equals("cylinder")) {
					s = processCylinderElement(elm, true);
				} else if (elm.getNodeName().equals("sphere")) {
					s = processSphereElement(elm, true);
				} else if (elm.getNodeName().equals("box")) {
					s = processBoxElement(elm, true);
				} else if (elm.getNodeName().equals("composite")) {
					s = processCompositeElement(elm, true);
				} else if (elm.getNodeName().equals("chain")) {
					processChainElement(elm);
				}
				if (s != null) {
					rootNode.attachChild(s);
					inventory.addItem(s);
				}
			}
		}
	}
	
	private void makeTable() {
		// make table
		tableSpatial = factory.makeBigBlock(name, tableWidth, TABLE_HEIGHT, tableDepth, ColorRGBA.White, 4);
		tableSpatial.setLocalTranslation(0, -TABLE_HEIGHT / 2, 0);
		addPhysicsControl(tableSpatial, 0);
		rootNode.attachChild(tableSpatial);
	}
	
	private void addPhysicsControl(Spatial s, float mass) {
		MyRigidBodyControl rbc = new MyRigidBodyControl(mass);
		s.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
	}
	
	private Spatial processBlockElement(Element elm, boolean hasPhysics) {
		String id = elm.getAttribute("id");
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
		
		if (hasPhysics) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			addPhysicsControl(s, mass);
		}
		
		return s;
	}

	private Spatial processCylinderElement(Element elm, boolean hasPhysics) {
		String id = elm.getAttribute("id");
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
		
		if (hasPhysics) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			addPhysicsControl(s, mass);
		}
		
		return s;
	}

	private Spatial processSphereElement(Element elm, boolean hasPhysics) {
		String id = elm.getAttribute("id");
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

		if (hasPhysics) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			addPhysicsControl(s, mass);
		}
		
		return s;
	}

	private Spatial processBoxElement(Element elm, boolean hasPhysics) {
		String id = elm.getAttribute("id");
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
		
		if (hasPhysics) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			addPhysicsControl(s, mass);
		}
		
		return s;
	}

	private Spatial processCompositeElement(Element elm, boolean hasPhysics) {
		String id = elm.getAttribute("id");
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

		if (hasPhysics) {
			float mass = Float.parseFloat(elm.getAttribute("mass"));
			addPhysicsControl(node, mass);
		}
		
		return node;
	}

	private void processChainElement(Element elm) {
		String id = elm.getAttribute("id");
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
			logger.log(Level.INFO, "chain " + id + ": linkCount=" + linkCount);
		} else {
			if ((float) linkCount * linkZspan < dist - linkZspan * .5f) {
				throw new IllegalArgumentException("linkCount " + linkCount 
						+ " too low to connect the start and end locations");
			}
		}
		
		// start making a chain
		//		
		Vector3f vec = new Vector3f(); // temporary storage
		MyRigidBodyControl rbc;
		SixDofJoint joint;
		final Vector3f endNodesSize = new Vector3f(.1f, .1f, .1f);
		final Vector3f linkPhysicsSize = new Vector3f(linkXspan / 2, linkYspan / 2, linkZspan / 2);
		// rotate the z axis to the start->end direction
		// when walking on the links from start to end, z increases in each local model space 
		Quaternion rotStartEndDir = new Quaternion();
		rotStartEndDir.lookAt(start.subtract(end), Vector3f.UNIT_Y);

		// make start node (static)
		Spatial startNode = factory.makeBlock(id + "-start", 
				endNodesSize.x, endNodesSize.y, endNodesSize.z, ColorRGBA.White);
		startNode.setLocalTranslation(start);
		startNode.setLocalRotation(rotStartEndDir);
		rbc = new MyRigidBodyControl(0);
		startNode.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
		rootNode.attachChild(startNode);
		inventory.addItem(startNode);
		
		// make end node (static)
		Spatial endNode = factory.makeBlock(id + "-end", 
				endNodesSize.x, endNodesSize.y, endNodesSize.z, ColorRGBA.White);
		endNode.setLocalTranslation(end);
		endNode.setLocalRotation(rotStartEndDir);
		rbc = new MyRigidBodyControl(0);
		endNode.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
		rootNode.attachChild(endNode);
		inventory.addItem(endNode);
		
		MyRigidBodyControl prevRbc = startNode.getControl(MyRigidBodyControl.class);
		Vector3f prevJointPt = new Vector3f(0, 0, -endNodesSize.z);
		for (int i = 0; i < linkCount; ++i) {
			// make a link
			Spatial link = factory.makeBlock(id + "-link" + i, 
					linkXspan, linkYspan, linkZspan + linkPadding * 2, color);
			link.setLocalRotation(rotStartEndDir);
			vec.interpolateLocal(start, end, (i + .5f) / linkCount);
			link.setLocalTranslation(vec);
			rbc = new MyRigidBodyControl(new BoxCollisionShape(linkPhysicsSize), linkMass);
			link.addControl(rbc);
			rbc.setAngularDamping(1);
			bulletAppState.getPhysicsSpace().add(rbc);
			rootNode.attachChild(link);
			inventory.addItem(link);
			
			// connect the link using a joint (or constraint)
			joint = new SixDofJoint(prevRbc, rbc, prevJointPt, new Vector3f(0, 0, linkZspan / 2), false);
			joint.setCollisionBetweenLinkedBodys(false);
			bulletAppState.getPhysicsSpace().add(joint);
			inventory.addPhysicsJoint(joint);
			
			prevRbc = rbc;
			prevJointPt = new Vector3f(0, 0, -linkZspan / 2);
		}
		// connect the last link to the end node
		vec.set(0, 0, endNodesSize.z);
		joint = new SixDofJoint(prevRbc, endNode.getControl(MyRigidBodyControl.class), prevJointPt, vec, false);
		joint.setCollisionBetweenLinkedBodys(false);
		bulletAppState.getPhysicsSpace().add(joint);
		inventory.addPhysicsJoint(joint);
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
				return new ColorRGBA(r / 255f, g / 255f, b / 255f, 1);
			}
			throw new IllegalArgumentException("could not parse '" + str + "'");
		}
	}
	
	public void dropRandomBlock() {
		final ColorRGBA[] colors = new ColorRGBA[] { ColorRGBA.Red,
				ColorRGBA.Blue, ColorRGBA.Yellow, ColorRGBA.Green,
				ColorRGBA.Brown, ColorRGBA.Cyan, ColorRGBA.Magenta,
				ColorRGBA.Orange };
		Spatial s = factory.makeBlock("larger-block-" + randomItemSN, 1.5f, 1.5f, 1.5f,
				colors[random.nextInt(colors.length)]);
		++randomItemSN;
		s.setLocalTranslation(
				(random.nextFloat() * 2 - 1) * (tableWidth / 2), 
				10,
				(random.nextFloat() * 2 - 1) * (tableDepth / 2));
		s.setLocalRotation(new Quaternion().fromAngleAxis(
				FastMath.HALF_PI * random.nextFloat(),
				Vector3f.UNIT_XYZ));
		MyRigidBodyControl rbc = new MyRigidBodyControl(1);
		s.addControl(rbc);
		bulletAppState.getPhysicsSpace().add(rbc);
		rootNode.attachChild(s);
		inventory.addItem(s);
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
            Spatial s = factory.makeBlock("small-block-" + randomItemSN, 
                    BOX_SIZE.x, BOX_SIZE.y, BOX_SIZE.z,
                    colors[random.nextInt(colors.length)]);
            ++randomItemSN;
            s.setLocalTranslation(pos);
            s.setLocalRotation(rot);
            MyRigidBodyControl rbc = new MyRigidBodyControl(1f);
            s.addControl(rbc);
            bulletAppState.getPhysicsSpace().add(rbc);            
            rootNode.attachChild(s);

            pos.y += BOX_SIZE.y;
            inventory.addItem(s);
        }
	}
	
	public void dropRandomBoxContainer() {
		Spatial boxContainer = factory.makeBoxContainer("container-" + randomItemSN, 5, 3, 5,
				0.5f, ColorRGBA.Gray);
		++randomItemSN;
		boxContainer.setLocalTranslation((random.nextFloat() * 2 - 1) * (tableWidth / 2), 
				10, 
				(random.nextFloat() * 2 - 1) * (tableDepth / 2));
		boxContainer.setLocalRotation(new Quaternion().fromAngleAxis(
				FastMath.HALF_PI * random.nextFloat(), Vector3f.UNIT_XYZ));
		MyRigidBodyControl boxContainerControl = new MyRigidBodyControl(3f);
		boxContainer.addControl(boxContainerControl);
		bulletAppState.getPhysicsSpace().add(boxContainerControl);
		rootNode.attachChild(boxContainer);
		inventory.addItem(boxContainer);
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
