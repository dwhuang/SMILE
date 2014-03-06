/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public interface DemonstrationListener {
    public void demoGrasp(Spatial s, Vector3f pos, Quaternion rot);
    public void demoRelease();
}
