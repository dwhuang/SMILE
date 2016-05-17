package edu.umd.smile.object;

import com.jme3.math.Quaternion;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class ToggleSwitchControl extends AbstractControl {
	static final Quaternion[] rot = new Quaternion[2];
	int vstate;
	int numStates;
	
	public ToggleSwitchControl(Inventory inv, Spatial spatial, float angle, boolean leftPressed,
			int numStates, int initState) {
		super(inv, spatial);
		rot[0] = new Quaternion();
		rot[1] = new Quaternion().fromAngles(0, 0, -angle);

		if (leftPressed) {
		    vstate = 0;
		} else {
		    vstate = 1;
		}
		this.numStates = numStates;
		
		if (!stateIsValid(initState)) {
	        throw new IllegalArgumentException("Invalid init state: " + initState);
		}
	    setState(initState, false);
        updateTogglePosition();
	}
	
	protected void updateTogglePosition() {
        spatial.setLocalRotation(rot[vstate]);
	}
	
	@Override
	protected boolean stateIsValid(int s) {
	    return s >= 0 && s < numStates;
	}
	
    @Override
    public String getName() {
        return "toggleSwitch";
    }
    
    @Override
    public String getStateName() {
        return "" + vstate;
    }

	@Override
	public boolean trigger(Object o, boolean announce) {
		if (o instanceof Geometry) {
			Geometry g = (Geometry) o;
			int buttonPressed = ((Node) spatial).getChildIndex(g);
			if (buttonPressed != vstate) {
			    vstate = buttonPressed;
			    updateTogglePosition();
			    int s = (getState() + 1) % numStates;
			    setState(s, true);
			    triggerDownstreams(announce);
			    return true;
			}
		} else if (o instanceof AbstractControl) {
			AbstractControl c = (AbstractControl) o;
			int s = c.getState();
			if (setState(s, true)) {
				triggerDownstreams(announce);
				return true;
			}
		}
		return false;
	}
}
