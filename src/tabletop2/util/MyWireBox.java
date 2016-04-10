package tabletop2.util;

import java.nio.FloatBuffer;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.scene.debug.WireBox;
import com.jme3.util.BufferUtils;

public class MyWireBox extends WireBox {

	public MyWireBox() {
		super();
	}
	
    public void updatePositions(float x, float y, float z, float xExt, float yExt, float zExt){
        VertexBuffer pvb = getBuffer(Type.Position);
        FloatBuffer pb;
        if (pvb == null){
            pvb = new VertexBuffer(Type.Position);
            pb = BufferUtils.createVector3Buffer(8);
            pvb.setupData(Usage.Dynamic, 3, Format.Float, pb);
            setBuffer(pvb);
        }else{
            pb = (FloatBuffer) pvb.getData();
            pvb.updateData(pb);
        }
        pb.rewind();
        pb.put(
            new float[]{
                x - xExt, y - yExt, z + zExt,
                x + xExt, y - yExt, z + zExt,
                x + xExt, y + yExt, z + zExt,
                x - xExt, y + yExt, z + zExt,

                x - xExt, y - yExt, z - zExt,
                x + xExt, y - yExt, z - zExt,
                x + xExt, y + yExt, z - zExt,
                x - xExt, y + yExt, z - zExt,
            }
        );
        updateBound();
    }

    public void fromBoundingBox(BoundingBox bbox){
    	Vector3f center = bbox.getCenter();
        updatePositions(center.x, center.y, center.z, bbox.getXExtent(), bbox.getYExtent(), bbox.getZExtent());
    }
}
