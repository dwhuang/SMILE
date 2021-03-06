package edu.umd.smile.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

import edu.umd.smile.MainApp;
import edu.umd.smile.gui.LogMessage;
import edu.umd.smile.object.ObjectBondTracker.ObjectBond;
import edu.umd.smile.object.ObjectBondTracker.ObjectBondTrackerMemento;
import edu.umd.smile.util.MyJoint;
import edu.umd.smile.util.MyRigidBodyControl;
import edu.umd.smile.util.MySixDofJoint;
import edu.umd.smile.util.MySliderJoint;

/**
 *
 * @author dwhuang
 */
public class Inventory {
    private static final Logger logger = Logger.getLogger(Table.class.getName());

    private Node rootNode;
	private BulletAppState bulletAppState;
	
    private HashSet<Spatial> items = new HashSet<Spatial>();
    private HashSet<PhysicsJoint> joints = new HashSet<PhysicsJoint>();
    private HashMap<Spatial, AbstractControl> controls = new HashMap<>();
    private ArrayList<InventoryListener> listeners = new ArrayList<InventoryListener>();
    private ObjectBondTracker objectBondTracker;

    public Inventory(MainApp app) {
    	rootNode = app.getRootNode();
    	bulletAppState = app.getBulletAppState();
    	objectBondTracker = new ObjectBondTracker(app, this);
    }
    
    public Set<Spatial> allItems() {
    	return new HashSet<Spatial>(items);
    }
    
    public Set<Spatial> allControlSpatials() {
        return new HashSet<>(controls.keySet());
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
		rbc.setDamping(0.2f, 0.2f);
    	rootNode.attachChild(item);
    	items.add(item);
    	
    	objectBondTracker.addBondPointsForItem(item);
    	
    	for (InventoryListener l : listeners) {
    		l.objectCreated(item);
    	}
    	
    	return rbc;
    }

    public void registerControl(Spatial s, AbstractControl c) {
    	controls.put(s, c);
    }

    /**
     * First call registerControl to add all state-controlled object, and then
     * call this function to initialize all the added objects.
     */
    public void initControls() {
        HashMap<Spatial, AbstractControl> controlsClone = new HashMap<>(controls);
        HashMap<String, Spatial> idMap = new HashMap<>();
        controls.clear();
        for (Spatial s : controlsClone.keySet()) {
            // check that each spatial belongs to an item
            Spatial item = getItem(s);
            if (item == null) {
                LogMessage.warn("State-controlled spatial " + s + " is not a part of any known item (removed)", logger);
                continue;
            }
            controls.put(s, controlsClone.get(s));
            // collect all state control ids
            idMap.put(s.getName(), s);
        }
        
        for (AbstractControl c : controls.values()) {
            c.resolveDownstreamIds(idMap);
            c.triggerDownstreams(false);
        }
        
        // control init events
        for (Map.Entry<Spatial, AbstractControl> entry : controls.entrySet()) {
            for (InventoryListener l : listeners) {
                l.objectControlInitialized(entry.getKey(), entry.getValue());
            }
        }
    }

    public AbstractControl getManuallyTriggerable(Geometry g) {
        Spatial cs = g;
    	while (cs != null) {
    	    AbstractControl c = controls.get(cs);
    		if (c != null && c.isManuallyTriggerable(g)) {
    		    return c;
    		}
    		cs = cs.getParent();
    	}
    	return null;
    }
    
    public AbstractControl getControl(Spatial s) {
        return controls.get(s);
    }

    public AbstractControl getControl(String id) {
        for (Spatial s : controls.keySet()) {
           if (s.getName().equals(id)) {
              return controls.get(s);
           }
        }
        return null;
    }
    
