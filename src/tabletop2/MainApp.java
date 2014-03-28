package tabletop2;

import java.util.HashSet;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

import tabletop2.gui.GuiController;

import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
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
    private GuiController guiController;
    
    private final boolean flyGripper = false;
    private Node gripperNode;
    private Gripper gripper;
        
    private boolean hasDeletedMouseTrigger = false;
    
    private float timeAccumulator = 0;
    
    private boolean isRunning = true;
    private FlyCamAppState flyCamAppState;
    
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
    
    public Demonstrator getDemonstrator() {
    	return demonstrator;
    }
    
    @Override
    public void simpleInitApp() {
    	initBulletAppState();
        flyCam.setMoveSpeed(10f);
        flyCamAppState = stateManager.getState(FlyCamAppState.class);
        
        factory = new Factory(assetManager);
        inventory = new Inventory(this);        
        table = new Table("table", this, robotLocationNode);
        table.reloadXml(DEFAULT_TABLE_XML_FNAME);
        
        rootNode.attachChild(robotLocationNode);
        robot = new Robot("baxter", this, robotLocationNode);
        robot.toggleHide();
        
        demonstrator = new Demonstrator("demo", this);
        
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
        bulletAppState.getPhysicsSpace().setAccuracy(1f/120f);
//        bulletAppState.setThreadingType(BulletAppState.ThreadingType.PARALLEL);
        bulletAppState.getPhysicsSpace().setMaxSubSteps(5);
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
        cam.setLocation(new Vector3f(0, 14, table.getDepth() / 2 + 6));
        cam.lookAt(new Vector3f(0, 0, 0), Vector3f.UNIT_Y);
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
        guiController = new GuiController();
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
        inputManager.addMapping("escapeKey", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("spaceKey", new KeyTrigger(KeyInput.KEY_SPACE));
        
        inputManager.addListener(this, "shiftKey");
        inputManager.addListener(this, "escapeKey");
        inputManager.addListener(this, "spaceKey");
        
        table.initKeys(inputManager);
        robot.initKeys(inputManager);
        demonstrator.initKeys(inputManager);

        if (flyGripper) {
            inputManager.addListener(gripper, "gripperOpen");
            inputManager.addListener(gripper, "gripperClose");
        }
    }
    
    @Override
    public void simpleUpdate(float tpf) {
    	if (!isRunning) {
    		return;
    	}
    	
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
            inputManager.deleteTrigger(SimpleApplication.INPUT_MAPPING_EXIT, new KeyTrigger(KeyInput.KEY_ESCAPE));
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
    	if (name.equals("spaceKey")) {
        	if (isPressed) {
        		if (isRunning) {
        			guiController.showPausePopup(true);
        			bulletAppState.setEnabled(false);
        			flyCamAppState.setEnabled(false);
        			robot.setEnabled(false);
        			table.setEnabled(false);
        			demonstrator.setEnabled(false);
        			setPauseOnLostFocus(true);
        			isRunning = false;
        		} else {
        			guiController.showPausePopup(false);
        			bulletAppState.setEnabled(true);
        			flyCamAppState.setEnabled(true);
        			robot.setEnabled(true);
        			table.setEnabled(true);
        			demonstrator.setEnabled(true);
        			setPauseOnLostFocus(false);
        			isRunning = true;
        		}
        	}
        } else if (name.equals("escapeKey")) {
        	stop();
        }
    }    
}
