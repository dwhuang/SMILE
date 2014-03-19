/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

import tabletop2.DemonstrationListener;
import tabletop2.Demonstrator;

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
public class DemoWindowController implements WindowController, DemonstrationListener {
    private Demonstrator demonstrator;
    private Window window;
    private Label lbGrasped;
    private Button btRelease;
    private Button btDestroy;
    private Button btPlaneRotL;
    private Button btPlaneRotR;
    private float planeRotDir = 0;

    private Scrollbar[] sbObjRot = new Scrollbar[3];
    private Label[] lbObjAngles = new Label[3];
    private Quaternion objRot = new Quaternion();
    private float[] prevObjAngles = new float[] {180, 180, 180};
    private final Vector3f[] objRotAxis = new Vector3f[] {
        Vector3f.UNIT_X, Vector3f.UNIT_Z.negate(), Vector3f.UNIT_Y};
    
    private transient Quaternion targetRot = new Quaternion();
    
    DemoWindowController(Demonstrator demonstrator) {
        this.demonstrator = demonstrator;
    }

    public void bind(Nifty nifty, Screen screen) {
        window = screen.findElementByName("wdDemo").getNiftyControl(Window.class);
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
        nifty.subscribeAnnotations(this);

        demoRelease();
    }

    public void init(Application app) {
        demonstrator.addListener(this);
    }

    public void update(float tpf) {
        if (planeRotDir != 0) {
            demonstrator.onAnalog("demoPlaneRotate", planeRotDir * tpf, tpf);
        }
    }

    public Window getWindow() {
        return window;
    }

    public void demoGrasp(Spatial s, Vector3f pos, Quaternion rot) {
        lbGrasped.setText(s.getName());
        btRelease.enable();
        btDestroy.enable();
        btPlaneRotL.enable();
        btPlaneRotR.enable();
        for (int i = 0; i < sbObjRot.length; ++i) {
            sbObjRot[i].enable();
            lbObjAngles[i].setText("0");
            prevObjAngles[i] = 180;
        }
        
        objRot = new Quaternion(rot);
    }

    public void demoRelease() {
        lbGrasped.setText("<empty>");
        btRelease.disable();
        btDestroy.disable();
        btPlaneRotL.disable();
        btPlaneRotR.disable();        
        
        for (int i = 0; i < sbObjRot.length; ++i) {
            sbObjRot[i].disable();
            sbObjRot[i].setValue(180);
            lbObjAngles[i].setText("");
        }
    }
    
    @NiftyEventSubscriber(id="btRelease")
    public void onBtRelease(String id, ButtonClickedEvent e) {
        demonstrator.release();
    }
    
    @NiftyEventSubscriber(id="btDestroy")
    public void onBtDestroy(String id, ButtonClickedEvent e) {
        demonstrator.destroy();
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
    public void onSbObjRotXChanged(String id, ScrollbarChangedEvent e) {
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
        
        float angleDiff = e.getValue() - prevObjAngles[ind];
        if (FastMath.abs(angleDiff) < 46) {
            targetRot.fromAngleNormalAxis(angleDiff * FastMath.DEG_TO_RAD, objRotAxis[ind]);
            targetRot.multLocal(objRot);
            float delta = demonstrator.rotate(targetRot, 0.02f);
            if (delta < 1) {
                // partial rotation
                e.getScrollbar().setValue(prevObjAngles[ind] + delta * angleDiff);
            }
            prevObjAngles[ind] = sbObjRot[ind].getValue();
            lbObjAngles[ind].setText(Integer.toString((int)sbObjRot[ind].getValue() - 180));
            objRot.set(targetRot);
        } else {
            // ignore huge-angle rotations
            e.getScrollbar().setValue(prevObjAngles[ind]);
        }
    }
}
