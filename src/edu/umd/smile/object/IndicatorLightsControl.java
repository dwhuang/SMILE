package edu.umd.smile.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class IndicatorLightsControl extends AbstractControl {
    protected class State {
        String name;
        ColorRGBA[] colors;
        protected State(String name, ColorRGBA[] colors) {
            // sanity check
            if (colors.length != lights.size()) {
                throw new IllegalArgumentException("lightStates does not have "
                        + lights.size() + " elements: " + colors.length);
            }
            this.name = name;
            this.colors = colors;
        }
        protected void engage() {
            ((Node) spatial).detachAllChildren();
            for (int i = 0; i < colors.length; ++i) {
                if (colors[i] != null) {
                    Geometry light = lights.get(i);
                    ((Node) spatial).attachChild(light);
                    // change light color
                    Material mat = light.getMaterial();
                    mat.setColor("Ambient", colors[i].mult(0.8f));
                    mat.setColor("Diffuse", colors[i]);
                }
            }
        }
    }
    
	List<Geometry> lights = new ArrayList<>();
	List<State> states = new ArrayList<>();
	
	public IndicatorLightsControl(Inventory inv, Spatial s, int initState, List<ColorRGBA[]> lightStates,
	        List<String> lightStateNames) {
		super(inv, s);
		// get all lights (Geometry)
		for (Spatial sl : ((Node) s).getChildren()) {
			if (sl instanceof Geometry) {
				lights.add((Geometry) sl);
			}
		}

		Iterator<ColorRGBA[]> itr1 = lightStates.iterator();
		Iterator<String> itr2 = lightStateNames.iterator();
		while (itr1.hasNext() && itr2.hasNext()) {
		    states.add(new State(itr2.next(), itr1.next()));
		}
		
		setState(initState, false);
	}

	@Override
	protected boolean setState(int s, boolean announce) {
		if (super.setState(s, announce)) {
		    states.get(getState()).engage();
		    return true;
		}
		return false;
	}
	
	@Override
	protected boolean stateIsValid(int s) {
	    return s >= 0 && s < states.size();
	}
	
    @Override
    public String getName() {
        return "indicatorLights";
    }

    @Override
    public String getStateName() {
        if (stateIsValid(getState())) {
            return states.get(getState()).name;
        }
        return "";
    }

    @Override
    public boolean trigger(Object o, boolean announce) {
        if (o instanceof AbstractControl) {
            AbstractControl c = (AbstractControl) o;
            if (setState(c.getState(), announce)) {
                triggerDownstreams(announce);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isManuallyTriggerable(Geometry g) {
        return false;
    }

}
