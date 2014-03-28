package tabletop2.gui;

import tabletop2.MainApp;
import tabletop2.Robot;

import com.jme3.app.Application;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.DropDown;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedEvent;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryReleaseEvent;
import de.lessvoid.nifty.screen.Screen;

public class RobotWindowController implements WindowController {
	private static final String[] ROBOT_JOINTS = {
		"Head", 
		"Left shoulder 0", 
		"Left shoulder 1", 
		"Left elbow 0", 
		"Left elbow 1", 
		"Left wrist 0", 
		"Left wrist 1", 
		"Left wrist 2",
		"Left gripper",
		"Right shoulder 0", 
		"Right shoulder 1", 
		"Right elbow 0", 
		"Right elbow 1", 
		"Right wrist 0", 
		"Right wrist 1", 
		"Right wrist 2",
		"Right gripper"};
	
	private static final Robot.Limb[] ROBOT_LIMBS = {
		Robot.Limb.Head,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftArm,
		Robot.Limb.LeftGripper,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightArm,
		Robot.Limb.RightGripper};
	
	private static final String[] ROBOT_JOINT_NAMES = {
		"",
		"S0",
		"S1",
		"E0",
		"E1",
		"W0",
		"W1",
		"W2",
		"",
		"S0",
		"S1",
		"E0",
		"E1",
		"W0",
		"W1",
		"W2",
		""};
	
	private Robot robot;
	private Window window;
	private DropDown<String> ddRobotJoint;
	
	private int ddRobotJointInd;
	private float jointVelocity = 0;

	@SuppressWarnings("unchecked")
	@Override
	public void bind(Nifty nifty, Screen screen) {
		window = screen.findNiftyControl("wdRobot", Window.class);
		ddRobotJoint = screen.findNiftyControl("ddRobotJoint", DropDown.class);
		
		for (String s : ROBOT_JOINTS) {
			ddRobotJoint.addItem(s);
		}
		ddRobotJointInd = ddRobotJoint.getSelectedIndex();
		
		nifty.subscribeAnnotations(this);
	}

	@Override
	public void init(Application app) {
		robot = ((MainApp) app).getRobot();
	}

	@Override
	public void update(float tpf) {
		if (jointVelocity != 0) {
			robot.setJointVelocity(ROBOT_LIMBS[ddRobotJointInd], 
					ROBOT_JOINT_NAMES[ddRobotJointInd], jointVelocity, true);
		}
	}

	@Override
	public Window getWindow() {
		return window;
	}

    @NiftyEventSubscriber(id="btRobotVisible")
    public void onBtRobotVisible(String id, ButtonClickedEvent e) {
    	robot.toggleHide();
    }

    @NiftyEventSubscriber(id="btRobotHeadView")
    public void onBtRobotHeadView(String id, ButtonClickedEvent e) {
    	robot.toggleHeadCameraView();
    }

    @NiftyEventSubscriber(id="btRobotMatlab")
    public void onBtRobotMatlab(String id, ButtonClickedEvent e) {
    	robot.toggleMatlabControl();
    }
    
    @NiftyEventSubscriber(pattern="btRobotJoint.?")
    public void onBtRobotJointClicked(String id, NiftyMousePrimaryClickedEvent e) {
    	if (id.equals("btRobotJointL")) {
    		jointVelocity = 1;
    		ddRobotJointInd = ddRobotJoint.getSelectedIndex();
    	} else if (id.equals("btRobotJointR")) {
    		jointVelocity = -1;
    		ddRobotJointInd = ddRobotJoint.getSelectedIndex();
    	}
    }
    
    @NiftyEventSubscriber(pattern="btRobotJoint.?")
    public void onBtRobotJointReleased(String id, NiftyMousePrimaryReleaseEvent e) {
		jointVelocity = 0;
    }
    
}
