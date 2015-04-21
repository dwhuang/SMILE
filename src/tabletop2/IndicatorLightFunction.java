package tabletop2;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class IndicatorLightFunction extends SpatialFunction {
	Node node;
	int numLights;
	Spatial[] lights;
	int logic = -1;
	
	public IndicatorLightFunction(Inventory inv, Spatial s) {
		super(inv, s, -1);
		node = (Node) s;
		numLights = node.getChildren().size();
		lights = new Spatial[numLights];
		for (int i = 0; i < lights.length; ++i) {
			lights[i] = node.getChild(i);
		}
		node.detachAllChildren(); // dim all lights
	}
	
	@Override
	public int setState(int st) {
		if (state == st) {
			return state;
		}
		node.detachAllChildren();
		state = st;
		if (state >= 0 && state < lights.length) {
			node.attachChild(lights[state]);
			logic = state;
		}
		
		// notification
		inventory.notifySpatialFunctionTriggered(this);
		
		return state;
	}

	@Override
	public void trigger(Object o) {
		if (o instanceof SwitchFunction) {
			SwitchFunction sw = (SwitchFunction) o;
			if (sw.getState() == 0) {
				setState(-1);
			} else if (sw.getState() == 1) {
				logic = (logic + 1) % numLights;
				setState(logic);
			}
		}
	}

}
