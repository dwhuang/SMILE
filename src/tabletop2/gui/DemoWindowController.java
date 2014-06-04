/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

import tabletop2.DemoActionListener;
import tabletop2.DemoRecorder;
import tabletop2.Demonstrator;
import tabletop2.Demonstrator.HandId;
import tabletop2.MainApp;

import com.jme3.app.Application;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.Button;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.Label;
import de.lessvoid.nifty.controls.RadioButton;
import de.lessvoid.nifty.controls.RadioButtonGroupStateChangedEvent;
import de.lessvoid.nifty.controls.Scrollbar;
import de.lessvoid.nifty.controls.ScrollbarChangedEvent;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedEvent;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryReleaseEvent;
import de.lessvoid.nifty.screen.Screen;

/**
 *
 * @author dwhuang
 */
public class DemoWindowController implements WindowController, DemoActionListener {
    private Demonstrator demonstrator;
    private DemoRecorder demoRecorder;
    private Window window;
    private RadioButton[] rbHand = new RadioButton[4];
    private Label lbGrasped;
    private Button btRelease;
    private Button btDestroy;
    private Button btPlaneRotL;
    private Button btPlaneRotR;
    private Scrollbar[] sbObjRot = new Scrollbar[3];
    private Label[] lbObjAngles = new Label[3];
    private Button btRecStart;
    private Button btRecFinish;
    private Button btRecUndo;
    
    private float planeRotDir = 0;
    
    public void bind(Nifty nifty, Screen screen) {
        window = screen.findElementByName("wdDemo").getNiftyControl(Window.class);
        rbHand[0] = screen.findElementByName("rbLeftHand").getNiftyControl(RadioButton.class);
        rbHand[1] = screen.findElementByName("rbRightHand").getNiftyControl(RadioButton.class);
        rbHand[2] = screen.findElementByName("rbBothHands").getNiftyControl(RadioButton.class);
        rbHand[3] = screen.findElementByName("rbAnyHand").getNiftyControl(RadioButton.class);
        lbGrasped = screen.findElementByName("lbGrasped").getNiftyControl(Label.class);
        btRelease = screen.findElementByName("btRelease").getNiftyControl(Button.class);
        btDestroy = screen.findElementByName("btDestroy").getNiftyControl(Button.class);
        btPlaneRotL = screen.findElementByName("btPlaneRotL").getNiftyControl(Button.class);
        btPlaneRotR = screen.findElementByName("btPlaneRotR").getNiftyControl(Button.class);
        sbObjRot[0] = screen.findElementByName("sbObjRotX").getNiftyControl(Scrollbar.class);
        sbObjRot[1] = screen.findElementByName("sbObjRotY").getNiftyControl(Scrollbar.class);
        sbObjRot[2] = screen.findElementByName("sbObjRotZ").getNiftyControl(Scrollbar.class);
        lbObjAngles[0] = screen.findElementByName("lbObjAngleX").getNiftyControl(Label.class);
        lbObjAngles[1] = screen.findElementByName("lbObjAngleY").getNiftyControl(Label.class);
        lbObjAngles[2] = screen.findElementByName("lbObjAngleZ").getNiftyControl(Label.class);
        btRecStart = screen.findNiftyControl("btRecStart", Button.class);
        btRecFinish = screen.findNiftyControl("btRecFinish", Button.class);
        btRecUndo = screen.findNiftyControl("btRecUndo", Button.class);
        nifty.subscribeAnnotations(this);
    }

    public void init(Application app) {
        this.demonstrator = ((MainApp) app).getDemonstrator();
        this.demonstrator.addActionListener(this);
        this.demoRecorder = ((MainApp) app).getDemoRecorder();
        
        updateRbHand();
        updateRecInfo();
    }

    public void update(float tpf) {
        if (planeRotDir != 0) {
            demonstrator.onAnalog(demonstrator.getName() + "PlaneRotate", planeRotDir * tpf, tpf);
        }
    }
    
    public Window getWindow() {
        return window;
    }

	@Override
	public void demoGrasp(HandId hId, Spatial s, Vector3f pos, Quaternion rot) {
		updateHandInfo();
        updateRecInfo();
	}

	@Override
	public void demoRelease(HandId hId) {
		updateHandInfo();
        updateRecInfo();
	}
	
	@Override
	public void demoDestroy(HandId hId) {
		updateHandInfo();
        updateRecInfo();
	}
	
	private void updateRbHand() {
		Demonstrator.Hand hand = demonstrator.getCurrHand();
		rbHand[hand.getId().getValue()].select();
	}
	
