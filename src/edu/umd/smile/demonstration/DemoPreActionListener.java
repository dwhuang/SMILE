package edu.umd.smile.demonstration;

import edu.umd.smile.demonstration.Demonstrator.HandId;

public interface DemoPreActionListener {
    public void demoPreGrasp(HandId handId);
    public void demoPreRelease(HandId handId);
    public void demoPreDestroy(HandId handId);
    public void demoPreFasten(HandId handId);
    public void demoPreLoosen(HandId handId);
}
