package tabletop2;

import java.util.HashSet;

import tabletop2.FunctionalJoint.FunctionalJointType;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class FunctionalItemPhysics implements PhysicsCollisionListener {
    public static final int COLLISION_GROUP = PhysicsCollisionObject.COLLISION_GROUP_02;
    public static final float COLLISION_SPHERE_RADIUS = 0.3f;
    public static final float MAX_DROPOFF_DIST = 0.6f;
    
    public enum FunctionType {
    	NONE, MAGNET_S, MAGNET_N
    }    
    
    private Inventory inventory;
    private HashSet<FunctionalJoint> functionalJoints = new HashSet<>();

    
    public FunctionalItemPhysics(MainApp app) {
    	this.inventory = app.getInventory();
    }
    
    public static FunctionType getFunctionType(Spatial s) {
    	String str = s.getUserData("functionType");
    	if (str == null) {
    		return FunctionType.NONE;
    	} else {
    		return FunctionType.valueOf(str.toUpperCase()); 
    	}
    }
    
	public FunctionalJoint tryAddingFunctionalJoint(Node node1, Node node2, float minAngleDeg) {
    	Spatial item1 = inventory.getItem(node1);
    	Spatial item2 = inventory.getItem(node2);
    	if (item1 == item2) {
    		// same item cannot add a joint to itself
    		return null;
    	}
    	if (inventory.getFunctionalJoint(item1, node1) != null 
    			|| inventory.getFunctionalJoint(item2, node2) != null) {
    		// if either node's function is already occupied
    		return null;
    	}
    	// test node angle: must > minAngleDeg
    	Vector3f v1 = node1.getWorldRotation().mult(Vector3f.UNIT_Z);
    	Vector3f v2 = node2.getWorldRotation().mult(Vector3f.UNIT_Z);
    	if (v1.angleBetween(v2) * FastMath.RAD_TO_DEG < minAngleDeg) {
    		return null;
    	}
    	// are node functions compatible?
    	FunctionType ft1 = getFunctionType(node1);
    	FunctionType ft2 = getFunctionType(node2);
    	FunctionalJointType fjt = FunctionalJointType.NONE;
    	if (ft1 == FunctionType.MAGNET_N && ft2 == FunctionType.MAGNET_S 
    			|| ft1 == FunctionType.MAGNET_S && ft2 == FunctionType.MAGNET_N) {
    		fjt = FunctionalJointType.MAGNET;
    	}  	
    	if (fjt == FunctionalJointType.NONE) {
    		return null;
    	}

    	// create the joint
    	FunctionalJoint fj = inventory.addFunctionalJoint(item1, item2, node1, node2, FunctionalJointType.MAGNET);
    	functionalJoints.add(fj);
    	return fj;
    }
	
	public void removeFunctionalJoint(Node node1, Node node2) {
		FunctionalJoint fj = inventory.removeFunctionalJoint(node1, node2);
		functionalJoints.remove(fj);
	}
    
    @Override
	public void collision(PhysicsCollisionEvent event) {
		if (event.getObjectA().getCollisionGroup() != COLLISION_GROUP ||
				event.getObjectB().getCollisionGroup() != COLLISION_GROUP) {
			return;
		}
		if (!(event.getNodeA() instanceof Node) || !(event.getNodeB() instanceof Node)) {
			return;
		}
		Node node1 = (Node) event.getNodeA();
		Node node2 = (Node) event.getNodeB();
		tryAddingFunctionalJoint(node1, node2, 150);
	}
    
    public void update(float tpf) {
    	for (FunctionalJoint fj : functionalJoints) {
    		if (fj.node1.getWorldTranslation().distance(fj.node2.getWorldTranslation()) > MAX_DROPOFF_DIST) {
    			removeFunctionalJoint(fj.node1, fj.node2);
    		}
    	}
    }
}
