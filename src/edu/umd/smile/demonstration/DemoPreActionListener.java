package edu.umd.smile.demonstration;

import com.jme3.scene.Spatial;

import edu.umd.smile.demonstration.Demonstrator.HandId;

public interface DemoPreActionListener {
    public void demoPreGrasp(HandId handId);
    public void demoPreRelease(HandId handId);
    public void demoPreDestroy(HandId handId);
    public void demoPreTrigger(HandId handId, Spatial s);
}
