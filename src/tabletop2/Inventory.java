package tabletop2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import tabletop2.FunctionalItemPhysics.FunctionType;
import tabletop2.FunctionalJoint.FunctionalJointType;
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
    private HashMap<Spatial, HashMap<Node, FunctionalJoint>> functionalJoints = new HashMap<>();
    private ArrayList<InventoryListener> listeners = new ArrayList<InventoryListener>();

    
    public Inventory(MainApp app) {
    	rootNode = app.getRootNode();
    	bulletAppState = app.getBulletAppState();
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
    	// collect and store all functional spots per item    	 
		class FuncNodeVisitor implements SceneGraphVisitor {
			public HashMap<Node, FunctionalJoint> funcNodes = new HashMap<>();			
			@Override
			public void visit(Spatial s) {
				if (FunctionalItemPhysics.getFunctionType(s) != FunctionType.NONE) {
					Node node = (Node) s;
					GhostControl gc = new GhostControl(
							new SphereCollisionShape(FunctionalItemPhysics.COLLISION_SPHERE_RADIUS));
					gc.setCollisionGroup(FunctionalItemPhysics.COLLISION_GROUP);
					gc.setCollideWithGroups(FunctionalItemPhysics.COLLISION_GROUP);
					node.addControl(gc);
					bulletAppState.getPhysicsSpace().add(gc);

					funcNodes.put(node, null);
				}
			}			
		}
		FuncNodeVisitor visitor = new FuncNodeVisitor();
    	item.depthFirstTraversal(visitor);
    	if (visitor.funcNodes.size() > 0) {
    		functionalJoints.put(item, visitor.funcNodes);
    	}

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
    
    public FunctionalJoint addFunctionalJoint(Spatial item1, Spatial item2, Node node1, Node node2, 
    		FunctionalJointType type) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	Vector3f refPt1 = new Vector3f();
    	getLocalToItemTransform(item1, node1).transformVector(refPt1, refPt1);
    	Vector3f refPt2 = new Vector3f();
    	getLocalToItemTransform(item2, node2).transformVector(refPt2, refPt2);
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	
    	// create the physics joint
    	SixDofJoint joint = new SixDofJoint(c1, c2, refPt1, refPt2, false);
    	joint.setCollisionBetweenLinkedBodys(false);
    	joint.setAngularLowerLimit(Vector3f.ZERO);
    	joint.setAngularUpperLimit(Vector3f.ZERO);
    	joint.setLinearLowerLimit(Vector3f.ZERO);
    	joint.setLinearUpperLimit(Vector3f.ZERO);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
        
        // create the functional joint
        FunctionalJoint fj = new FunctionalJoint();
        fj.joint = joint;
        fj.item1 = item1;
        fj.item2 = item2;
        fj.node1 = node1;
        fj.node2 = node2;
        fj.type = type;
        
        // record the functional joint
        functionalJoints.get(item1).put(node1, fj);
        functionalJoints.get(item2).put(node2, fj);
        
        // wake up the connected items if needed
        updateItemInsomnia(item1);
        
    	return fj;
    }
    
    public FunctionalJoint removeFunctionalJoint(Node node1, Node node2) {
    	Spatial item1 = getItem(node1);
    	Spatial item2 = getItem(node2);
    	FunctionalJoint fj1 = getFunctionalJoint(item1, node1);
    	FunctionalJoint fj2 = getFunctionalJoint(item2, node2);
    	if (fj1 != fj2) {
    		throw new IllegalArgumentException("node " + node1.getName() + " and node " + node2.getName() 
    				+ " do not have a common functional joint");
    	}
    	// remove the physics joint
		fj1.joint.getBodyA().removeJoint(fj1.joint);
		fj1.joint.getBodyB().removeJoint(fj1.joint);
        bulletAppState.getPhysicsSpace().remove(fj1.joint);
        joints.remove(fj1.joint);
        fj1.joint.destroy();
        // remove the functional joint
        functionalJoints.get(item1).put(node1, null);
        functionalJoints.get(item2).put(node2, null);
        // wake up both items if needed
        updateItemInsomnia(item1);
        updateItemInsomnia(item2);        
        return fj1;
    }
    
    public FunctionalJoint getFunctionalJoint(Spatial item, Node node) {
    	return functionalJoints.get(item).get(node);
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
        // remove functional joint records and ghost controls
        if (functionalJoints.get(item) != null) {
	        HashSet<FunctionalJoint> funcJoints = new HashSet<>(functionalJoints.get(item).values());
	        for (FunctionalJoint fj : funcJoints) {
	        	removeFunctionalJoint(fj.node1, fj.node2);
	        	GhostControl gc = item.getControl(GhostControl.class);
	        	if (gc != null) {
	        		bulletAppState.getPhysicsSpace().remove(gc);
	        		item.removeControl(gc);
	        	}
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
    
    public Transform getLocalToItemTransform(Spatial item, Node node) {
    	Transform tr = new Transform();
    	tr.set(node.getLocalTransform());
    	
    	Node tmp = node;
    	while (tmp.getParent() != item) {
    		tmp = tmp.getParent();
    		tr.combineWithParent(tmp.getLocalTransform());
    	}
    	return tr;
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
