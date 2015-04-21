package tabletop2;

import com.jme3.scene.Spatial;

public interface InventoryListener {
	void objectCreated(Spatial obj);
	void objectDeleted(Spatial obj);
	void objectTriggered(Spatial obj, String name, int state);
}
