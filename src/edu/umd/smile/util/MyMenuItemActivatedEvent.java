package edu.umd.smile.util;


import de.lessvoid.nifty.NiftyEvent;

/**
 * This is a copy of MenuItemActivatedEvent, except that now it uses MyMenu instead of MenuControl.
 * This could've been avoided if the member variables were not made private.
 */
public class MyMenuItemActivatedEvent<T> implements NiftyEvent<T> {
    protected MyMenu<T> menu;
    protected String itemName;
    protected T itemUserObject;

    public MyMenuItemActivatedEvent(final MyMenu<T> menu, String name, T userObject) {
        this.menu = menu;
        this.itemName = name;
        this.itemUserObject = userObject;
    }

    public MyMenu<T> getMenu() {
        return menu;
    }

    public String getItemName() {
        return itemName;
    }
    
    public T getItemUserObject() {
        return itemUserObject;
    }
}
