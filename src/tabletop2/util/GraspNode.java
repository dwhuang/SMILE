package tabletop2.util;

import java.util.ArrayList;
import java.util.Set;

import tabletop2.Inventory;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.math.Matrix4f;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class GraspNode extends Node {
//	private final Inventory inventory;
	private ArrayList<Spatial> graspedChildren = new ArrayList<Spatial>();
	
	private transient Matrix4f mat = new Matrix4f();
	
//	public GraspNode(final String name, final Inventory inventory) {
//		super(name);
//		this.inventory = inventory;
//	}
//	
//	public void attach(Spatial obj) {
//		this.getLocalToWorldMatrix(mat);
//		mat.invertLocal();
//		Transform myInvTrans = new Transform(mat.toTranslationVector(), mat.toRotationQuat());
//		obj.setLocalTransform(obj.getLocalTransform().combineWithParent(myInvTrans));
//		
//		obj.getParent().detachChild(obj);
//		super.attachChild(obj);
//		graspedChildren.add(obj);
//		
//        MyRigidBodyControl rbc = obj.getControl(MyRigidBodyControl.class);
//        rbc.setMass(rbc.getMass() + 9999);
//        rbc.setKinematic(true);
//
//        // wake up connected (by joints) items and prevent them from going into sleep,
//        // so that they are aware of any translation/rotation occurring at the grasped item.
//        Set<PhysicsJoint> joints = inventory.getPhysicsJointsForItem(obj);
//        if (joints != null) {
//            for (PhysicsJoint joint : joints) {
//                MyRigidBodyControl c = (MyRigidBodyControl)joint.getBodyA();
//                c.forceActivationState(CollisionObject.DISABLE_DEACTIVATION);
//                c.activate();
//                c = (MyRigidBodyControl)joint.getBodyB();
//                c.forceActivationState(CollisionObject.DISABLE_DEACTIVATION);
//                c.activate();
//            }
//        }
//	}	
}
