package tabletop2;

import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class FunctionalJoint {
    public enum FunctionalJointType {
    	NONE, MAGNET
    }

    public FunctionalJointType type;
	public Node node1;
	public Node node2;
	public Spatial item1;
	public Spatial item2;
	public PhysicsJoint joint;
}
