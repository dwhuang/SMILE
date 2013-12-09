/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image.Format;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 *
 * @author dwhuang
 */
public class ImageCapturer {
    
    public static final int IMAGE_TYPE = BufferedImage.TYPE_4BYTE_ABGR;

    protected Camera cam;
    protected RenderManager renderManager;
    protected Node camNode;
    protected ViewPort vp;
    protected FrameBuffer fbuf;
    protected ByteBuffer bbuf;
    protected BufferedImage image;
    
    @SuppressWarnings("LeakingThisInConstructor")
    public ImageCapturer(Camera cam, RenderManager renderManager, Node camNode, Node rootNode) {
        this.cam = cam;
        this.renderManager = renderManager;
        this.camNode = camNode;

//        bbuf = BufferUtils.createByteBuffer(cam.getWidth() * cam.getHeight() * 4);
        bbuf = BufferUtils.createByteBuffer(cam.getWidth() * cam.getHeight() * 4);
        image = new BufferedImage(cam.getWidth(), cam.getHeight(), IMAGE_TYPE);

        vp = renderManager.createPreView(cam.getName() + " recorder", cam);
        vp.setBackgroundColor(ColorRGBA.Black);
        vp.setClearFlags(true, true, true);
        fbuf = new FrameBuffer(cam.getWidth(), cam.getHeight(), 1);
        fbuf.setDepthBuffer(Format.Depth);
        fbuf.setColorBuffer(Format.RGBA8);
        vp.setOutputFrameBuffer(fbuf);
        vp.attachScene(rootNode);                
    }
    
    public void syncCamera() {
        cam.setLocation(camNode.getWorldTranslation());
        cam.setRotation(camNode.getWorldRotation());
    }
    
    public BufferedImage takePicture() {
        bbuf.clear();
        renderManager.getRenderer().readFrameBuffer(fbuf, bbuf);
        Screenshots.convertScreenShot(bbuf, image);
        return image;
    }
}
