package tabletop2;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import de.lessvoid.nifty.Nifty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import tabletop2.gui.GuiController;

/**
 * test
 *
 * @author normenhansen
 */
public class Main extends SimpleApplication implements ActionListener {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final Vector3f TABLE_SIZE = new Vector3f(
            20f, 4f, 12f);
    private static final ColorRGBA TABLE_COLOR = new ColorRGBA(
            1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector3f CAMERA_INIT_LOCATION = new Vector3f(
            0, 15, -TABLE_SIZE.z / 2 - 10);
    private static final Vector3f CAMERA_INIT_LOOKAT = new Vector3f(
            0, 0, TABLE_SIZE.z / 2);

    private BulletAppState bulletAppState;
    private Factory factory;
    
    private HashMap<Geometry, Spatial> grabbables = new HashMap<Geometry, Spatial>();
    private static Random random = new Random(5566);
        
    private Robot robot;
    
    private final boolean flyGripper = false;
    private Node gripperNode;
    private Gripper gripper;
    
    private Demonstrator demonstrator;
    
    private boolean hasDeletedMouseTrigger = false;
    private boolean shiftKey = false;
    
    private ArrayList<String> hudTextBuffer = new ArrayList<String>();
    private ArrayList<BitmapText> hudText = new ArrayList<BitmapText>();
    private Node hudNode;
    private Geometry hudBackground;

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
        settings.putBoolean("DisableJoysticks", false);
        
        app.setSettings(settings);
//        app.setShowSettings(true);
        
        app.setDisplayStatView(false);
        app.setDisplayFps(false);
        
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
        node.setLocalTranslation(0, 2, TABLE_SIZE.z / 2 + 3);
        Quaternion q = new Quaternion(new float[] {0, FastMath.HALF_PI, 0});
        node.setLocalRotation(q);
        rootNode.attachChild(node);        
        robot = new Robot("baxter", node, assetManager, renderManager, 
                bulletAppState.getPhysicsSpace(), factory);
        
        demonstrator = new Demonstrator("demonstrator", rootNode, viewPort, 
                assetManager, inputManager, 
                new Vector2f(TABLE_SIZE.x * 3, TABLE_SIZE.z * 4), 
                grabbables, factory, robot);
        initStage();
        initKeys();
        initHUD();
        
        
//        stateManager.attach(new VideoRecorderAppState()); //start recording
        
//        Joystick[] joysticks = inputManager.getJoysticks();
//        for( Joystick j : joysticks ) {
//            System.out.println( "Joystick[" + j.getJoyId() + "]:" + j.getName() );
//            System.out.println( "  buttons:" + j.getButtonCount() );
//            for( JoystickButton b : j.getButtons() ) {
//                System.out.println( "   " + b );
//            }
//            
//            System.out.println( "  axes:" + j.getAxisCount() );
//            for( JoystickAxis axis : j.getAxes() ) {
//                System.out.println( "   " + axis );
//            }
//        }
        
        GuiController guiController = new GuiController(demonstrator);
        NiftyJmeDisplay niftyDisplay= new NiftyJmeDisplay(assetManager, inputManager, audioRenderer, guiViewPort);
        Nifty nifty = niftyDisplay.getNifty();
        String viewFname = "Interface/guiview.xml";
        try {
            nifty.validateXml(viewFname);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, viewFname + " not valid", ex);
        }
        nifty.fromXml(viewFname, "guiView", guiController);
        stateManager.attach(guiController);
//        nifty.setDebugOptionPanelColors(true);
        guiViewPort.addProcessor(niftyDisplay);
    }

    
    private void initStage() {
        // table
        Spatial table = factory.makeBigBlock("table", 
                TABLE_SIZE.x, TABLE_SIZE.y, TABLE_SIZE.z, TABLE_COLOR, 4);
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
        boxContainer.setLocalTranslation(1f, 2f, -2f);
        RigidBodyControl boxContainerControl = new RigidBodyControl(1f);
        boxContainer.addControl(boxContainerControl);
        bulletAppState.getPhysicsSpace().add(boxContainerControl);
        addGrabbable(boxContainer);
        
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
    
    private void initKeys() {
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
        inputManager.addMapping("demoLeftClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("demoRightClick", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping("demoMouseMove", 
                new MouseAxisTrigger(MouseInput.AXIS_X, true), 
                new MouseAxisTrigger(MouseInput.AXIS_X, false),
                new MouseAxisTrigger(MouseInput.AXIS_Y, true),
                new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("demoPlaneRotate", new KeyTrigger(KeyInput.KEY_SLASH));

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
        inputManager.addListener(demonstrator, "shiftKey");
        inputManager.addListener(demonstrator, "demoLeftClick");
        inputManager.addListener(demonstrator, "demoRightClick");
        inputManager.addListener(demonstrator, "demoMouseMove");
        inputManager.addListener(demonstrator, "demoPlaneRotate");

        if (flyGripper) {
            inputManager.addListener(gripper, "gripperOpen");
            inputManager.addListener(gripper, "gripperClose");
        }        
    }
    
    private void initHUD() {
        hudNode = new Node();
        guiNode.attachChild(hudNode);
        
        hudTextBuffer.add("matlab");
        
        hudBackground = factory.makeUnshadedPlane("", 1, 1, ColorRGBA.Black.mult(0.5f));
        hudBackground.setLocalTranslation(0, 0, -0.01f);
        hudNode.attachChild(hudBackground);
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
        
        // cleanup unused objects
        rootNode.depthFirstTraversal(new SceneGraphVisitor() {
            public void visit(Spatial spatial) {
                if (spatial.getControl(RigidBodyControl.class) != null) {
                    if (spatial.getLocalTranslation().y < -1000) {
                        RigidBodyControl rbc = spatial.getControl(RigidBodyControl.class);
                        if (rbc == null) {
                            return;
                        }
                        bulletAppState.getPhysicsSpace().remove(rbc);
                        spatial.removeControl(rbc);
                        // remove from grabbables
                        spatial.depthFirstTraversal(new SceneGraphVisitor() {
                            public void visit(Spatial spatial) {
                                if (spatial instanceof Geometry) {
                                    grabbables.remove((Geometry)spatial);
                                }
                            }
                        });
                        // remove from the scene graph
                        if (spatial.getParent() != null) {
                            spatial.getParent().detachChild(spatial);
                        }
                    }
                }
            }
        });
        
        
        if (robot.matlabAgentAlive()) {
            hudTextBuffer.set(0, "Matlab agent: ON");
        } else {
            hudTextBuffer.set(0, "Matlab agent: OFF");
        }
        updateHUD();
    }
    
    // if hudTextBuffer differs from hudText, update hudText
    private void updateHUD() {
        boolean hasChanged = false;
        if (hudText.size() != hudTextBuffer.size()) {
            hasChanged = true;
            int diff = hudTextBuffer.size() - hudText.size();
            if (diff > 0) {
                for (int i = 0; i < diff; ++i) {
                    BitmapText bt = new BitmapText(guiFont);
                    hudText.add(bt);
                    hudNode.attachChild(bt);
                }
            } else {
                for (int i = 0; i < -diff; ++i) {
                    BitmapText bt = hudText.get(hudText.size() - 1);
                    hudText.remove(bt);
                    hudNode.detachChild(bt);
                }
            }
        } else {
            for (int i = 0; i < hudText.size(); ++i) {
                if (!hudTextBuffer.get(i).equals(hudText.get(i).getText())) {
                    hasChanged = true;
                    break;
                }
            }
        }
        
        if (hasChanged) {
            float width = 0;
            float height = 0;
            for (int i = hudText.size() - 1; i >= 0; --i) {
                BitmapText bt = hudText.get(i);
                String newStr = hudTextBuffer.get(i);
                if (!bt.getText().equals(newStr)) {
                    bt.setText(newStr);
                }
                if (width < bt.getLineWidth()) {
                    width = bt.getLineWidth();
                }
                height += bt.getLineCount() * bt.getLineHeight();
                bt.setLocalTranslation(0, height, 0);
            }
            
            hudBackground.setLocalScale(width + 10, height, 0);
            hudNode.setLocalTranslation(0, cam.getHeight() - height, 0);
        }
    }

    @Override
    public void stop() {
        super.stop();
        robot.stop();
    }

    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.equals("shiftKey")) {
            shiftKey = isPressed;
        } else if (name.equals("makeBlock")) {
            if (isPressed) {
                final ColorRGBA[] colors = new ColorRGBA[] {
                    ColorRGBA.Red, ColorRGBA.Blue, ColorRGBA.Yellow,
                    ColorRGBA.Green, ColorRGBA.Brown, ColorRGBA.Cyan,
                    ColorRGBA.Magenta, ColorRGBA.Orange};
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
                addGrabbable(g);
            }
        } else if (name.equals("makeStack")) {
            if (isPressed) {
                final Vector3f BOX_SIZE = new Vector3f(1, 1, 1);
                final ColorRGBA[] colors = new ColorRGBA[] {
                    ColorRGBA.Red, ColorRGBA.Blue, ColorRGBA.Yellow,
                    ColorRGBA.Green, ColorRGBA.Brown, ColorRGBA.Cyan,
                    ColorRGBA.Magenta, ColorRGBA.Orange};
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
                    addGrabbable(g);
                }
            }
        } else if (name.equals("clearTable")) {
            Iterator<Entry<Geometry, Spatial>> itr = grabbables.entrySet().iterator();
            ArrayList<Spatial> objToBeRemoved = new ArrayList<Spatial>();
            while (itr.hasNext()) {
                Entry<Geometry, Spatial> entry = itr.next();
                Spatial s = entry.getValue();
                if (s.getParent() != rootNode) {
                    // this item may be held by a gripper
                    continue;
                }
                objToBeRemoved.add(s);
                itr.remove();
            }
            // objToBeRemoved may contain duplicates
            for (Spatial s : objToBeRemoved) {           
                RigidBodyControl rbc = s.getControl(RigidBodyControl.class);
                if (rbc != null) {
                    bulletAppState.getPhysicsSpace().remove(rbc);
                    s.removeControl(rbc);
                }
                if (s.getParent() != null) {
                    s.getParent().detachChild(s);
                }
            }            
        } else if (name.equals("cameraRobotHead")) {
            if (isPressed) {
                robot.toggleHeadCameraView(rootNode);
            }
        }
    }
    
   private void addGrabbable(Spatial gb) {
        if (gb instanceof Geometry) {
            grabbables.put((Geometry) gb, gb);
        } else if (gb instanceof Node) {
            final Node gbNode = (Node)gb;
            gbNode.depthFirstTraversal(new SceneGraphVisitor() {
                public void visit(Spatial s) {
                    if (s instanceof Geometry) {
                        grabbables.put((Geometry)s, gbNode);
                    }
                }
            });
        } else {
            logger.log(Level.WARNING, "unknown spatial type for {0}", gb.getName());
        }
   }
}
