package edu.umd.smile.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import edu.umd.smile.util.MyRigidBodyControl;

public class CustomControl extends AbstractControl {
    private class State {
        String name;
        List<Spatial> spatials;
        State(String name, List<Spatial> spatials) {
            this.name = name;
            this.spatials = new LinkedList<>(spatials);
        }
        void engage() {
            MyRigidBodyControl rbc = ((Node) spatial).getControl(MyRigidBodyControl.class);
            if (rbc != null) {
                inventory.wakeNearbyItems(spatial, 1.05f);
            }
            ((Node) spatial).detachAllChildren();
            for (Spatial s : spatials) {
                ((Node) spatial).attachChild(s);
            }
            if (rbc != null) {
                rbc.updateForSpatialChange();
            }
        }
    }
    
    protected List<State> states = new ArrayList<>();

    public CustomControl(Inventory inv, Spatial s, int initState, List<List<Spatial>> stateSpatials,
            List<String> stateNames) {
        super(inv, s);
        
        Iterator<List<Spatial>> itr1 = stateSpatials.iterator();
        Iterator<String> itr2 = stateNames.iterator();
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
        if (stateIsValid(getState())) {
            return states.get(getState()).name;
        }
        return "";
    }
    
    private int findNextStateForGeometry(Geometry g) {
        Spatial s = g;
        while (s != spatial) {
            Integer ns = s.getUserData("nextStateWhenTriggered");
            if (ns != null) {
                return ns;
            }
            s = s.getParent();
        }
        return -1;
    }

    @Override
    public boolean trigger(Object o, boolean announce) {
        if (o instanceof Geometry) {
            int ns = findNextStateForGeometry((Geometry) o);
            if (setState(ns, announce)) {
                triggerDownstreams(announce);
                return true;
            }
        } else if (o instanceof AbstractControl) {
            AbstractControl c = (AbstractControl) o;
            if (setState(c.getState(), announce)) {
                triggerDownstreams(announce);
                return true;
            }
        }
        return false;
    }
}
