/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Node;

/**
 *
 * @author dwhuang
 */
public class JointState {
    protected Node node = new Node();
    protected float velocity = 0;
    protected float minAngle = 0;
    protected float maxAngle = 0;
    protected int manualControlOrientation = 1;
    
    private float[] angles = new float[3];
    private Quaternion quat = new Quaternion();

    public JointState(float angle, float minAngle, float maxAngle, int manualControlOrientation) {
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.manualControlOrientation = manualControlOrientation;
        node.getLocalRotation().toAngles(angles);
        setAngle(angle);
    }

    public Node getNode() {
        return node;
    }
    
    public void setVelocity(float v, boolean isManualControl) {
        if (isManualControl && manualControlOrientation < 0) {
            velocity = -v;
        } else {
            velocity = v;
        }
    }

    public float getAngle() {
        return angles[1];
    }

    public final void setAngle(float ang) {
        angles[1] = ang;
        angles[1] = FastMath.clamp(angles[1], minAngle, maxAngle);
        quat.fromAngles(angles);
        node.setLocalRotation(quat);
    }

    public boolean update(float tpf) {
        if (velocity != 0) {
            float f = getAngle();
            f += velocity * tpf;
            setAngle(f);
            velocity = 0;
            return true;
        }
        return false;
    }
}
