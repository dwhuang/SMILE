package edu.umd.smile.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

import edu.umd.smile.MainApp;
import edu.umd.smile.demonstration.Demonstrator;
import edu.umd.smile.util.MyRigidBodyControl;
import edu.umd.smile.util.MySixDofJoint;
import edu.umd.smile.util.TransformUtils;

public class ObjectBondTracker {
    public class ObjectBond {
        protected Node host;
        protected Node guest;
        protected Spatial hostItem;
        protected Spatial guestItem;
        protected List<Transform> hostRelTrs = new ArrayList<>(); // bond point transform relative to the item
        protected Transform guestRelTr;
        protected int tightness = 0;
        protected int maxTightness;

        public ObjectBond(Node host, Node guest) {
            this.host = host;
            this.guest = guest;
            init();
        }
        
        protected ObjectBond(Node host, Node guest, int tightness) {
            this(host, guest);
            this.tightness = tightness;
        }
        
        private void init() {
            this.hostItem = inventory.getItem(host);
            this.guestItem = inventory.getItem(guest);
            this.hostRelTrs.add(getRelativeTransform(host, hostItem, new Transform()));
            for (Spatial kid : host.getChildren()) {
                if (kid instanceof Node) {
                    this.hostRelTrs.add(getRelativeTransform((Node) kid, hostItem, new Transform()));
                }
            }
            Quaternion gExtraRot = new Quaternion();
            gExtraRot.fromAngleAxis(FastMath.PI, Vector3f.UNIT_X);
            this.guestRelTr = getRelativeTransform(guest, guestItem, new Transform(gExtraRot));
            this.maxTightness = hostRelTrs.size() - 1;
            if (maxTightness < 1) {
                throw new IllegalStateException("maxTightness = " + maxTightness);
            }
        }
        
        private Transform getRelativeTransform(Node bondPoint, Spatial whole, Transform extra) {
            Transform tr = extra;
            Spatial s = bondPoint;
            while (s != null) {
                if (s == whole) {
                    return tr;
                }
                tr.combineWithParent(s.getLocalTransform());
                s = s.getParent();
            }
            throw new IllegalArgumentException("'bondPoint' is not a part of 'whole'");
        }
        
        public Spatial getHostItem() {
            return hostItem;
        }
        
        public Node getHostBondPoint() {
            return host;
        }
        
        public Spatial getGuestItem() {
            return guestItem;
        }
        
        public Node getGuestBondPoint() {
            return guest;
        }
        
        public int getTightness() {
            return tightness;
        }
        
        public void fasten() {
            if (!isFastenable()) {
                throw new IllegalStateException("Object bond between '"
                        + host + "' and '" + guest + "' cannot be fastened");
            }
            if (tightness == 0) {
                addBond(this);
            }
            setTightness(tightness + 1);
        }
        
        public void loosen() {
            if (!isLoosenable()) {
                throw new IllegalStateException("Object bond between '"
                        + host + "' and '" + guest + "' cannot be loosened");
            }
            setTightness(tightness - 1);
            if (tightness == 0) {
                removeBond(this);
            }
        }
        
        public boolean isFastenable() {
            return tightness < maxTightness;
        }

        public boolean isLoosenable() {
            return tightness > 0;
        }
        
        /**
         * update physics (joint and guest location) according to tightness
         */
        protected void setTightness(int tn) {
            if (tn < 0 || tn > maxTightness) {
                throw new IllegalArgumentException("Invalid tightness " + tn);
            }
            if (tightness == tn) {
                return;
            }
            Transform hostRelTr = hostRelTrs.get(tn);
            
            if (tightness > 0) {
                inventory.removeJoint(hostItem, guestItem, MySixDofJoint.class);
            }
            if (tn > 0) {
                // add physics joint
                MySixDofJoint joint = inventory.addSixDofJoint(hostItem, guestItem,
                        hostRelTr.getTranslation(), guestRelTr.getTranslation(),
                        hostRelTr.getRotation().toRotationMatrix(),
                        guestRelTr.getRotation().toRotationMatrix(), false);
                joint.setAngularLowerLimit(Vector3f.ZERO);
                joint.setAngularUpperLimit(Vector3f.ZERO);
                joint.setLinearLowerLimit(Vector3f.ZERO);
                joint.setLinearUpperLimit(Vector3f.ZERO);
            }
                            
            // snap guest item to the right location (according to host tightness location)
            //
            Transform tr = TransformUtils.invertTransform(guestRelTr, null);
            tr.combineWithParent(hostRelTr);
            tr.combineWithParent(hostItem.getWorldTransform());
            
            Demonstrator.Hand hand = app.getDemonstrator().getHandGraspingItem(guestItem);
            if (hand != null) {
                hand.move(tr.getTranslation(), 0.1f);
                hand.rotate(tr.getRotation(), 0.1f);
            } else {
                MyRigidBodyControl guestRbc = guestItem.getControl(MyRigidBodyControl.class);
                guestRbc.setPhysicsLocation(tr.getTranslation());
                guestRbc.setPhysicsRotation(tr.getRotation());
            }
            inventory.updateItemInsomnia(guestItem);
            inventory.updateItemInsomnia(hostItem);
            
            tightness = tn;
        }
    }

