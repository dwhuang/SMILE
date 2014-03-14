/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author dwhuang
 */
public class Inventory {
    private HashMap<Geometry, Spatial> itemsByGeo = new HashMap<Geometry, Spatial>();
    private HashMap<Spatial, HashSet<PhysicsJoint>> jointsForItem 
            = new HashMap<Spatial, HashSet<PhysicsJoint>>();
    
    private transient HashSet<Spatial> items = new HashSet<Spatial>();
    
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
            Set<PhysicsJoint> jointsToBeRemoved = getPhysicsJointsForItem(s);
            if (jointsToBeRemoved != null) {
                for (PhysicsJoint joint : jointsToBeRemoved) {
                    removePhysicsJoint(joint);
                }
            }
        }
    }
    
    public void removeItem(Spatial item) {
        items.clear();
        items.add(item);
        removeItems(items);
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
        
        HashSet joints;
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
            e.getValue().remove(joint);
            if (e.getValue().isEmpty()) {
                itemsToBeRemoved.add(e.getKey());
            }
        }
        
        for (Spatial item : itemsToBeRemoved) {
            jointsForItem.remove(item);
        }
    }
}
