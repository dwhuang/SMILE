/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.AbstractPhysicsControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dwhuang
 */
public class Gripper implements AnalogListener {
    private static final Logger logger = Logger.getLogger(
            Gripper.class.getName());
    static {
        logger.setLevel(Level.SEVERE);
    }

    public static final Vector3f FINGER_SIZE = new Vector3f(0.3f, 0.4f, 1.5f);
    protected static final float MAX_OPENING = 2;
    protected static final float FINGER_MASS = 5;
    protected static final float FINGER_SPEED = 1;    
    protected static final ColorRGBA COLOR = new ColorRGBA(0.5f, 0.1f, 0.1f, 1);
    
    protected String name;
    protected PhysicsSpace physicsSpace;
    protected Factory factory;

    protected Node base;

    protected Physics phy;
    
    public Gripper(String name, Node parentNode, PhysicsSpace physicsSpace, Factory factory) {
        this.name = name;
        this.physicsSpace = physicsSpace;
        this.factory = factory;
        buildGripper(parentNode);
    }
    
    protected final void buildGripper(Node parentNode) {
        base = new Node(name);
        parentNode.attachChild(base);
        
        Geometry g;
        g = factory.makeBlock(name + " left-finger", 
                FINGER_SIZE.x, FINGER_SIZE.y, FINGER_SIZE.z, COLOR);
        g.setLocalTranslation(-MAX_OPENING / 2 - FINGER_SIZE.x / 2, 0, 0);
        base.attachChild(g);        
        
        g = factory.makeBlock(name + " right-finger", 
                FINGER_SIZE.x, FINGER_SIZE.y, FINGER_SIZE.z, COLOR);
        g.setLocalTranslation(MAX_OPENING / 2 + FINGER_SIZE.x / 2, 0, 0);
        base.attachChild(g);
        
        g = factory.makeCylinder(name + " platform", 
                FINGER_SIZE.y / 2, MAX_OPENING + FINGER_SIZE.x * 2 + 0.01f, COLOR);
        g.setLocalRotation(new Quaternion(new float[] {0, FastMath.HALF_PI, 0}));
        g.setLocalTranslation(0, 0, FINGER_SIZE.z / 2);
        base.attachChild(g);

        phy = new Physics();
        base.addControl(phy);
        physicsSpace.add(phy);
    }
    
    public void onAnalog(String name, float value, float tpf) {
        if (name.toLowerCase().matches(".*gripperopen")) {
            phy.addChange(1);
        } else if (name.toLowerCase().matches(".*gripperclose")) {
            phy.addChange(-1);
        }
    }

    protected class Physics extends AbstractPhysicsControl implements PhysicsCollisionListener {
        private Geometry leftFinger;
        private Geometry rightFinger;
        private RigidBodyControl leftFingerControl = null;
        private RigidBodyControl rightFingerControl = null;
        
        // for grabbing objects
        private int change = 0; // -1: shrinking, 1: expanding, 0: unchanged
        private float opening = MAX_OPENING;
        private float fingerPressure = 0;
        private TreeMap<Float, Spatial> leftContacts = new TreeMap<Float, Spatial>();
        private TreeMap<Float, Spatial> rightContacts = new TreeMap<Float, Spatial>();
        private HashSet<Spatial> holding = new HashSet<Spatial>();

        // temporary variables
        private Matrix4f mat = new Matrix4f();
        private Matrix4f mat2 = new Matrix4f();
    
        @Override
        protected void createSpatialData(Spatial spat) {
            Node node = (Node) spat;
            
            leftFinger = (Geometry) node.getChild(Gripper.this.name + " left-finger");
            if (leftFingerControl == null) {
                leftFingerControl = new RigidBodyControl(Gripper.FINGER_MASS);
            }
            leftFinger.addControl(leftFingerControl);
            leftFingerControl.setKinematic(true);
            
            rightFinger = (Geometry) node.getChild(Gripper.this.name + " right-finger");
            if (rightFingerControl == null) {
                rightFingerControl = new RigidBodyControl(Gripper.FINGER_MASS);
            }
            rightFinger.addControl(rightFingerControl);
            rightFingerControl.setKinematic(true);
        }

        @Override
        protected void removeSpatialData(Spatial spat) {
            leftFinger.removeControl(leftFingerControl);
            leftFinger = null;
            rightFinger.removeControl(rightFingerControl);
            rightFinger = null;
        }

        @Override
        protected void setPhysicsLocation(Vector3f vec) {
        }

        @Override
        protected void setPhysicsRotation(Quaternion quat) {
        }

        @Override
        protected void addPhysics(PhysicsSpace space) {
            space.add(leftFingerControl);
            space.add(rightFingerControl);
            space.addCollisionListener(this);
        }

        @Override
        protected void removePhysics(PhysicsSpace space) {
            space.removeCollisionListener(this);
            space.remove(leftFingerControl);
            space.remove(rightFingerControl);
        }

        public Control cloneForSpatial(Spatial spat) {
            Physics phy = new Physics();
            phy.createSpatialData(spat);
            return phy;
        }

