/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.joints.PhysicsJoint;
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
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author dwhuang
 */
public class Demonstrator implements ActionListener, AnalogListener {

    private enum DemoState {
        Idle, Grasped, Moving
    }
    private Node rootNode;
    private BulletAppState bulletAppState;
    private Camera cam;
    private InputManager inputManager;
    private final Inventory inventory;
    private Spatial table;

    private ArrayList<DemonstrationListener> demoListeners = new ArrayList<DemonstrationListener>();
    private DemonstratorSceneProcessor sceneProcessor;

    private DemoState state = DemoState.Idle;
    private Node visualAid;
    private Node movingPlane;
    private Node graspNode;
    private Spatial graspedItem = null;
    private Vector3f movingCursorOffset;
    
    private boolean shiftKey = false;
    
    private transient Ray ray = new Ray();
    private transient CollisionResults collisionResults = new CollisionResults();
    private transient Vector3f vec = new Vector3f();
    private transient Transform transform = new Transform();
    private transient Transform graspNodeTarget = new Transform();

//    public Demonstrator(String name, Node rootNode, ViewPort viewPort,
//            AssetManager assetManager, InputManager inputManager, 
//            Vector2f movingPlaneSize, 
//            final HashMap<Geometry, Spatial> grabbables, 
//            Factory factory, Robot robot) {
    public Demonstrator(String name, MainApp app) {
        rootNode = app.getRootNode();
        bulletAppState = app.getBulletAppState();
        cam = app.getViewPort().getCamera();
        inputManager = app.getInputManager();
        inventory = app.getInventory();
        table = app.getTable();
        
        visualAid = new Node(name + "visualAid");
        movingPlane = new Node(name + " movingPlane");
        visualAid.attachChild(movingPlane);        
        Factory factory = app.getFactory();
        
        Vector2f planeSize = new Vector2f(MainApp.TABLE_SIZE.x, MainApp.TABLE_SIZE.x);
        planeSize.multLocal(4);
        Geometry g = factory.makeUnshadedPlane(name + " movingSubplane1",
                planeSize.x, planeSize.y, new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalTranslation(-planeSize.x / 2, -planeSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedPlane(name + " movingSubplane2",
                planeSize.x, planeSize.y, new ColorRGBA(0.5f, 0.5f, 1, 0.3f));
        g.setLocalRotation(new Quaternion(new float[]{0, FastMath.PI, 0}));
        g.setLocalTranslation(planeSize.x / 2, -planeSize.y / 2, 0);
        movingPlane.attachChild(g);
        g = factory.makeUnshadedLine(name + " shadowLine", Vector3f.ZERO, 
                new Vector3f(0, -planeSize.y / 2, 0), ColorRGBA.Black);
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

        sceneProcessor = new DemonstratorSceneProcessor(app.getAssetManager(), visualAid);
        app.getViewPort().addProcessor(sceneProcessor);
    }

    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals("shiftKey")) {
            shiftKey = isPressed;
        } else if (name.equals("demoLeftClick")) {
            if (state == DemoState.Idle) {
                if (!isPressed) {
                    Spatial cursorObj = getCursorItem(rootNode);
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
        } else if (name.equals("demoMouseMove")) {
            if (state == DemoState.Moving) {
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
    }

    public void grasp(Spatial s) {
        if (state != DemoState.Idle) {
            throw new IllegalStateException("illegal operation");
        }
        if (s == null) {
            throw new NullPointerException();
        }
        
        graspedItem = s;
        graspNode.setLocalTransform(s.getLocalTransform());
        rootNode.detachChild(s);
        s.setLocalTransform(Transform.IDENTITY);
        graspNode.attachChild(s);

        MyRigidBodyControl rbc = graspedItem.getControl(MyRigidBodyControl.class);
        rbc.setMass(rbc.getMass() + 9999);    
        rbc.setKinematic(true);
        
        Set<PhysicsJoint> joints = inventory.getPhysicsJointsForItem(graspedItem);
        if (joints != null) {
            for (PhysicsJoint joint : joints) {
                MyRigidBodyControl c = (MyRigidBodyControl)joint.getBodyA();
                c.forceActivationState(CollisionObject.DISABLE_DEACTIVATION);
                c.activate();
                c = (MyRigidBodyControl)joint.getBodyB();
                c.forceActivationState(CollisionObject.DISABLE_DEACTIVATION);
                c.activate();
            }
        }

        visualAid.setLocalTranslation(graspNode.getLocalTranslation());
        // init the moving plane such that it is perpendicular to the camera direction
        float[] angles = new float[3];
        cam.getRotation().toAngles(angles);
        movingPlane.setLocalRotation(new Quaternion().fromAngleAxis(angles[1], Vector3f.UNIT_Y));
        // draw visual aid
        sceneProcessor.setShowVisualAid(true);
        sceneProcessor.highlightSpatial(s);
        
        state = DemoState.Grasped;
        for (DemonstrationListener l : demoListeners) {
            l.demoGrasp(graspedItem, graspNode.getLocalTranslation(), graspNode.getLocalRotation());
        }
    }

    private boolean movingStart() {
        Spatial cursorObj = getCursorItem(rootNode);
        movingCursorOffset = getCursorPosOnMovingPlane();
        if (cursorObj == graspedItem && movingCursorOffset != null) {
            movingCursorOffset.negateLocal();
            movingCursorOffset.addLocal(graspNode.getLocalTranslation());
            state = DemoState.Moving;
            return true;
        } else {
            return false;
        }
    }
    
    public float move(Vector3f pos, float deltaRangePrecision) {
        if (state != DemoState.Grasped && state != DemoState.Moving) {
            throw new IllegalStateException("illegal operation");
        }
        
        if (pos == null) {
            throw new NullPointerException();
        }
        graspNodeTarget.set(graspNode.getLocalTransform());
        graspNodeTarget.setTranslation(pos);
        float delta = tryTransformingGraspNode(graspNodeTarget, deltaRangePrecision);
        if (delta < 1) {
            pos.set(graspNode.getLocalTranslation());
        }
        visualAid.setLocalTranslation(pos);
        return delta;
    }
    
    private void movingEnd() {
        state = DemoState.Grasped;
    }
    
    public float rotate(Quaternion rot, float deltaRangePrecision) {
        if (state != DemoState.Grasped) {
            throw new IllegalStateException("illegal operation");
        }
        if (rot == null) {
            throw new NullPointerException();
        }
        graspNodeTarget.set(graspNode.getLocalTransform());
        graspNodeTarget.setRotation(rot);
        float delta = tryTransformingGraspNode(graspNodeTarget, deltaRangePrecision);
        if (delta < 1) {
            rot.set(graspNode.getLocalRotation());
        }
        return delta;
    }
    
    public void release() {
        if (state != DemoState.Grasped) {
            throw new IllegalStateException("illegal operation");
        }

        graspNode.detachChild(graspedItem);
        graspedItem.setLocalTransform(graspNode.getLocalTransform());
        rootNode.attachChild(graspedItem);

        MyRigidBodyControl rbc = graspedItem.getControl(MyRigidBodyControl.class);
        rbc.setKinematic(false);
        rbc.setMass(rbc.getMass() - 9999);

        Set<PhysicsJoint> joints = inventory.getPhysicsJointsForItem(graspedItem);
        if (joints != null) {
            for (PhysicsJoint joint : joints) {
                MyRigidBodyControl c = (MyRigidBodyControl)joint.getBodyA();
                c.forceActivationState(CollisionObject.ACTIVE_TAG);
                c.activate();
                c = (MyRigidBodyControl)joint.getBodyB();
                c.forceActivationState(CollisionObject.ACTIVE_TAG);
                c.activate();
            }
        }

        graspedItem = null;
        
        sceneProcessor.setShowVisualAid(false);
        sceneProcessor.highlightSpatial(null);
        
        state = DemoState.Idle;
        for (DemonstrationListener l : demoListeners) {
            l.demoRelease();
        }
    }

    public void destroy() {
        if (state != DemoState.Grasped) {
            throw new IllegalStateException("illegal operation");
        }

        graspNode.detachChild(graspedItem);
        
        Set<PhysicsJoint> joints = inventory.getPhysicsJointsForItem(graspedItem);
        if (joints != null) {
            for (PhysicsJoint j : joints) {
                bulletAppState.getPhysicsSpace().remove(j);
                
                MyRigidBodyControl c = (MyRigidBodyControl)j.getBodyA();
                c.forceActivationState(CollisionObject.ACTIVE_TAG);
                c.activate();
                c = (MyRigidBodyControl)j.getBodyB();
                c.forceActivationState(CollisionObject.ACTIVE_TAG);
                c.activate();
            }
        }
        MyRigidBodyControl rbc = graspedItem.getControl(MyRigidBodyControl.class);
        bulletAppState.getPhysicsSpace().remove(rbc);

        inventory.removeItem(graspedItem);        
        graspedItem = null;
        
        sceneProcessor.setShowVisualAid(false);
        sceneProcessor.highlightSpatial(null);
        
        state = DemoState.Idle;
        for (DemonstrationListener l : demoListeners) {
            l.demoRelease();
        }
    }

    private boolean graspNodeCollidedWithTable() {
        collisionResults.clear();
        graspNode.collideWith(table.getWorldBound(), collisionResults);
        if (collisionResults.size() > 0) {
            return true;
        }
        return false;
    }
    
    private transient Transform midTransform = new Transform();
    private transient Transform minTransform = new Transform();
    private float tryTransformingGraspNode(Transform target, float deltaRangePrecision) {
        minTransform.set(graspNode.getLocalTransform());

        // if transforming to the target does not cause a collision, do it.
        graspNode.setLocalTransform(target);
        if (!graspNodeCollidedWithTable()) {
            return 1;
        }
        
        float min = 0;
        float max = 1;
        float delta;
        while (max - min > deltaRangePrecision) {
            delta = (min + max) / 2f;
            midTransform.interpolateTransforms(minTransform, target, delta);
            graspNode.setLocalTransform(midTransform);
            if (graspNodeCollidedWithTable()) {
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
    
    public void addListener(DemonstrationListener listener) {
        demoListeners.add(listener);
    }
    
    public void removeListener(DemonstrationListener listener) {
        demoListeners.remove(listener);
    }
}
