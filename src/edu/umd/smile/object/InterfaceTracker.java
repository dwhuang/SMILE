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

public class InterfaceTracker {
    public class InterfaceConnection {
        protected Node host;
        protected Node guest;
        protected Spatial hostItem;
        protected Spatial guestItem;
        protected List<Transform> hostRelTrs = new ArrayList<>(); // interface transform relative to the item
        protected Transform guestRelTr;
        protected int tightness = 0;
        protected int maxTightness;

        public InterfaceConnection(Node host, Node guest) {
            this.host = host;
            this.guest = guest;
            init();
        }
        
        protected InterfaceConnection(Node host, Node guest, int tightness) {
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
        
        private Transform getRelativeTransform(Node intf, Spatial whole, Transform extra) {
            Transform tr = extra;
            Spatial s = intf;
            while (s != null) {
                if (s == whole) {
                    return tr;
                }
                tr.combineWithParent(s.getLocalTransform());
                s = s.getParent();
            }
            throw new IllegalArgumentException("'intf' is not a part of 'whole'");
        }
        
        public Spatial getHostItem() {
            return hostItem;
        }
        
        public Node getHostInterface() {
            return host;
        }
        
        public Spatial getGuestItem() {
            return guestItem;
        }
        
        public Node getGuestInterface() {
            return guest;
        }
        
        public int getTightness() {
            return tightness;
        }
        
        public void fasten() {
            if (!isFastenable()) {
                throw new IllegalStateException("Connection between '"
                        + host + "' and '" + guest + "' cannot be fastened");
            }
            if (tightness == 0) {
                addConnection(this);
            }
            setTightness(tightness + 1);
        }
        
        public void loosen() {
            if (!isLoosenable()) {
                throw new IllegalStateException("Connection between '"
                        + host + "' and '" + guest + "' cannot be loosened");
            }
            setTightness(tightness - 1);
            if (tightness == 0) {
                removeConnection(this);
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
    protected Set<Node> hostInterfaces = new HashSet<>();
    protected Set<Node> guestInterfaces = new HashSet<>();
    protected Set<InterfaceConnection> conns = new HashSet<>();
    // derived
    protected Map<Spatial, Set<Node>> guestInterfacesByItem = new HashMap<>();
    protected Map<Node, InterfaceConnection> connsByInterface = new HashMap<>();
    protected Map<Spatial, Set<Spatial>> itemAdjacency = new HashMap<>();
    
    public InterfaceTracker(MainApp app, Inventory inv) {
        this.app = app;
        inventory = inv;
    }
    
    protected void addItemInterfaces(final Spatial item) {
        item.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Node) {
                    String role = spatial.getUserData("interface");
                    Node intf = (Node) spatial;
                    if ("host".equals(role)) {
                        hostInterfaces.add(intf);
                    } else if ("guest".equals(role)) {
                        guestInterfaces.add(intf);
                        Set<Node> set = guestInterfacesByItem.get(item);
                        if (set == null) {
                            set = new HashSet<>();
                            guestInterfacesByItem.put(item, set);
                        }
                        set.add(intf);
                    }
                }
            }
        });
    }

