package edu.umd.smile.robot;

import java.util.Map;
import java.util.TreeMap;

import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class RobotLocTracker {
	private TreeMap<String, Node> trackers = new TreeMap<String, Node>();
	private TreeMap<String, Vector3f> loc = new TreeMap<String, Vector3f>();
	
	public void put(String name, Node node) {
		trackers.put(name,  node);
	}
	
	public TreeMap<String, Vector3f> getLocations() {
		loc.clear();
		for (Map.Entry<String, Node> e : trackers.entrySet()) {
			loc.put(e.getKey(), e.getValue().getWorldTranslation());
		}
		return loc;
	}
}
