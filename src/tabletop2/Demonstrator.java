/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import java.util.ArrayList;
import java.util.Arrays;

import tabletop2.util.MyRigidBodyControl;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public class Demonstrator implements ActionListener, AnalogListener {

    private enum HandState {
        Idle, Grasped, Moving
    }
    
    public enum HandId {
    	LeftHand(0), RightHand(1), BothHands(2), AnyHand(3);
    	
    	private final int value;
    	private HandId(int value) {
    		this.value = value;
    	}
    	public int getValue() {
    		return value;
    	}
    }
    
    private String name;
    private boolean enabled = true;
    private MainApp app;
    private Node rootNode;
    private Camera cam;
    private InputManager inputManager;
    private final Inventory inventory;
    private Table table;

    private ArrayList<DemoActionListener> demoActionListeners = new ArrayList<DemoActionListener>();
    private ArrayList<DemoPreActionListener> demoPreActionListeners = new ArrayList<DemoPreActionListener>();
    private DemoSceneProcessor sceneProcessor;

    private Hand[] hands = new Hand[HandId.values().length];
    private Hand currHand = null;
    private Node visualAid;
    private Node movingPlane;
    private Vector3f movingCursorOffset;
    
    private boolean shiftKey = false;
    
    private transient Ray ray = new Ray();
    private transient CollisionResults collisionResults = new CollisionResults();
    private transient Vector3f vec = new Vector3f();
    private transient Transform transform = new Transform();
    private transient Transform graspNodeTarget = new Transform();
    private transient Transform midTransform = new Transform();
    private transient Transform minTransform = new Transform();
    private transient Quaternion quat = new Quaternion();
    
    public class Hand {
    	private HandId id;
    	private HandState state = HandState.Idle;
    	private Node graspNode = null;
    	private Spatial graspedItem = null;
    	private float[] userRotAngles = {0, 0, 0};
    	private boolean graspedItemIsKinematic = false;
        
        private Hand(String name, HandId id) {
        	this.id = id;
        	graspNode = new Node(name);
        	rootNode.attachChild(graspNode);
        }
        
        private Hand() {}
        
        public HandId getId() {
        	return id;
        }
        
        public boolean isIdle() {
        	return state == HandState.Idle;
        }
        
        public String getGraspedItemName() {
        	if (isIdle()) {
        		return "<empty>";
        	} else {
        		return new String(graspedItem.getName());
        	}
        }
        
        public float[] getUserRotAngles() {
        	if (isIdle()) {
        		return new float[]{0, 0, 0};
        	} else {
        		return Arrays.copyOf(userRotAngles, 3);
        	}
        }
        
        private void processSelect() {
        	if (state == HandState.Idle) {
        		Demonstrator.this.showVisualAid(null);
        	} else {
        		Demonstrator.this.setVisualAid(graspNode);
        		Demonstrator.this.showVisualAid(graspedItem);
        	}
        }
        
        private void processDeselect() {
        	if (state == HandState.Moving) {
        		movingEnd();
        	}
        }
        
        private void processRotatePlane90() {
        	movingPlane.rotate(0, FastMath.HALF_PI, 0);
        	if (state == HandState.Moving) {
        		if (!movingStart()) {
        			movingEnd();
        		}
        	}
        }
        
        private void processMouseButtonEvent(boolean isPressed) {
            if (state == HandState.Idle) {
                if (!isPressed) {
                    Spatial cursorObj = getCursorItem(rootNode);
                    if (cursorObj != null) { 
                        grasp(cursorObj);
                    }
                }
            } else if (state == HandState.Grasped) {
                if (isPressed) {
                    if (!movingStart()) {
                        release();
                    }
                }
            } else if (state == HandState.Moving) {
                if (!isPressed) {
                    movingEnd();
                }
            }
        }
        
        private void processMouseMoveEvent() {
            if (state == HandState.Moving) {
                Vector3f pos = getCursorPosOnMovingPlane();
                if (pos != null) {
                    pos.addLocal(movingCursorOffset);
                    if (move(pos, 0.1f) < 1 && !movingStart()) {
                        movingEnd();
                    }
                } else {
                    movingEnd();
                }
            }
        }

    	private void grasp(Spatial s) {
            if (state != HandState.Idle) {
                throw new IllegalStateException("illegal operation");
            }
            if (s == null) {
                throw new NullPointerException();
            }
            
        	// if it is currently being grasped by another hand
            if (s.getParent() != rootNode) {
            	Hand graspingHand = null;
            	for (Hand h : hands) {
            		if (h.graspedItem == s) {
            			graspingHand = h;
            			break;
            		}
            	}
            	if (graspingHand == null) {
            		throw new IllegalStateException("item " + s 
            				+ " is neither free nor being grapsed by a demonstrator hand.");
            	}
            	HandId currHandId = currHand.id;
            	selectHand(graspingHand.id);
            	graspingHand.release();
            	selectHand(currHandId);
            }

            // make sure only two hands are in use
            if (id == HandId.LeftHand || id == HandId.RightHand) {
            	if (getHand(HandId.BothHands).state != HandState.Idle) {
            		getHand(HandId.BothHands).release();
            	}
                if (id == HandId.LeftHand 
                		&& getHand(HandId.RightHand).state != HandState.Idle
            			&& getHand(HandId.AnyHand).state != HandState.Idle) {
            		getHand(HandId.AnyHand).release();
                }
                if (id == HandId.RightHand 
                		&& getHand(HandId.LeftHand).state != HandState.Idle
            			&& getHand(HandId.AnyHand).state != HandState.Idle) {
            		getHand(HandId.AnyHand).release();
                }
            }
            // if both hands are going to grasp an item (together), release any items held by
            // all other hands
            if (id == HandId.BothHands) {
            	for (HandId id : HandId.values()) {
            		if (id != HandId.BothHands && getHand(id).state != HandState.Idle) {
            			getHand(id).release();
            		}
            	}
            }
            // if "any" hand is used, release all other hands
            if (id == HandId.AnyHand) {
            	if (getHand(HandId.BothHands).state != HandState.Idle) {
            		app.showMessage("both hands are occupied");
            		return;
            	}
            	if (getHand(HandId.LeftHand).state != HandState.Idle
            			&& getHand(HandId.RightHand).state != HandState.Idle) {
            		app.showMessage("both hands are occupied");
            		return;
            	}
            }

            // notify listeners
            for (DemoPreActionListener l : Demonstrator.this.demoPreActionListeners) {
                l.demoPreGrasp(currHand.id);
            }

            graspedItem = s;
            graspNode.setLocalTransform(s.getLocalTransform());
            
            rootNode.detachChild(s);
            s.setLocalTransform(Transform.IDENTITY);
            graspNode.attachChild(s);

            MyRigidBodyControl rbc = graspedItem.getControl(MyRigidBodyControl.class);
            graspedItemIsKinematic = (rbc.getMass() == 0);
            if (!graspedItemIsKinematic) {
                rbc.changeMass(rbc.getMass() + 9999);
                rbc.setKinematic(true);
            }
            
            // wake up connected (by joints) items and prevent them from going into sleep,
            // so that they are aware of any translation/rotation occurring at the grasped item.
            inventory.updateItemInsomnia(graspedItem);

            Demonstrator.this.setVisualAid(graspNode);
            Demonstrator.this.showVisualAid(graspedItem);
            
            state = HandState.Grasped;
            userRotAngles[0] = 0;
            userRotAngles[1] = 0;
            userRotAngles[2] = 0;
            
            // notify listeners
            for (DemoActionListener l : Demonstrator.this.demoActionListeners) {
                l.demoGrasp(currHand.id, graspedItem, 
                		graspNode.getLocalTranslation(), graspNode.getLocalRotation());
            }
        }
    	
        private boolean movingStart() {
            Spatial cursorObj = getCursorItem(rootNode);
            movingCursorOffset = getCursorPosOnMovingPlane();
            if (cursorObj == graspedItem && movingCursorOffset != null) {
                movingCursorOffset.negateLocal();
                movingCursorOffset.addLocal(graspNode.getLocalTranslation());
                state = HandState.Moving;
                return true;
            } else {
                return false;
            }
        }
        
        private float move(Vector3f pos, float deltaRangePrecision) {
            if (state != HandState.Grasped && state != HandState.Moving) {
                throw new IllegalStateException("illegal operation");
            }
            
            if (pos == null) {
                throw new NullPointerException();
            }
            graspNodeTarget.set(graspNode.getLocalTransform());
            graspNodeTarget.setTranslation(pos);
            float delta = tryTransformingGraspNode(graspNode, graspNodeTarget, deltaRangePrecision);
            if (delta < 1) {
                pos.set(graspNode.getLocalTranslation());
            }
            visualAid.setLocalTranslation(pos);
            return delta;
        }
        
        private void movingEnd() {
            state = HandState.Grasped;
        }
        
        private float rotate(Quaternion rot, float deltaRangePrecision) {
            if (state != HandState.Grasped) {
                throw new IllegalStateException("illegal operation");
            }
            if (rot == null) {
                throw new NullPointerException();
            }
            graspNodeTarget.set(graspNode.getLocalTransform());
            graspNodeTarget.setRotation(rot);
            float delta = tryTransformingGraspNode(graspNode, graspNodeTarget, deltaRangePrecision);
            if (delta < 1) {
                rot.set(graspNode.getLocalRotation());
            }
            return delta;
        }
        
        private float rotateAroundCanonicalAxis(int axisIndex, float angle, float deltaRangePrecision) {
        	Vector3f rotAxis = null;
        	if (axisIndex == 0) {
        		rotAxis = Vector3f.UNIT_X;
        	} else if (axisIndex == 1) {
        		rotAxis = Vector3f.UNIT_Y;
        	} else if (axisIndex == 2) {
        		rotAxis = Vector3f.UNIT_Z;
        	} else {
        		throw new IllegalArgumentException("unknown axis index " + axisIndex);
        	}
        	float angleDiff = angle - userRotAngles[axisIndex];    	
        	quat.fromAngleNormalAxis(angleDiff, rotAxis);
        	quat.multLocal(graspNode.getLocalRotation());
        	float delta = rotate(quat, deltaRangePrecision);
    		userRotAngles[axisIndex] += angleDiff * delta;
        	return userRotAngles[axisIndex];
        }
            
        private void release() {
            if (state != HandState.Grasped) {
                throw new IllegalStateException("illegal operation");
            }

            // notify listeners
            for (DemoPreActionListener l : Demonstrator.this.demoPreActionListeners) {
                l.demoPreRelease(currHand.id);
            }

            graspNode.detachChild(graspedItem);
            graspedItem.setLocalTransform(graspNode.getLocalTransform());
            rootNode.attachChild(graspedItem);

            MyRigidBodyControl rbc = graspedItem.getControl(MyRigidBodyControl.class);
            if (!graspedItemIsKinematic) {
                rbc.setKinematic(false);
                rbc.changeMass(rbc.getMass() - 9999);
            }

            // allow the connected (by joints) items of the grasped item to go back to sleep
            inventory.updateItemInsomnia(graspedItem);

            graspedItem = null;
            
            Demonstrator.this.showVisualAid(null);
            
            state = HandState.Idle;
            
            // notify listeners
            for (DemoActionListener l : demoActionListeners) {
                l.demoRelease(currHand.id);
            }
        }

        private void destroy() {
            if (state != HandState.Grasped) {
                throw new IllegalStateException("illegal operation");
            }

            // notify listeners
            for (DemoPreActionListener l : Demonstrator.this.demoPreActionListeners) {
                l.demoPreDestroy(currHand.id);
            }

            // wake up connected (by joints) items
            graspedItem.getControl(MyRigidBodyControl.class).setKinematic(false);
            inventory.updateItemInsomnia(graspedItem);
//            Set<PhysicsJoint> joints = inventory.getPhysicsJointsForItem(graspedItem);
//            if (joints != null) {
//                for (PhysicsJoint j : joints) {
//                    bulletAppState.getPhysicsSpace().remove(j);
//                    
//                    MyRigidBodyControl c = (MyRigidBodyControl)j.getBodyA();
//                    c.forceActivationState(CollisionObject.ACTIVE_TAG);
//                    c.activate();
//                    c = (MyRigidBodyControl)j.getBodyB();
//                    c.forceActivationState(CollisionObject.ACTIVE_TAG);
//                    c.activate();
//                }
//            }

            inventory.removeItem(graspedItem);
            graspedItem = null;
            
            Demonstrator.this.showVisualAid(null);
            
            state = HandState.Idle;
            
            // notify listeners
            for (DemoActionListener l : demoActionListeners) {
                l.demoDestroy(currHand.id);
            }
        }
        
        private class Memento {
        	private HandId id;
        	private HandState state;
//        	private Transform graspNodeTrans;
        	private Spatial graspedItem;
        	private float[] userRotAngles;
        }
        
        private Memento saveToMemento() {
        	Memento m = new Memento();
        	m.id = id;
        	m.state = state;
//        	m.graspNodeTrans = graspNode.getLocalTransform().clone();
        	m.graspedItem = graspedItem;
        	m.userRotAngles = userRotAngles.clone();
        	return m;
        }
        
        private void restoreFromMemento(Memento m) {
        	if (m.id != id) {
        		throw new IllegalArgumentException("inconsistent memento hand id: " + m.id 
        				+ " (my id is " + id + ")");
        	}
        	state = m.state;
        	if (state != HandState.Idle) {
        		// attach grasped item
        		graspedItem = m.graspedItem;
        		graspedItem.getParent().detachChild(graspedItem);
        		graspNode.attachChild(graspedItem);
            	graspNode.setLocalTransform(graspedItem.getLocalTransform());
            	graspedItem.setLocalTransform(Transform.IDENTITY);
            	// set userRotAngles
            	userRotAngles[0] = m.userRotAngles[0];
            	userRotAngles[1] = m.userRotAngles[1];
            	userRotAngles[2] = m.userRotAngles[2];
        	}
        }
    }    
    
    public Demonstrator(String name, MainApp app) {
    	this.name = name;
    	this.app = app;
        rootNode = app.getRootNode();
        cam = app.getViewPort().getCamera();
        inputManager = app.getInputManager();
        inventory = app.getInventory();
        table = app.getTable();
        
        visualAid = new Node(name + "VisualAid");
        movingPlane = new Node(name + "MovingPlane");
        visualAid.attachChild(movingPlane);        
        Factory factory = app.getFactory();
        
        Vector2f planeSize = new Vector2f(table.getWidth(), table.getWidth());
        planeSize.multLocal(4);
        Geometry g = factory.makeUnshadedPlane(name + "MovingSubplane1",
                planeSize.x, planeSize.y, new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalTranslation(-planeSize.x / 2, -planeSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedPlane(name + "MovingSubplane2",
                planeSize.x, planeSize.y, new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalRotation(new Quaternion(new float[]{0, FastMath.PI, 0}));
        g.setLocalTranslation(planeSize.x / 2, -planeSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedLine(name + "ShadowLine", Vector3f.ZERO, 
                new Vector3f(0, -planeSize.y / 2, 0), ColorRGBA.Black);
        movingPlane.attachChild(g);
        
        g = factory.makeUnshadedArrow(name + "AxisArrowX", 
                Vector3f.UNIT_X.mult(10), 2, ColorRGBA.Red);
        g.setLocalTranslation(Vector3f.UNIT_X.negate().multLocal(5));
        visualAid.attachChild(g);
        g = factory.makeUnshadedArrow(name + "AxisArrowY", 
                Vector3f.UNIT_Y.mult(10), 2, ColorRGBA.Blue);
        g.setLocalTranslation(Vector3f.UNIT_Y.negate().multLocal(5));
        visualAid.attachChild(g);
        g = factory.makeUnshadedArrow(name + "AxisArrowZ", 
                Vector3f.UNIT_Z.mult(10).negateLocal(), 2, ColorRGBA.Green);
        g.setLocalTranslation(Vector3f.UNIT_Z.mult(5));
        visualAid.attachChild(g);
        
        sceneProcessor = new DemoSceneProcessor(app, visualAid);
        app.getViewPort().addProcessor(sceneProcessor);

        for (HandId hId : HandId.values()) {
        	int hIndex = hId.getValue();
        	hands[hIndex] = new Hand(name + " graspNode" + hIndex, hId);
        }
        selectHand(HandId.AnyHand);
    }
    
    public void setEnabled(boolean v) {
    	enabled = v;
    }
    
    public String getName() {
    	return name;
    }
    
    public void selectHand(HandId handId) {
    	if (currHand != null) {
    		currHand.processDeselect();
    	}
		currHand = getHand(handId);
		currHand.processSelect();
    }
    
    public Hand getCurrHand() {
    	return currHand;
    }
    
    private Hand getHand(HandId id) {
    	return hands[id.getValue()];
    }
    
    public void initKeys(InputManager inputManager) {
        inputManager.addMapping(name + "LeftClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(name + "RightClick", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping(name + "MouseMove", 
                new MouseAxisTrigger(MouseInput.AXIS_X, true), 
                new MouseAxisTrigger(MouseInput.AXIS_X, false),
                new MouseAxisTrigger(MouseInput.AXIS_Y, true),
                new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping(name + "PlaneRotate", new KeyTrigger(KeyInput.KEY_SLASH));
        inputManager.addMapping(name + "PlaneRotate90", new KeyTrigger(KeyInput.KEY_LCONTROL));

        inputManager.addListener(this, "shiftKey");

        inputManager.addListener(this, name + "LeftClick");
        inputManager.addListener(this, name + "RightClick");
        inputManager.addListener(this, name + "MouseMove");
        inputManager.addListener(this, name + "PlaneRotate");
        inputManager.addListener(this, name + "PlaneRotate90");
    }

    public void onAction(String eName, boolean isPressed, float tpf) {
    	if (!enabled) {
    		return;
    	}
    	if (eName.equals("shiftKey")) {
            shiftKey = isPressed;
        } else if (eName.equals(name + "LeftClick")) {
        	currHand.processMouseButtonEvent(isPressed);
        } else if (eName.equals(name + "PlaneRotate90")) {
        	currHand.processRotatePlane90();
        }
    }

    public void onAnalog(String eName, float value, float tpf) {
    	if (!enabled) {
    		return;
    	}
        if (eName.equals(name + "PlaneRotate")) {
            if (currHand.state == HandState.Grasped) {
                transform.set(movingPlane.getLocalTransform());
                if (!shiftKey) {
                    movingPlane.rotate(0, value * 0.3f, 0);
                } else {
                    movingPlane.rotate(0, -value * 0.3f, 0);
                }
                
                // move the camera with respect to the rotating movingPlane
                vec.set(cam.getLocation());
                visualAid.getLocalTransform().transformInverseVector(vec, vec);
                transform.transformInverseVector(vec, vec);
                movingPlane.getLocalTransform().transformVector(vec, vec);
                visualAid.getLocalTransform().transformVector(vec, vec);
                cam.setLocation(vec);
                // adjust the camera direction too
                // NOTE: camera direction is unaffected by the translation of
                // visualAid
                vec.set(cam.getDirection());
                transform.transformInverseVector(vec, vec);
                movingPlane.getLocalTransform().transformVector(vec, vec);
                cam.lookAtDirection(vec, Vector3f.UNIT_Y);
            }
        } else if (eName.equals(name + "MouseMove")) {
        	currHand.processMouseMoveEvent();
        }
    }
    
    public void release() {
    	currHand.release();
    }
    
    public void destroy() {
    	currHand.destroy();
    }
    
    public float rotate(int axisInd, float targetUserAngle) {
    	return currHand.rotateAroundCanonicalAxis(axisInd, targetUserAngle, 0.02f);
    }

    private void setVisualAid(Node graspNode) {
        visualAid.setLocalTranslation(graspNode.getLocalTranslation());
        // init the moving plane to be perpendicular to the camera's horizontal direction
        float[] angles = new float[3];
        cam.getRotation().toAngles(angles);
        movingPlane.setLocalRotation(new Quaternion().fromAngleAxis(angles[1], Vector3f.UNIT_Y));
    }
    
    private void showVisualAid(Spatial highlightSpatial) {
    	if (highlightSpatial != null) {
            // draw visual aid
            sceneProcessor.setShowVisualAid(true);
            sceneProcessor.highlightSpatial(null);
            sceneProcessor.highlightSpatial(highlightSpatial);
    	} else {
            sceneProcessor.setShowVisualAid(false);
            sceneProcessor.highlightSpatial(null);    		
    	}
    }

    private boolean graspNodeCollidedWithTable(Node graspNode) {
        collisionResults.clear();
        graspNode.collideWith(table.getWorldBound(), collisionResults);
        if (collisionResults.size() > 0) {
            return true;
        }
        return false;
    }
    
    private float tryTransformingGraspNode(Node graspNode, Transform target, float deltaRangePrecision) {
        minTransform.set(graspNode.getLocalTransform());

        // if transforming to the target does not cause a collision, do it.
        graspNode.setLocalTransform(target);
        if (!graspNodeCollidedWithTable(graspNode)) {
            return 1;
        }
        
        float min = 0;
        float max = 1;
        float delta;
        while (max - min > deltaRangePrecision) {
            delta = (min + max) / 2f;
            midTransform.interpolateTransforms(minTransform, target, delta);
            graspNode.setLocalTransform(midTransform);
            if (graspNodeCollidedWithTable(graspNode)) {
                midTransform.interpolateTransforms(minTransform, target, min);
                graspNode.setLocalTransform(midTransform);
                max = delta;
            } else {
                min = delta;
            }
        }
        return min;
    }
    
    private Spatial getCursorItem(Node rootNode) {
        CollisionResult r = getCursorClosestCollision(rootNode);
        if (r == null) {
            return null;
        }
        Spatial item = inventory.getItem(r.getGeometry());
        return item;
    }
    
    private Vector3f getCursorRayDirection() {
        Vector2f screenCoords = inputManager.getCursorPosition();
        Vector3f dir = cam.getWorldCoordinates(screenCoords, 1).subtractLocal(
                cam.getWorldCoordinates(screenCoords, 0)).normalizeLocal();
        return dir;
    }
    
    private CollisionResult getCursorClosestCollision(Spatial objTree) {
        Vector3f dir = getCursorRayDirection();
        ray.setOrigin(cam.getLocation());
        ray.setDirection(dir);
        collisionResults.clear();
        objTree.collideWith(ray, collisionResults);
        if (collisionResults.size() > 0) {
            return collisionResults.getClosestCollision();
        } else {
            return null;
        }
    }
    
    private Vector3f getCursorPosOnMovingPlane() {
        CollisionResult res = getCursorClosestCollision(movingPlane);
        if (res != null) {
            return res.getContactPoint();
        } else {
            return null;
        }
    }
    
    public void addActionListener(DemoActionListener listener) {
        demoActionListeners.add(listener);
    }
    
    public void addPreActionListener(DemoPreActionListener listener) {
    	demoPreActionListeners.add(listener);
    }
    
    public class Memento {
    	private Hand.Memento[] handMementos;
    	private HandId currHandId;
    	private Memento(int size) {
    		handMementos = new Hand.Memento[size];
    	}
    }
    
    public Memento saveToMemento() {
    	Memento m = new Memento(hands.length);
    	for (int h = 0; h < hands.length; ++h) {
    		m.handMementos[h] = hands[h].saveToMemento();
    	}
    	m.currHandId = currHand.id;
    	return m;
    }
    
    public void restoreFromMemento(Memento m) {
    	for (int h = 0; h < hands.length; ++h) {
    		hands[h].restoreFromMemento(m.handMementos[h]);
    	}
    	selectHand(m.currHandId);
    }
}
