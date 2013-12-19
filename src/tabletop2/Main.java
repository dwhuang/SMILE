package tabletop2;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.VideoRecorderAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Logger;

/**
 * test
 *
 * @author normenhansen
 */
public class Main extends SimpleApplication implements ActionListener, SceneGraphVisitor {
    protected static final Logger logger = Logger.getLogger(Main.class.getName());
    protected static final Vector3f TABLE_SIZE = new Vector3f(
            20f, 4f, 12f);
    protected static final ColorRGBA TABLE_COLOR = new ColorRGBA(
            1.0f, 1.0f, 1.0f, 1.0f);
    protected static final Vector3f CAMERA_INIT_LOCATION = new Vector3f(
            0, 15, -TABLE_SIZE.z / 2 - 10);
    protected static final Vector3f CAMERA_INIT_LOOKAT = new Vector3f(
            0, 0, TABLE_SIZE.z / 2);
    
    protected BulletAppState bulletAppState;
    protected RigidBodyControl camPhysics;
    protected Factory factory;
    
    protected ArrayList<Spatial> grabbables = new ArrayList<Spatial>();
    private static Random random = new Random(5566);
        
    protected Robot robot;
    
    private final boolean flyGripper = false;
    Node gripperNode;
    Gripper gripper;
    
    private boolean hasDeletedMouseTrigger = false;
    
    public static void main(String[] args) {
        Main app = new Main();
        app.setPauseOnLostFocus(false);
        
        AppSettings settings = new AppSettings(true);
        settings.setTitle("tabletop2");
        settings.setSettingsDialogImage("Interface/baxter.png");
        settings.setFrameRate(60);
//        settings.setVSync(true);
        settings.setResolution(800, 600);
//        settings.setSamples(4);
//        settings.setFullscreen(true);
//        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
//        DisplayMode[] modes = device.getDisplayModes();
//        for (int i = 0; i < modes.length; ++i) {
//            System.err.println(modes[i].getWidth() + "x" + modes[i].getHeight() + " " + modes[i].getRefreshRate()
//                    + " " + modes[i].getBitDepth());
//        }
//        
//        int i = 10; // note: there are usually several, let's pick the first
//        settings.setResolution(modes[i].getWidth(), modes[i].getHeight());
//        settings.setFrequency(modes[i].getRefreshRate());
//        settings.setBitsPerPixel(modes[i].getBitDepth());
////        settings.setFullscreen(device.isFullScreenSupported());
        settings.setSamples(2);
////        settings.setVSync(true);
//        settings.setFrameRate(60);
        
        app.setSettings(settings);
//        app.setShowSettings(true);
        app.start(); // restart the context to apply changes
        
        
//        app.setSettings(settings);
//        app.setShowSettings(true);
//        app.start();
    }
    
