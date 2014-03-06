/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedEvent;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedMovedEvent;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryReleaseEvent;

/**
 *
 * @author dwhuang
 */
public class AnalogPadController {
    private Element pad;
    private int width;
    private int height;
    
    private boolean anchored = false;
    private int anchorX;
    private int anchorY;
    private int x;
    private int y;

    public AnalogPadController(Element pad) {
        this.pad = pad;
        width = pad.getWidth();
        height = pad.getHeight();
    }
    
    @NiftyEventSubscriber(pattern=".*AnalogPad")
    public void onMouseDown(String id, NiftyMousePrimaryClickedEvent e) {
        if (e.getElement() != pad) {
            return;
        }
        anchored = true;
        anchorX = e.getMouseX();
        anchorY = e.getMouseY();
        x = anchorX;
        y = anchorY;
    }
    
    @NiftyEventSubscriber(pattern=".*AnalogPad")
    public void onMouseMove(String id, NiftyMousePrimaryClickedMovedEvent e) {
        if (e.getElement() != pad || !anchored) {
            return;
        }
        x = e.getMouseX();
        y = e.getMouseY();
    }

    @NiftyEventSubscriber(pattern=".*AnalogPad")
    public void onMouseMove(String id, NiftyMousePrimaryReleaseEvent e) {
        if (e.getElement() != pad || !anchored) {
            return;
        }
        anchored = false;
    }

    public float getInputX() {
        if (!anchored) {
            return 0;
        }
        return (float)(x - anchorX) / (float)width;
    }
    
    public float getInputY() {
        if (!anchored) {
            return 0;
        }
        return (float)(y - anchorY) / (float)height;
    }
}
