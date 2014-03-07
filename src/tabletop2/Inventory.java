/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author dwhuang
 */
public class Inventory {
    private HashMap<Geometry, Spatial> items = new HashMap<Geometry, Spatial>();
    private HashSet<Spatial> itemsMarkedForRemoval = new HashSet<Spatial>();
    
    public void addItem(Spatial item) {
        if (item instanceof Geometry) {
            items.put((Geometry) item, item);
        } else if (item instanceof Node) {
            final Node itemNode = (Node) item;
            // collect and save all geometries under itemNode
            itemNode.depthFirstTraversal(new SceneGraphVisitor() {
                public void visit(Spatial s) {
                    if (s instanceof Geometry) {
                        items.put((Geometry) s, itemNode);
                    }
                }
            });
        } else {
            throw new IllegalArgumentException(item.getName());
        }
    }
    
    public void markItemForRemoval(Spatial item) {
        itemsMarkedForRemoval.add(item);
    }
    
    public void purge() {
        Iterator<Map.Entry<Geometry, Spatial>> itr = items.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<Geometry, Spatial> entry = itr.next();
            if (itemsMarkedForRemoval.contains(entry.getValue())) {
                itr.remove();
            }
        }
        itemsMarkedForRemoval.clear();
    }
    
    public Spatial getItem(Geometry g) {
        return items.get(g);
    }
    
    public Collection<Spatial> allItems() {
        return items.values();
    }

}
