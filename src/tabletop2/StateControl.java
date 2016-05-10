package tabletop2;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.scene.Spatial;

public abstract class StateControl {
	private static final Logger logger = Logger.getLogger(StateControl.class.getName());

	private Inventory inventory;
	protected Spatial spatial;
	protected int state;
	protected int visibleState;
	protected LinkedList<StateControl> downstreams = new LinkedList<>();
	protected LinkedList<String> downstreamIds = new LinkedList<>();
	
	public StateControl(Inventory inv, Spatial s) {
		inventory = inv;
		spatial = s;
		state = -1;
		visibleState = -1;
	}
	
	public void addDownstreamId(String id) {
		downstreamIds.add(id);
	}
	
	public void resolveDownstreamIds(HashMap<String, Spatial> map) {
		downstreams.clear();
		for (String id : downstreamIds) {
			Spatial s = map.get(id);
			if (s != null) {
				downstreams.add(inventory.getDeepestStateControlFromSpatial(s));
			} else {
				logger.log(Level.WARNING, "cannot resolve downstream id " + id);
			}
		}
	}
	
	protected void triggerDownstreams(boolean notify) {
		for (StateControl c : downstreams) {
			c.trigger(this, notify);
		}
	}
	
	protected void notifyVisibleStateChanged() {
		inventory.notifyStateChanged(this);
	}
	
	public Spatial getSpatial() {
		return spatial;
	}
	
	public int getState() {
		return state;
	}
	
	public void restoreStates(int state, int visibleState) {
		this.state = state;
		setVisibleState(visibleState, false);
	}
	
	/**
	 * Set the appearance of the object
	 * @param vs Visible state
	 * @return state changed
	 */
	protected boolean setVisibleState(int vs, boolean notify) {
		if (visibleState != vs) {
			visibleState = vs;
			if (notify) {
				inventory.notifyStateChanged(this);
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Trigger an internal state change, which may in turn affect the object's appearance
	 * @param o
	 */
	public abstract void trigger(Object o, boolean notify);

	public abstract String getType();
	
	public int getVisibleState() {
	    return visibleState;
	}
	
    public String getVisibleStateString() {
        return "" + visibleState;
    }
}