/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.demonstration;

import java.util.ArrayList;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.texture.FrameBuffer;

import edu.umd.smile.MainApp;
import edu.umd.smile.object.Factory;
import edu.umd.smile.util.MyWireBox;
import edu.umd.smile.util.MyWireSphere;

/**
 *
 * @author dwhuang
 */
public class DemoSceneProcessor implements SceneProcessor {
	private Factory factory;
    private boolean isInit = false;
    private boolean showVisualAid = false;
    private ArrayList<Geometry> visualAidGeos = new ArrayList<Geometry>();
    // highlighting objects
    private Spatial hlOriginal = null;
    private Spatial hlClone = null;
    private ArrayList<Geometry> hlGeos = new ArrayList<Geometry>();
    private Material hlMaterial;
    
    private Matrix4f mat = new Matrix4f();
    
    public DemoSceneProcessor(MainApp app, Node visualAid) {
    	this.factory = app.getFactory();
        visualAid.depthFirstTraversal(new SceneGraphVisitor() {
            public void visit(Spatial s) {
                if (s instanceof Geometry) {
                    visualAidGeos.add((Geometry)s);
                }
            }
        });
        
        hlMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        hlMaterial.setColor("Color", ColorRGBA.Black);
    }
    
    public void initialize(RenderManager rm, ViewPort vp) {
        isInit = true;
    }

    public void reshape(ViewPort vp, int w, int h) {
    }

    public boolean isInitialized() {
        return isInit;
    }

    public void preFrame(float tpf) {
        if (hlOriginal != null) {
            hlOriginal.getLocalToWorldMatrix(mat);
            hlClone.setLocalTranslation(mat.toTranslationVector());
            hlClone.setLocalRotation(mat.toRotationMatrix());
            hlClone.updateGeometricState();
        }
    }

    public void postQueue(RenderQueue rq) {
        if (hlOriginal != null) {
            for (Geometry g : hlGeos) {
                rq.addToQueue(g, RenderQueue.Bucket.Translucent);
            }
        }
        if (showVisualAid) {
            for (Geometry g : visualAidGeos) {
                rq.addToQueue(g, RenderQueue.Bucket.Translucent);
            }
        }
    }

    public void postFrame(FrameBuffer out) {
    }

    public void cleanup() {
    }
    
    public void setShowVisualAid(boolean enabled) {
        showVisualAid = enabled;
    }
    
    public void highlightSpatial(Spatial s) {
        hlOriginal = s;
        if (s != null) {
            hlClone = hlOriginal.deepClone();
            hlClone.depthFirstTraversal(new SceneGraphVisitor() {
                public void visit(Spatial s) {
                    if (s instanceof Geometry) {
                        Geometry g = (Geometry)s;
                        BoundingVolume vol = g.getModelBound();
                        
                        Mesh wireframe;
                        if (vol.getType() == BoundingVolume.Type.AABB) {
                            wireframe = new MyWireBox();
                            ((MyWireBox)wireframe).fromBoundingBox((BoundingBox)vol);
                        } else {
                            wireframe = new MyWireSphere();
                            ((MyWireSphere)wireframe).fromBoundingSphere((BoundingSphere)vol);
                        }
                        g.setMesh(wireframe);
                        g.getMesh().setLineWidth(2);
                        g.setLocalScale(g.getLocalScale().mult(1.01f));
                        g.setMaterial(hlMaterial);
                        hlGeos.add(g);
                    } else if (s instanceof Node) {
                        if (s.getUserData("assembly") != null) {
                        	Geometry g = factory.makeUnshadedArrow("viz", Vector3f.UNIT_Z, 1, ColorRGBA.Yellow);
                        	((Node)s).attachChild(g);
                        	hlGeos.add(g);
                        }
                    }
                }
            });
        } else {
            hlClone = null;
            hlGeos.clear();
        }
    }
}
