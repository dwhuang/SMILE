package tabletop2;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class SwitchFunction extends SpatialFunction {
	SpatialFunction targetFunction = null;
	static final Quaternion rot1 = new Quaternion().fromAngles(0, 0, -3 * FastMath.DEG_TO_RAD);
	static final Quaternion rot2 = new Quaternion().fromAngles(0, 0, 3 * FastMath.DEG_TO_RAD);
	
	public SwitchFunction(Inventory inv, Spatial spatial, SpatialFunction targFunc) {
		super(inv, spatial, -1);
		this.targetFunction = targFunc;
		setState(1);
	}
	
	@Override
	public int setState(int st) {
		if (state == st) {
			return state;
		}
		state = st;
		if (st == 0) {
			spatial.setLocalRotation(rot1);
		} else {
			spatial.setLocalRotation(rot2);
		}
		
		// notifications
		inventory.notifySpatialFunctionTriggered(this);
		if (targetFunction != null) {
			targetFunction.trigger(this);
		}
		
		return state;
	}
	
	@Override
	public void trigger(Object o) {
		if (o instanceof Geometry) {
			Geometry g = (Geometry) o;
			if (((Node) spatial).getChildIndex(g) == 1) {
				setState(0);
			} else {
				setState(1);
			}
		}
	}
}
