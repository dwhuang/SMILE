package tabletop2.gui;

import tabletop2.MainApp;
import tabletop2.Robot;

import com.jme3.app.Application;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Label;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.screen.Screen;

public class StatusWindowController implements WindowController {
	private final int FPS_UPDATE_INTERVAL = 2;
	
	private Window window;
	private Label lbFps;
	private Label lbMatlabStatus;
	
	private Robot robot;
	
	private float timer = 0;
	private int frameCount = 0;
	private boolean lastMatlabStatus = false;
	
	@Override
	public void bind(Nifty nifty, Screen screen) {
		window = screen.findElementByName("wdStatus").getNiftyControl(Window.class);
		lbFps = screen.findElementByName("lbFps").getNiftyControl(Label.class);
		lbMatlabStatus = screen.findElementByName("lbMatlabStatus").getNiftyControl(Label.class);
		nifty.subscribeAnnotations(this);
	}

	@Override
	public void init(Application app) {
		robot = ((MainApp) app).getRobot();
	}

	@Override
	public void update(float tpf) {
		timer += tpf;
		++frameCount;
		if (timer >= FPS_UPDATE_INTERVAL) {
			int fps = (int) (frameCount / timer);
			lbFps.setText("" + fps);
			timer = 0;
			frameCount = 0;
		}
		
		boolean matlabStatus = robot.matlabAgentAlive();
		if (matlabStatus != lastMatlabStatus) {
			lbMatlabStatus.setText("" + matlabStatus);
			lastMatlabStatus = matlabStatus;
		}
	}

	@Override
	public Window getWindow() {
		return window;
	}
}