	public MySixDofJoint addSixDofJoint(Spatial item1, Spatial item2, Vector3f refPt1, Vector3f refPt2,
	        Matrix3f rot1, Matrix3f rot2, boolean collision) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	
    	MySixDofJoint joint = new MySixDofJoint(c1, c2, refPt1, refPt2, rot1, rot2, false);
    	joint.setCollisionBetweenLinkedBodys(collision);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
    	return joint;
    }
    
    public MySliderJoint addSliderJoint(Spatial item1, Spatial item2, Vector3f refPt1, Vector3f refPt2,
    		Matrix3f rot1, Matrix3f rot2, float lowerLinLimit, float upperLinLimit, boolean collision) {
    	if (!items.contains(item1)) {
    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
    	}
    	if (!items.contains(item2)) {
    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
    	}
    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
    	if (refPt1 == null) {
    		refPt1 = new Vector3f();
    	}
    	if (refPt2 == null) {
    		refPt2 = new Vector3f();
    	}
    	if (rot1 == null) {
    		rot1 = new Matrix3f();
    	}
    	if (rot2 == null) {
    		rot2 = new Matrix3f();
    	}
    	
    	MySliderJoint joint = new MySliderJoint(c1, c2, refPt1, refPt2, rot1, rot2, false);
    	joint.setCollisionBetweenLinkedBodys(collision);
    	joint.setUpperLinLimit(upperLinLimit);
    	joint.setLowerLinLimit(lowerLinLimit);
        joints.add(joint);
        bulletAppState.getPhysicsSpace().add(joint);
    	return joint;
    }
    
