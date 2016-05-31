package edu.umd.smile.object;

import com.jme3.scene.Spatial;

public interface InventoryListener {
	void objectCreated(Spatial obj);
	void objectDeleted(Spatial obj);
    void objectControlInitialized(Spatial obj, AbstractControl c);
}
