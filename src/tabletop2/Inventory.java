/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public class Inventory {
	private Node rootNode;
	private BulletAppState bulletAppState;
	
    private HashMap<Geometry, Spatial> itemsByGeo = new HashMap<Geometry, Spatial>();
    private HashMap<Spatial, HashSet<PhysicsJoint>> jointsForItem 
            = new HashMap<Spatial, HashSet<PhysicsJoint>>();
    
    public Inventory(MainApp app) {
    	rootNode = app.getRootNode();
    	bulletAppState = app.getBulletAppState();
    }
    
    public void addItem(Spatial item) {
        if (item instanceof Geometry) {
            itemsByGeo.put((Geometry) item, item);
        } else if (item instanceof Node) {
            final Node itemNode = (Node) item;
            // collect and save all geometries under itemNode
            itemNode.depthFirstTraversal(new SceneGraphVisitor() {
                public void visit(Spatial s) {
                    if (s instanceof Geometry) {
                        itemsByGeo.put((Geometry) s, itemNode);
                    }
                }
            });
        } else {
            throw new IllegalArgumentException(item.getName());
        }
    }
    
    public void removeItems(Collection<Spatial> itemsToBeRemoved) {
        Iterator<Map.Entry<Geometry, Spatial>> itr = itemsByGeo.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<Geometry, Spatial> entry = itr.next();
            if (itemsToBeRemoved.contains(entry.getValue())) {
                itr.remove();
            }
        }
        
        for (Spatial s : itemsToBeRemoved) {
            removeItemPhysics(s);
            rootNode.detachChild(s);
        }
    }
    
    public void removeItem(Spatial item) {
    	HashSet<Spatial> tmpItemSet = new HashSet<Spatial>();
        tmpItemSet.add(item);
        removeItems(tmpItemSet);
    }
    
    private void removeItemPhysics(Spatial item) {
        MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
        if (rbc != null) {
            bulletAppState.getPhysicsSpace().remove(rbc);
            item.removeControl(rbc);
        }

        // remove joints associated with item
        Set<PhysicsJoint> jointsToBeRemoved = getPhysicsJointsForItem(item);
        if (jointsToBeRemoved != null) {
            for (PhysicsJoint joint : jointsToBeRemoved) {
                removePhysicsJoint(joint);
            }
        }
    }
    
    public void removeAllFreeItems() {
    	HashSet<Spatial> tmpItemSet = new HashSet<Spatial>();
        for (Spatial item : allItems()) {
            if (item.getParent() == rootNode) {
                tmpItemSet.add(item);
            }
        }
        removeItems(tmpItemSet);
    }
    
    public Spatial getItem(Geometry g) {
        return itemsByGeo.get(g);
    }
    
    public Collection<Spatial> allItems() {
        return itemsByGeo.values();
    }

    public void addPhysicsJoint(PhysicsJoint joint) {
        Spatial item1 = ((MyRigidBodyControl)joint.getBodyA()).getSpatial();
        Spatial item2 = ((MyRigidBodyControl)joint.getBodyB()).getSpatial();
        boolean item1Exists = itemsByGeo.values().contains(item1);
        boolean item2Exists = itemsByGeo.values().contains(item2);
        if (!item1Exists && !item2Exists) {
            throw new IllegalArgumentException("both spatials are not valid items: " + item1 + " and " + item2);
        }
        
        HashSet<PhysicsJoint> joints;
        if (item1Exists) {
            joints = jointsForItem.get(item1);
            if (joints == null) {
                joints = new HashSet<PhysicsJoint>();
                jointsForItem.put(item1, joints);
            }
            joints.add(joint);
        }
        
        if (item2Exists) {
            joints = jointsForItem.get(item2);
            if (joints == null) {
                joints = new HashSet<PhysicsJoint>();
                jointsForItem.put(item2, joints);
            }
            joints.add(joint);        
        }
    }
    
    public Set<PhysicsJoint> getPhysicsJointsForItem(Spatial item) {
        HashSet<PhysicsJoint> joints = jointsForItem.get(item);
        if (joints == null) {
            return null;
        } else {
            return new HashSet<PhysicsJoint>(joints);
        }
    }
    
    private void removePhysicsJoint(PhysicsJoint joint) {
        HashSet<Spatial> itemsToBeRemoved = new HashSet<Spatial>();
        for (Map.Entry<Spatial, HashSet<PhysicsJoint>> e : jointsForItem.entrySet()) {
            e.getValue().remove(joint); // may fail because e.getValue() might not contain the joint, but it's OK
            if (e.getValue().isEmpty()) {
                itemsToBeRemoved.add(e.getKey());
            }
        }
        
        for (Spatial item : itemsToBeRemoved) {
            jointsForItem.remove(item);
        }

        bulletAppState.getPhysicsSpace().remove(joint);
    }
}