    private void updateHandInfo() {
    	if (demonstrator == null) {
    		return;
    	}
    	
    	Demonstrator.Hand hand = demonstrator.getCurrHand();
    	
    	if (hand.isIdle()) {
            lbGrasped.setText("<empty>");
            btRelease.disable();
            btDestroy.disable();
            btPlaneRotL.disable();
            btPlaneRotR.disable();                    
            for (int i = 0; i < sbObjRot.length; ++i) {
                sbObjRot[i].disable();
                sbObjRot[i].setValue(180);
                lbObjAngles[i].setText("0");
            }
    	} else {
            lbGrasped.setText(hand.getGraspedItemName());
            btRelease.enable();
            btDestroy.enable();
            btPlaneRotL.enable();
            btPlaneRotR.enable();
        	float[] userRotAngles = hand.getUserRotAngles();
        	float tmp = userRotAngles[2];
        	userRotAngles[2] = userRotAngles[1];
        	userRotAngles[1] = -tmp;
            for (int i = 0; i < sbObjRot.length; ++i) {
        		userRotAngles[i] *= FastMath.RAD_TO_DEG;
        		int angleRounded = Math.round(userRotAngles[i]);
                sbObjRot[i].disable();
                sbObjRot[i].setValue(180 + angleRounded);
                lbObjAngles[i].setText("" + angleRounded);
                sbObjRot[i].enable();
            }
    	}
    }
    
    private void updateRecInfo() {
		btRecStart.setEnabled(!demoRecorder.isRecording());
		btRecFinish.setEnabled(demoRecorder.isRecording());
		btRecUndo.setEnabled(demoRecorder.isRecording() && demoRecorder.isUndoable());
    }
    
    @NiftyEventSubscriber(id="rbgHand")
    public void onRbgHand(String id, RadioButtonGroupStateChangedEvent e) {
    	if (demonstrator == null) {
    		// not yet initialized
    		return;
    	}
    	if (e.getSelectedId().equals(rbHand[0].getId())) {
    		demonstrator.selectHand(HandId.LeftHand);
    	} else if (e.getSelectedId().equals(rbHand[1].getId())) {
    		demonstrator.selectHand(HandId.RightHand);
    	} else if (e.getSelectedId().equals(rbHand[2].getId())) {
    		demonstrator.selectHand(HandId.BothHands);
    	} else if (e.getSelectedId().equals(rbHand[3].getId())) {
    		demonstrator.selectHand(HandId.AnyHand);
    	}
    	updateHandInfo();
    }
    
    @NiftyEventSubscriber(id="btRelease")
    public void onBtRelease(String id, ButtonClickedEvent e) {
        demonstrator.release();
        updateHandInfo();
    }
    
    @NiftyEventSubscriber(id="btDestroy")
    public void onBtDestroy(String id, ButtonClickedEvent e) {
        demonstrator.destroy();
        updateHandInfo();
    }
    
    @NiftyEventSubscriber(pattern="btPlaneRot(L|R)")
    public void onBtPlaneRotClicked(String id, NiftyMousePrimaryClickedEvent e) {
        if (e.getElement() == btPlaneRotL.getElement()) {
            planeRotDir = 1;
        } else if (e.getElement() == btPlaneRotR.getElement()) {
            planeRotDir = -1;
        }
    }

    @NiftyEventSubscriber(pattern="btPlaneRot(L|R)")
    public void onBtPlaneRotReleased(String id, NiftyMousePrimaryReleaseEvent e) {
        planeRotDir = 0;
    }
    
    @NiftyEventSubscriber(pattern="sbObjRot(X|Y|Z)")
    public void onSbObjRotChanged(String id, ScrollbarChangedEvent e) {
        if (!e.getScrollbar().isEnabled()) {
            return;
        }
        int ind = -1;
        for (int i = 0; i < sbObjRot.length; ++i) {
            if (e.getScrollbar() == sbObjRot[i]) {
                ind = i;
                break;
            }
        }
        if (ind < 0) {
            return;
        }
        
        int angle = Math.round(e.getValue() - 180);
        int prevAngle = Integer.parseInt(lbObjAngles[ind].getText());
        
        if (FastMath.abs(angle - prevAngle) < 46) {
        	int axisInd = -1;
        	if (ind == 0) {
        		axisInd = 0;
        	} else if (ind == 1) {
        		axisInd = 2;
        		angle = -angle;
        	} else if (ind == 2) {
        		axisInd = 1;
        	}
            float newAngle = demonstrator.rotate(axisInd, angle * FastMath.DEG_TO_RAD);
        	newAngle *= FastMath.RAD_TO_DEG;
        	if (axisInd == 2) {
        		newAngle = -newAngle;
        	}
        	int newAngleRounded = Math.round(newAngle);
        	e.getScrollbar().disable();
    		e.getScrollbar().setValue(180 + newAngleRounded);
    		e.getScrollbar().enable();
    		lbObjAngles[ind].setText("" + newAngleRounded);
        } else {
            // ignore huge-angle rotations
        	e.getScrollbar().disable();
            e.getScrollbar().setValue(180 + prevAngle);
            e.getScrollbar().enable();
        }
    }

    @NiftyEventSubscriber(id="btRecStart")
    public void onBtRecStart(String id, ButtonClickedEvent e) {
    	demoRecorder.processStart();
    	updateRecInfo();
    }
    
    @NiftyEventSubscriber(id="btRecFinish")
    public void onBtRecFinish(String id, ButtonClickedEvent e) {
    	demoRecorder.processFinish();
    	updateRecInfo();
    }
    
    @NiftyEventSubscriber(id="btRecUndo")
    public void onBtRecUndo(String id, ButtonClickedEvent e) {
    	demoRecorder.undo();
    	updateRbHand();
    	updateRecInfo();
    	updateHandInfo();
    }
}
