package edu.umd.smile.util;

import com.jme3.math.Matrix4f;
import com.jme3.math.Transform;


public class TransformUtils {
    public static Matrix4f transformToMatrix(Transform tr, Matrix4f store) {
        if (store == null) {
            store = new Matrix4f();
        }
        store.setTransform(tr.getTranslation(), tr.getScale(), tr.getRotation().toRotationMatrix());
        return store;
    }
    
    public static Transform matrixToTransform(Matrix4f mat, Transform store) {
        if (store == null) {
            store = new Transform();
        }
        store.setTranslation(mat.toTranslationVector());
        store.setRotation(mat.toRotationQuat());
        store.setScale(mat.toScaleVector());
        return store;
    }
    
    public static Transform invertTransform(Transform tr, Transform store) {
        return matrixToTransform(transformToMatrix(tr, null).invert(), store);
    }
}
