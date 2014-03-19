package tabletop2;

import java.util.HashSet;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

import tabletop2.gui.GuiController;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;

import de.lessvoid.nifty.Nifty;

/**
 * test
 *
 * @author dwhuang
 */
public class MainApp extends SimpleApplication implements ActionListener {
    private static final Logger logger = Logger.getLogger(MainApp.class.getName());
	
    public static final String DEFAULT_TABLE_XML_FNAME = "xml/default.xml";
    
    private BulletAppState bulletAppState = new BulletAppState();    
    private Factory factory;
    private Inventory inventory;
    private Table table;
    private Node robotLocationNode = new Node("robotLocationNode");
    private Robot robot;
    private Demonstrator demonstrator;
//    private Spatial tableSpatial;
    
    private final boolean flyGripper = false;
    private Node gripperNode;
    private Gripper gripper;
        
    private boolean hasDeletedMouseTrigger = false;
    
    private float timeAccumulator = 0;
    
    private transient HashSet<Spatial> itemsToRemove = new HashSet<Spatial>();
    
    public static void main(String[] args) throws BackingStoreException {
    	Locale.setDefault(Locale.ENGLISH);
        MainApp app = new MainApp();
        app.setPauseOnLostFocus(false);
        app.setDisplayStatView(false);
        app.setDisplayFps(false);

                
        AppSettings settings = new AppSettings(false);
        settings.load("tabletop2");
        settings.setTitle("tabletop2");
        settings.setSettingsDialogImage("Interface/baxter.png");
        settings.setFrameRate(60);
        settings.setSamples(2);
        settings.putBoolean("DisableJoysticks", false);
        settings.save("tabletop2");

        //        settings.setVSync(true);
//        settings.setResolution(800, 600);
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
        
        app.setSettings(settings);
        app.setShowSettings(true);        
        
        app.start(); // restart the context to apply changes
    }
    
    public BulletAppState getBulletAppState() {
        return bulletAppState;
    }
    
    public final Factory getFactory() {
        return factory;
    }
    
    public final Inventory getInventory() {
        return inventory;
    }
    
    public Robot getRobot() {
        return robot;
    }
    
    public Table getTable() {
        return table;
    }
    
    @Override
    public void simpleInitApp() {
    	initBulletAppState();
        flyCam.setMoveSpeed(10f);
        
        factory = new Factory(assetManager);
        inventory = new Inventory(this);        
        table = new Table("table", this, robotLocationNode);
        table.reloadXml(DEFAULT_TABLE_XML_FNAME);
        
        rootNode.attachChild(robotLocationNode);
        robot = new Robot("baxter", this, robotLocationNode);        
        
        demonstrator = new Demonstrator("demonstrator", this);
        
        initLighting();
        initCamera();
        initKeys();
        initGui();
        if (flyGripper) {
            // have a gripper attached to the fly cam
        	initFlyGripper();
        }
        
        // stateManager.attach(new VideoRecorderAppState()); //start recording
    }

    private void initBulletAppState() {
        stateManager.attach(bulletAppState);
        bulletAppState.getPhysicsSpace().setAccuracy(1f/240f);
//        bulletAppState.setThreadingType(BulletAppState.ThreadingType.PARALLEL);
        bulletAppState.getPhysicsSpace().setMaxSubSteps(10);
//        bulletAppState.getPhysicsSpace().setGravity(new Vector3f(0f, -9.81f, 0f));
        bulletAppState.getPhysicsSpace().getDynamicsWorld().getSolverInfo().splitImpulsePenetrationThreshold = -0.04f;
        bulletAppState.setSpeed(10f);
//        bulletAppState.setDebugEnabled(true);

//        DynamicsWorld dw = bulletAppState.getPhysicsSpace().getDynamicsWorld();
//        System.err.println(dw.getSolverInfo().splitImpulse);
//        dw.getSolverInfo().splitImpulse = true;
    }
    
    private void initLighting() {        
        // lights
        AmbientLight ambientLight = new AmbientLight();
        //ambientLight.setColor(ColorRGBA.White.mult(1f));
        rootNode.addLight(ambientLight);

        PointLight light = new PointLight();
        light.setColor(ColorRGBA.White);
        light.setPosition(new Vector3f(0f, 10f, 0f));
        light.setRadius(50f);
        rootNode.addLight(light);        
    }
    
    private void initCamera() {
        // set up camera and background
        //viewPort.setBackgroundColor(new ColorRGBA(0.5f, 0.7f, 0.5f, 1.0f));
        float aspect = (float)cam.getWidth() / (float)cam.getHeight();
        cam.setFrustumPerspective(45f, aspect, 0.01f, 100f);
        cam.setLocation(new Vector3f(0, 15, -table.getDepth() / 2 - 10));
        cam.lookAt(new Vector3f(0, 0, table.getDepth() / 2), Vector3f.UNIT_Y);
    }
    
    private void initFlyGripper() {
        gripperNode = new Node();
        rootNode.attachChild(gripperNode);
        Node node = new Node();
        gripperNode.attachChild(node);
        node.setLocalTranslation(0, -1, -3);
        gripper = new Gripper("baxter right-gripper", node, 
                bulletAppState.getPhysicsSpace(), factory);
    }
    
    private void initGui() {
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
    
    @Override
    public void simpleUpdate(float tpf) {
        timeAccumulator += tpf;
        
        if (!hasDeletedMouseTrigger) {
            // TODO this is a quick fix to disable mouse-controlled camera 
            // rotation;
            inputManager.deleteTrigger("FLYCAM_Left", new MouseAxisTrigger(MouseInput.AXIS_X, true));
            inputManager.deleteTrigger("FLYCAM_Right", new MouseAxisTrigger(MouseInput.AXIS_X, false));
            inputManager.deleteTrigger("FLYCAM_Up", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
            inputManager.deleteTrigger("FLYCAM_Down", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
            inputManager.deleteTrigger("FLYCAM_ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
            inputManager.deleteTrigger("FLYCAM_ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
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
        if (timeAccumulator > 2) {
            timeAccumulator = 0;
            itemsToRemove.clear();
            for (Spatial item : inventory.allItems()) {
                if (item.getParent() == rootNode && item.getLocalTranslation().y < -1000) {
                    itemsToRemove.add(item);
                }
            }
            inventory.removeItems(itemsToRemove);
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
            	table.dropRandomBlock();
            }
        } else if (name.equals("makeStack")) {
            if (isPressed) {
            	table.dropRandomStackOfBlocks(5);
            }
        } else if (name.equals("clearTable")) {
        	if (isPressed) {
        		inventory.removeAllFreeItems();
        	}
        } else if (name.equals("cameraRobotHead")) {
            if (isPressed) {
                robot.toggleHeadCameraView(rootNode);
            }
        }
    }    
}
