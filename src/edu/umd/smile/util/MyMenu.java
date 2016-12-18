package edu.umd.smile.util;

import java.util.Collection;
import java.util.Hashtable;
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
    public class Item {
        String name;
        String text;
        Element element;
        T userObject;
        public Item(String name, String text, Element element, T userObject) {
            this.name = name;
            this.text = text;
            this.element = element;
            this.userObject = userObject;
        }
        public void setUserObject(T userObject) {
            this.userObject = userObject;
        }
        public T getUserObject() {
            return this.userObject;
        }
    }
    protected Nifty nifty;
    protected Screen screen;
    protected Element element;
    protected boolean bound;

    // This will keep a map of all items (T) added to this menu with the elementId
    // of the Nifty element as the key. We'll use this map to find the added item
    // that we'll need to return when the item with an elementId has been activated.
    protected Map<String, Item> items = new Hashtable<>();
    protected Map<String, Item> itemsByName = new Hashtable<>();

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
    
    public String addMenuItem(String name, final String text, final T userObject) {
        final String id = NiftyIdCreator.generate();
        Element elm = new ControlBuilder(id, "myNiftyMenuItem") {
            {
                set("menuText", nifty.specialValuesReplace(text));
                set("menuOnClick", "activateItem(" + id + ")");
                set("menuIconVisible", "false");
            }
        }.build(nifty, screen, element);
        Item item = new Item(name, text, elm, userObject);
        items.put(id, item);
        itemsByName.put(name,  item);
        return id;
    }

//    public void addMenuItem(String name, final String menuText, final String menuIcon, final T item) {
//        final String id = NiftyIdCreator.generate();
//        Element menuItemElm = new ControlBuilder(id, "niftyMenuItem") {
//            {
//                set("menuText", nifty.specialValuesReplace(menuText));
//                set("menuOnClick", "activateItem(" + id + ")");
//                if (menuIcon != null) {
//                    set("menuIcon", menuIcon);
//                    set("menuIconVisible", "true");
//                } else {
//                    set("menuIconVisible", "false");
//                }
//            }
//        }.build(nifty, screen, element);
//        items.put(id, item);
//        menuItems.add(menuItemElm);
//    }
    
    // ====================================================
    public Item getItemByName(String name) {
        return itemsByName.get(name);
    }
    
    public Collection<Item> getAllItems() {
        return itemsByName.values();
    }
    
    // return true if at least one is enabled
    public boolean updateMenuItemEnabled() {
        boolean anyEnabled = false;
        for (Item item : items.values()) {
            if (item.userObject == null) {
                item.element.disable();
            } else {
                anyEnabled = true;
                item.element.enable();
            }
        }
        return anyEnabled;
    }

    // ====================================================

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
        Item item = items.get(menuItemId);
        nifty.publishEvent(element.getId(),
                new MyMenuItemActivatedEvent<T>(MyMenu.this, item.name, item.userObject));
        return true;
    }

    // Internals

    private void movePopup() {
        element.setConstraintX(new SizeValue(nifty.getNiftyMouse().getX() + "px"));
        element.setConstraintY(new SizeValue(nifty.getNiftyMouse().getY() + "px"));
        element.getParent().layoutElements();
    }

}
