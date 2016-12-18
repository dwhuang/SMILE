package edu.umd.smile.gui;

public interface ContextMenuListener<T> {
    public void onContextMenu(String itemName, T eventObject);
}
