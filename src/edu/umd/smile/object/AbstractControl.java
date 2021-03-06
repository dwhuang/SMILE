package edu.umd.smile.object;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

public abstract class AbstractControl {
	private static final Logger logger = Logger.getLogger(AbstractControl.class.getName());

	protected Inventory inventory;
	protected Spatial spatial;
	private int state;
	private int overrideState;
	private String prevStateName;
	private LinkedList<AbstractControl> downstreams = new LinkedList<>();
	private LinkedList<String> downstreamIds = new LinkedList<>();
	private Boolean stateChanged = false;
	
	public AbstractControl(Inventory inv, Spatial s) {
		inventory = inv;
		spatial = s;
		state = -1;
		overrideState = -1;
		prevStateName = "";
	}
	
	public void addDownstreamId(String id) {
		downstreamIds.add(id);
	}
	
	public void resolveDownstreamIds(HashMap<String, Spatial> map) {
		downstreams.clear();
		for (String id : downstreamIds) {
			Spatial s = map.get(id);
			if (s != null) {
				downstreams.add(inventory.getControl(s));
			} else {
				logger.log(Level.WARNING, "cannot resolve downstream id " + id);
			}
		}
	}
	
	protected void triggerDownstreams(boolean announce) {
		for (AbstractControl c : downstreams) {
			c.trigger(this, announce);
		}
	}
	
	private void announceStateChanged() {
	    synchronized(stateChanged) {
	        stateChanged = true;
	    }
	}
	
    public void resetStateChanged() {
        synchronized(stateChanged) {
            stateChanged = false;
        }
    }
    
    public boolean isStateChanged() {
        return stateChanged;
    }
    
	public Spatial getSpatial() {
		return spatial;
	}

	protected boolean setOverrideState(int s, boolean announce) {
	   if (s >= -1) {
	      overrideState = s;
         String currStateName = getStateName();
	      if (announce && !currStateName.equals(prevStateName)) {
	          prevStateName = currStateName;
	          announceStateChanged();
	      }
	      return true;
      }
      return false;
   }
	
	protected int getState() {
		if (overrideState > -1)
		   return overrideState;
		else
         return state;
	}
	
    public void saveStates(Map<String, Object> p) {
        p.put("state", state);
        p.put("overrideState", overrideState);
    }
    
	public void restoreStates(Map<String, Object> p) {
		setOverrideState((Integer) p.get("overrideState"), false);
		setState((Integer) p.get("state"), false);
	}
		
	/**
	 * Transition to a new state.
	 * @param state
	 * @param announce
	 * @return state was changed
	 */
	protected boolean setState(int s, boolean announce) {
	    if (canSetState(s)) {
	        state = s;
           String currStateName = getStateName();
	        if (overrideState == -1 && announce && !currStateName.equals(prevStateName)) {
	            prevStateName = currStateName;
	            announceStateChanged();
	        }
	        return true;
	    }
	    return false;
	}
	
	protected boolean canSetState(int s) {
	    return state != s && stateIsValid(s);
	}
	
	/**
	 * Whether a given state is valid
	 * @param s
	 * @return
	 */
	protected boolean stateIsValid(int s) {
	    return s >= 0;
	}
	
	/**
	 * Name of the control. For recorded demonstrations only.
	 * @return
	 */
	public abstract String getName();

    /**
     * Name of the current state. For recorded demonstrations only.
     * @return
     */
    public String getStateName() {
        if (overrideState != -1)
           return "" + overrideState;
        else
           return "" + state;
    }

    /**
     * Trigger an internal state change, which may in turn affect the object's appearance
     * @param o Spatial if triggered by the demonstrator; AbstractControl if by another control
     * @return if the trigger was executed successfully
     */
    public abstract boolean trigger(Object o, boolean announce);

    public abstract boolean isManuallyTriggerable(Geometry g);
}