    @Override
    public void simpleInitApp() {
        factory = new Factory(assetManager);
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
//        bulletAppState.getPhysicsSpace().setAccuracy(1f/120f);
//        bulletAppState.getPhysicsSpace().setMaxSubSteps(10);
//        bulletAppState.getPhysicsSpace().setGravity(new Vector3f(0f, -9.81f, 0f));
//        bulletAppState.getPhysicsSpace().getDynamicsWorld().getSolverInfo().splitImpulsePenetrationThreshold = -0.04f;
        bulletAppState.setSpeed(10f);
//        bulletAppState.setDebugEnabled(true);
        
//        DynamicsWorld dw = bulletAppState.getPhysicsSpace().getDynamicsWorld();
//        System.err.println(dw.getSolverInfo().splitImpulse);
//        dw.getSolverInfo().splitImpulse = true;

        flyCam.setMoveSpeed(10f);
        
        Node node = new Node("robot location");
        node.setLocalTranslation(0, 0, TABLE_SIZE.z / 2 + 3);
        Quaternion q = new Quaternion(new float[] {0, FastMath.HALF_PI, 0});
        node.setLocalRotation(q);
        rootNode.attachChild(node);        
        robot = new Robot("baxter", node, assetManager, renderManager, 
                bulletAppState.getPhysicsSpace(), factory);
        
        initStage();
        initKeys();        
        
//        stateManager.attach(new VideoRecorderAppState()); //start recording
    }

    
    protected void initStage() {
        // table
        Spatial table = factory.makeBigBlock("table", 
                TABLE_SIZE.x, TABLE_SIZE.y, TABLE_SIZE.z, ColorRGBA.White, 4);
        table.setLocalTranslation(0.0f, -TABLE_SIZE.y / 2, 0.0f);
        rootNode.attachChild(table);
        RigidBodyControl tablePhysics = new RigidBodyControl(0.0f);
        table.addControl(tablePhysics);
        bulletAppState.getPhysicsSpace().add(tablePhysics);
        
        // lights
        AmbientLight ambientLight = new AmbientLight();
        //ambientLight.setColor(ColorRGBA.White.mult(1f));
        rootNode.addLight(ambientLight);

        PointLight light = new PointLight();
        light.setColor(ColorRGBA.White);
        light.setPosition(new Vector3f(0f, 10f, 0f));
        light.setRadius(50f);
        rootNode.addLight(light);
        
        // set up camera and background
        //viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 0.5f, 1.0f));
        float aspect = (float)cam.getWidth() / (float)cam.getHeight();
        cam.setFrustumPerspective(45f, aspect, 0.01f, 100f);
        cam.setLocation(CAMERA_INIT_LOCATION);
        cam.lookAt(CAMERA_INIT_LOOKAT, new Vector3f(0.0f, 1.0f, 0.0f));

        
        // make a box        
        Spatial boxContainer = factory.makeBoxContainer("container", 5, 3, 5, 
                0.5f, ColorRGBA.Gray);
        rootNode.attachChild(boxContainer);
        boxContainer.setLocalTranslation(0f, 5f, 0f);
        RigidBodyControl boxContainerControl = new RigidBodyControl(1f);
        boxContainer.addControl(boxContainerControl);
        bulletAppState.getPhysicsSpace().add(boxContainerControl);
        grabbables.add(boxContainer);
        

        // have a gripper attached to fly cam
        if (flyGripper) {
            gripperNode = new Node();
            rootNode.attachChild(gripperNode);
            Node node = new Node();
            gripperNode.attachChild(node);
            node.setLocalTranslation(0, -1, -3);
            gripper = new Gripper("baxter right-gripper", node, 
                    bulletAppState.getPhysicsSpace(), factory);
        }               
    }
    