        // NOTE called from the rendering thread (after update())
        public void collision(PhysicsCollisionEvent event) {
            if (holding.size() > 0 || change >= 0) {
                return;
            }
            Spatial nodeA = event.getNodeA();
            Spatial nodeB = event.getNodeB();
            Spatial grabbable = null;
            TreeMap<Float, Spatial> contacts = null;

            if (nodeA == leftFinger || nodeA == rightFinger) {
                if (nodeB == leftFinger || nodeB == rightFinger) {
                    return;
                }
                if (nodeA == leftFinger) {
                    contacts = leftContacts;
                } else {
                    contacts = rightContacts;
                }
                grabbable = nodeB;
            } else if (nodeB == leftFinger) {
                grabbable = nodeA;
                contacts = leftContacts;
            } else if (nodeB == rightFinger) {
                grabbable = nodeA;
                contacts = rightContacts;
            }
            
            if (contacts == null || grabbable == null) {
                return;
            }

            contacts.put(event.getLocalPointA().z, grabbable);
            
            if (contacts == leftContacts) {
                logger.log(Level.INFO, "left {0}", event.getLocalPointA().z);
            } else {
                logger.log(Level.INFO, "right {0}", event.getLocalPointA().z);
            }
            
            fingerPressure += event.getAppliedImpulse();
        }        

        @Override
        public void update(float tpf) {
            super.update(tpf);
            if (change > 0 && holding.size() > 0) {
                release();
            }
            
            if (holding.size() > 0) {
                change = 0;
                return;
            }
            
            if (change != 0) {
                if (change < 0) {
                    float force = FINGER_SPEED * tpf * FINGER_MASS * 2;
                    force -= fingerPressure;
                    float offset = force / (FINGER_MASS * 2);
                    offset = FastMath.clamp(offset, 0, offset);
                    opening -= offset;
                } else {
                    opening += FINGER_SPEED * tpf;
                }
                opening = FastMath.clamp(opening, 0, Gripper.MAX_OPENING);

                // update finger opeining
                Vector3f v = leftFinger.getLocalTranslation();
                v.x = -opening / 2 - Gripper.FINGER_SIZE.x / 2;
                leftFinger.setLocalTranslation(v);
                v = rightFinger.getLocalTranslation();
                v.x = opening / 2 + Gripper.FINGER_SIZE.x / 2;
                rightFinger.setLocalTranslation(v);
                
                fingerPressure = 0;
                change = 0;
            }

            // check holding objects
            float maxDiff = opening * FastMath.tan(FastMath.QUARTER_PI / 4);
            for (Entry<Float, Spatial> e1 : leftContacts.entrySet()) {
                for (Entry<Float, Spatial> e2 : rightContacts.entrySet()) {
                    if (e1.getValue() != e2.getValue()) {
                        continue;
                    }
                    if (FastMath.abs(e1.getKey() - e2.getKey()) > maxDiff) {
                        continue;
                    }
                    Spatial s = e1.getValue();
                    if (holding.contains(s)) {
                        continue;
                    }
                    RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
                    if (rbc == null || rbc.getMass() == 0 || rbc.isKinematic()) {
                        continue;
                    }
                    hold(s);
                    logger.log(Level.INFO, "hold");
                }
            }
            
            if ((leftContacts.size() > 0 || rightContacts.size() > 0) && holding.size() <= 0) {
                logger.log(Level.INFO, "cannot hold: {0}", maxDiff);
            }
                        
            leftContacts.clear();
            rightContacts.clear();
        }
        
        private void hold(Spatial s) {
            // calc new transormation matrix for the object
            Gripper.this.base.getLocalToWorldMatrix(mat);
            s.getLocalToWorldMatrix(mat2);
            mat.invertLocal();
            mat.multLocal(mat2);
            // move the spatial under the gripper
            s.getParent().detachChild(s);
            Gripper.this.base.attachChild(s);
            s.setLocalTranslation(mat.toTranslationVector());
            s.setLocalRotation(mat.toRotationMatrix());
            s.setLocalScale(mat.toScaleVector());
            // kinematic
            RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
            rbc.setKinematic(true);
            rbc.setMass(rbc.getMass() + FINGER_MASS);
            
            holding.add(s);
        }
        
        private void release() {
            // find rootNode
            Node root = Gripper.this.base;
            while (root.getParent() != null) {
                root = root.getParent();
            }
            for (Spatial s : holding) {
                // attach the spatial to the world
                s.getLocalToWorldMatrix(mat);
                Gripper.this.base.detachChild(s);
                root.attachChild(s);
                s.setLocalTranslation(mat.toTranslationVector());
                s.setLocalRotation(mat.toRotationMatrix());
                s.setLocalScale(mat.toScaleVector());
                // make a new control, because the old one won't go
                // to sleep when its kinematic is turned off
                RigidBodyControl oldControl = s.getControl(RigidBodyControl.class);
                RigidBodyControl newControl = new RigidBodyControl(
                        oldControl.getMass() - FINGER_MASS);
                s.addControl(newControl);
                oldControl.getPhysicsSpace().add(newControl);                
                s.removeControl(oldControl);
                oldControl.getPhysicsSpace().remove(oldControl);
            }
            holding.clear();
        }

        public void addChange(int change) {
            this.change += change;
        }
    }
}