    protected void removeItemInterfaces(Spatial item) {
        item.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Node) {
                    Node intf = (Node) spatial;
                    InterfaceConnection conn = connsByInterface.get(intf);
                    if (conn != null) {
                        inventory.removeJoint(conn.hostItem, conn.guestItem, MySixDofJoint.class);
                        removeConnection(conn);
                    }
                    String role = spatial.getUserData("interface");
                    if ("host".equals(role)) {
                        hostInterfaces.remove(intf);
                    } else if ("guest".equals(role)) {
                        guestInterfaces.remove(intf);
                    }
                }
            }
        });
        guestInterfacesByItem.remove(item);
    }
    
    protected int getNumInterfaces() {
        return hostInterfaces.size() + guestInterfaces.size();
    }

    protected void addConnection(InterfaceConnection conn) {
        conns.add(conn);
        connsByInterface.put(conn.host, conn);
        connsByInterface.put(conn.guest, conn);
        Set<Spatial> neibs = itemAdjacency.get(conn.hostItem);
        if (neibs == null) {
            neibs = new HashSet<>();
            itemAdjacency.put(conn.hostItem, neibs);
        }
        neibs.add(conn.guestItem);
        neibs = itemAdjacency.get(conn.guestItem);
        if (neibs == null) {
            neibs = new HashSet<>();
            itemAdjacency.put(conn.guestItem, neibs);
        }
        neibs.add(conn.hostItem);
    }
    
    protected void removeConnection(InterfaceConnection conn) {
        conns.remove(conn);
        connsByInterface.remove(conn.host);
        connsByInterface.remove(conn.guest);
        itemAdjacency.get(conn.hostItem).remove(conn.guestItem);
        itemAdjacency.get(conn.guestItem).remove(conn.hostItem);
    }

    protected InterfaceConnection getConnection(Node intf) {
        return connsByInterface.get(intf);
    }
    
    /**
     * Find or create an interface connection between
     * (1) the guest interface on guestItem closest to clickedLocation and
     * (2) an appropriate host interface as follows:
     *     (a) the existing connection, if the guest is already connected to a host interface,
     *         and the host interface is still fastenable.
     *     (b) a "valid" new connection to the host interface closest to the guest interface.
     * 
     * A valid host interface is one that:
     * (1) is within distance distTolerance to the guest interface
     * (2) is of the same type as the guest interface,
     * (3) has not already been fasten to another guest interface, and
     * (4) the item it belongs to has not already been directly or indirectly connected to guestItem.
     * @param guestItem
     * @param clickedLocation
     * @param distTolerance
     * @return a connection, or null if failed.
     */
    protected InterfaceConnection findFastenable(Spatial guestItem, Vector3f clickedLocation,
            float distTolerance) {
        Node guest = getNearestGuestInterface(guestItem, clickedLocation);
        if (guest == null) {
            return null;
        }
        InterfaceConnection conn = getConnection(guest);
        if (conn != null) {
            // guest is already connected
            if (conn.isFastenable()) {
                return conn;
            }
            return null;
        } else {
            // guest is not already connected; create a new interface connection
            //
            Vector3f guestLoc = guest.getWorldTranslation();
            Set<Spatial> invalidItems = gatherConnectedItems(guestItem, new HashSet<Spatial>());
            float minDist = Float.MAX_VALUE;
            Node minHost = null;
            for (Node host : hostInterfaces) {
                if (!host.getUserData("interfaceType").equals(guest.getUserData("interfaceType"))) {
                    continue;
                }
                if (getConnection(host) != null) {
                    // already connected to another guest interface
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
            return new InterfaceConnection(minHost, guest);
        }
    }

    /**
     * Find an existing (loosenable) interface connection containing the guest interface on guestItem
     * closest to clickedLocation.
     * @param guestItem
     * @param clickedLocation
     * @return a connection, or null if failed.
     */
    protected InterfaceConnection findLoosenable(Spatial guestItem, Vector3f clickedLocation) {
        Node guest = getNearestGuestInterface(guestItem, clickedLocation);
        if (guest == null) {
            return null;
        }
        return getConnection(guest);
    }
    
    private Node getNearestGuestInterface(Spatial guestItem, Vector3f loc) {
        Set<Node> guests = guestInterfacesByItem.get(guestItem);
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

    protected class InterfaceMemento {
        Set<Node> hostInterfaces = new HashSet<>();
        Set<Node> guestInterfaces = new HashSet<>();
        Set<ConnectionMemento> conns = new HashSet<>();
    }
    
    protected class ConnectionMemento {
        Node host;
        Node guest;
        int tightness;
    }
    
    protected InterfaceMemento saveToMemento() {
        InterfaceMemento m = new InterfaceMemento();
        m.hostInterfaces.addAll(hostInterfaces);
        m.guestInterfaces.addAll(guestInterfaces);
        for (InterfaceConnection conn : conns) {
            ConnectionMemento cm = new ConnectionMemento();
            cm.host = conn.host;
            cm.guest = conn.guest;
            cm.tightness = conn.tightness;
            m.conns.add(cm);
        }
        return m;
    }

    protected void restoreFromMemento(InterfaceMemento m) {
        hostInterfaces.addAll(m.hostInterfaces);
        guestInterfaces.addAll(m.guestInterfaces);
        for (Node g : guestInterfaces) {
            Spatial item = inventory.getItem(g);
            Set<Node> set = guestInterfacesByItem.get(item);
            if (set == null) {
                set = new HashSet<>();
                guestInterfacesByItem.put(item, set);
            }
            set.add(g);
        }
        // connections
        for (ConnectionMemento cm : m.conns) {
            InterfaceConnection c = new InterfaceConnection(cm.host, cm.guest, cm.tightness);
            addConnection(c);
        }
    }
}

