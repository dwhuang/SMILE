/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;

/**
 *
 * @author dwhuang
 */
public class AmbientColorEnhancer implements SceneGraphVisitor {
    float ambientDiffuseRatio;
    
    public AmbientColorEnhancer(float ambientDiffuseRatio) {
        this.ambientDiffuseRatio = ambientDiffuseRatio;
    }
    
    public void visit(Spatial spatial) {
        if (spatial instanceof Geometry) {
            Geometry g = (Geometry) spatial;
            Material mat = g.getMaterial();
//            Collection<MatParam> params = mat.getParams();
//            System.err.println(g.getName());
//            for (MatParam p : params) {
//                System.err.println("  " + p.getName() + " " + p.getValueAsString());
//            }
            
            MatParam p = mat.getParam("Diffuse");
            if (p != null) {
                ColorRGBA color = ((ColorRGBA) p.getValue()).clone();
                color.addLocal(ColorRGBA.White.mult(0.1f));
                color.multLocal(ambientDiffuseRatio);
                mat.setColor("Ambient", color);
            }
            
        }
    }
    
}
