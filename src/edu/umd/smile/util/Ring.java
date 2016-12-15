package edu.umd.smile.util;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.BufferUtils;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

public class Ring extends Mesh {
    public Ring(float radiusOuter, float radiusInner, float height, int sides) {
        FloatBuffer vb = BufferUtils.createFloatBuffer((sides * 2 * 2 + sides * 2 * 2) * 3);
        FloatBuffer nb = BufferUtils.createFloatBuffer((sides * 2 * 2 + sides * 2 * 2) * 3);
        ShortBuffer ib = BufferUtils.createShortBuffer(sides * 4 * 2 * 3);
        
        float angleIncr = FastMath.TWO_PI / sides;
        float angle;
        int ind = 0;
        
        // outer & inner faces
        angle = 0;
        for (int i = 0; i < sides; ++i) {
            float x = FastMath.cos(angle);
            float z = -FastMath.sin(angle);
            vb.put(radiusOuter * x).put(height / 2).put(radiusOuter * z);
            vb.put(radiusOuter * x).put(-height / 2).put(radiusOuter * z);
            vb.put(radiusInner * x).put(height / 2).put(radiusInner * z);
            vb.put(radiusInner * x).put(-height / 2).put(radiusInner * z);
            nb.put(x).put(0).put(z);
            nb.put(x).put(0).put(z);
            nb.put(-x).put(0).put(-z);
            nb.put(-x).put(0).put(-z);
            
            ind += 4;
            angle += angleIncr;
        }
        for (int i = 0; i < sides - 1; ++i) {
            ib.put((short) (i * 4)).put((short) (i * 4 + 1)).put((short) ((i + 1) * 4 + 1));
            ib.put((short) (i * 4)).put((short) ((i + 1) * 4 + 1)).put((short) ((i + 1) * 4));
            ib.put((short) (i * 4 + 2)).put((short) ((i + 1) * 4 + 3)).put((short) (i * 4 + 3));
            ib.put((short) (i * 4 + 2)).put((short) ((i + 1) * 4 + 2)).put((short) ((i + 1) * 4 + 3));
        }
        ib.put((short) (ind - 4)).put((short) (ind - 3)).put((short) 1);
        ib.put((short) (ind - 4)).put((short) 1).put((short) 0);
        ib.put((short) (ind - 2)).put((short) 3).put((short) (ind - 1));
        ib.put((short) (ind - 2)).put((short) 2).put((short) 3);
        
        // top & bottom faces
        int indStart = ind;
        angle = 0;
        for (int i = 0; i < sides; ++i) {
            float x = FastMath.cos(angle);
            float z = -FastMath.sin(angle);
            vb.put(radiusOuter * x).put(height / 2).put(radiusOuter * z);
            vb.put(radiusOuter * x).put(-height / 2).put(radiusOuter * z);
            vb.put(radiusInner * x).put(height / 2).put(radiusInner * z);
            vb.put(radiusInner * x).put(-height / 2).put(radiusInner * z);
            nb.put(0).put(1).put(0);
            nb.put(0).put(-1).put(0);
            nb.put(0).put(1).put(0);
            nb.put(0).put(-1).put(0);
            
            ind += 4;
            angle += angleIncr;
        }
        for (int i = 0; i < sides - 1; ++i) {
            ib.put((short) (indStart + i * 4)).put((short) (indStart + (i + 1) * 4)).put((short) (indStart + i * 4 + 2));
            ib.put((short) (indStart + (i + 1) * 4)).put((short) (indStart + (i + 1) * 4 + 2)).put((short) (indStart + i * 4 + 2));
            ib.put((short) (indStart + i * 4 + 1)).put((short) (indStart + i * 4 + 3)).put((short) (indStart + (i + 1) * 4 + 1));
            ib.put((short) (indStart + (i + 1) * 4 + 1)).put((short) (indStart + i * 4 + 3)).put((short) (indStart + (i + 1) * 4 + 3));
        }
        ib.put((short) (ind - 4)).put((short) indStart).put((short) (ind - 2));
        ib.put((short) (ind - 2)).put((short) indStart).put((short) (indStart + 2));
        ib.put((short) (ind - 3)).put((short) (ind - 1)).put((short) (indStart + 1));
        ib.put((short) (indStart + 1)).put((short) (ind - 1)).put((short) (indStart + 3));
        
        
        vb.rewind();
        ib.rewind();
        nb.rewind();
        setBuffer(VertexBuffer.Type.Position, 3, vb);
        setBuffer(VertexBuffer.Type.Index, 3, ib);
        setBuffer(VertexBuffer.Type.Normal, 3, nb);
        
        updateBound();
    }
}
