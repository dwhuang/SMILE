package edu.umd.smile.util;

import java.util.HashMap;

import com.jme3.math.Matrix3f;

public interface MyJoint {
    public enum Type {
        Slider, SixDof
    }
    public Type getType();
    public Matrix3f getRotA();
    public Matrix3f getRotB();
    /**
     * Used by the memento for the undo function
     * @param p
     */
    public void saveParam(HashMap<String, Object> p);
    public void loadParam(HashMap<String, Object> p);
    public String toParamString();
}
