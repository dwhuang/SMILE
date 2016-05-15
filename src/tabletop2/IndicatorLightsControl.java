package tabletop2;

import java.util.ArrayList;
import java.util.List;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class IndicatorLightsControl extends StateControl {
	List<Geometry> lights = new ArrayList<>();
	List<ColorRGBA[]> lightStates;
	List<String> lightStateNames;
	
	public IndicatorLightsControl(Inventory inv, Spatial s, int initState, List<ColorRGBA[]> lightStates,
	        List<String> lightStateNames) {
		super(inv, s);
		// get all lights (Geometry)
		for (Spatial sl : ((Node) s).getChildren()) {
			if (sl instanceof Geometry) {
				lights.add((Geometry) sl);
			}
		}
		
		this.lightStates = lightStates;
		this.lightStateNames = lightStateNames;
		// sanity check
		for (ColorRGBA[] colors : lightStates) {
			if (colors.length != lights.size()) {
				throw new IllegalArgumentException("lightStates does not have "
						+ lights.size() + " elements: " + colors.length);
			}
		}
		
		setState(initState, false);
	}
	
	public void setState(int s, boolean notify) {
		if (s >= 0 && s < lightStates.size() && state != s) {
			state = s;
			setVisibleState(s, notify);
		}
	}
	
	@Override
	public boolean setVisibleState(int vs, boolean notify) {
		if (super.setVisibleState(vs, notify)) {
			((Node) spatial).detachAllChildren();
			ColorRGBA[] ls = lightStates.get(vs);
			for (int i = 0; i < ls.length; ++i) {
				if (ls[i] != null) {
					Geometry light = lights.get(i);
					((Node) spatial).attachChild(light);
					// change light color
					Material mat = light.getMaterial();
			        mat.setColor("Ambient", ls[i].mult(0.8f));
			        mat.setColor("Diffuse", ls[i]);
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void trigger(Object o, boolean notify) {
		if (o instanceof StateControl) {
			StateControl c = (StateControl) o;
			setState(c.getState(), notify);
            triggerDownstreams(notify);
		}
	}

    @Override
    public String getVisibleStateString() {
        return lightStateNames.get(visibleState);
    }

    @Override
    public String getType() {
        return "indicatorLights";
    }
}
