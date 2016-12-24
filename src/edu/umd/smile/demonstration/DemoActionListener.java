/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.demonstration;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import edu.umd.smile.demonstration.Demonstrator.HandId;
import edu.umd.smile.object.ObjectBondTracker.ObjectBond;

/**
 *
 * @author dwhuang
 */
public interface DemoActionListener {
    public void demoGrasp(HandId handId, Spatial s, Vector3f pos, Quaternion rot);
    public void demoRelease(HandId handId);
    public void demoDestroy(HandId handId);
	public void demoTrigger(HandId handId, Spatial s);
	public void demoPointTo(HandId handId, Spatial s);
	public void demoFasten(HandId handId, ObjectBond bond);
	public void demoLoosen(HandId handId, ObjectBond bond);
}
