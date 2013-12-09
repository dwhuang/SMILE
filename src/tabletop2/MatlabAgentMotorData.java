/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tabletop2;

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
    
    protected void sendTemplateToMatlab(MatlabProxy matlab) throws MatlabInvocationException {
        matlab.setVariable("motor", this);
        matlab.eval("motor = struct(motor);");
    }
    
    protected void reset() {
        for (int i = 0; i < jointVelocities.length; ++i) {
            for (int j = 0; j < jointVelocities[0].length; ++j) {
                jointVelocities[i][j] = 0;
            }
        }
    }
    
    protected void recvFromMatlab(MatlabProxy matlab) throws MatlabInvocationException {
        Object[] ret = matlab.returningEval("class(motor);", 1);
        String type = (String) ret[0];
        if (!type.equals("struct")) {
            throw new RuntimeException("invalid 'motor': not a struct");
        }
        
        ret = matlab.returningEval("fieldnames(motor);", 1);
        String[] keys = (String[]) ret[0];
        for (String key : keys) {
            if (key.equals("jointVelocities")) {
                MatlabTypeConverter processor = new MatlabTypeConverter(matlab);
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
            } else {
                throw new RuntimeException("invalid 'motor': unrecognized field '" + key + "'");
            }
        }        
    }
    
    public void execute(JointState[] leftJoints, JointState[] rightJoints) {
        for (int i = 0; i < leftJoints.length; ++i) {
            leftJoints[i].setVelocity((float) jointVelocities[0][i], false);
        }
        for (int i = 0; i < rightJoints.length; ++i) {
            rightJoints[i].setVelocity((float) jointVelocities[1][i], false);
        }
    }
}
