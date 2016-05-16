package edu.umd.smile.gui;

import java.io.File;
import java.util.Arrays;

import com.jme3.app.Application;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ButtonClickedEvent;
import de.lessvoid.nifty.controls.DropDown;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.elements.events.NiftyMousePrimaryClickedEvent;
import de.lessvoid.nifty.screen.Screen;
import edu.umd.smile.MainApp;
import edu.umd.smile.object.Inventory;
import edu.umd.smile.object.Table;

public class ObjectsWindowController implements WindowController {
	private static final String[] PRESET_OBJECTS = {
		"Make a big block", 
		"Make a stack of 3 blocks", 
		"Make a stack of 5 blocks", 
		"Make a stack of 8 blocks", 
		"Make a box container", 
		"Clear the table"};
	
	private Window window;
	private DropDown<String> ddXml;
	private DropDown<String> ddPresetObject;
	
	private String dirName;
	private Table table;
	private Inventory inventory;
	
	@SuppressWarnings("unchecked")
	@Override
	public void bind(Nifty nifty, Screen screen) {
		window = screen.findNiftyControl("wdObjects", Window.class);
		ddXml = screen.findNiftyControl("ddXml", DropDown.class);
		ddPresetObject = screen.findNiftyControl("ddPresetObject", DropDown.class);
		nifty.subscribeAnnotations(this);
		
		String defaultFname = MainApp.DEFAULT_TABLESETUP_FNAME;
		int n = defaultFname.lastIndexOf('/');
		dirName = defaultFname.substring(0, n);
		refreshDdXml(defaultFname.substring(n + 1));
		
		ddPresetObject.addAllItems(Arrays.asList(PRESET_OBJECTS));
	}

	@Override
	public void init(Application app) {
		table = ((MainApp) app).getTable();
		inventory = ((MainApp) app).getInventory();
	}

	@Override
	public void update(float tpf) {
	}

	@Override
	public Window getWindow() {
		return window;
	}
	
	private void refreshDdXml(String selectItem) {
		ddXml.clear();
		File dir = new File(dirName + "/");
		for (final File file : dir.listFiles()) {
			if (file.isFile() && file.getName().endsWith(".xml")) {
				ddXml.addItem(file.getName());
			}
		}
		ddXml.selectItem(selectItem);
	}
	
	@NiftyEventSubscriber(id="ddXml")
	public void onDdXmlClicked(String id, NiftyMousePrimaryClickedEvent e) {
		refreshDdXml(ddXml.getSelection());
	}
	
	@NiftyEventSubscriber(id="btLoadXml")
	public void onBtLoadXml(String id, ButtonClickedEvent e) {
		String xmlFname = ddXml.getSelection();
		table.reloadXml(dirName + "/" + xmlFname);
	}

	@NiftyEventSubscriber(id="btExecute")
	public void onBtMaterialize(String id, ButtonClickedEvent e) {
		String selected = ddPresetObject.getSelection(); 
		if (selected.equals(PRESET_OBJECTS[0])) {
			// make a large block
			table.dropRandomBlock();
		} else if (selected.equals(PRESET_OBJECTS[1])) {
			// make a stack of 3 blocks
			table.dropRandomStackOfBlocks(3);
		} else if (selected.equals(PRESET_OBJECTS[2])) {
			// make a stack of 5 blocks
			table.dropRandomStackOfBlocks(5);
		} else if (selected.equals(PRESET_OBJECTS[3])) {
			// make a stack of 8 blocks
			table.dropRandomStackOfBlocks(8);
		} else if (selected.equals(PRESET_OBJECTS[4])) {
			// make a box container
			table.dropRandomBoxContainer();
		} else if (selected.equals(PRESET_OBJECTS[5])) {
			// clear the table
			inventory.removeAllFreeItems();
		}
	}
}
