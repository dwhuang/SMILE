package edu.umd.smile.util;

import java.nio.FloatBuffer;

import com.jme3.bounding.BoundingSphere;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.scene.debug.WireSphere;
import com.jme3.util.BufferUtils;

public class MyWireSphere extends WireSphere {
    private static final int samples = 30;
    private static final int zSamples = 10;

	public MyWireSphere() {
		super();
	}
	
    public void updatePositions(float cx, float cy, float cz, float radius) {
        VertexBuffer pvb = getBuffer(Type.Position);
        FloatBuffer pb;

        if (pvb == null) {
            pvb = new VertexBuffer(Type.Position);
            pb = BufferUtils.createVector3Buffer(samples * 2 + samples * zSamples /*+ 6 * 3*/);
            pvb.setupData(Usage.Dynamic, 3, Format.Float, pb);
            setBuffer(pvb);
        } else {
            pb = (FloatBuffer) pvb.getData();
        }

        pb.rewind();

        // X axis
//        pb.put(radius).put(0).put(0);
//        pb.put(-radius).put(0).put(0);
//
//        // Y axis
//        pb.put(0).put(radius).put(0);
//        pb.put(0).put(-radius).put(0);
//
//        // Z axis
//        pb.put(0).put(0).put(radius);
//        pb.put(0).put(0).put(-radius);

        float rate = FastMath.TWO_PI / (float) samples;
        float angle = 0;
        for (int i = 0; i < samples; i++) {
            float x = radius * FastMath.cos(angle);
            float y = radius * FastMath.sin(angle);
            pb.put(cx + x).put(cy + y).put(cz);
            angle += rate;
        }

        angle = 0;
        for (int i = 0; i < samples; i++) {
            float x = radius * FastMath.cos(angle);
            float y = radius * FastMath.sin(angle);
            pb.put(cx).put(cy + x).put(cz + y);
            angle += rate;
        }

        float zRate = (radius * 2) / (float) (zSamples);
        float zHeight = -radius + (zRate / 2f);


        float rb = 1f / zSamples;
        float b = rb / 2f;

        for (int k = 0; k < zSamples; k++) {
            angle = 0;
            float scale = FastMath.sin(b * FastMath.PI);
            for (int i = 0; i < samples; i++) {
                float x = radius * FastMath.cos(angle);
                float y = radius * FastMath.sin(angle);

                pb.put(cx + x * scale).put(cy + zHeight).put(cz + y * scale);

                angle += rate;
            }
            zHeight += zRate;
            b += rb;
        }
    }

    /**
     * Create a WireSphere from a BoundingSphere
     *
     * @param bsph
     *     BoundingSphere used to create the WireSphere
     *
     */
    public void fromBoundingSphere(BoundingSphere bsph) {
    	Vector3f c = bsph.getCenter();
        updatePositions(c.x, c.y, c.z, bsph.getRadius());
    }
	
}
