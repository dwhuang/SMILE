package edu.umd.smile.util;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public class Frustum extends Mesh {
    public Frustum(float radiusTop, float radiusBottom, float height, int sides) {
        // num of vertices: 4 each side + top (num of sides & center) + bottom (num of sides & center)
        FloatBuffer vb = BufferUtils.createFloatBuffer((sides * 4 + sides * 2 + 2) * 3);
        FloatBuffer nb = BufferUtils.createFloatBuffer((sides * 4 + sides * 2 + 2) * 3);
        // num of faces: side (num of sides * 2) + top (num of sides) + bottom (num of sides)
        ShortBuffer ib = BufferUtils.createShortBuffer(sides * 3 * 2 + sides * 3 * 2);
        
        // top center
        vb.put(0).put(height / 2).put(0);
        nb.put(0).put(1).put(0);
        // bottom center
        vb.put(0).put(-height / 2).put(0);
        nb.put(0).put(-1).put(0);
        
        float angleIncr = FastMath.TWO_PI / sides;
        float normAngleY = FastMath.atan((radiusBottom - radiusTop) / height); // vertical angle
        float prevX = 1; // angle = 0
        float prevZ = 0; // angle = 0
        float angle = angleIncr;
        short ind = 2;
        // process sides
        for (int i = 0; i < sides; ++i) {
            // add vertices
            float x = FastMath.cos(angle);
            float z = -FastMath.sin(angle);
            vb.put(radiusTop * prevX).put(height / 2).put(radiusTop * prevZ);
            vb.put(radiusBottom * prevX).put(-height / 2).put(radiusBottom * prevZ);
            vb.put(radiusTop * x).put(height / 2).put(radiusTop * z);
            vb.put(radiusBottom * x).put(-height / 2).put(radiusBottom * z);
            
            // add normals (the same)
            float normAngle = angle - angleIncr / 2;
            Vector3f normal = new Vector3f(FastMath.cos(normAngle), FastMath.sin(normAngleY),
                    -FastMath.sin(normAngle));
            normal.normalizeLocal();
            for (int j = 0; j < 4; ++j) {
                nb.put(normal.x).put(normal.y).put(normal.z);
            }
            
            // define faces
            ib.put(ind).put((short) (ind + 1)).put((short) (ind + 2));
            ib.put((short) (ind + 2)).put((short) (ind + 1)).put((short) (ind + 3));
            
            angle += angleIncr;
            prevX = x;
            prevZ = z;
            ind += 4;
        }
        
        // process top & bottom planes
        short indStart = ind;
        angle = 0;
        // add vertices & normals
        for (int i = 0; i < sides; ++i) {
            float x = FastMath.cos(angle);
            float z = -FastMath.sin(angle);
            vb.put(radiusTop * x).put(height / 2).put(radiusTop * z);
            vb.put(radiusBottom * x).put(-height / 2).put(radiusBottom * z);
            nb.put(0).put(1).put(0);
            nb.put(0).put(-1).put(0);
            
            angle += angleIncr;
            ind += 2;
        }
        // define faces
        for (int i = 0; i < sides - 1; ++i) {
            ib.put((short) (indStart + i * 2)).put((short) (indStart + (i + 1) * 2)).put((short) 0);
            ib.put((short) (indStart + (i + 1) * 2 + 1)).put((short) (indStart + i * 2 + 1)).put((short) 1);
        }
        ib.put((short) (ind - 2)).put(indStart).put((short) 0);
        ib.put((short) (indStart + 1)).put((short) (ind - 1)).put((short) 1);

        
        vb.rewind();
        ib.rewind();
        nb.rewind();
        setBuffer(VertexBuffer.Type.Position, 3, vb);
        setBuffer(VertexBuffer.Type.Index, 3, ib);
        setBuffer(VertexBuffer.Type.Normal, 3, nb);
        
        updateBound();
    }
}