    protected void initKeys() {
        inputManager.addMapping("shiftKey", new KeyTrigger(KeyInput.KEY_LSHIFT));
        inputManager.addMapping("shiftKey", new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("makeBlock", new KeyTrigger(KeyInput.KEY_B));
        inputManager.addMapping("makeStack", new KeyTrigger(KeyInput.KEY_N));
        inputManager.addMapping("clearTable", new KeyTrigger(KeyInput.KEY_C));        
        inputManager.addMapping("cameraRobotHead", new KeyTrigger(KeyInput.KEY_1));
        inputManager.addMapping("robotRightArmS0", new KeyTrigger(KeyInput.KEY_LBRACKET));
        inputManager.addMapping("robotRightArmS1", new KeyTrigger(KeyInput.KEY_P));
        inputManager.addMapping("robotRightArmE0", new KeyTrigger(KeyInput.KEY_O));
        inputManager.addMapping("robotRightArmE1", new KeyTrigger(KeyInput.KEY_I));
        inputManager.addMapping("robotRightArmW0", new KeyTrigger(KeyInput.KEY_U));
        inputManager.addMapping("robotRightArmW1", new KeyTrigger(KeyInput.KEY_Y));
        inputManager.addMapping("robotRightArmW2", new KeyTrigger(KeyInput.KEY_T));
        inputManager.addMapping("robotRightGripperOpen", new KeyTrigger(KeyInput.KEY_EQUALS));
        inputManager.addMapping("robotRightGripperClose", new KeyTrigger(KeyInput.KEY_MINUS));
        inputManager.addMapping("robotLeftArmS0", new KeyTrigger(KeyInput.KEY_APOSTROPHE));
        inputManager.addMapping("robotLeftArmS1", new KeyTrigger(KeyInput.KEY_SEMICOLON));
        inputManager.addMapping("robotLeftArmE0", new KeyTrigger(KeyInput.KEY_L));
        inputManager.addMapping("robotLeftArmE1", new KeyTrigger(KeyInput.KEY_K));
        inputManager.addMapping("robotLeftArmW0", new KeyTrigger(KeyInput.KEY_J));
        inputManager.addMapping("robotLeftArmW1", new KeyTrigger(KeyInput.KEY_H));
        inputManager.addMapping("robotLeftArmW2", new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping("robotLeftGripperOpen", new KeyTrigger(KeyInput.KEY_0));
        inputManager.addMapping("robotLeftGripperClose", new KeyTrigger(KeyInput.KEY_9));
        inputManager.addMapping("robotHeadH0", new KeyTrigger(KeyInput.KEY_RBRACKET));
        inputManager.addMapping("robotScreen", new KeyTrigger(KeyInput.KEY_BACKSLASH));
        inputManager.addMapping("robotMatlabToggle", new KeyTrigger(KeyInput.KEY_M));
        inputManager.addMapping("robotTakePic", new KeyTrigger(KeyInput.KEY_2));

        inputManager.addListener(this, "shiftKey");
        inputManager.addListener(this, "makeBlock");
        inputManager.addListener(this, "makeStack");
        inputManager.addListener(this, "clearTable");
        inputManager.addListener(this, "cameraRobotHead");
        inputManager.addListener(robot, "shiftKey");
        inputManager.addListener(robot, "robotRightArmS0");
        inputManager.addListener(robot, "robotRightArmS1");
        inputManager.addListener(robot, "robotRightArmE0");
        inputManager.addListener(robot, "robotRightArmE1");
        inputManager.addListener(robot, "robotRightArmW0");
        inputManager.addListener(robot, "robotRightArmW1");
        inputManager.addListener(robot, "robotRightArmW2");
        inputManager.addListener(robot, "robotRightGripperOpen");
        inputManager.addListener(robot, "robotRightGripperClose");
        inputManager.addListener(robot, "robotLeftArmS0");
        inputManager.addListener(robot, "robotLeftArmS1");
        inputManager.addListener(robot, "robotLeftArmE0");
        inputManager.addListener(robot, "robotLeftArmE1");
        inputManager.addListener(robot, "robotLeftArmW0");
        inputManager.addListener(robot, "robotLeftArmW1");
        inputManager.addListener(robot, "robotLeftArmW2");
        inputManager.addListener(robot, "robotLeftGripperOpen");
        inputManager.addListener(robot, "robotLeftGripperClose");
        inputManager.addListener(robot, "robotHeadH0");
        inputManager.addListener(robot, "robotScreen");
        inputManager.addListener(robot, "robotMatlabToggle");
        inputManager.addListener(robot, "robotTakePic");

        if (flyGripper) {
            inputManager.addListener(gripper, "gripperOpen");
            inputManager.addListener(gripper, "gripperClose");
        }        

    }

    @Override
    public void simpleUpdate(float tpf) {
        if (!hasDeletedMouseTrigger) {
            // TODO this is a quick fix to disable mouse-controlled camera 
            // rotation;
            inputManager.deleteTrigger("FLYCAM_Left", new MouseAxisTrigger(0, true));
            inputManager.deleteTrigger("FLYCAM_Right", new MouseAxisTrigger(0, false));
            inputManager.deleteTrigger("FLYCAM_Up", new MouseAxisTrigger(1, false));
            inputManager.deleteTrigger("FLYCAM_Down", new MouseAxisTrigger(1, true));
            hasDeletedMouseTrigger = true;
            inputManager.setCursorVisible(true);
        }
        
        robot.update(tpf);

        if (flyGripper) {
            Matrix4f projMat = cam.getViewMatrix().invert();
            gripperNode.setLocalTranslation(projMat.toTranslationVector());
            gripperNode.setLocalRotation(projMat.toRotationMatrix());
        }        

        rootNode.depthFirstTraversal(this); // cleanup unused objects        
    }
    
    public void visit(Spatial spatial) {
        if (spatial.getControl(RigidBodyControl.class) != null) {
            if (spatial.getLocalTranslation().y < -1000) {
                RigidBodyControl rbc = spatial.getControl(RigidBodyControl.class);
                if (rbc == null) {
                    return;
                }
                bulletAppState.getPhysicsSpace().remove(rbc);
                spatial.removeControl(rbc);
                if (spatial.getParent() != null) {
                    spatial.getParent().detachChild(spatial);
                }
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        robot.stop();
    }

    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals("makeBlock")) {
            if (isPressed) {
                final ColorRGBA[] colors = new ColorRGBA[] {
                    ColorRGBA.Red, ColorRGBA.Blue, ColorRGBA.Yellow,
                    ColorRGBA.Green, ColorRGBA.Brown, ColorRGBA.Cyan,
                    ColorRGBA.Magenta, ColorRGBA.Orange, ColorRGBA.Pink};
                Spatial g = factory.makeBlock("big brick", 1.5f, 1.5f, 1.5f, 
                        colors[random.nextInt(colors.length)]);
                rootNode.attachChild(g);
                RigidBodyControl c = new RigidBodyControl(1f);
                g.addControl(c);
                bulletAppState.getPhysicsSpace().add(c);
                c.setUserObject(g);
                Vector3f v = new Vector3f();
                v.x = (random.nextFloat() * 2 - 1) * (TABLE_SIZE.x / 2);
                v.y = 10;
                v.z = (random.nextFloat() * 2 - 1) * (TABLE_SIZE.z / 2);
                c.setPhysicsLocation(v);
                Quaternion rot = new Quaternion();
                rot.fromAngleAxis(FastMath.HALF_PI * random.nextFloat(), Vector3f.UNIT_XYZ);
                c.setPhysicsRotation(rot);
                grabbables.add(g);
            }
        } else if (name.equals("makeStack")) {
            if (isPressed) {
                final Vector3f BOX_SIZE = new Vector3f(1, 1, 1);
                final ColorRGBA[] colors = new ColorRGBA[] {
                    ColorRGBA.Red, ColorRGBA.Blue, ColorRGBA.Yellow,
                    ColorRGBA.Green, ColorRGBA.Brown, ColorRGBA.Cyan,
                    ColorRGBA.Magenta, ColorRGBA.Orange, ColorRGBA.Pink};
                Vector3f v = new Vector3f();
                v.x = (random.nextFloat() * 2 - 1) * (TABLE_SIZE.x / 2);
                v.y = BOX_SIZE.y / 2;
                v.z = (random.nextFloat() * 2 - 1) * (TABLE_SIZE.z / 2);
                for (int i = 0; i < 5; ++i) {
                    Spatial g = factory.makeBlock("small brick", 
                            BOX_SIZE.x, BOX_SIZE.y, BOX_SIZE.z,
                            colors[random.nextInt(colors.length)]);
                    rootNode.attachChild(g);
                    RigidBodyControl c = new RigidBodyControl(1f);
                    g.addControl(c);
                    bulletAppState.getPhysicsSpace().add(c);
                    c.setUserObject(g);
                    c.setPhysicsLocation(v);

                    v.y += BOX_SIZE.y;
                    grabbables.add(g);
                }
            }
        } else if (name.equals("clearTable")) {
            Iterator<Spatial> itr = grabbables.iterator();
            while (itr.hasNext()) {
                Spatial s = itr.next();
                if (s.getParent() != rootNode) {
                    // this item may be held by a gripper
                    continue;
                }
                RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
                if (rbc == null) {
                    continue;
                }
                bulletAppState.getPhysicsSpace().remove(rbc);
                s.removeControl(rbc);
                if (s.getParent() != null) {
                    s.getParent().detachChild(s);
                }
                itr.remove();
            }
        } else if (name.equals("cameraRobotHead")) {
            if (isPressed) {
                robot.toggleHeadCameraView(rootNode);
            }
        }
    }


}
