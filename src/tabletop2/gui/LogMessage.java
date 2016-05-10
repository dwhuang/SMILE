package tabletop2.gui;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

public class LogMessage {
    public static void showDialog(String msg, int dialogWidth) {
        JOptionPane.showMessageDialog(null, "<html><body width='" + dialogWidth + "'>" + msg + "</body></html>");
    }
    
    public static void showDialog(String msg) {
        showDialog(msg, 400);
    }
    
    public static void warn(String msg, Logger logger, Throwable e) {
        if (e != null) {
            logger.log(Level.WARNING, msg, e);
            showDialog(msg + ": " + e);
        } else {
            logger.log(Level.WARNING, msg);
            showDialog(msg);
        }
    }

    public static void warn(String msg, Logger logger) {
        warn(msg, logger, null);
    }
}
