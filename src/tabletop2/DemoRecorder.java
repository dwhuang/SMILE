package tabletop2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import tabletop2.Demonstrator.HandId;
import tabletop2.util.FileUtils;
import tabletop2.util.MyRigidBodyControl;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class DemoRecorder implements DemoPreActionListener, DemoActionListener, InventoryListener {
	private static final Logger logger = Logger.getLogger(DemoRecorder.class.getName());
	private static final float DEMORECORDER_TPF = 1f / 4f;

	private Inventory inventory;
	private Demonstrator demonstrator;
	private Robot robot;
	private LinkedList<MementoSet> history = new LinkedList<MementoSet>();
	
	private boolean isRecording = false;
	private File demoDir;
	private int currSeg = 0;
	private PrintStream currSegSymbWritter = null;
	private File currSegVisionDir = null;
	private long frameId = 0;
	private float timeElapsed = 0;
	private HashMap<Spatial, ItemTransformState> itemStates = new HashMap<Spatial, ItemTransformState>();

	private transient StringBuilder buf = new StringBuilder();
	private transient ItemTransformState tmpItemState = new ItemTransformState();
	

	private class ItemTransformState {
		boolean isActive = false;
		Vector3f location = new Vector3f();
		float[] angles = new float[3]; // in degrees
		
		ItemTransformState(ItemTransformState st) {
			set(st);
		}	
		
		ItemTransformState() {
		}
		
		void set(Spatial item) {
			isActive = item.getControl(MyRigidBodyControl.class).isActive();
			
			final Transform trans = item.getWorldTransform();			
			trans.getTranslation(location);
			trans.getRotation().toAngles(angles);
			// location: transform to user coordinate system
			float tmp = location.y;
			location.y = -location.z;
			location.z = tmp;
			// rotation
			tmp = angles[1];
			angles[1] = -angles[2];
			angles[2] = tmp;
			angles[0] *= FastMath.RAD_TO_DEG;
			angles[1] *= FastMath.RAD_TO_DEG;
			angles[2] *= FastMath.RAD_TO_DEG;
		}
		
		void set(ItemTransformState st) {
			isActive = st.isActive;
			location.set(st.location);
			angles[0] = st.angles[0];
			angles[1] = st.angles[1];
			angles[2] = st.angles[2];
		}
		
		boolean isSimilarEnough(ItemTransformState st) {
			if (location.distanceSquared(st.location) > 0.001) {
				return false;
			}
			if (Math.abs(angles[0] - st.angles[0]) > 0.5) {
				return false;
			}
			if (Math.abs(angles[1] - st.angles[1]) > 0.5) {
				return false;
			}
			if (Math.abs(angles[2] - st.angles[2]) > 0.5) {
				return false;
			}
			return true;
		}
		
		@Override
		public String toString() {
			return location.x + "," + location.y + "," + location.z + "," + angles[0]
					+ "," + angles[1] + "," + angles[2];
		}
	}
	
	private class MementoSet {
		private long lastFrameId;
		private HashMap<Spatial, ItemTransformState> lastItemStates;
		private Inventory.Memento inventoryMemento;
		private Demonstrator.Memento demoMemento;
	}

	public DemoRecorder(MainApp app) {
		this.inventory = app.getInventory();
		this.demonstrator = app.getDemonstrator();
		this.robot = app.getRobot();
		demonstrator.addPreActionListener(this);
		demonstrator.addActionListener(this);
		inventory.addListener(this);
	}
	
	public void processStart() {
		demoDir = new File(MainApp.DEMO_RECORDING_DIRNAME);
		FileUtils.deleteRecursively(demoDir);
		demoDir.mkdir();
		
		if (startNewSegment()) {			
			isRecording = true;
			for (Spatial item : inventory.allItems()) {
				printObjectCreateSymbols(item);
			}
		}
	}
	
	public void processFinish() {
		isRecording = false;
		history.clear();

		currSeg = 0;
		if (currSegSymbWritter != null) {
			currSegSymbWritter.close();
			currSegSymbWritter = null;
		}
		frameId = 0;
		itemStates.clear();
	}
	
	public boolean isUndoable() {
		return !history.isEmpty();
	}
	
	public boolean isRecording() {
		return isRecording;
	}
	
	public void undo() {
		MementoSet ms = history.pop();
		if (ms == null) {
			return;
		}
		discardCurrSegment();
		
		inventory.removeListener(this);
		frameId = ms.lastFrameId;
		itemStates = ms.lastItemStates;
		inventory.restoreFromMemento(ms.inventoryMemento);
		demonstrator.restoreFromMemento(ms.demoMemento);
		inventory.addListener(this);
	}
	
	private void saveToHistory() {
		MementoSet ms = new MementoSet();
		ms.lastFrameId = frameId;
		ms.lastItemStates = new HashMap<Spatial, ItemTransformState>();
		for (Spatial key : itemStates.keySet()) {
			ms.lastItemStates.put(key, new ItemTransformState(itemStates.get(key)));
		}
		ms.demoMemento = demonstrator.saveToMemento();
		ms.inventoryMemento = inventory.saveToMemento();
		history.push(ms);
		
		if (!startNewSegment()) {
			processFinish();
		}
	}

	@Override
	public void demoPreGrasp(HandId handId) {
		if (isRecording) {
			saveToHistory();
		}		
	}

	@Override
	public void demoPreRelease(HandId handId) {
		if (isRecording) {
			saveToHistory();
		}		
	}

	@Override
	public void demoPreDestroy(HandId handId) {
		if (isRecording) {
			saveToHistory();
		}
	}

	@Override
	public void demoPreTrigger(HandId handId, Spatial s) {
		if (isRecording) {
			saveToHistory();
			currSegSymbWritter.print(frameId + ",trigger," + handId.toString() + "," + s.getName() + "\n");
		}
	}
	
	@Override
	public void demoGrasp(HandId handId, Spatial s, Vector3f pos, Quaternion rot) {
		if (isRecording) {
			currSegSymbWritter.print(frameId + ",grasp," + handId.toString() + "," + s.getName() + "\n");
		}
	}

	@Override
	public void demoRelease(HandId handId) {
		if (isRecording) {
			currSegSymbWritter.print(frameId + ",release," + handId.toString() + "\n");
		}
	}

	@Override
	public void demoDestroy(HandId handId) {
		if (isRecording) {
			currSegSymbWritter.print(frameId + ",destroy," + handId.toString() + "\n");
		}
	}

	@Override
	public void demoTrigger(HandId handId, Spatial s) {
	}
	
	@Override
	public void objectCreated(Spatial obj) {
		if (isRecording) {
			printObjectCreateSymbols(obj);
		}
	}

	@Override
	public void objectDeleted(Spatial obj) {
		if (isRecording) {
			currSegSymbWritter.print(frameId + ",delete," + obj.getName() + "\n");
		}
	}

	@Override
	public void objectTriggered(Spatial obj, String name, int state) {
		if (isRecording) {
			currSegSymbWritter.print(frameId + ",event," + name + "," + state + "\n");
			saveRobotVision();
		}
	}

	private boolean startNewSegment() {
		if (currSegSymbWritter != null) {
			currSegSymbWritter.close();
			currSegSymbWritter = null;
			++currSeg;
		}
		
		currSegVisionDir = new File(demoDir, currSeg + "");
		currSegVisionDir.mkdir();

		File symbFile = new File(demoDir, currSeg + ".txt");
		try {
			currSegSymbWritter = new PrintStream(new FileOutputStream(symbFile));
		} catch (FileNotFoundException e) {
			logger.log(Level.SEVERE, "cannot write to " + symbFile, e);
			currSegSymbWritter = null;
			return false;			
		}
		return true;
	}
	
	private boolean discardCurrSegment() {
		if (currSeg == 0) {
			throw new IllegalStateException("cannot discard the only segment");
		}
		FileUtils.deleteRecursively(currSegVisionDir);
		currSegSymbWritter.close();
		currSegSymbWritter = null;
		File symbFile = new File(demoDir, currSeg + ".txt");
		symbFile.delete();
		
		--currSeg;
		currSegVisionDir = new File(demoDir, currSeg + "");
		symbFile = new File(demoDir, currSeg + ".txt");
		try {
			currSegSymbWritter = new PrintStream(new FileOutputStream(symbFile, true)); // append mode
		} catch (FileNotFoundException e) {
			logger.log(Level.SEVERE, "cannot append to " + symbFile, e);
			currSegSymbWritter = null;
			return false;			
		}
		return true;
	}
	
	public void update(float tpf) {
		if (!isRecording) {
			return;
		}
		timeElapsed += tpf;
		if (timeElapsed >= DEMORECORDER_TPF) {
			if (printObjectMoveSymbols()) {
				saveRobotVision();
			}
			timeElapsed = 0;
			++frameId;
		}
	}
	
	private void saveRobotVision() {
		BufferedImage img = robot.getVisualImage();
		File imgFile = new File(currSegVisionDir, frameId + ".png");
		try {
			ImageIO.write(img, "png", imgFile);
		} catch (IOException e) {
			logger.log(Level.WARNING, "cannot save visual image: " + imgFile, e);
		}
	}

	private boolean printObjectMoveSymbols() {
		buf.setLength(0);
		for (Spatial item : inventory.allItems()) {
			MyRigidBodyControl rbc = item.getControl(MyRigidBodyControl.class);
			tmpItemState.set(item);
			boolean isPrinting = false;
			if (!itemStates.containsKey(item)) {
				isPrinting = true;
			} else if (!rbc.isActive() && itemStates.get(item).isActive) {
				isPrinting = true;
			} else if (rbc.isActive() && !itemStates.get(item).isSimilarEnough(tmpItemState)) {
				isPrinting = true;
			}
			
			if (isPrinting) {
				buf.append(frameId + ",move," + item.getName() + "," + tmpItemState + "\n");
				if (!itemStates.containsKey(item)) {
					itemStates.put(item, new ItemTransformState(tmpItemState));
				} else {
					itemStates.get(item).set(tmpItemState);
				}
			}
		}
		currSegSymbWritter.print(buf);
		return buf.length() > 0;
	}
	
	private void printObjectCreateSymbols(Spatial item) {
		buf.setLength(0);
		buf.append(frameId + ",create," + item.getName());
		String shape = item.getUserData("obj_shape");
		buf.append(",shape," + shape);
		buf.append(",mass," + item.getControl(MyRigidBodyControl.class).getMass());
				
		for (String k : item.getUserDataKeys()) {
			if (k.startsWith("obj_") && !k.equals("obj_shape")) {
				String kName = k.substring(4);
				buf.append("," + kName);
				if (kName.endsWith("Color") || kName.endsWith("color")) {
					buf.append(",#");
					ColorRGBA color = item.getUserData(k);
					int rgba = color.asIntRGBA();
					rgba &= 0xffffff00;
					rgba >>= 8;
					String hex = Integer.toHexString(rgba);
					if (hex.length() < 6) {
						for (int i = 0; i < 6 - hex.length(); ++i) {
							buf.append("0");
						}
					}
					buf.append(hex);
				} else {
					buf.append("," + item.getUserData(k));
				}
			}
		}
		
		buf.append("\n");
		currSegSymbWritter.print(buf);
	}
}
