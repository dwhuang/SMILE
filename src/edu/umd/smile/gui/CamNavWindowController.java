/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.gui;

import com.jme3.app.Application;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedEvent;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryReleaseEvent;
import de.lessvoid.nifty.screen.Screen;
import edu.umd.smile.util.CameraControl;

/**
 *
 * @author dwhuang
 */
public class CamNavWindowController implements WindowController {
    private Window window;
    private CameraControl camControl;    
    private AnalogPadController navPadController;
    private AnalogPadController oriPadController;
    private float altitudeChange = 0;
    
    // called by GuiController
    @Override
    public void bind(Nifty nifty, Screen screen) {
        window = screen.findElementByName("wdCamNav").getNiftyControl(Window.class);
                
        navPadController = new AnalogPadController(screen.findElementByName("navAnalogPad"));
        nifty.subscribeAnnotations(navPadController);
        oriPadController = new AnalogPadController(screen.findElementByName("oriAnalogPad"));
        nifty.subscribeAnnotations(oriPadController);

        nifty.subscribeAnnotations(this);
    }
    
    // called by GuiController
    @Override
    public void init(Application app) {
        camControl = new CameraControl(app.getCamera());
    }
    
    // called by GuiController
    @Override
    public void update(float tpf) {
        if (navPadController == null) {
            return;
        }
        float x, y;
        // moving
        x = navPadController.getInputX();
        y = navPadController.getInputY();
        if (x != 0 || y != 0) {
            camControl.move(-y * tpf * 2, -x * tpf * 2);
        }
        // moving up/down
        if (altitudeChange != 0) {
            camControl.rise(altitudeChange * tpf);
        }
        // looking around
        x = oriPadController.getInputX();
        y = oriPadController.getInputY();
        if (x != 0 || y != 0) {
            camControl.rotate(-y * tpf * 2, -x * tpf * 2);
        }
    }
    
    @Override
    public Window getWindow() {
        return window;
    }

    @NiftyEventSubscriber(pattern="(btAscend|btDescend)")
    public void onNavAltClicked(String id, NiftyMousePrimaryClickedEvent e) {
        if (id.equals("btAscend")) {
            altitudeChange = 1;
        } else {
            altitudeChange = -1;
        }
    }

    @NiftyEventSubscriber(pattern="(btAscend|btDescend)")
    public void onNavAltReleased(String id, NiftyMousePrimaryReleaseEvent e) {
        altitudeChange = 0;
    }
}
