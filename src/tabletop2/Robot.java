/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public class Robot implements AnalogListener, ActionListener {
    private static final float SCALE = 10;
    private static final String[] FACE_PIC_NAMES = new String[] {
        null, "neutral.jpg", "bluecartooneyes.jpg", "domoface.jpg"
    };
    public static final int HEAD_CAM_RES_WIDTH = 200;
    public static final int HEAD_CAM_RES_HEIGHT = 150;
    public static final float HEAD_CAM_INTV = 0.25f; // sec
    public static final float HEAD_CAM_FOV = 60;
    
    private AssetManager assetManager;
    private RenderManager renderManager;
    private PhysicsSpace physicsSpace;
    private Factory factory;
    private Node rootNode;
    
    private Node base;
    private Geometry screen;
    private Gripper rightGripper;
    private Gripper leftGripper;
    private Node rightEndEffector;
    private Node leftEndEffector;
    private Node headCamNode;
    private Camera headCam;
    private Camera headCamcorder;
    private ImageCapturer headImageCapturer;
    private int screenPicIndex = 0;
    private Material screenDefault;    
    private MatlabAgent matlabAgent;    
    
    // joints & manual control
    private static final int S0 = 0;
    private static final int S1 = 1;
    private static final int E0 = 2;
    private static final int E1 = 3;
    private static final int W0 = 4;
    private static final int W1 = 5;
    private static final int W2 = 6;
    public static final int DOF = 7;
    private boolean shiftKey = false;
    private RobotJointState[] rightJointStates = new RobotJointState[] {
        new RobotJointState(-0.25f, -1.7f, 1.7f, -1),
        new RobotJointState(0, -2.147f, 1.047f, 1),
        new RobotJointState(1.527f, -3.054f, 3.054f, -1),
        new RobotJointState(2.618f, -0.05f, 2.618f, 1),
        new RobotJointState(-1.529f, -3.059f, 3.059f, -1),
        new RobotJointState(1.5f, -1.571f, 2.094f, 1),
        new RobotJointState(0, -3.059f, 3.059f, -1)
    };    
    private RobotJointState[] leftJointStates = new RobotJointState[] {
        new RobotJointState(0.25f, -1.7f, 1.7f, 1),
        new RobotJointState(0, -2.147f, 1.047f, 1),
        new RobotJointState(-1.527f, -3.054f, 3.054f, 1),
        new RobotJointState(2.618f, -0.05f, 2.618f, 1),
        new RobotJointState(1.529f, -3.059f, 3.059f, 1),
        new RobotJointState(1.5f, -1.571f, 2.094f, 1),
        new RobotJointState(0, -3.059f, 3.059f, 1)
    };
    private RobotJointState[] headJointStates = new RobotJointState[] {
        new RobotJointState(0, -1.571f, 1.571f, 1)
    };
    
    private boolean demoCue = false;
    private float timeSinceLastHeadVision = 0;
    
    private Vector3f vec = new Vector3f();
    private Quaternion quat = new Quaternion();
    
    
    public Robot(String name, MainApp app, Node robotLocationNode) {
        assetManager = app.getAssetManager();
        renderManager = app.getRenderManager();
        physicsSpace = app.getBulletAppState().getPhysicsSpace();
        factory = app.getFactory();
        rootNode = app.getRootNode();
        
        buildRobot(name, robotLocationNode);

        // the camera attached near the top of the head screen is used
        // to take pictures, which are sent to the control agent
        headCamcorder = new Camera(HEAD_CAM_RES_WIDTH, HEAD_CAM_RES_HEIGHT);
        headCamcorder.setFrustumPerspective(HEAD_CAM_FOV, 
                HEAD_CAM_RES_WIDTH / HEAD_CAM_RES_HEIGHT, 0.01f, 100);
        headImageCapturer = new ImageCapturer(headCamcorder, renderManager, 
                headCamNode, this.rootNode);
        headImageCapturer.syncCamera();
        
        // facial expressoin
        showNextFacialExpression();
        
        // control agent
        matlabAgent = new MatlabAgent(leftJointStates, rightJointStates, 
                leftGripper, rightGripper, rootNode, factory);
    }
    
    public void stop() {
        matlabAgent.stop();
    }
    
    public boolean matlabAgentAlive() {
        return matlabAgent.isAlive();
    }
    
    private void buildRobot(String name, Node parentNode) {
        // torso + head
        base = new Node(name);
        parentNode.attachChild(base);
        
        Node node = attachLink(name + " torso", base, null, "torso");
        
        // head pan
        vec.set(0.0947f, 0.686f, 0);
        vec.addLocal(-0.03477f, 0.00953f, 0);
        node = attachSpatialCenter(name + " H0", node, vec.x, vec.y, vec.z, 0, 0, 0);
        node = attachLink(" H0", node, headJointStates[0], "head0");

        // head camera position
        headCamNode = attachSpatialCenter(name + " head camera", node, 0.12839f, 0.06368f + 0.2f, 0, 
        		1.92f - 1.57f + FastMath.PI / 5, 1.57f, 0);
        // NOTE cheat a little here to get a better view of the table,
        // by moving the real camera position up by 0.2f and rotating downward by pi/6
        
        // head screen
        node = attachSpatialCenter(name + " H1a", node, 0.088f, 0, 0, -1.92f, -1.571f, 0);
        node = attachSpatialCenter(name + " H1b", node, 0, -0.03477f, -0.00953f, 0, 0, 1.571f);
        node = attachLink(" H1", node, null, "head1");
        screen = (Geometry) node.getChild("H12");
        screenDefault = screen.getMaterial();

        // right arm
        node = attachSpatialCenter(name + " right limb", base, 
                0.024645f, 0.118588f, 0.219645f,
                0, -0.7845f, 0);
        node = attachLimb(name + " right", node, rightJointStates);        
        rightGripper = new Gripper(name + "right-gripper", node, physicsSpace, factory);
        rightEndEffector = node;
        
        // left arm
        node = attachSpatialCenter(name + " left limb", base, 
                0.024645f, 0.118588f, -0.219645f,
                0, 0.7845f, 0);
        node = attachLimb(name + " left", node, leftJointStates);
        leftGripper = new Gripper(name + "left-gripper", node, physicsSpace, factory);
        leftEndEffector = node;

        // enhance ambient color
        base.depthFirstTraversal(new SceneGraphVisitor() {
            public void visit(Spatial spatial) {
                if (spatial instanceof Geometry) {
                    Geometry g = (Geometry) spatial;
                    Material mat = g.getMaterial();
        //            Collection<MatParam> params = mat.getParams();
        //            System.err.println(g.getName());
        //            for (MatParam p : params) {
        //                System.err.println("  " + p.getName() + " " + p.getValueAsString());
        //            }
                    MatParam p = mat.getParam("Diffuse");
                    if (p != null) {
                        ColorRGBA color = ((ColorRGBA) p.getValue()).clone();
                        color.addLocal(ColorRGBA.White.mult(0.1f));
                        color.multLocal(0.8f);
                        mat.setColor("Ambient", color);
                    }
                }
            }
        });
    }
    
    private Node attachLimb(String name, Node parentNode, RobotJointState[] jointStates) {
        Node node = attachSpatialCenter(name + " S0", parentNode, 
                0.055695f, 0, -0.011038f,
                0, 0, 0);
        node = attachLink(name + " S0", node, jointStates[S0], "shoulder0");
        addLinkPhysics(name + " S0", node, 5.7f, 0.06f, 0.2722f, 0, 0.1361f, 0);

        node = attachSpatialCenter(name + " S1", node, 
                0.069f, 0.27035f, 0,
                -1.571f, 0, 0);
        node = attachLink(name + " S1", node, jointStates[S1], "shoulder1");
        addLinkPhysics(name + " S1", node, 3.23f, 0.06f, 0.12f, 0, 0, 0);

        node = attachSpatialCenter(name + " E0", node, 
                0.102f, 0, 0,
                1.571f, 1.571f, 0);
        node = attachLink(name + " E0", node, jointStates[E0], "elbow0");
        addLinkPhysics(name + " E0", node, 4.31f, 0.06f, 0.107f + 0.3f, 
                0, -0.0535f + 0.15f, 0);

        node = attachSpatialCenter(name + " E1", node, 
                0.069f, 0.26242f, 0,
                -1.571f, 0, 1.571f);
        node = attachLink(name + " E1", node, jointStates[E1], "elbow1");
        addLinkPhysics(name + " E1", node, 2.07f, 0.06f, 0.1f, 0, 0, 0);

        node = attachSpatialCenter(name + " W0", node, 
                0.10359f, 0, 0,
                1.571f, 1.571f, 0);
        node = attachLink(name + " W0", node, jointStates[W0], "wrist0");
        addLinkPhysics(name + " W0", node, 2.25f, 0.06f, 0.088f + 0.2f, 
                0, -0.044f + 0.1f, 0);

        node = attachSpatialCenter(name + " W1", node, 
                0.01f, 0.2707f, 0,
                -1.571f, 0, 1.571f);
        node = attachLink(name + " W1", node, jointStates[W1], "wrist1");
        addLinkPhysics(name + " W1", node, 1.61f, 0.06f, 0.1f, 0, 0, 0);
        
        node = attachSpatialCenter(name + " W2", node, 
                0.115975f, 0, 0,
                1.571f, 1.571f, 0);
        node = attachLink(name + " W2", node, jointStates[W2], "wrist2");
        addLinkPhysics(name + " W2", node, 0.35f, 0.06f, 0.165f + 0.05f, 
                0, 0 + 0.025f, 0);

        vec.set(0, 0.11355f, 0);
        vec.addLocal(0, -0.0232f, 0);
        vec.addLocal(0, 0.05f, 0f);
        vec.addLocal(0, -0.02f + Gripper.FINGER_SIZE.z / 2 / SCALE, 0);
        node = attachSpatialCenter(name + " gripper", node, 
                vec.x, vec.y, vec.z,
                FastMath.HALF_PI, -FastMath.HALF_PI, 0);
        
        return node;
    }
    
    private Node attachSpatialCenter(String name, Node parentNode, 
            float xOffset, float yOffset, float zOffset,
            float xAngle, float yAngle, float zAngle) {
        Node mount = new Node(name + " center");
        mount.setLocalTranslation(xOffset * SCALE, yOffset * SCALE, zOffset * SCALE);
        quat.fromAngles(xAngle, yAngle, zAngle);
        mount.setLocalRotation(quat);
        parentNode.attachChild(mount);
        
        return mount;
    }
    
    private Node attachLink(String name, Node parentNode, RobotJointState js, 
            String spatialModelName) {
        Spatial spatialModel = assetManager.loadModel(
                "Models/baxter/" + spatialModelName + ".j3o");
        spatialModel.setLocalScale(SCALE);

        Node node;
        if (js != null) {
            node = js.getNode();
            node.setName(name + " joint");
        } else {
            node = new Node(name + " joint(fixed)");
        }
        node.attachChild(spatialModel);
        parentNode.attachChild(node);
        return node;
    }
    
    private void addLinkPhysics(String name, Node parentNode, 
            float mass, float radius, float height,
            float xOffset, float yOffset, float zOffset) {
        Node node = new Node(name + " collision center");
        node.setLocalTranslation(xOffset * SCALE, yOffset * SCALE, zOffset * SCALE);
        parentNode.attachChild(node);
        // make an upright cylinder
        vec.set(radius * SCALE, height / 2 * SCALE, radius * SCALE);
        CylinderCollisionShape cs = new CylinderCollisionShape(vec, 1);        
        RigidBodyControl rbc = new RigidBodyControl(cs, mass * SCALE);
        node.addControl(rbc);
        physicsSpace.add(rbc);
        rbc.setKinematic(true);        
    }
    
    public void onAnalog(String name, float value, float tpf) {
        RobotJointState[] joints = null;
        String lowerCaseName = name.toLowerCase();
        if (lowerCaseName.matches(".*rightarm.*")) {
            joints = rightJointStates;
        } else if (lowerCaseName.matches(".*leftarm.*")) {
            joints = leftJointStates;
        } else if (lowerCaseName.matches(".*head.*")) {
            joints = headJointStates;
        } else if (lowerCaseName.matches(".*rightgripper.*")) {
            rightGripper.onAnalog(name, value, tpf);
        } else if (lowerCaseName.matches(".*leftgripper.*")) {
            leftGripper.onAnalog(name, value, tpf);
        }
        
        if (joints != null) {
            String jName = lowerCaseName.substring(lowerCaseName.length() - 2);
            int jointIndex = -1;
            if (jName.equals("s0")) {
                jointIndex = S0;
            } else if (jName.equals("s1")) {
                jointIndex = S1;
            } else if (jName.equals("e0")) {
                jointIndex = E0;
            } else if (jName.equals("e1")) {
                jointIndex = E1;
            } else if (jName.equals("w0")) {
                jointIndex = W0;
            } else if (jName.equals("w1")) {
                jointIndex = W1;
            } else if (jName.equals("w2")) {
                jointIndex = W2;
            } else if (jName.equals("h0")) {
                jointIndex = 0;
            }
            if (shiftKey) {
                joints[jointIndex].setVelocity(-1, true);
            } else {
                joints[jointIndex].setVelocity(1, true);
            }
        }
    }

    public void onAction(String name, boolean pressed, float tpf) {
        String lowerCaseName = name.toLowerCase();
        if (lowerCaseName.equals("shiftkey")) {
            shiftKey = pressed;
        } else if (lowerCaseName.matches(".*screen.*")) {
            if (!pressed) {
                showNextFacialExpression();
            }
        } else if (lowerCaseName.matches(".*matlab.*")) {
            if (!pressed) {
                if (matlabAgent.isAlive()) {
                    matlabAgent.stop();
                } else {
                    matlabAgent.start();
                }
            }
        } else if (lowerCaseName.matches(".*takepic")) {            
            BufferedImage img = headImageCapturer.takePicture();            
            try {
                String fname = "headcam" + (new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())) + ".png";
                ImageIO.write(img, "png", new File(fname));
            } catch (IOException ex) {
                Logger.getLogger(Robot.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void update(float tpf) {
        if (matlabAgent.isAlive()) {
            timeSinceLastHeadVision += tpf;
            BufferedImage img = null;
            if (timeSinceLastHeadVision > HEAD_CAM_INTV) {
                img = headImageCapturer.takePicture();
                timeSinceLastHeadVision = 0;
            }
            matlabAgent.poll(tpf,
                    leftEndEffector.getWorldTranslation(),
                    rightEndEffector.getWorldTranslation(), img, demoCue);
        }
        
        for (RobotJointState js : rightJointStates) {
            js.update(tpf);
        }
        for (RobotJointState js : leftJointStates) {
            js.update(tpf);
        }
        
        boolean headMoved = false;
        for (RobotJointState js : headJointStates) {
            headMoved |= js.update(tpf);
        }
        if (headMoved) {
            headImageCapturer.syncCamera();
            if (headCam != null) {
                updateHeadCam();
            }
        }
        
//        rightEndEffector.localToWorld(Vector3f.ZERO, vec);
//        System.err.println(vec);
    }
    
    public void updateHeadCam() {
        headCam.setLocation(headCamNode.getWorldTranslation());
        headCam.setRotation(headCamNode.getWorldRotation());
    }
    
    public void toggleHeadCameraView(Node rootNode) {
        if (headCam != null) {
            headCam = null;
            renderManager.removeMainView("robot head camera");
        } else {
            headCam = renderManager.getMainView("Default").getCamera().clone();
            float vpXMin = 0.6f;
            float vpXMax = 1;
            float vpYMin = 0;
            float vpYMax = vpYMin + (vpXMax - vpXMin) 
                    * ((float)HEAD_CAM_RES_HEIGHT / (float)HEAD_CAM_RES_WIDTH)
                    / ((float)headCam.getHeight() / (float)headCam.getWidth());
            
            headCam.setViewPort(vpXMin, vpXMax, vpYMin, vpYMax);
            headCam.setFrustumPerspective(HEAD_CAM_FOV, 
                HEAD_CAM_RES_WIDTH / HEAD_CAM_RES_HEIGHT, 0.01f, 100);
            updateHeadCam();
            ViewPort vp = renderManager.createMainView("robot head camera", headCam);
            vp.setClearFlags(true, true, true);
            vp.attachScene(rootNode);
        }
    }

    private void showNextFacialExpression() {
        ++screenPicIndex;
        screenPicIndex %= FACE_PIC_NAMES.length;
        String picName = FACE_PIC_NAMES[screenPicIndex];
        if (picName == null) {
            screen.setMaterial(screenDefault);
        } else {
            Material screenPicMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            screenPicMat.setTexture("ColorMap", assetManager.loadTexture("Textures/" + picName));
            screen.setMaterial(screenPicMat);
        }
    }
    
    public void setDemoCue(boolean cue) {
        demoCue = cue;
    }
}
