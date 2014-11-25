package tabletop2;

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
    public static final String FUNC_SPOT_SCREWHEAD = "screwHead";
    public static final String FUNC_SPOT_SCREWDRIVERTIP = "screwdriverTip";
    
    private Inventory inventory;
    
    public enum FunctionalRel {
    	MAGNET
    }
    
    public FunctionalItemPhysics(MainApp app) {
    	this.inventory = app.getInventory();
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
		if (inventory.getFuncSpotJoint(node1, node2) != null) {
			if (node1.getWorldTranslation().distance(node2.getWorldTranslation()) 
					> COLLISION_SPHERE_RADIUS * 1.8f) {
				inventory.removeFuncSpotJoint(node1, node2);
			}
		} else {	
			if (testFunctionalRel(FunctionalRel.MAGNET, node1, node2) && testAngle(150, node1, node2)) {
				if (inventory.canAddFuncSpotJoint(node1, node2)) {
					inventory.addFuncSpotJoint(FunctionalRel.MAGNET, node1, node2);
				}
			}
		}
	}
    
    public void update(float tpf) {
    	
    }

    private boolean testFunctionalRel(FunctionalRel r, Spatial s1, Spatial s2) {
    	if (r == FunctionalRel.MAGNET) {
    		String fsType1 = s1.getUserData("functionalSpotType");
    		String fsType2 = s2.getUserData("functionalSpotType");
    		if ((fsType1.equals(FUNC_SPOT_SCREWDRIVERTIP) && fsType2.equals(FUNC_SPOT_SCREWHEAD)) || 
    				(fsType2.equals(FUNC_SPOT_SCREWDRIVERTIP) && fsType1.equals(FUNC_SPOT_SCREWHEAD))) {
    			return true;
    		}
    		return false;
    	}
    	return false;
    }
    
    // in degrees
    private boolean testAngle(float minDeg, Spatial s1, Spatial s2) {
    	Vector3f v1 = s1.getWorldRotation().mult(Vector3f.UNIT_Z);
    	Vector3f v2 = s2.getWorldRotation().mult(Vector3f.UNIT_Z);
    	if (v1.angleBetween(v2) * FastMath.RAD_TO_DEG >= minDeg) {
    		return true;
    	}
    	return false;
    }
    
}
