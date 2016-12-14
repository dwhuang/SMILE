/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.robot;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.AbstractPhysicsControl;
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

import edu.umd.smile.object.Factory;
import edu.umd.smile.util.MyRigidBodyControl;

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
    private static final float MAX_OPENING = 2;
    private static final float FINGER_MASS = 100;
    private static final ColorRGBA COLOR = new ColorRGBA(0.5f, 0.1f, 0.1f, 1);
    
    private String name;
    private boolean enabled = true;
    private PhysicsSpace physicsSpace;
    private Factory factory;

    private Node base;

    private Physics phy;
    
    public Gripper(String name, Node parentNode, PhysicsSpace physicsSpace, Factory factory) {
        this.name = name;
        this.physicsSpace = physicsSpace;
        this.factory = factory;
        buildGripper(parentNode);
    }
    
    private void buildGripper(Node parentNode) {
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
                FINGER_SIZE.y / 2, MAX_OPENING + FINGER_SIZE.x * 2 + 0.01f, 32, COLOR);
        g.setLocalRotation(new Quaternion(new float[] {0, FastMath.HALF_PI, 0}));
        g.setLocalTranslation(0, 0, FINGER_SIZE.z / 2);
        base.attachChild(g);
        
        
        phy = new Physics();
        base.addControl(phy);
        physicsSpace.add(phy);
        physicsSpace.addCollisionListener(phy);
    }
    
    public void setEnabled(boolean v) {
    	enabled = v;
    }
    
    public void onAnalog(String name, float value, float tpf) {
    	if (!enabled) {
    		return;
    	}
        if (name.toLowerCase().matches(".*gripperopen")) {
        	setTargetVelocity(1);
        } else if (name.toLowerCase().matches(".*gripperclose")) {
            setTargetVelocity(-1);
        }
    }
    
    public void setTargetVelocity(float tv) {
        phy.velocity = tv;
    }
    
    public float getOpening() {
        return phy.opening;
    }
    
    public Physics getPhysics() {
    	return phy;
    }
    
    public void addLocTrackers(String prefix, RobotLocTracker trackers) {
        // add "location sensors" that report their spatial locations
        float offset;
        Node tracker;
        offset = base.getChild(name + " left-finger").getLocalTranslation().x;
        tracker = new Node(prefix + "-ln");
        tracker.setLocalTranslation(offset, 0, FINGER_SIZE.z / 2);
        base.attachChild(tracker);
        trackers.put(tracker.getName(), tracker);
        
        tracker = new Node(prefix + "-lf");
        tracker.setLocalTranslation(offset, 0, -FINGER_SIZE.z / 2);
        base.attachChild(tracker);
        trackers.put(tracker.getName(), tracker);
        
        offset = base.getChild(name + " right-finger").getLocalTranslation().x;
        tracker = new Node(prefix + "-rn");
        tracker.setLocalTranslation(offset, 0, FINGER_SIZE.z / 2);
        base.attachChild(tracker);
        trackers.put(tracker.getName(), tracker);

        tracker = new Node(prefix + "-rf");
        tracker.setLocalTranslation(offset, 0, -FINGER_SIZE.z / 2);
        base.attachChild(tracker);
        trackers.put(tracker.getName(), tracker);
    }

    private class Physics extends AbstractPhysicsControl implements PhysicsCollisionListener {
        private Geometry leftFinger;
        private Geometry rightFinger;
        private MyRigidBodyControl leftFingerControl = null;
        private MyRigidBodyControl rightFingerControl = null;
        
        // for grabbing objects
        private float velocity = 0; // -1: shrinking, 1: expanding, 0: unchanged
        private float opening = MAX_OPENING;
        private float fingerPressure = 0;
        private TreeMap<Float, Spatial> leftContacts = new TreeMap<Float, Spatial>();
        private TreeMap<Float, Spatial> rightContacts = new TreeMap<Float, Spatial>();
        private HashSet<Spatial> holding = new HashSet<Spatial>();
        private boolean checkDigitCollision = false;

        // temporary variables
        private Matrix4f mat = new Matrix4f();
        private Matrix4f mat2 = new Matrix4f();
    
        @Override
        protected void createSpatialData(Spatial spat) {
            Node node = (Node) spat;
            
            leftFinger = (Geometry) node.getChild(Gripper.this.name + " left-finger");
            if (leftFingerControl == null) {
                leftFingerControl = new MyRigidBodyControl(Gripper.FINGER_MASS);
            }
            leftFinger.addControl(leftFingerControl);
            leftFingerControl.setKinematic(true);
            
            rightFinger = (Geometry) node.getChild(Gripper.this.name + " right-finger");
            if (rightFingerControl == null) {
                rightFingerControl = new MyRigidBodyControl(Gripper.FINGER_MASS);
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
        }

        @Override
        protected void removePhysics(PhysicsSpace space) {
            space.remove(leftFingerControl);
            space.remove(rightFingerControl);
        }

        public Control cloneForSpatial(Spatial spat) {
            Physics phy = new Physics();
            phy.createSpatialData(spat);
            return phy;
        }

        // NOTE called from the rendering thread
        // call order seems to be like: input events -> collision -> update
        public void collision(PhysicsCollisionEvent event) {
        	if (!Gripper.this.enabled) {
        		return;
        	}
            if (holding.size() > 0 || !checkDigitCollision) {
                return;
            }
            Spatial nodeA = event.getNodeA();
            Spatial nodeB = event.getNodeB();

            if (nodeA == leftFinger || nodeA == rightFinger) {
                if (nodeB == leftFinger || nodeB == rightFinger || nodeB == null) {
                    return;
                }
                if (nodeA == leftFinger) {
                	leftContacts.put(event.getLocalPointA().z, nodeB);
                } else {
                	rightContacts.put(event.getLocalPointA().z, nodeB);
                }
            } else if (nodeB == leftFinger) {
            	if (nodeA == null) {
            		return;
            	}
            	leftContacts.put(event.getLocalPointB().z, nodeA);
            } else if (nodeB == rightFinger) {
            	if (nodeA == null) {
            		return;
            	}
            	rightContacts.put(event.getLocalPointB().z, nodeA);
            }
            
            fingerPressure += event.getAppliedImpulse();
        }

        @Override
        public void update(float tpf) {
        	if (!enabled) {
        		return;
        	}
            super.update(tpf);
            if (velocity > 0 && holding.size() > 0) {
                release();
            }
            
            if (holding.size() > 0) {
                velocity = 0;
                return;
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
                    MyRigidBodyControl rbc = s.getControl(MyRigidBodyControl.class);
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

            
            if (velocity != 0) {
                if (velocity < 0) {
                    float impulse = -velocity * tpf * FINGER_MASS * 2;
                    impulse -= fingerPressure;
                    float offset = impulse / (FINGER_MASS * 2);
                    offset = FastMath.clamp(offset, 0, offset);
                    opening -= offset;
                    checkDigitCollision = true;
                } else {
                    opening += velocity * tpf;
                    checkDigitCollision = false;
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
                velocity = 0;
            } else {
                checkDigitCollision = false;
            }
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
            MyRigidBodyControl rbc = s.getControl(MyRigidBodyControl.class);
            rbc.setKinematic(true);
            rbc.changeMass(rbc.getMass() + FINGER_MASS);
            
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

                MyRigidBodyControl rbc = s.getControl(MyRigidBodyControl.class);
                rbc.changeMass(rbc.getMass() - FINGER_MASS);
                rbc.setKinematic(false);
            }
            holding.clear();
        }
    }
}
