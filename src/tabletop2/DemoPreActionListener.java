package tabletop2;

import com.jme3.scene.Spatial;

import tabletop2.Demonstrator.HandId;

public interface DemoPreActionListener {
    public void demoPreGrasp(HandId handId);
    public void demoPreRelease(HandId handId);
    public void demoPreDestroy(HandId handId);
    public void demoPreTrigger(HandId handId, Spatial s);
}
