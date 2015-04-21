/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.util;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Spatial;

/**
 * Bug fix for RigidBodyControl in JME3
 * @author dwhuang
 *
 */
public class MyRigidBodyControl extends RigidBodyControl {
    
    public MyRigidBodyControl(float mass) {
        super(mass);
    }
    
    public MyRigidBodyControl(CollisionShape shape, float mass) {
        super(shape, mass);
    }

    @Override
    public void setKinematic(boolean kinematic) {
        this.kinematic = kinematic;
        if (kinematic) {
            rBody.setCollisionFlags(rBody.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
            rBody.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        } else {
            rBody.setCollisionFlags(rBody.getCollisionFlags() & ~CollisionFlags.KINEMATIC_OBJECT);
            // use forceActivationState instead of setActivationState
            // otherwise once the state is set to DISABLE_DEACTIVATION, it can
            // never set back to ACTIVE_TAG
            rBody.forceActivationState(CollisionObject.ACTIVE_TAG);
        }
        rBody.activate();
    }
    
    public void forceActivationState(int state) {
        rBody.forceActivationState(state);
    }
    
    public int getActivationState() {
    	return rBody.getActivationState();
    }
    
    public Spatial getSpatial() {
        return spatial;
    }
    
    public void changeMass(float mass) {
    	setMass(mass);
    	PhysicsSpace ps = this.getPhysicsSpace();
    	ps.remove(this);
    	ps.add(this);
    }
    
//    @Override
//    public Control cloneForSpatial(Spatial spatial) {
//    	// copy from super class (RigidBodyControl.java)
//    	MyRigidBodyControl control = new MyRigidBodyControl(collisionShape, mass);
//        control.setAngularFactor(getAngularFactor());
//        control.setAngularSleepingThreshold(getAngularSleepingThreshold());
//        control.setCcdMotionThreshold(getCcdMotionThreshold());
//        control.setCcdSweptSphereRadius(getCcdSweptSphereRadius());
//        control.setCollideWithGroups(getCollideWithGroups());
//        control.setCollisionGroup(getCollisionGroup());
//        control.setDamping(getLinearDamping(), getAngularDamping());
//        control.setFriction(getFriction());
//        control.setGravity(getGravity());
//        control.setKinematic(isKinematic());
//        control.setKinematicSpatial(isKinematicSpatial());
//        control.setLinearSleepingThreshold(getLinearSleepingThreshold());
//        control.setPhysicsLocation(getPhysicsLocation(null));
//        control.setPhysicsRotation(getPhysicsRotationMatrix(null));
//        control.setRestitution(getRestitution());
//
//        if (mass > 0) {
//            control.setAngularVelocity(getAngularVelocity());
//            control.setLinearVelocity(getLinearVelocity());
//        }
//        control.setApplyPhysicsLocal(isApplyPhysicsLocal());
//
//        control.setSpatial(spatial);
//        return control;
//    }
}
