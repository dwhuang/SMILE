/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.gui;

import java.util.ArrayList;
import java.util.HashMap;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.Label;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.effects.EffectEventId;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.tools.SizeValue;
import edu.umd.smile.MainApp;
import edu.umd.smile.demonstration.Demonstrator.ContextMenuParam;
import edu.umd.smile.util.MyMenu;
import edu.umd.smile.util.MyMenuItemActivatedEvent;

/**
 *
 * @author dwhuang
 */
public class GuiController extends AbstractAppState implements ScreenController {
    private MainApp app;
	private Nifty nifty;
	private Screen screen;
	
    private ArrayList<WindowController> windowControllers = new ArrayList<WindowController>();
    private Element puPause;
    private boolean isPaused = false;
    
    private Element puMessage;
    private Label lbMessage;
    private float messageDuration = 0;
    private float messageTimeElapsed;
    
    Element puContextMenu;
    MyMenu<ContextMenuParam> mnContextMenu;
    ContextMenuListener<ContextMenuParam> contextMenuListener = null;
        
    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = (MainApp) app;
        for (WindowController wc : windowControllers) {
            wc.init(app);
        }
    }
    
    @Override
    public void update(float tpf) {
    	if (isPaused) {
    		return;
    	}
        for (WindowController wc : windowControllers) {
            wc.update(tpf);
        }

        if (messageDuration > 0) {
	        messageTimeElapsed += tpf;
	        if (messageTimeElapsed >= messageDuration) {
	        	nifty.closePopup(puMessage.getId());
	        	messageDuration = 0;
	        }
        }
    }

    @SuppressWarnings("unchecked")
    public void bind(Nifty nifty, Screen screen) {
    	this.nifty = nifty;
    	this.screen = screen;
    	
        puPause = nifty.createPopup("puPause");
        puMessage = nifty.createPopup("puMessage");
        lbMessage = puMessage.findNiftyControl("lbMessage", Label.class);
        puContextMenu = nifty.createPopup("puContextMenu");
        mnContextMenu = puContextMenu.findNiftyControl("mnContextMenu", MyMenu.class);
        mnContextMenu.addMenuItem("trigger", "Trigger", null);
        mnContextMenu.addMenuItem("pointTo", "Point To", null);
        mnContextMenu.addMenuItem("fasten", "Fasten", null);
        mnContextMenu.addMenuItem("loosen", "Loosen", null);
        
        windowControllers.add(new StatusWindowController());
        windowControllers.add(new CamNavWindowController());
        windowControllers.add(new RobotWindowController());
        windowControllers.add(new DemoWindowController());
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
    	isPaused = enabled;
    	if (enabled) {
    		nifty.showPopup(screen, puPause.getId(), null);
    	} else {
    		nifty.closePopup(puPause.getId());
    	}
    }
    
    public void showMessagePopup(String str, float duration) {
    	if (messageDuration <= 0) {
        	nifty.showPopup(screen, puMessage.getId(), null);
    	}
    	messageDuration = duration;
    	messageTimeElapsed = 0;
    	lbMessage.setText(str);
    }
    
    public void showContextMenu(HashMap<String, ContextMenuParam> info, ContextMenuListener<ContextMenuParam> listener) {
        for (MyMenu<ContextMenuParam>.Item item : mnContextMenu.getAllItems()) {
            item.setUserObject(null);
        }
        for (String key : info.keySet()) {
            MyMenu<ContextMenuParam>.Item item = mnContextMenu.getItemByName(key);
            if (item != null) {
                item.setUserObject(info.get(key));
            }
        }
        if (listener != null && mnContextMenu.updateMenuItemEnabled()) {
            nifty.showPopup(screen, puContextMenu.getId(), null);
            contextMenuListener = listener;
            app.setPause("contextMenu", true);
        }
    }
    
    public void closeContextMenu() {
        app.setPause("contextMenu", false);
        contextMenuListener = null;
        nifty.closePopup(puContextMenu.getId());
    }

    @NiftyEventSubscriber(pattern="mnContextMenu")
    public void onMenuItem(String id, MyMenuItemActivatedEvent<ContextMenuParam> e) {
        if (contextMenuListener != null) {
            contextMenuListener.onContextMenu(e.getItemName(), e.getItemUserObject());
        }
        closeContextMenu();
    }
    
    @NiftyEventSubscriber(pattern="bt.*")
    public void onWindowButton(String id, ButtonClickedEvent e) {
        String windowId = "wd" + id.substring(2);
        for (WindowController wc : windowControllers) {
            Window window = wc.getWindow();
            if (window.getId().equals(windowId)) {
                Element windowElement = window.getElement();
                if (windowElement.isVisible()) {
                	break;
                }
                // move the selected window to the top layer
                windowElement.markForMove(windowElement.getParent());
                windowElement.reactivate();
                windowElement.startEffect(EffectEventId.onCustom);

                windowElement.show();
                for (Element elm : windowElement.getElements()) {
                	// have to do this after markForMove(), otherwise radio button selections
                	// are not displayed properly
                	elm.show();
                }
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
