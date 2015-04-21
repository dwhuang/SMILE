package tabletop2;

import com.jme3.scene.Spatial;

public abstract class SpatialFunction {
	protected Inventory inventory;
	protected Spatial spatial;
	protected int state;
	
	public SpatialFunction(Inventory inv, Spatial s, int st) {
		inventory = inv;
		spatial = s;
		state = st;
	}
	
	public Spatial getSpatial() {
		return spatial;
	}
	
	public int getState() {
		return state;
	}
	
	public abstract int setState(int st);
	public abstract void trigger(Object o);
}