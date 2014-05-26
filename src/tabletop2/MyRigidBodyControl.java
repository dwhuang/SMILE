/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
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
        super (shape, mass);
    }

    @Override
    public void setKinematic(boolean kinematic) {
        this.kinematic = kinematic;
        if (kinematic) {
            rBody.setCollisionFlags(rBody.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
            rBody.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        } else {
            rBody.setCollisionFlags(rBody.getCollisionFlags() & ~CollisionFlags.KINEMATIC_OBJECT);
            // use forceActivationState instead of forceActivationState
            // otherwise once the state is set to DISABLE_DEACTIVATION, it can
            // never set back to ACTIVE_TAG
            rBody.forceActivationState(CollisionObject.ACTIVE_TAG);
        }
        rBody.activate();
    }
    
    public void forceActivationState(int state) {
        rBody.forceActivationState(state);
    }
    
    public Spatial getSpatial() {
        return spatial;
    }
}