    protected Inventory inventory;
    protected MainApp app;
    // main storage
    protected Map<String, Node> hostBondPoints = new HashMap<>();
    protected Map<String, Node> guestBondPoints = new HashMap<>();
    protected Set<ObjectBond> bonds = new HashSet<>();
    // derived
    protected Map<Spatial, Set<Node>> guestBondPointsByItem = new HashMap<>();
    protected Map<Node, ObjectBond> bondForPoint = new HashMap<>();
    protected Map<Spatial, Set<Spatial>> itemAdjacency = new HashMap<>();
    
    public ObjectBondTracker(MainApp app, Inventory inv) {
        this.app = app;
        inventory = inv;
    }
    
    protected void addBondPointsForItem(final Spatial item) {
        item.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Node) {
                    String role = spatial.getUserData("bondPoint");
                    Node bondPoint = (Node) spatial;
                    if ("host".equals(role)) {
                        hostBondPoints.put(bondPoint.getName(), bondPoint);
                    } else if ("guest".equals(role)) {
                        guestBondPoints.put(bondPoint.getName(), bondPoint);
                        Set<Node> set = guestBondPointsByItem.get(item);
                        if (set == null) {
                            set = new HashSet<>();
                            guestBondPointsByItem.put(item, set);
                        }
                        set.add(bondPoint);
                    }
                }
            }
        });
    }

    protected void removeBondPointsForItem(Spatial item) {
        item.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Node) {
                    Node bondPoint = (Node) spatial;
                    ObjectBond bond = bondForPoint.get(bondPoint);
                    if (bond != null) {
                        inventory.removeJoint(bond.hostItem, bond.guestItem, MySixDofJoint.class);
                        removeBond(bond);
                    }
                    String role = spatial.getUserData("bondPoint");
                    if ("host".equals(role)) {
                        hostBondPoints.remove(bondPoint);
                    } else if ("guest".equals(role)) {
                        guestBondPoints.remove(bondPoint);
                    }
                }
            }
        });
        guestBondPointsByItem.remove(item);
    }
    
    protected int getNumBondPoints() {
        return hostBondPoints.size() + guestBondPoints.size();
    }

    protected void addBond(ObjectBond bond) {
        bonds.add(bond);
        bondForPoint.put(bond.host, bond);
        bondForPoint.put(bond.guest, bond);
        Set<Spatial> neibs = itemAdjacency.get(bond.hostItem);
        if (neibs == null) {
            neibs = new HashSet<>();
            itemAdjacency.put(bond.hostItem, neibs);
        }
        neibs.add(bond.guestItem);
        neibs = itemAdjacency.get(bond.guestItem);
        if (neibs == null) {
            neibs = new HashSet<>();
            itemAdjacency.put(bond.guestItem, neibs);
        }
        neibs.add(bond.hostItem);
    }
    
    protected void removeBond(ObjectBond bond) {
        bonds.remove(bond);
        bondForPoint.remove(bond.host);
        bondForPoint.remove(bond.guest);
        itemAdjacency.get(bond.hostItem).remove(bond.guestItem);
        itemAdjacency.get(bond.guestItem).remove(bond.hostItem);
    }

    protected ObjectBond getBond(Node bondPoint) {
        return bondForPoint.get(bondPoint);
    }
    
    /**
     * Find or create an object bond between
     * (1) the guest bond point on guestItem closest to clickedLocation and
     * (2) an appropriate host bond point as follows:
     *     (a) the existing bond, if the guest is already connected to a host bond point,
     *         and the bond is still fastenable.
     *     (b) a "valid" new bond to the host bond point closest to the guest bond point.
     * 
     * A valid host bond point is one that:
     * (1) is within distance distTolerance to the guest bond point
     * (2) is of the same bond type as the guest bond point,
     * (3) has not already been connected to another guest bond point, and
     * (4) the item it belongs to has not already been directly or indirectly connected to guestItem.
     * @param guestItem
     * @param clickedLocation
     * @param distTolerance
     * @return a bond, or null if failed.
     */
    protected ObjectBond findFastenable(Spatial guestItem, Vector3f clickedLocation,
            float distTolerance) {
        Node guest = getNearestGuestBondPoint(guestItem, clickedLocation);
        if (guest == null) {
            return null;
        }
        ObjectBond bond = getBond(guest);
        if (bond != null) {
            // guest is already connected
            if (bond.isFastenable()) {
                return bond;
            }
            return null;
        } else {
            // guest is not already connected; create a new object bond
            //
            Vector3f guestLoc = guest.getWorldTranslation();
            Set<Spatial> invalidItems = gatherConnectedItems(guestItem, new HashSet<Spatial>());
            float minDist = Float.MAX_VALUE;
            Node minHost = null;
            for (Node host : hostBondPoints.values()) {
                if (!host.getUserData("bondType").equals(guest.getUserData("bondType"))) {
                    continue;
                }
                if (getBond(host) != null) {
                    // already connected to another guest bond point
                    continue;
                }
                Spatial hostItem = inventory.getItem(host);
                if (invalidItems.contains(hostItem)) {
                    continue;
                }
                float dist = host.getWorldTranslation().distance(guestLoc);
                if (dist < distTolerance && dist < minDist) {
                    minDist = dist;
                    minHost = host;
                }
            }
            if (minHost == null) {
                return null;
            }
            return new ObjectBond(minHost, guest);
        }
    }

    /**
     * Find an existing (loosenable) object bond containing the guest bond point on guestItem
     * closest to clickedLocation.
     * @param guestItem
     * @param clickedLocation
     * @return a bond, or null if failed.
     */
    protected ObjectBond findLoosenable(Spatial guestItem, Vector3f clickedLocation) {
        Node guest = getNearestGuestBondPoint(guestItem, clickedLocation);
        if (guest == null) {
            return null;
        }
        return getBond(guest);
    }
    
    protected void createInitBond(Node guest, String hostId, int tightness) {
        Node host = hostBondPoints.get(hostId);
        if (host == null) {
            throw new IllegalArgumentException("hostId " + hostId + " not found");
        }
        if (getBond(guest) != null) {
            throw new IllegalArgumentException("guest bond point " + guest + " is busy");
        }
        if (getBond(host) != null) {
            throw new IllegalArgumentException("host bond point " + host + " is busy");
        }
        if (!host.getUserData("bondType").equals(guest.getUserData("bondType"))) {
            throw new IllegalArgumentException("host and guest are of different bond types");
        }
        Spatial guestItem = inventory.getItem(guest);
        Spatial hostItem = inventory.getItem(host);
        Set<Spatial> invalidItems = gatherConnectedItems(guestItem, new HashSet<Spatial>());
        if (invalidItems.contains(hostItem)) {
            throw new IllegalArgumentException("hostItem already connected (directly or indirectly) to guestItem");
        }
        ObjectBond bond = new ObjectBond(host, guest);
        bond.setTightness(tightness);
        if (tightness > 0) {
            addBond(bond);
        }
    }
    
    private Node getNearestGuestBondPoint(Spatial guestItem, Vector3f loc) {
        Set<Node> guests = guestBondPointsByItem.get(guestItem);
        if (guests == null) {
            return null;
        }
        float minDist = Float.MAX_VALUE;
        Node minGuest = null;
        for (Node g : guests) {
            float dist = loc.distance(g.getWorldTranslation());
            if (minDist > dist) {
                minDist = dist;
                minGuest = g;
            }
        }
        return minGuest;
    }
    
    private Set<Spatial> gatherConnectedItems(Spatial item, Set<Spatial> visited) {
        if (visited.contains(item)) {
            return visited;
        }
        visited.add(item);
        Set<Spatial> neighbors = itemAdjacency.get(item);
        if (neighbors != null) {
            for (Spatial n : neighbors) {
                gatherConnectedItems(n, visited);
            }
        }
        return visited;
    }

    protected class ObjectBondTrackerMemento {
        Map<String, Node> hostBondPoints = new HashMap<>();
        Map<String, Node> guestBondPoints = new HashMap<>();
        Set<ObjectBondMemento> bonds = new HashSet<>();
    }
    
    protected class ObjectBondMemento {
        Node host;
        Node guest;
        int tightness;
    }
    
    protected ObjectBondTrackerMemento saveToMemento() {
        ObjectBondTrackerMemento m = new ObjectBondTrackerMemento();
        m.hostBondPoints.putAll(hostBondPoints);
        m.guestBondPoints.putAll(guestBondPoints);
        for (ObjectBond bond : bonds) {
            ObjectBondMemento cm = new ObjectBondMemento();
            cm.host = bond.host;
            cm.guest = bond.guest;
            cm.tightness = bond.tightness;
            m.bonds.add(cm);
        }
        return m;
    }

    protected void restoreFromMemento(ObjectBondTrackerMemento m) {
        hostBondPoints.putAll(m.hostBondPoints);
        guestBondPoints.putAll(m.guestBondPoints);
        for (Node g : guestBondPoints.values()) {
            Spatial item = inventory.getItem(g);
            Set<Node> set = guestBondPointsByItem.get(item);
            if (set == null) {
                set = new HashSet<>();
                guestBondPointsByItem.put(item, set);
            }
            set.add(g);
        }
        // bonds
        for (ObjectBondMemento cm : m.bonds) {
            ObjectBond c = new ObjectBond(cm.host, cm.guest, cm.tightness);
            addBond(c);
        }
    }
}