//    public FunctionalJoint addFunctionalJoint(Spatial item1, Spatial item2, Node node1, Node node2,
//    		FunctionalJointType type) {
//    	if (!items.contains(item1)) {
//    		throw new IllegalArgumentException(item1 + " does not exist in the inventory");
//    	}
//    	if (!items.contains(item2)) {
//    		throw new IllegalArgumentException(item2 + " does not exist in the inventory");
//    	}
//    	Vector3f refPt1 = new Vector3f();
//    	getLocalToItemTransform(item1, node1).transformVector(refPt1, refPt1);
//    	Vector3f refPt2 = new Vector3f();
//    	getLocalToItemTransform(item2, node2).transformVector(refPt2, refPt2);
//    	MyRigidBodyControl c1 = item1.getControl(MyRigidBodyControl.class);
//    	MyRigidBodyControl c2 = item2.getControl(MyRigidBodyControl.class);
//
//    	// create the physics joint
//    	SixDofJoint joint = new SixDofJoint(c1, c2, refPt1, refPt2, false);
//    	joint.setCollisionBetweenLinkedBodys(false);
//    	joint.setAngularLowerLimit(Vector3f.ZERO);
//    	joint.setAngularUpperLimit(Vector3f.ZERO);
//    	joint.setLinearLowerLimit(Vector3f.ZERO);
//    	joint.setLinearUpperLimit(Vector3f.ZERO);
//        joints.add(joint);
//        bulletAppState.getPhysicsSpace().add(joint);
//
//        // create the functional joint
//        FunctionalJoint fj = new FunctionalJoint();
//        fj.joint = joint;
//        fj.item1 = item1;
//        fj.item2 = item2;
//        fj.node1 = node1;
//        fj.node2 = node2;
//        fj.type = type;
//
//        // record the functional joint
//        functionalJoints.get(item1).put(node1, fj);
//        functionalJoints.get(item2).put(node2, fj);
//
//        // wake up the connected items if needed
//        updateItemInsomnia(item1);
//
//    	return fj;
//    }
//
//    public FunctionalJoint removeFunctionalJoint(Node node1, Node node2) {
//    	Spatial item1 = getItem(node1);
//    	Spatial item2 = getItem(node2);
//    	FunctionalJoint fj1 = getFunctionalJoint(item1, node1);
//    	FunctionalJoint fj2 = getFunctionalJoint(item2, node2);
//    	if (fj1 != fj2) {
//    		throw new IllegalArgumentException("node " + node1.getName() + " and node " + node2.getName()
//    				+ " do not have a common functional joint");
//    	}
//    	// remove the physics joint
//		fj1.joint.getBodyA().removeJoint(fj1.joint);
//		fj1.joint.getBodyB().removeJoint(fj1.joint);
//        bulletAppState.getPhysicsSpace().remove(fj1.joint);
//        joints.remove(fj1.joint);
//        fj1.joint.destroy();
//        // remove the functional joint
//        functionalJoints.get(item1).put(node1, null);
//        functionalJoints.get(item2).put(node2, null);
//        // wake up both items if needed
//        updateItemInsomnia(item1);
//        updateItemInsomnia(item2);
//        return fj1;
//    }
//
//    public FunctionalJoint getFunctionalJoint(Spatial item, Node node) {
//    	return functionalJoints.get(item).get(node);
//    }
    
    public void removeItem(Spatial item) {
    	if (items.contains(item)) {
    	    objectBondTracker.removeBondPointsForItem(item);
    		removeItemControls(item);
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
    	if (!controls.isEmpty()) {
            throw new IllegalStateException("controls is not empty");
    	}
        if (objectBondTracker.getNumBondPoints() > 0) {
            throw new IllegalStateException("objectBondManager is not empty");
        }
    }
    
    private void removeItemPhysics(Spatial item) {
        // remove joints associated with the item
        List<PhysicsJoint> jointList = new ArrayList<PhysicsJoint>(
        		item.getControl(MyRigidBodyControl.class).getJoints());
        if (jointList != null) {
        	for (PhysicsJoint joint : jointList) {
        	    removeJoint(joint);
        	}
        }
        
        // remove physics control from the item
        MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
        if (rbc != null) {
            bulletAppState.getPhysicsSpace().remove(rbc);
            item.removeControl(rbc);
        }
    }
    
    public void removeJoint(Spatial item1, Spatial item2, Class<? extends PhysicsJoint> jointClass) {
        MyRigidBodyControl rbc1 = item1.getControl(MyRigidBodyControl.class);
        MyRigidBodyControl rbc2 = item2.getControl(MyRigidBodyControl.class);
        for (PhysicsJoint joint : rbc1.getJoints()) {
            if (!jointClass.isInstance(joint)) {
                continue;
            }
            if (joint.getBodyA() == rbc1 && joint.getBodyB() == rbc2
                    || joint.getBodyA() == rbc2 && joint.getBodyB() == rbc1) {
                removeJoint(joint);
                return;
            }
        }
        throw new IllegalArgumentException("cannot find a joint between " + item1 + " and " + item2
                + " of type " + jointClass);
    }

    private void removeJoint(PhysicsJoint joint) {
        joint.getBodyA().removeJoint(joint);
        joint.getBodyB().removeJoint(joint);
        bulletAppState.getPhysicsSpace().remove(joint);
        joints.remove(joint);
        joint.destroy();
    }
    
    private void removeItemControls(Spatial item) {
    	item.depthFirstTraversal(new SceneGraphVisitor() {
			@Override
			public void visit(Spatial s) {
				if (controls.containsKey(s)) {
					controls.remove(s);
				}
			}
    	});
    }
    
    public Spatial getItem(Spatial g) {
    	Spatial s = g;
    	while (s != null && s != rootNode) {
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
    
    public Spatial getPointable(Spatial g) {
        Spatial s = g;
        while (s != null && s != rootNode) {
            if (Boolean.parseBoolean((String) s.getUserData("pointable")) || items.contains(s)) {
                return s;
            }
            s = s.getParent();
        }
        return null;
    }
    
//    public Transform getLocalToItemTransform(Spatial item, Node node) {
//    	Transform tr = new Transform();
//    	tr.set(node.getLocalTransform());
//
//    	Node tmp = node;
//    	while (tmp.getParent() != item) {
//    		tmp = tmp.getParent();
//    		tr.combineWithParent(tmp.getLocalTransform());
//    	}
//    	return tr;
//    }
    
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
    
    public void wakeNearbyItems(Spatial s, float boundingVolumeScale) {
        Spatial item = getItem(s);
        if (item == null) {
            return;
        }
        BoundingVolume bv = item.getWorldBound().clone();
        Transform trans = new Transform(Vector3f.ZERO, Quaternion.IDENTITY,
                Vector3f.UNIT_XYZ.mult(boundingVolumeScale));
        bv = bv.transform(trans);
        CollisionResults res = new CollisionResults();
        for (Spatial i : items) {
            if (i == item) {
                continue;
            }
            i.collideWith(bv, res);
            if (res.size() > 0) {
                i.getControl(MyRigidBodyControl.class).activate();
            }
        }
    }
    
    public void createInitBonds() {
        for (Spatial item : items) {
            item.depthFirstTraversal(new SceneGraphVisitor() {
                @Override
                public void visit(Spatial spatial) {
                    if (spatial instanceof Node) {
                        Node node = (Node) spatial;
                        String hostId = node.getUserData("initBondHostId");
                        if (hostId != null) {
                            objectBondTracker.createInitBond(node, hostId,
                                    (Integer) node.getUserData("initBondTightness"));
                        }
                    }
                }
            });
        }
    }

    public ObjectBond findFastenable(Spatial guestItem, Vector3f clickedLocation, float distTolerance) {
        return objectBondTracker.findFastenable(guestItem, clickedLocation, distTolerance);
    }
    
    public ObjectBond findLoosenable(Spatial guestItem, Vector3f clickedLocation) {
        return objectBondTracker.findLoosenable(guestItem, clickedLocation);
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
    
	private class JointInfo {
		MyJoint.Type type;
		Spatial item1;
		Spatial item2;
		Vector3f pivot1;
		Vector3f pivot2;
		Matrix3f rot1;
		Matrix3f rot2;
		HashMap<String, Object> param = new HashMap<String, Object>();
    }
	
	private class ControlInfo {
		Spatial s;
		AbstractControl control;
		HashMap<String, Object> states= new HashMap<>();
	}
	
    public class Memento {
    	private HashSet<ItemInfo> itemInfoSet = new HashSet<ItemInfo>();
    	private HashSet<JointInfo> jointInfoSet = new HashSet<JointInfo>();
    	private HashSet<ControlInfo> controlInfoSet = new HashSet<>();
    	private ObjectBondTrackerMemento bondInfo;
    	
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
			info.type = ((MyJoint)joint).getType();
			((MyJoint) joint).saveParam(info.param);
			info.item1 = item1;
			info.item2 = item2;
			info.pivot1 = new Vector3f(joint.getPivotA());
			info.pivot2 = new Vector3f(joint.getPivotB());
            info.rot1 = new Matrix3f(((MyJoint) joint).getRotA());
            info.rot2 = new Matrix3f(((MyJoint) joint).getRotB());
			m.jointInfoSet.add(info);
    	}
    	
    	for (Spatial s : controls.keySet()) {
    		ControlInfo info = new ControlInfo();
    		info.s = s;
    		info.control = controls.get(s);
    		info.control.saveStates(info.states);
    		m.controlInfoSet.add(info);
    	}
    	
    	m.bondInfo = objectBondTracker.saveToMemento();

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
    	    MyJoint joint = null;
    		if (info.type == MyJoint.Type.Slider) {
        		joint = addSliderJoint(info.item1, info.item2, info.pivot1, info.pivot2,
        				info.rot1, info.rot2, 0, 0, false);
    		} else if (info.type == MyJoint.Type.SixDof) {
    			joint = addSixDofJoint(info.item1, info.item2, info.pivot1, info.pivot2,
    			        info.rot1, info.rot2, false);
    		}
    		if (joint != null) {
    		    joint.loadParam(info.param);
    		    updateItemInsomnia(info.item1);
    		    updateItemInsomnia(info.item2);
    		}
    	}
    	for (ControlInfo info : m.controlInfoSet) {
    		registerControl(info.s, info.control);
    		info.control.restoreStates(info.states);
    	}
    	objectBondTracker.restoreFromMemento(m.bondInfo);
    }
}
