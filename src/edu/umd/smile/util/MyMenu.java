package edu.umd.smile.util;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyIdCreator;
import de.lessvoid.nifty.builder.ControlBuilder;
import de.lessvoid.nifty.controls.Controller;
import de.lessvoid.nifty.controls.NiftyControl;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.tools.SizeValue;
import de.lessvoid.xml.xpp3.Attributes;

public class MyMenu<T> implements Controller, NiftyControl {
    protected Nifty nifty;
    protected Screen screen;
    protected Element element;
    protected boolean bound;

    // This will keep a map of all items (T) added to this menu with the elementId
    // of the Nifty element as the key. We'll use this map to find the added item
    // that we'll need to return when the item with an elementId has been activated.
    protected Map<String, T> items = new Hashtable<String, T>();
    protected List<Element> menuItems = new ArrayList<Element>();

    public void bind(final Nifty niftyParam, final Screen screenParam, final Element newElement,
            final Properties properties, final Attributes controlDefinitionAttributesParam) {
        nifty = niftyParam;
        screen = screenParam;
        element = newElement;
    }

    @Override
    public void init(final Properties parameter, final Attributes controlDefinitionAttributes) {
        bound = true;
    }

    @Override
    public void onStartScreen() {
        element.layoutElements();
        movePopup();
    }

    @Override
    public boolean inputEvent(final NiftyInputEvent inputEvent) {
        return false;
    }

    @Override
    public void onFocus(final boolean getFocus) {
    }
    
    public void addMenuItem(final String menuText, final T item) {
        final String id = NiftyIdCreator.generate();
        Element menuItemElm = new ControlBuilder(id, "myNiftyMenuItem") {
            {
                set("menuText", nifty.specialValuesReplace(menuText));
                set("menuOnClick", "activateItem(" + id + ")");
                set("menuIconVisible", "false");
            }
        }.build(nifty, screen, element);
        items.put(id, item);
        menuItems.add(menuItemElm);
    }

    public void addMenuItem(final String menuText, final String menuIcon, final T item) {
        final String id = NiftyIdCreator.generate();
        Element menuItemElm = new ControlBuilder(id, "niftyMenuItem") {
            {
                set("menuText", nifty.specialValuesReplace(menuText));
                set("menuOnClick", "activateItem(" + id + ")");
                if (menuIcon != null) {
                    set("menuIcon", menuIcon);
                    set("menuIconVisible", "true");
                } else {
                    set("menuIconVisible", "false");
                }
            }
        }.build(nifty, screen, element);
        items.put(id, item);
        menuItems.add(menuItemElm);
    }
    
    /**
     * This is new
     * @param ind
     * @return
     */
    public Element getMenuItem(int ind) {
        return menuItems.get(ind);
    }
    
    public List<Element> getMenuItems() {
        return menuItems;
    }

    public void addMenuItemSeparator() {
        new ControlBuilder("niftyMenuItemSeparator").build(nifty, screen, element);
    }

    // NiftyControl implementation

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public void enable() {
        element.enable();
    }

    @Override
    public void disable() {
        element.disable();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (enabled) {
            element.enable();
        } else {
            element.disable();
        }
    }

    @Override
    public boolean isEnabled() {
        return element.isEnabled();
    }

    @Override
    public String getId() {
        return element.getId();
    }

    @Override
    public void setId(final String id) {
        element.setId(id);
    }

    @Override
    public int getWidth() {
        return element.getWidth();
    }

    @Override
    public void setWidth(final SizeValue width) {
        element.setConstraintWidth(width);
    }

    @Override
    public int getHeight() {
        return element.getHeight();
    }

    @Override
    public void setHeight(final SizeValue height) {
        element.setConstraintHeight(height);
    }

    @Override
    public String getStyle() {
        return element.getStyle();
    }

    @Override
    public void setStyle(final String style) {
        element.setStyle(style);
    }

    @Override
    public void setFocus() {
    }

    @Override
    public void setFocusable(final boolean focusable) {
    }

    @Override
    public boolean hasFocus() {
        return false;
    }

    @Override
    public void layoutCallback() {
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    public String toString() {
        return super.toString() + " {" + (element == null ? "" : element.getId()) + "}";
    }

    // interact callbacks

    public boolean activateItem(final String menuItemId) {
        nifty.publishEvent(element.getId(), new MyMenuItemActivatedEvent<T>(MyMenu.this, items.get(menuItemId)));
        return true;
    }

    // Internals

    private void movePopup() {
        element.setConstraintX(new SizeValue(nifty.getNiftyMouse().getX() + "px"));
        element.setConstraintY(new SizeValue(nifty.getNiftyMouse().getY() + "px"));
        element.getParent().layoutElements();
    }

}
