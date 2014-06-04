/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.util;

import com.jme3.input.FlyByCamera;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 *
 * @author dwhuang
 */
public class CameraControl extends FlyByCamera {
    private Vector3f vec = new Vector3f();
    
    public CameraControl(Camera cam) {
        super(cam);
    }
    
    public void move(float forward, float left) {
        if (forward != 0) {
            moveCamera(forward * moveSpeed, false);
        }
        if (left != 0) {
            moveCamera(left * moveSpeed, true);
        }
    }
    
    public void rise(float up) {
        if (up != 0) {
            riseCamera(up * moveSpeed);
        }
    }
    
    public void rotate(float up, float left) {
        if (up != 0) {
            cam.getLeft(vec);
            rotateCamera(-up * rotationSpeed, vec);
        }
        if (left != 0) {
            rotateCamera(left * rotationSpeed, Vector3f.UNIT_Y);
        }
    }
}
