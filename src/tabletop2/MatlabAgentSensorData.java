/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

import com.jme3.math.Vector3f;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;

/**
 *
 * @author dwhuang
 */
public class MatlabAgentSensorData implements Serializable {

    private static final long serialVersionUID = 1L;

    private double timeElapsed;
    private double[][] jointAngles = new double[2][Robot.DOF];
    private double[] gripperOpening = new double[2];
    private double[][] endEffPos = new double[2][3];
    private double[] rgbVision = new double[Robot.HEAD_CAM_RES_HEIGHT * Robot.HEAD_CAM_RES_WIDTH * 3];
    private boolean demoCue;
    
    private transient int[] rgbVisionBuffer = new int[Robot.HEAD_CAM_RES_WIDTH * Robot.HEAD_CAM_RES_HEIGHT];
    private transient boolean rgbVisionReady = false;
            
    public void populate(float tpf, final JointState[] leftJoints, final JointState[] rightJoints,
            double leftGripperOpening, double rightGripperOpening,
            final Vector3f leftEndEffPos, final Vector3f rightEndEffPos,
            final BufferedImage vision, boolean demoCue) {
        timeElapsed = tpf;
        if (leftJoints.length != jointAngles[0].length) {
            throw new IllegalArgumentException("left joint counts mismatch " 
                    + leftJoints.length + " " + jointAngles[0].length);
        }
        for (int i = 0; i < leftJoints.length; ++i) {
            jointAngles[0][i] = leftJoints[i].getAngle();
        }
        if (rightJoints.length != jointAngles[1].length) {
            throw new IllegalArgumentException("right joint counts mismatch "
                    + rightJoints.length + " " + jointAngles[1].length);
        }
        for (int i = 0; i < rightJoints.length; ++i) {
            jointAngles[1][i] = rightJoints[i].getAngle();
        }
        
        gripperOpening[0] = leftGripperOpening;
        gripperOpening[1] = rightGripperOpening;
        
        // convert to a more intuitive coordinate system:
        // x, y parallel to the table surface, z vertical
        endEffPos[0][0] = leftEndEffPos.x;
        endEffPos[0][1] = -leftEndEffPos.z;
        endEffPos[0][2] = leftEndEffPos.y;
        endEffPos[1][0] = rightEndEffPos.x;
        endEffPos[1][1] = -rightEndEffPos.z;
        endEffPos[1][2] = rightEndEffPos.y;

        // convert image to matlab rgb array
        if (vision != null) {
            if (vision.getWidth() != Robot.HEAD_CAM_RES_WIDTH 
                    || vision.getHeight() != Robot.HEAD_CAM_RES_HEIGHT
                    || vision.getType() != ImageCapturer.IMAGE_TYPE) {
                throw new IllegalArgumentException("vision format not supported");
            }
            vision.getRGB(0, 0, vision.getWidth(), vision.getHeight(), 
                    rgbVisionBuffer, 0, vision.getWidth(null));

            int i = 0;
            for (int color = 0; color < 3; ++color) {
                for (int col = 0; col < Robot.HEAD_CAM_RES_WIDTH; ++col) {
                    for (int row = 0; row < Robot.HEAD_CAM_RES_HEIGHT; ++row) {
                        int intensity = (rgbVisionBuffer[row * Robot.HEAD_CAM_RES_WIDTH + col] 
                                >> ((2 - color) * 8)) & 0xff;
                        rgbVision[i++] = intensity / 255.0;
                    }
                }
            }
            rgbVisionReady = true;
            this.demoCue = demoCue;
        } else {
            rgbVisionReady = false;
            this.demoCue = false;
        }
    }

    public void sendToMatlab(MatlabProxy matlab) throws MatlabInvocationException {
        StringBuilder buf = new StringBuilder();
        buf.append("sensor.timeElapsed = ").append(timeElapsed).append(";");
        buf.append("sensor.jointAngles = [");
        for (int i = 0; i < jointAngles.length; ++i) {
            for (int j = 0; j < jointAngles[0].length; ++j) {
                buf.append(jointAngles[i][j]).append(" ");
            }
            buf.append(";");
        }
        buf.append("];");

        buf.append("sensor.endEffPos = [");
        for (int i = 0; i < endEffPos.length; ++i) {
            for (int j = 0; j < endEffPos[0].length; ++j) {
                buf.append(endEffPos[i][j]).append(" ");
            }
            buf.append(";");
        }
        buf.append("];");        
        matlab.eval(buf.toString());

        matlab.eval("sensor.gripperOpening = [" + gripperOpening[0] + ", " + gripperOpening[1] + "];");
        
        if (rgbVisionReady) {
            Object[] ret = matlab.returningEval("genvarname('rgbVision', who)", 1);
            String varName = (String) ret[0];
            matlab.setVariable(varName, rgbVision);
            matlab.eval("sensor.rgbVision = reshape(" + varName + ", " + Robot.HEAD_CAM_RES_HEIGHT
                    + ", " + Robot.HEAD_CAM_RES_WIDTH + ", 3);");
            matlab.eval("clear " + varName);
        }
        
        matlab.eval("sensor.demoCue = " + demoCue + ";");
    }
}
