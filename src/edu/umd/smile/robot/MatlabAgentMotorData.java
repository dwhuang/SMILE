/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.robot;

import java.io.Serializable;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;
import matlabcontrol.extensions.MatlabNumericArray;
import matlabcontrol.extensions.MatlabTypeConverter;

/**
 *
 * @author dwhuang
 */
public class MatlabAgentMotorData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public double[][] jointVelocities = new double[2][Robot.DOF];
    public double[] gripperVelocities = new double[2];
    
    private double[][] jointAngles = new double[2][Robot.DOF];    
    private boolean recvJointAngles = false;

    public void sendTemplateToMatlab(MatlabProxy matlab) throws MatlabInvocationException {
        reset();
        matlab.setVariable("motor", this);
        matlab.eval("motor = struct(motor);");
    }
    
    public void reset() {
        for (int i = 0; i < jointVelocities.length; ++i) {
            for (int j = 0; j < jointVelocities[0].length; ++j) {
                jointVelocities[i][j] = 0;
            }
        }
        for (int i = 0; i < gripperVelocities.length; ++i) {
            gripperVelocities[i] = 0;
        }
        recvJointAngles = false;
    }
    
    public void recvFromMatlab(MatlabProxy matlab) throws MatlabInvocationException {
        Object[] ret = matlab.returningEval("class(motor);", 1);
        String type = (String) ret[0];
        if (!type.equals("struct")) {
            throw new RuntimeException("invalid 'motor': not a struct");
        }
        
        ret = matlab.returningEval("fieldnames(motor);", 1);
        String[] keys = (String[]) ret[0];
        MatlabTypeConverter processor = new MatlabTypeConverter(matlab);
        recvJointAngles = false;
        for (String key : keys) {
            if (key.equals("jointVelocities")) {
                MatlabNumericArray matlabArray = processor.getNumericArray("motor." + key);
                int[] sizes = matlabArray.getLengths();
                if (sizes.length != 2) {
                    throw new RuntimeException("invalid 'motor': jointVelocities field does not contain a 2D array");
                }
                if (sizes[0] != jointVelocities.length) {
                    throw new RuntimeException("invalid 'motor': jointVelocities field does not contain " 
                            + jointVelocities.length + " rows");
                }
                if (sizes[1] != jointVelocities[0].length) {
                    throw new RuntimeException("invalid 'motor': jointVelocities field does not contain " 
                            + jointVelocities[0].length + " columns");
                }
                for (int i = 0; i < jointVelocities.length; ++i) {
                    for (int j = 0; j < jointVelocities[0].length; ++j) {
                        jointVelocities[i][j] = matlabArray.getRealValue(i, j);
                    }
                }
            } else if (key.equals("gripperVelocities")) {
                MatlabNumericArray matlabArray = processor.getNumericArray("motor." + key);
                int[] sizes = matlabArray.getLengths();
                if (sizes.length != 2) {
                    throw new RuntimeException("invalid 'motor': gripperVelocities field does not return a 2D array");
                }
                if (sizes[0] != gripperVelocities.length) {
                    throw new RuntimeException("invalid 'motor': gripperVelocities field does not contain " 
                            + gripperVelocities.length + " elements");
                }
                for (int i = 0; i < gripperVelocities.length; ++i) {
                    gripperVelocities[i] = matlabArray.getRealValue(i);
                }
            } else if (key.equals("jointAngles")) {
                MatlabNumericArray matlabArray = processor.getNumericArray("motor." + key);
                int[] sizes = matlabArray.getLengths();
                if (sizes.length != 2) {
                    throw new RuntimeException("invalid 'motor': jointAngles field does not contain a 2D array");
                }
                if (sizes[0] != jointAngles.length) {
                    throw new RuntimeException("invalid 'motor': jointAngles field does not contain " 
                            + jointAngles.length + " rows");
                }
                if (sizes[1] != jointAngles[0].length) {
                    throw new RuntimeException("invalid 'motor': jointAngles field does not contain " 
                            + jointAngles[0].length + " columns");
                }
                recvJointAngles = true;
                for (int i = 0; i < jointAngles.length; ++i) {
                    for (int j = 0; j < jointAngles[0].length; ++j) {
                        jointAngles[i][j] = matlabArray.getRealValue(i, j);
                    }
                }
            } else {
                throw new RuntimeException("invalid 'motor': unrecognized field '" + key + "'");
            }
        }        
    }
    
    public void execute(RobotJointState[] leftJoints, RobotJointState[] rightJoints,
            Gripper leftGripper, Gripper rightGripper) {
    	if (recvJointAngles) {
            for (int i = 0; i < leftJoints.length; ++i) {
                leftJoints[i].setAngle((float) jointAngles[0][i]);
            }
            for (int i = 0; i < rightJoints.length; ++i) {
                rightJoints[i].setAngle((float) jointAngles[1][i]);
            }
    	} else {
	        for (int i = 0; i < leftJoints.length; ++i) {
	            leftJoints[i].setVelocity((float) jointVelocities[0][i], false);
	        }
	        for (int i = 0; i < rightJoints.length; ++i) {
	            rightJoints[i].setVelocity((float) jointVelocities[1][i], false);
	        }
    	}
        leftGripper.setTargetVelocity((float) gripperVelocities[0]);
        rightGripper.setTargetVelocity((float) gripperVelocities[1]);
    }
}
