/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

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
import tabletop2.DemonstrationListener;
import tabletop2.Demonstrator;

/**
 *
 * @author dwhuang
 */
public class DemoWindowController implements WindowController, DemonstrationListener {
    private Demonstrator demonstrator;
    private Window window;
    private Label lbGrasped;
    private Button btRelease;
    private Button btPlaneRotL;
    private Button btPlaneRotR;
    private Scrollbar sbObjRotX;
    private Scrollbar sbObjRotY;
    private Scrollbar sbObjRotZ;
    private Label lbObjRotX;
    private Label lbObjRotY;
    private Label lbObjRotZ;
    
    private float planeRotDir = 0;
//    private Quaternion graspRot = new Quaternion();
    private Quaternion objRot = new Quaternion();
    private Quaternion quat = new Quaternion(); // temporary variable
    private float prevObjRotX = 180;
    private float prevObjRotY = 180;
    private float prevObjRotZ = 180;
    
    DemoWindowController(Demonstrator demonstrator) {
        this.demonstrator = demonstrator;
    }

    public void bind(Nifty nifty, Screen screen) {
        window = screen.findElementByName("wdDemo").getNiftyControl(Window.class);
        lbGrasped = screen.findElementByName("lbGrasped").getNiftyControl(Label.class);
        btRelease = screen.findElementByName("btRelease").getNiftyControl(Button.class);
        btPlaneRotL = screen.findElementByName("btPlaneRotL").getNiftyControl(Button.class);
        btPlaneRotR = screen.findElementByName("btPlaneRotR").getNiftyControl(Button.class);
        sbObjRotX = screen.findElementByName("sbObjRotX").getNiftyControl(Scrollbar.class);
        sbObjRotY = screen.findElementByName("sbObjRotY").getNiftyControl(Scrollbar.class);
        sbObjRotZ = screen.findElementByName("sbObjRotZ").getNiftyControl(Scrollbar.class);
        lbObjRotX = screen.findElementByName("lbObjRotX").getNiftyControl(Label.class);
        lbObjRotY = screen.findElementByName("lbObjRotY").getNiftyControl(Label.class);
        lbObjRotZ = screen.findElementByName("lbObjRotZ").getNiftyControl(Label.class);
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
        btPlaneRotL.enable();
        btPlaneRotR.enable();
        sbObjRotX.enable();
        sbObjRotY.enable();
        sbObjRotZ.enable();
        lbObjRotX.setText("0");
        lbObjRotY.setText("0");
        lbObjRotZ.setText("0");

        prevObjRotX = 180;
        prevObjRotY = 180;
        prevObjRotZ = 180;
        
        objRot = new Quaternion(rot);
    }

    public void demoRelease() {
        lbGrasped.setText("<empty>");
        btRelease.disable();
        btPlaneRotL.disable();
        btPlaneRotR.disable();        
        
        sbObjRotX.disable();
        sbObjRotX.setValue(180);
        sbObjRotY.disable();
        sbObjRotY.setValue(180);
        sbObjRotZ.disable();
        sbObjRotZ.setValue(180);
        
        lbObjRotX.setText("");
        lbObjRotY.setText("");
        lbObjRotZ.setText("");
    }
    
    @NiftyEventSubscriber(id="btRelease")
    public void onBtRelease(String id, ButtonClickedEvent e) {
        demonstrator.release();
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
        if (e.getScrollbar() == sbObjRotX) {
            quat.fromAngleNormalAxis((e.getValue() - prevObjRotX) * FastMath.DEG_TO_RAD, Vector3f.UNIT_X);
            prevObjRotX = e.getValue();
            lbObjRotX.setText(Integer.toString((int)e.getValue() - 180));
        } else if (e.getScrollbar() == sbObjRotY) {
            quat.fromAngleNormalAxis((e.getValue() - prevObjRotY) * FastMath.DEG_TO_RAD, Vector3f.UNIT_Z.negate());
            prevObjRotY = e.getValue();
            lbObjRotY.setText(Integer.toString((int)e.getValue() - 180));
        } else if (e.getScrollbar() == sbObjRotZ) {
            quat.fromAngleNormalAxis((e.getValue() - prevObjRotZ) * FastMath.DEG_TO_RAD, Vector3f.UNIT_Y);
            prevObjRotZ = e.getValue();
            lbObjRotZ.setText(Integer.toString((int)e.getValue() - 180));
        } else {
            return;
        }
        quat.multLocal(objRot);
        objRot.set(quat);
        demonstrator.rotate(quat);
    }
}
