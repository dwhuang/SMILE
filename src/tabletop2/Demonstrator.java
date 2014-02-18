/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.HashMap;

/**
 *
 * @author dwhuang
 */
public class Demonstrator implements ActionListener, AnalogListener {

    protected enum DemoState {
        Idle, Anchored, Moving
    }
    protected Node rootNode;
    protected ViewPort viewPort;
    protected Camera cam;
    protected InputManager inputManager;
    protected Robot robot;
    protected DemoState state = DemoState.Idle;
    protected Node movingPlane;
    protected Node movingNode;
    protected DemonstratorSceneProcessor sceneProcessor;
    protected Spatial grabbedObject = null;
    protected final HashMap<Geometry, Spatial> grabbables;
    
    private boolean shiftKey = false;
    private float rightClickTime = 0;
    
    private Ray ray = new Ray();
    private CollisionResults collisionResults = new CollisionResults();
    private Matrix4f mat = new Matrix4f();
    private float[] angles = new float[3];
    private Quaternion quat = new Quaternion();

    public Demonstrator(String name, Node rootNode, ViewPort viewPort,
            AssetManager assetManager, InputManager inputManager, 
            Vector2f movingPlaneSize, 
            final HashMap<Geometry, Spatial> grabbables, 
            Factory factory, Robot robot) {
        this.rootNode = rootNode;
        this.viewPort = viewPort;
        this.cam = viewPort.getCamera();
        this.inputManager = inputManager;
        this.grabbables = grabbables;
        this.robot = robot;

        movingPlane = new Node(name + " movingPlane");
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

        movingNode = new Node(name + " movingNode");
        rootNode.attachChild(movingNode);

        sceneProcessor = new DemonstratorSceneProcessor(assetManager, movingPlane);
        viewPort.addProcessor(sceneProcessor);
    }

    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals("shiftKey")) {
            shiftKey = isPressed;
        } else if (name.equals("demoLeftClick")) {
            if (state == DemoState.Idle) {
                if (!isPressed) {
                    CollisionResult result = getCursorClosestCollision(rootNode);
                    if (result != null) {
                        Geometry target = result.getGeometry();
                        Spatial s = grabbables.get(target);
                        if (s != null && s.getParent() == rootNode) {
                            // translate the demoplane to the center of the picked object
                            gotoAnchoredState(s);
                        }
                    }
                }
            } else if (state == DemoState.Anchored) {
                if (isPressed) {
                    CollisionResult result 
                            = getCursorClosestCollision(grabbedObject);
                    if (result == null) {
                        gotoIdleState(false);
                    } else {
                        Vector3f pos = getCursorPosOnMovingPlane();
                        if (pos == null) {
                            gotoIdleState(false);
                        } else {
                            gotoMovingState(pos);
                        }
                    }
                } else {
                    // impossible
                    throw new IllegalStateException();
                }
            } else if (state == DemoState.Moving) {
                if (!isPressed) {
                    gotoIdleState(true);
                }
            }
        } else if (name.equals("demoRightClick")) {
            if (!isPressed) {
                if (state == DemoState.Idle) {
                    CollisionResult result = getCursorClosestCollision(rootNode);
                    if (result != null) {
                        Geometry target = result.getGeometry();
                        Spatial s = grabbables.get(target);
                        if (s != null && s.getParent() == rootNode) {
                            RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
                            Vector3f dir = getCursorRayDirection();
                            dir.y = 0.05f; // ignore vertical impulse
                            dir.multLocal(rightClickTime * 10);
                            Vector3f pt = result.getContactPoint();
                            s.getLocalTransform().transformInverseVector(pt, pt);                            
                            rbc.applyImpulse(dir, pt);
                        }
                    }
                }
                rightClickTime = 0;
            }
        }
    }

    public void onAnalog(String name, float value, float tpf) {
        if (name.equals("demoPlaneRotate")) {
            if (state != DemoState.Idle) {
                movingPlane.setLocalTranslation(movingNode.getLocalTranslation());
                if (!shiftKey) {
                    movingPlane.rotate(0, value * 0.3f, 0);
                } else {
                    movingPlane.rotate(0, -value * 0.3f, 0);
                }
            }
        } else if (name.equals("demoMouseMove")) {
            if (state == DemoState.Moving) {
                Vector3f pos = getCursorPosOnMovingPlane();
                if (pos != null) {
                    movingNode.setLocalTranslation(pos);
                } else {
                    gotoIdleState(true);
                }
            }
        } else if (name.equals("demoRightClick")) {
            rightClickTime += tpf;
        }
    }


    private void gotoIdleState(boolean release) {
        if (release) {
            releaseObject();
        }
        grabbedObject = null;
        sceneProcessor.setShowMovingPlane(false);
        sceneProcessor.highlightSpatial(null);
        state = DemoState.Idle;
        
        robot.setDemoCue(false);
    }
    
    private void gotoAnchoredState(Spatial object) {
        grabbedObject = object;
        Vector3f objCenter = object.getLocalTranslation();
        objCenter.y = 0;
        cam.getRotation().toAngles(angles);
        angles[0] = 0;
        angles[2] = 0;
        quat.fromAngles(angles);
        
        movingPlane.setLocalTranslation(objCenter);
        movingPlane.setLocalRotation(quat);
        movingNode.setLocalTranslation(objCenter);
        sceneProcessor.setShowMovingPlane(true);
        sceneProcessor.highlightSpatial(object);
        state = DemoState.Anchored;

        robot.setDemoCue(false);
    }
    
    private void gotoMovingState(Vector3f planeContact) {
        holdObject(planeContact);
        state = DemoState.Moving;
        
        robot.setDemoCue(true);
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
     
    private void holdObject(Vector3f onPlanePos) {
        movingNode.setLocalTranslation(onPlanePos);
        Vector3f relativePos = grabbedObject.getLocalTranslation();
        relativePos.subtractLocal(onPlanePos);

        grabbedObject.getParent().detachChild(grabbedObject);
        movingNode.attachChild(grabbedObject);
        grabbedObject.setLocalTranslation(relativePos);

        RigidBodyControl rbc = grabbedObject.getControl(RigidBodyControl.class);
        rbc.setKinematic(true);
        rbc.setMass(rbc.getMass() + 9999);                            
    }
    
    private void releaseObject() {
        if (grabbedObject == null) {
            return;
        }
        Vector3f pos = movingNode.getLocalTranslation();
        pos.addLocal(grabbedObject.getLocalTranslation());
        movingNode.detachChild(grabbedObject);
        rootNode.attachChild(grabbedObject);
        grabbedObject.setLocalTranslation(pos);

        // make a new control, because the old one won't go
        // to sleep when its kinematic is turned off
        RigidBodyControl oldControl = grabbedObject.getControl(RigidBodyControl.class);
        RigidBodyControl newControl = new RigidBodyControl(oldControl.getMass() - 9999);
        grabbedObject.addControl(newControl);
        oldControl.getPhysicsSpace().add(newControl);                
        grabbedObject.removeControl(oldControl);
        oldControl.getPhysicsSpace().remove(oldControl);
    }
    
}
