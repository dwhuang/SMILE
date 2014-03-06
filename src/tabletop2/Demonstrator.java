/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Transform;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author dwhuang
 */
public class Demonstrator implements ActionListener, AnalogListener, PhysicsCollisionListener {

    private enum DemoState {
        Idle, Grasped, Moving
    }
    private Node rootNode;
    private Camera cam;
    private InputManager inputManager;
    private Robot robot;
    private final HashMap<Geometry, Spatial> grabbables;

    private ArrayList<DemonstrationListener> demoListeners = new ArrayList<DemonstrationListener>();
    private DemonstratorSceneProcessor sceneProcessor;

    private DemoState state = DemoState.Idle;
    private Node visualAid;
    private Node movingPlane;
    private Node graspNode;
    private Spatial graspedObject = null;
    private Vector3f movingCursorOffset;
    
    private boolean shiftKey = false;
//    private float rightClickTime = 0;
    
    private Ray ray = new Ray();
    private CollisionResults collisionResults = new CollisionResults();
    private float[] angles = new float[3];
    private Quaternion quat = new Quaternion();
    private Vector3f vec = new Vector3f();
    private Transform transform = new Transform();

    public Demonstrator(String name, Node rootNode, ViewPort viewPort,
            AssetManager assetManager, InputManager inputManager, 
            Vector2f movingPlaneSize, 
            final HashMap<Geometry, Spatial> grabbables, 
            Factory factory, Robot robot) {
        this.rootNode = rootNode;
        this.cam = viewPort.getCamera();
        this.inputManager = inputManager;
        this.grabbables = grabbables;
        this.robot = robot;

        visualAid = new Node(name + "visualAid");
        movingPlane = new Node(name + " movingPlane");
        visualAid.attachChild(movingPlane);
        Geometry g = factory.makeUnshadedPlane(name + " movingSubplane1",
                movingPlaneSize.x, movingPlaneSize.y,
                new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalTranslation(-movingPlaneSize.x / 2, -movingPlaneSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedPlane(name + " movingSubplane2",
                movingPlaneSize.x, movingPlaneSize.y,
                new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalRotation(new Quaternion(new float[]{0, FastMath.PI, 0}));
        g.setLocalTranslation(movingPlaneSize.x / 2, -movingPlaneSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedLine(name + " shadowLine", Vector3f.ZERO, 
                new Vector3f(0, -movingPlaneSize.y / 2, 0), ColorRGBA.Black);
        movingPlane.attachChild(g);
        
        g = factory.makeUnshadedArrow(name + " axisArrowX", 
                Vector3f.UNIT_X.mult(10), 2, ColorRGBA.Red);
        g.setLocalTranslation(Vector3f.UNIT_X.negate().multLocal(5));
        visualAid.attachChild(g);
        g = factory.makeUnshadedArrow(name + " axisArrowY", 
                Vector3f.UNIT_Y.mult(10), 2, ColorRGBA.Blue);
        g.setLocalTranslation(Vector3f.UNIT_Y.negate().multLocal(5));
        visualAid.attachChild(g);
        g = factory.makeUnshadedArrow(name + " axisArrowZ", 
                Vector3f.UNIT_Z.mult(10).negateLocal(), 2, ColorRGBA.Green);
        g.setLocalTranslation(Vector3f.UNIT_Z.mult(5));
        visualAid.attachChild(g);
        
        graspNode = new Node(name + " graspNode");
        rootNode.attachChild(graspNode);

        sceneProcessor = new DemonstratorSceneProcessor(assetManager, visualAid);
        viewPort.addProcessor(sceneProcessor);
    }

    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals("shiftKey")) {
            shiftKey = isPressed;
        } else if (name.equals("demoLeftClick")) {
            if (state == DemoState.Idle) {
                if (!isPressed) {
                    Spatial cursorObj = getCursorObject(rootNode);
                    if (cursorObj != null) { 
                        grasp(cursorObj);
                    }
                }
            } else if (state == DemoState.Grasped) {
                if (isPressed) {
                    if (!movingStart()) {
                        release();
                    }
                }
            } else if (state == DemoState.Moving) {
                if (!isPressed) {
                    movingEnd();
                }
            }
//        } else if (name.equals("demoRightClick")) {
//            if (!isPressed) {
//                if (state == DemoState.Idle) {
//                    CollisionResult result = getCursorClosestCollision(rootNode);
//                    if (result != null) {
//                        Geometry target = result.getGeometry();
//                        Spatial s = grabbables.get(target);
//                        if (s != null && s.getParent() == rootNode) {
//                            RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
//                            Vector3f dir = getCursorRayDirection();
//                            dir.y = 0.05f; // ignore vertical impulse
//                            dir.multLocal(rightClickTime * 10);
//                            Vector3f pt = result.getContactPoint();
//                            s.getLocalTransform().transformInverseVector(pt, pt);                            
//                            rbc.applyImpulse(dir, pt);
//                        }
//                    }
//                }
//                rightClickTime = 0;
//            }
        }
    }

    public void onAnalog(String name, float value, float tpf) {
        if (name.equals("demoPlaneRotate")) {
            if (state == DemoState.Grasped) {
                transform.set(movingPlane.getLocalTransform());
                if (!shiftKey) {
                    movingPlane.rotate(0, value * 0.3f, 0);
                } else {
                    movingPlane.rotate(0, -value * 0.3f, 0);
                }
                
                // move camera relatively to the rotated movingPlane
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
        } else if (name.equals("demoMouseMove")) {
            if (state == DemoState.Moving) {
                Vector3f pos = getCursorPosOnMovingPlane();
                if (pos != null) {
                    pos.addLocal(movingCursorOffset);
                    move(pos);
                } else {
                    movingEnd();
                }
            }
//        } else if (name.equals("demoRightClick")) {
//            rightClickTime += tpf;
        }
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {
        
    }
    
    public void grasp(Spatial s) {
        if (state != DemoState.Idle) {
            throw new IllegalStateException("illegal operation");
        }
        if (s == null) {
            throw new NullPointerException();
        }
        
        graspedObject = s;
        graspNode.setLocalTransform(s.getLocalTransform());
        rootNode.detachChild(s);
        s.setLocalTransform(Transform.IDENTITY);
        graspNode.attachChild(s);

        RigidBodyControl rbc = graspedObject.getControl(RigidBodyControl.class);
        rbc.setKinematic(true);
        rbc.setMass(rbc.getMass() + 9999);                                    

        visualAid.setLocalTranslation(graspNode.getLocalTranslation());
        // init the moving plane such that it is perpendicular to the camera direction
        angles = new float[3];
        cam.getRotation().toAngles(angles);
        angles[0] = 0;
        angles[2] = 0;
        quat.fromAngles(angles);
        movingPlane.setLocalRotation(quat);
        // draw visual aid
        sceneProcessor.setShowVisualAid(true);
        sceneProcessor.highlightSpatial(s);
        
        state = DemoState.Grasped;
        for (DemonstrationListener l : demoListeners) {
            l.demoGrasp(graspedObject, graspNode.getLocalTranslation(), graspNode.getLocalRotation());
        }
    }

    private boolean movingStart() {
        Spatial cursorObj = getCursorObject(rootNode);
        movingCursorOffset = getCursorPosOnMovingPlane();
        if (cursorObj == graspedObject && movingCursorOffset != null) {
            movingCursorOffset.negateLocal();
            movingCursorOffset.addLocal(graspNode.getLocalTranslation());
            state = DemoState.Moving;
            return true;
        } else {
            return false;
        }
    }
    
    public void move(Vector3f pos) {
        if (state != DemoState.Grasped && state != DemoState.Moving) {
            throw new IllegalStateException("illegal operation");
        }
        
        if (pos == null) {
            throw new NullPointerException();
        }
        graspNode.setLocalTranslation(pos);
        visualAid.setLocalTranslation(pos);
    }
    
    private void movingEnd() {
        state = DemoState.Grasped;
    }
    
    public void rotate(Quaternion rot) {
        if (state != DemoState.Grasped) {
            throw new IllegalStateException("illegal operation");
        }
        graspNode.setLocalRotation(rot);
    }
    
    public void release() {
        if (state != DemoState.Grasped) {
            throw new IllegalStateException("illegal operation");
        }

        graspNode.detachChild(graspedObject);
        graspedObject.setLocalTransform(graspNode.getLocalTransform());
        rootNode.attachChild(graspedObject);

        RigidBodyControl oldControl = graspedObject.getControl(RigidBodyControl.class);
        RigidBodyControl newControl = new RigidBodyControl(oldControl.getMass() - 9999);
        graspedObject.addControl(newControl);
        oldControl.getPhysicsSpace().add(newControl);                
        graspedObject.removeControl(oldControl);
        oldControl.getPhysicsSpace().remove(oldControl);

        graspedObject = null;
        
        sceneProcessor.setShowVisualAid(false);
        sceneProcessor.highlightSpatial(null);
        
        state = DemoState.Idle;
        for (DemonstrationListener l : demoListeners) {
            l.demoRelease();
        }
    }

//    private void gotoIdleState(boolean release) {
//        if (release) {
//            releaseObject();
//        }
//        selectedObject = null;
//        sceneProcessor.setShowVisualAid(false);
//        sceneProcessor.highlightSpatial(null);
//        state = DemoState.Idle;
//        
//        robot.setDemoCue(false);
//    }
//    
//    private void gotoSelectedState(Spatial object) {
//        selectedObject = object;
//        Vector3f objCenter = object.getLocalTranslation();
//        objCenter.y = 0;
//        cam.getRotation().toAngles(angles);
//        angles[0] = 0;
//        angles[2] = 0;
//        quat.fromAngles(angles);
//        
//        movingPlane.setLocalTranslation(objCenter);
//        movingPlane.setLocalRotation(quat);
//        demoNode.setLocalTranslation(objCenter);
//        sceneProcessor.setShowVisualAid(true);
//        sceneProcessor.highlightSpatial(object);
//        state = DemoState.Anchored;
//
//        robot.setDemoCue(false);
//    }
    
//    private void gotoMovingState(Vector3f planeContact) {
//        holdObject(planeContact);
//        state = DemoState.Moving;
//        
//        robot.setDemoCue(true);
//    }

    private Spatial getCursorObject(Node rootNode) {
        CollisionResult r = getCursorClosestCollision(rootNode);
        if (r == null) {
            return null;
        }
        return getWholeGrabbableSpatial(r.getGeometry());
    }
    
    private Spatial getWholeGrabbableSpatial(Spatial s) {
        if (s instanceof Geometry) {
            Spatial obj = grabbables.get((Geometry)s);
            if (obj != null && obj.getParent() != rootNode && obj.getParent() != graspNode) {
                return null;
            } else {
                return obj;
            }
        } else {
            if (!grabbables.containsValue(s) || (s.getParent() != rootNode && s.getParent() != graspNode)) {
                return null;
            } else {
                return s;
            }
        }
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
    
    public void addListener(DemonstrationListener listener) {
        demoListeners.add(listener);
    }
    
    public void removeListener(DemonstrationListener listener) {
        demoListeners.remove(listener);
    }
}
