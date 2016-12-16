package edu.umd.smile.util;


import de.lessvoid.nifty.NiftyEvent;

/**
 * Nifty generates this event when a menu item has been activated. The item that was activated is being transmitted in
 * this event.
 * 
 * @author void
 */
public class MyMenuItemActivatedEvent<T> implements NiftyEvent<T> {
    private MyMenu<T> menu;
    private T item;

    public MyMenuItemActivatedEvent(final MyMenu<T> menu, final T item) {
        this.menu = menu;
        this.item = item;
    }

    public MyMenu<T> getMenu() {
        return menu;
    }

    public T getItem() {
        return item;
    }
}
