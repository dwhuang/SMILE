package tabletop2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import tabletop2.FunctionalItemPhysics.FunctionalRel;
import tabletop2.util.MyRigidBodyControl;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.GhostControl;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.bullet.joints.SliderJoint;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public class Inventory {
	private Node rootNode;
	private BulletAppState bulletAppState;
	
    private HashSet<Spatial> items = new HashSet<Spatial>();
    private HashSet<PhysicsJoint> joints = new HashSet<PhysicsJoint>();
    private ArrayList<InventoryListener> listeners = new ArrayList<InventoryListener>();
    private HashMap<Spatial, HashMap<Node, PhysicsJoint>> itemFuncSpots = new HashMap<>();
    
    public Inventory(MainApp app) {
    	rootNode = app.getRootNode();
    	bulletAppState = app.getBulletAppState();
    }
    
    public static boolean isFunctionalSpot(Spatial s) {
    	if (s instanceof Node) {
    		Node node = (Node) s;
        	if (node.getUserData("functionalSpot") != null) {
        		return true;
        	}
    	}
    	return false;
    }
    
    public HashSet<Spatial> allItems() {
    	return new HashSet<Spatial>(items);
    }
    
    public MyRigidBodyControl addItem(Spatial item, float mass) {
    	return addItem(item, mass, null);
    }
    
    public MyRigidBodyControl addItem(Spatial item, float mass, CollisionShape cs) {
    	MyRigidBodyControl rbc;
    	if (cs != null) {
    		rbc = new MyRigidBodyControl(cs, mass);
    	} else {
    		rbc = new MyRigidBodyControl(mass);
    	}
    	item.addControl(rbc);
    	bulletAppState.getPhysicsSpace().add(rbc);
    	rootNode.attachChild(item);
    	items.add(item);
    	
    	// add ghost control for functional objects, and
    	// collect all functional spots in the item    	 
		class FuncSpotVisitor implements SceneGraphVisitor {
			public HashMap<Node, PhysicsJoint> allFuncSpots = new HashMap<>();
			
			@Override
			public void visit(Spatial s) {
				if (isFunctionalSpot(s)) {
					Node node = (Node) s;
					GhostControl gc = new GhostControl(
							new SphereCollisionShape(FunctionalItemPhysics.COLLISION_SPHERE_RADIUS));
					gc.setCollisionGroup(FunctionalItemPhysics.COLLISION_GROUP);
					gc.setCollideWithGroups(FunctionalItemPhysics.COLLISION_GROUP);
					node.addControl(gc);
					bulletAppState.getPhysicsSpace().add(gc);

					allFuncSpots.put(node, null);
				}
			}			
		}
		FuncSpotVisitor visitor = new FuncSpotVisitor();
    	item.depthFirstTraversal(visitor);
    	itemFuncSpots.put(item, visitor.allFuncSpots);
    	
    	for (InventoryListener l : listeners) {
    		l.objectCreated(item);
    	}
    	
    	return rbc;
    }
    
    public SixDofJoint addSixDofJoint(Spatial item1, Spatial item2, Vector3f refPt1, Vector3f refPt2) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	
    	SixDofJoint joint = new SixDofJoint(c1, c2, refPt1, refPt2, false);
    	joint.setCollisionBetweenLinkedBodys(false);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
    	return joint;
    }
    
    public SliderJoint addSliderJoint(Spatial item1, Spatial item2, Vector3f refPt1, Vector3f refPt2,
    		float lowerLinLimit, float upperLinLimit) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	
    	SliderJoint joint = new SliderJoint(c1, c2, refPt1, refPt2, false);
    	joint.setCollisionBetweenLinkedBodys(false);
    	joint.setUpperLinLimit(upperLinLimit);
    	joint.setLowerLinLimit(lowerLinLimit);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
    	return joint;
    }
    
    public SixDofJoint addFixedJoint(Spatial item1, Spatial item2, Vector3f refPt1, Vector3f refPt2) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	
    	SixDofJoint joint = new SixDofJoint(c1, c2, refPt1, refPt2, false);
    	joint.setCollisionBetweenLinkedBodys(false);
    	joint.setAngularLowerLimit(Vector3f.ZERO);
    	joint.setAngularUpperLimit(Vector3f.ZERO);
    	joint.setLinearLowerLimit(Vector3f.ZERO);
    	joint.setLinearUpperLimit(Vector3f.ZERO);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
    	return joint;
    }
    
    public void removeItem(Spatial item) {
    	if (items.contains(item)) {
    		removeItemPhysics(item);
    		item.getParent().detachChild(item);
    		items.remove(item);
        	
    		for (InventoryListener l : listeners) {
        		l.objectDeleted(item);
        	}
    	}
    }
    
    public void removeAllFreeItems() {
    	HashSet<Spatial> itemsClone = new HashSet<Spatial>(items);
    	for (Spatial item : itemsClone) {
    		if (item.getParent() == rootNode) {
    			removeItem(item);
    		}
    	}
    }
    
    public void removeAllItems() {
    	HashSet<Spatial> itemsClone = new HashSet<Spatial>(items);
    	for (Spatial item : itemsClone) {
    		removeItem(item);
    	}
    	if (!items.isEmpty()) {
    		throw new IllegalStateException("items is not empty");
    	}
    	if (!joints.isEmpty()) {
    		throw new IllegalStateException("joints is not empty");
    	}
    }
    
    private void removeItemPhysics(Spatial item) {
        // remove joints associated with the item
        List<PhysicsJoint> jointList = new ArrayList<PhysicsJoint>(
        		item.getControl(MyRigidBodyControl.class).getJoints());
        if (jointList != null) {
        	for (PhysicsJoint joint : jointList) {
        		joint.getBodyA().removeJoint(joint);
        		joint.getBodyB().removeJoint(joint);
                bulletAppState.getPhysicsSpace().remove(joint);
                joints.remove(joint);
                joint.destroy();
        	}
        }
        // remove functional spot joint records and ghost controls
        HashMap<Node, PhysicsJoint> fsMap = itemFuncSpots.get(item);
        for (Node fn : fsMap.keySet()) {
        	fsMap.put(fn, null);
        	GhostControl gc = fn.getControl(GhostControl.class);
        	if (gc != null) {
        		bulletAppState.getPhysicsSpace().remove(gc);
        		fn.removeControl(gc);
        	}
        }
        // remove physics control from the item
        MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
        if (rbc != null) {
            bulletAppState.getPhysicsSpace().remove(rbc);
            item.removeControl(rbc);
        }
    }
    
    public Spatial getItem(Spatial g) {
    	Spatial s = g;
    	while (s != rootNode) {
    		if (items.contains(s)) {
    			return s;
    		}
    		s = s.getParent();
    	}
    	return null;
    }
    
    // may be slow
    public Spatial getItem(String id) {
    	for (Spatial s : items) {
    		if (s.getName().equals(id)) {
    			return s;
    		}
    	}
    	return null;
    }
    
    public boolean updateItemInsomnia(Spatial item) {
    	MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
    	boolean isInsomniac = rbc.isKinematic();
    	
		for (PhysicsJoint joint : rbc.getJoints()) {
			MyRigidBodyControl c1 = (MyRigidBodyControl) joint.getBodyA();
			MyRigidBodyControl c2 = (MyRigidBodyControl) joint.getBodyB();
			if (c1.isKinematic() || c2.isKinematic()) {
				isInsomniac = true;
			}
		}
    	
		for (PhysicsJoint joint : rbc.getJoints()) {
			MyRigidBodyControl c1 = (MyRigidBodyControl) joint.getBodyA();
			MyRigidBodyControl c2 = (MyRigidBodyControl) joint.getBodyB();
			c1.forceActivationState(isInsomniac ? CollisionObject.DISABLE_DEACTIVATION : CollisionObject.ACTIVE_TAG);
			c1.activate();
			c2.forceActivationState(isInsomniac ? CollisionObject.DISABLE_DEACTIVATION : CollisionObject.ACTIVE_TAG);
			c2.activate();
		}
		rbc.forceActivationState(isInsomniac ? CollisionObject.DISABLE_DEACTIVATION : CollisionObject.ACTIVE_TAG);
		rbc.activate();
		return isInsomniac;
    }
    
    public void addListener(InventoryListener l) {
    	listeners.add(l);
    }
    
    public void removeListener(InventoryListener l) {
    	listeners.remove(l);
    }

	public PhysicsJoint getFuncSpotJoint(Node node1, Node node2) {
    	Spatial item1 = getItem(node1);
    	Spatial item2 = getItem(node2);    	
    	if (itemFuncSpots.get(item1).get(node1) == itemFuncSpots.get(item2).get(node2)) {
    		return itemFuncSpots.get(item1).get(node1);
    	}
    	return null;
	}
    
	public boolean canAddFuncSpotJoint(Node node1, Node node2) {
    	Spatial item1 = getItem(node1);
    	Spatial item2 = getItem(node2);    	
    	if (item1 == item2) {
    		// same item cannot add a joint to itself
    		return false;
    	}
    	if (itemFuncSpots.get(item1).get(node1) != null || itemFuncSpots.get(item2).get(node2) != null) {
    		return false;
    	}
    	return true;
    }
	
	public void addFuncSpotJoint(FunctionalRel rel, Node node1, Node node2) {
    	PhysicsJoint joint = addFixedJoint(getItem(node1), getItem(node2), 
    			node1.getLocalTranslation(), node2.getLocalTranslation());
    	Spatial item1 = getItem(node1);
    	Spatial item2 = getItem(node2);
    	itemFuncSpots.get(item1).put(node1, joint);
    	itemFuncSpots.get(item2).put(node2, joint);

    	updateItemInsomnia(item1); // this will update item2 too since they are connected
    }
	
	public void removeFuncSpotJoint(Node node1, Node node2) {
		PhysicsJoint joint = getFuncSpotJoint(node1, node2);
		joint.getBodyA().removeJoint(joint);
		joint.getBodyB().removeJoint(joint);
        bulletAppState.getPhysicsSpace().remove(joint);
        joints.remove(joint);
        joint.destroy();
        
        Spatial item1 = getItem(node1);
        Spatial item2 = getItem(node2);
        itemFuncSpots.get(item1).put(node1, null);
        itemFuncSpots.get(item2).put(node2, null);
        
        updateItemInsomnia(item1);
        updateItemInsomnia(item2);
	}
    
    private class ItemInfo {
    	public Spatial spatial;
    	public Transform trans;
    	public float mass;
    	public boolean isKinematic; 
    }
    
	public enum JointType {
		Slider, SixDof
	}

	private class JointInfo {
		JointType type;
		Spatial item1;
		Spatial item2;
		Vector3f pivot1;
		Vector3f pivot2;
		HashMap<String, Object> param = new HashMap<String, Object>();
    }
    
    public class Memento {
    	// TODO: fix for functional spot joints
    	private HashSet<ItemInfo> itemInfoSet = new HashSet<ItemInfo>();
    	private HashSet<JointInfo> jointInfoSet = new HashSet<JointInfo>();
//    	private HashMap<PhysicsJoint, ArrayList<Spatial>> jointSpatials 
//    			= new HashMap<PhysicsJoint, ArrayList<Spatial>>();
    	
    	private Memento() {
    	}    	
    }
    
    public Memento saveToMemento() {
    	Memento m = new Memento();
    	for (Spatial item : items) {
    		ItemInfo info = new ItemInfo();
    		info.spatial = item;
    		info.trans = item.getWorldTransform().clone();
    		MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
    		info.mass = rbc.getMass();
    		info.isKinematic = rbc.isKinematic();
    		m.itemInfoSet.add(info);
    	}
    	
    	for (PhysicsJoint joint : joints) {
			Spatial item1 = ((MyRigidBodyControl)joint.getBodyA()).getSpatial();
			Spatial item2 = ((MyRigidBodyControl)joint.getBodyB()).getSpatial();
			JointInfo info = new JointInfo();
			if (joint instanceof SliderJoint) {
				info.type = JointType.Slider;
				info.param.put("lowerLinLimit", new Float(((SliderJoint) joint).getLowerLinLimit()));
				info.param.put("upperLinLimit", new Float(((SliderJoint) joint).getUpperLinLimit()));
			} else if (joint instanceof SixDofJoint) {
				info.type = JointType.SixDof;
			}
			info.item1 = item1;
			info.item2 = item2;
			info.pivot1 = new Vector3f(joint.getPivotA());
			info.pivot2 = new Vector3f(joint.getPivotB());
			m.jointInfoSet.add(info);
    	}

    	return m;
    }
    
    public void restoreFromMemento(Memento m) {
    	removeAllItems();
    	for (ItemInfo info : m.itemInfoSet) {
    		Spatial item = info.spatial;
        	item.setLocalTransform(info.trans);
        	MyRigidBodyControl rbc = addItem(item, info.mass);
        	rbc.setKinematic(info.isKinematic);
    	}
    	for (JointInfo info : m.jointInfoSet) {
    		if (info.type == JointType.Slider) {
        		addSliderJoint(info.item1, info.item2, info.pivot1, info.pivot2,
        				(Float) info.param.get("lowerLinLimit"), (Float) info.param.get("upperLinLimit"));
    		} else {
    			addSixDofJoint(info.item1, info.item2, info.pivot1, info.pivot2);
    		}
    	}
    }
}
