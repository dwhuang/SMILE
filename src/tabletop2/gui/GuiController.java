/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

import java.util.ArrayList;

import tabletop2.Demonstrator;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.effects.EffectEventId;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.SizeValue;

/**
 *
 * @author dwhuang
 */
public class GuiController extends AbstractAppState implements ScreenController {
    
	private Nifty nifty;
	private Screen screen;
	
    private Demonstrator demonstrator;    
    private ArrayList<WindowController> windowControllers = new ArrayList<WindowController>();
    private Element puPause;
    
    public GuiController(Demonstrator demonstrator) {
        this.demonstrator = demonstrator;
    }
    
    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        for (WindowController wc : windowControllers) {
            wc.init(app);
        }
    }
    
    @Override
    public void update(float tpf) {
        for (WindowController wc : windowControllers) {
            wc.update(tpf);
        }
    }

    public void bind(Nifty nifty, Screen screen) {
    	this.nifty = nifty;
    	this.screen = screen;
    	
        puPause = nifty.createPopup("puPause");
        
        windowControllers.add(new StatusWindowController());
        windowControllers.add(new CamNavWindowController());
        windowControllers.add(new DemoWindowController(demonstrator));
        windowControllers.add(new ObjectsWindowController());

        for (WindowController wc : windowControllers) {
            wc.bind(nifty, screen);
            
            Window window = wc.getWindow();
            if (wc.getClass() != StatusWindowController.class) {
            	window.getElement().hide();
            }
        }
        organizeWindows();
    }

    public void onStartScreen() {
    }

    public void onEndScreen() {
    }
    
    public void showPausePopup(boolean enabled) {
    	if (enabled) {
    		nifty.showPopup(screen, puPause.getId(), null);
    	} else {
    		nifty.closePopup(puPause.getId());
    	}
    }
    
    @NiftyEventSubscriber(pattern="bt.*")
    public void onWindowButton(String id, ButtonClickedEvent e) {
        String windowId = "wd" + id.substring(2);
        for (WindowController wc : windowControllers) {
            Window window = wc.getWindow();
            if (window.getId().equals(windowId)) {
                Element windowElement = window.getElement();
                if (!windowElement.isVisible()) {
                    windowElement.show();
                }
                windowElement.markForMove(window.getElement().getParent());
                windowElement.reactivate();
                windowElement.startEffect(EffectEventId.onCustom);
                break;
            }
        }
    }
    
    @NiftyEventSubscriber(id="btShowAll")
    public void onBtShowAll(String id, ButtonClickedEvent e) {
        for (WindowController wc : windowControllers) {
            Element windowElement = wc.getWindow().getElement();
            if (!windowElement.isVisible()) {
                windowElement.show();
            }
        }
    }
    
    @NiftyEventSubscriber(id="btHideAll")
    public void onBtHideAll(String id, ButtonClickedEvent e) {
        for (WindowController wc : windowControllers) {
            Element windowElement = wc.getWindow().getElement();
            if (windowElement.isVisible()) {
                windowElement.hide();
            }
        }
    }
    
    @NiftyEventSubscriber(id="btOrganize")
    public void onBtOrganize(String id, ButtonClickedEvent e) {
        organizeWindows();
    }
    
    private void organizeWindows() {
        int y = 0;
        for (WindowController wc : windowControllers) {
            Element windowElement = wc.getWindow().getElement();
            if (windowElement.isVisible()) {
                windowElement.setConstraintX(SizeValue.px(0));
                windowElement.setConstraintY(SizeValue.px(y));
                y += windowElement.getHeight();

                windowElement.getParent().layoutElements();
            }
        }
        // for invisible windows
        for (WindowController wc : windowControllers) {
            Element windowElement = wc.getWindow().getElement();
            if (!windowElement.isVisible()) {
                windowElement.setConstraintX(SizeValue.px(0));
                windowElement.setConstraintY(SizeValue.px(y));
                y += windowElement.getHeight();
            }
        }
    }

}
