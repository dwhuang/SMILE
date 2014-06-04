package tabletop2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import tabletop2.util.MyRigidBodyControl;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.bullet.joints.SliderJoint;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
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
    
    public void forceItemPhysicsActivationStatesViaJoints(Spatial item, int actState) {
    	MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
    	rbc.forceActivationState(actState);
    	rbc.activate();

    	List<PhysicsJoint> jointList = item.getControl(MyRigidBodyControl.class).getJoints();
    	if (jointList != null) {
    		for (PhysicsJoint joint : jointList) {
    			MyRigidBodyControl c1 = (MyRigidBodyControl) joint.getBodyA();
    			MyRigidBodyControl c2 = (MyRigidBodyControl) joint.getBodyB();
    			if (item == c1.getSpatial()) {
    				c2.forceActivationState(actState);
    				c2.activate();
    			} else {
    				c1.forceActivationState(actState);
    				c1.activate();
    			}
    		}
    	}
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
