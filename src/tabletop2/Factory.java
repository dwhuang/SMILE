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
        Geometry block = new Geometry(name, new Box(w / 2, h / 2, d / 2));
        block.setMaterial(mat);

        block.setUserData("obj_shape", "block");
        block.setUserData("obj_width", w);
        block.setUserData("obj_height", h);
        block.setUserData("obj_depth", d);
        block.setUserData("obj_color", color);
        
        return block;
    }
    
    public Geometry makeCustom(String name, String file, float w, float h, float d, ColorRGBA color) {
    
    	//TODO add functionality here
    	
    	//temporarily using makeBlock code to validate my design
    	
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Ambient", color.mult(0.8f));      
        mat.setColor("Diffuse", color);
        mat.setColor("Specular", ColorRGBA.White);
//        mat.setBoolean("HighQuality", false);
        Geometry block = new Geometry(name, new Box(w / 2, h / 2, d / 2));
        block.setMaterial(mat);

        block.setUserData("obj_shape", "block");
        block.setUserData("obj_width", w);
        block.setUserData("obj_height", h);
        block.setUserData("obj_depth", d);
        block.setUserData("obj_color", color);
        
    	return block;
    }
    
    public Node makeBigBlock(String name, float w, float h, float d, 
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
        
        bigBlock.setUserData("obj_shape", "bigblock");
        bigBlock.setUserData("obj_width", w);
        bigBlock.setUserData("obj_height", h);
        bigBlock.setUserData("obj_depth", d);
        bigBlock.setUserData("obj_color", color);
        bigBlock.setUserData("obj_unitBlockSize", unitBlockSize); 
        
//        return GeometryBatchFactory.optimize(bigBlock);
        return bigBlock;
    }
    
    public Node makeBoxContainer(String name, float w, float h, float d, 
            float thickness, ColorRGBA color) {        
        return makeBoxContainer(name, w, h, d, thickness, thickness, thickness, color);
    }
    
    public Node makeBoxContainer(String name, float w, float h, float d, 
            float xThickness, float yThickness, float zThickness, ColorRGBA color) {        
        Node boxContainer = new Node(name);
        float halfW = w / 2;
        float halfH = h / 2;
        float halfD = d / 2;
        float halfXT = xThickness / 2;
        float halfYT = yThickness / 2;
        float halfZT = zThickness / 2;
        
        Spatial s = makeBlock(name + "(floor)", w, yThickness, d, color);
        s.setLocalTranslation(0f, halfYT - halfH, 0f);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(left)", xThickness, h - yThickness, d - zThickness * 2, color);
        s.setLocalTranslation(halfXT - halfW, halfYT, 0);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(right)", xThickness, h - yThickness, d - zThickness * 2, color);
        s.setLocalTranslation(-halfXT + halfW, halfYT, 0);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(near)", w, h - yThickness, zThickness, color);
        s.setLocalTranslation(0, halfYT, halfZT - halfD);
        boxContainer.attachChild(s);
        
        s = makeBlock(name + "(far)", w, h - yThickness, zThickness, color);
        s.setLocalTranslation(0, halfYT, -halfZT + halfD);
        boxContainer.attachChild(s);
        
        boxContainer.setUserData("obj_shape", "box");
        boxContainer.setUserData("obj_width", w);
        boxContainer.setUserData("obj_height", h);
        boxContainer.setUserData("obj_depth", d);
        boxContainer.setUserData("obj_color", color);
        boxContainer.setUserData("obj_xthickness", xThickness); 
        boxContainer.setUserData("obj_ythickness", zThickness); 
        boxContainer.setUserData("obj_zthickness", yThickness); 

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

        cylinder.setUserData("obj_shape", "cylinder");
        cylinder.setUserData("obj_radius", radius);
        cylinder.setUserData("obj_height", height);
        cylinder.setUserData("obj_color", color);

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

        sphere.setUserData("obj_shape", "cylinder");
        sphere.setUserData("obj_radius", radius);
        sphere.setUserData("obj_color", color);

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

        plane.setUserData("obj_shape", "plane");
        plane.setUserData("obj_width", width);
        plane.setUserData("obj_height", height);
        plane.setUserData("obj_color", color);

        return plane;
    }
    
    public Geometry makeUnshadedLine(String name, Vector3f start, Vector3f end, ColorRGBA color) {
        Material mat = new Material(assetManager, 
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);      
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry line = new Geometry(name, new Line(start, end));
        line.setMaterial(mat);

        line.setUserData("obj_shape", "line");
        line.setUserData("obj_start", start);
        line.setUserData("obj_end", end);
        line.setUserData("obj_color", color);

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

        line.setUserData("obj_shape", "arrow");
        line.setUserData("obj_extend", extend);
        line.setUserData("obj_lineWidth", lineWidth);
        line.setUserData("obj_color", color);

        return line;
    }
    
    public Node makeUnshadedAxisArrows(String name, float length, float lineWidth, boolean isInternalCoordsSystem) {
    	Node base = new Node(name);
    	Geometry g = makeUnshadedArrow(name + "-x", Vector3f.UNIT_X.mult(length), lineWidth, ColorRGBA.Red);
    	base.attachChild(g);
    	if (isInternalCoordsSystem) {
    		g = makeUnshadedArrow(name + "-y", Vector3f.UNIT_Y.mult(length), lineWidth, ColorRGBA.Green);
    	} else {
    		g = makeUnshadedArrow(name + "-y", Vector3f.UNIT_Z.mult(-length), lineWidth, ColorRGBA.Green);
    	}
    	base.attachChild(g);
    	if (isInternalCoordsSystem) {
        	g = makeUnshadedArrow(name + "-z", Vector3f.UNIT_Z.mult(length), lineWidth, ColorRGBA.Blue);
    	} else {
    		g = makeUnshadedArrow(name + "-z", Vector3f.UNIT_Y.mult(length), lineWidth, ColorRGBA.Blue);
    	}
    	base.attachChild(g);
    	
    	base.setUserData("obj_shape", "axisArrows");
    	base.setUserData("obj_length", length);
    	base.setUserData("obj_lineWidth", lineWidth);
    	base.setUserData("obj_isInternalCoordsSystem", isInternalCoordsSystem);
    	
    	return base;
    }
}
