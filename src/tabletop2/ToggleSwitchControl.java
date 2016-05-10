package tabletop2;

import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class ToggleSwitchControl extends StateControl {
	static final Quaternion[] rot = new Quaternion[2];
	int numStates;
	
	public ToggleSwitchControl(Inventory inv, Spatial spatial, float angle, boolean leftPressed,
			int numStates, int initState) {
		super(inv, spatial);
		rot[0] = new Quaternion();
		rot[1] = new Quaternion().fromAngles(0, 0, -angle);
		if (leftPressed) {
			setVisibleState(0, false);
		} else {
			setVisibleState(1, false);
		}
		this.numStates = numStates;
		if (initState < 0 || initState >= numStates) {
			state = 0;
		} else {
			state = initState;
		}
	}
	
	@Override
	protected boolean setVisibleState(int vs, boolean notify) {
		if (super.setVisibleState(vs, notify)) {
			spatial.setLocalRotation(rot[vs]);
			return true;
		}
		return false;
	}
	
	@Override
	public void trigger(Object o, boolean notify) {
		if (o instanceof Geometry) {
			Geometry g = (Geometry) o;
			int buttonPressed = ((Node) spatial).getChildIndex(g);
			if (setVisibleState(buttonPressed, notify)) {
				state = (state + 1) % numStates;
				triggerDownstreams(notify);
			}
		} else if (o instanceof StateControl) {
			StateControl c = (StateControl) o;
			int st = c.getState();
			if (st >= 0 && st < numStates && st != state) {
				state = st;
				triggerDownstreams(notify);
			}
		}
	}
	
	@Override
	public String getType() {
	    return "toggleSwitch";
	}
}
