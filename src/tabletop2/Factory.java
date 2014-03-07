/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import jme3tools.optimize.GeometryBatchFactory;

/**
 *
 * @author dwhuang
 */
public class Factory {
    private AssetManager assetManager;
    
    public Factory(AssetManager assetManager) {
        this.assetManager = assetManager;
    }
    
    public Geometry makeBlock(String name, float w, float h, float d, 
            ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", color.mult(0.8f));      
        mat.setColor("Diffuse", color);
        mat.setColor("Specular", ColorRGBA.White);
//        mat.setBoolean("HighQuality", false);
        Geometry box = new Geometry(name, new Box(w / 2, h / 2, d / 2));
        box.setMaterial(mat);
        return box;
    }
    
    public Spatial makeBigBlock(String name, float w, float h, float d, 
            ColorRGBA color, float unitBlockSize) {
        int wCount = (int) FastMath.ceil(w / unitBlockSize);
        int hCount = (int) FastMath.ceil(h / unitBlockSize);
        int dCount = (int) FastMath.ceil(d / unitBlockSize);
//        System.err.println(wCount + " " + dCount);
        Node bigBlock = new Node(name);
        
        float xMin = -(wCount / 2) * unitBlockSize;
        if (wCount % 2 == 0) {
            xMin += unitBlockSize / 2;
        }
        float yMin = -(hCount / 2) * unitBlockSize;
        if (hCount % 2 == 0) {
            yMin += unitBlockSize / 2;
        }
        float zMin = -(dCount / 2) * unitBlockSize;
        if (dCount % 2 == 0) {
            zMin += unitBlockSize / 2;
        }

        Vector3f c = new Vector3f(xMin, 0f, 0f);
        for (int i = 0; i < wCount; ++i) {
            c.y = yMin;
            for (int j = 0; j < hCount; ++j) {
                c.z = zMin;
                for (int k = 0; k < dCount; ++k) {
                    Spatial unitBlock = makeBlock(
                            name + "(" + i + ", " + j + ")", 
                            unitBlockSize, unitBlockSize, unitBlockSize, color);
                    unitBlock.setLocalTranslation(c);
                    bigBlock.attachChild(unitBlock);
                    c.z += unitBlockSize;
                }
                c.y += unitBlockSize;
            }
            c.x += unitBlockSize;
        }
        
//        return GeometryBatchFactory.optimize(bigBlock);
        return bigBlock;
    }
    
    public Spatial makeBoxContainer(String name, float w, float h, float d, 
            float thickness, ColorRGBA color) {        
        Node boxContainer = new Node(name);
        float halfW = w / 2;
        float halfH = h / 2;
        float halfD = d / 2;
        float halfT = thickness / 2;
        
        Spatial s = makeBlock(name + "(floor)", 
                w - thickness * 2f,
                thickness, 
                d - thickness * 2f,
                color);
        s.setLocalTranslation(0f, halfT - halfH, 0f);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(left)", thickness, h, d - thickness * 2, color);
        s.setLocalTranslation(halfT - halfW, 0, 0);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(right)", thickness, h, d - thickness * 2, color);
        s.setLocalTranslation(-halfT + halfW, 0, 0);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(near)", w, h, thickness, color);
        s.setLocalTranslation(0, 0, halfT - halfD);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(far)", w, h, thickness, color);
        s.setLocalTranslation(0, 0, -halfT + halfD);
        boxContainer.attachChild(s);
        
        return boxContainer;
    }
    
    public Geometry makeCylinder(String name, float radius, float height, 
            ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", color.mult(0.8f));      
        mat.setColor("Diffuse", color);
        mat.setColor("Specular", ColorRGBA.White);
//        mat.setBoolean("HighQuality", false);
        Geometry cylinder = new Geometry(name, new Cylinder(32, 32, radius, height, true));
        cylinder.setMaterial(mat);
        return cylinder;        
    }

    public Geometry makeSphere(String name, float radius, ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", color.mult(0.8f));      
        mat.setColor("Diffuse", color);
        mat.setColor("Specular", ColorRGBA.White);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        mat.setBoolean("UseAlpha", true);
//        mat.setBoolean("HighQuality", false);
        Geometry sphere = new Geometry(name, new Sphere(16, 16, radius));
        sphere.setMaterial(mat);
        return sphere;
    }

    public Geometry makeUnshadedPlane(String name, float width, float height, ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);      
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
//        mat.setBoolean("UseAlpha", true);
//        mat.setBoolean("HighQuality", false);
        Geometry plane = new Geometry(name, new Quad(width, height));
        plane.setMaterial(mat);
        return plane;
    }
    
    public Geometry makeUnshadedLine(String name, Vector3f start, Vector3f end, ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);      
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry line = new Geometry(name, new Line(start, end));
        line.setMaterial(mat);
        return line;
    }

    public Geometry makeUnshadedArrow(String name, Vector3f extend, float lineWidth, ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);      
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Arrow arrow = new Arrow(extend);
        arrow.setLineWidth(lineWidth);
        Geometry line = new Geometry(name, arrow);
        line.setMaterial(mat);
        return line;
    }
}
