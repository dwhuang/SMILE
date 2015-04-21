/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import tabletop2.Demonstrator.HandId;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public interface DemoActionListener {
    public void demoGrasp(HandId handId, Spatial s, Vector3f pos, Quaternion rot);
    public void demoRelease(HandId handId);
    public void demoDestroy(HandId handId);
	public void demoTrigger(HandId handId, Spatial s);
}
