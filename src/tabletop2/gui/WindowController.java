/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2.gui;

import com.jme3.app.Application;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.screen.Screen;

/**
 *
 * @author dwhuang
 */
public interface WindowController {

    // called by GuiController
    void bind(Nifty nifty, Screen screen);

    // called by GuiController
    void init(Application app);

    // called by GuiController
    void update(float tpf);

    Window getWindow();
}
