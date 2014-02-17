/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.post.SceneProcessor;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.debug.WireSphere;
import com.jme3.texture.FrameBuffer;
import java.util.ArrayList;

/**
 *
 * @author dwhuang
 */
public class DemonstratorSceneProcessor implements SceneProcessor {
    protected AssetManager assetManager;
    
    protected boolean isInit = false;
    protected Node movingPlane;
    protected boolean showMovingPlane = false;
    protected ArrayList<Geometry> movingPlaneGeos = new ArrayList<Geometry>();
    // highlighting objects
    protected Spatial hlOriginal = null;
    protected Spatial hlClone = null;
    protected ArrayList<Geometry> hlGeos = new ArrayList<Geometry>();
    protected Material hlMaterial;
    
    private Matrix4f mat = new Matrix4f();
    
    public DemonstratorSceneProcessor(AssetManager assetManager, Node movingPlane) {
        this.assetManager = assetManager;
        this.movingPlane = movingPlane;
        movingPlane.depthFirstTraversal(new SceneGraphVisitor() {
            public void visit(Spatial s) {
                if (s instanceof Geometry) {
                    movingPlaneGeos.add((Geometry)s);
                }
            }
        });
        
        hlMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
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
        if (showMovingPlane) {
            for (Geometry g : movingPlaneGeos) {
                rq.addToQueue(g, RenderQueue.Bucket.Translucent);
            }
        }
    }

    public void postFrame(FrameBuffer out) {
    }

    public void cleanup() {
    }
    
    public void setShowMovingPlane(boolean enabled) {
        showMovingPlane = enabled;
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
                            wireframe = new WireBox();
                            ((WireBox)wireframe).fromBoundingBox((BoundingBox)vol);
                        } else {
                            wireframe = new WireSphere();
                            ((WireSphere)wireframe).fromBoundingSphere((BoundingSphere)vol);
                        }
                        wireframe.setLineWidth(3);                        
                        g.setMesh(wireframe);
                        g.getMesh().setLineWidth(2);
                        g.setLocalScale(1.01f);
                        g.setMaterial(hlMaterial);
                        hlGeos.add(g);
                    }
                }
            });
        } else {
            hlClone = null;
            hlGeos.clear();
        }
    }
}
