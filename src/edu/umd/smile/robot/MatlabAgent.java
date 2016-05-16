/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.umd.smile.robot;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

import edu.umd.smile.object.Factory;

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import matlabcontrol.LoggingMatlabProxy;
import matlabcontrol.MatlabConnectionException;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;
import matlabcontrol.MatlabProxyFactory;
import matlabcontrol.MatlabProxyFactoryOptions;
import matlabcontrol.extensions.MatlabNumericArray;
import matlabcontrol.extensions.MatlabTypeConverter;

/**
 *
 * @author dwhuang
 */
public class MatlabAgent {
    private static final Logger logger = Logger.getLogger(MatlabAgent.class.getName());
    static {
        logger.setLevel(Level.SEVERE);
    }

    private static final boolean debug = false;
    
    private RobotJointState[] leftJoints;
    private RobotJointState[] rightJoints;
    private Gripper leftGripper;
    private Gripper rightGripper;
    private RobotLocTracker locTracker;
    private Node rootNode;
    private Factory factory;
    
    private Node markerNode = new Node();
    
    private LoggingMatlabProxy matlabWithLogging = null;
    private MatlabProxy matlab = null;
    private MatlabAgentSensorData sensorData = new MatlabAgentSensorData();
    private MatlabAgentMotorData motorData = new MatlabAgentMotorData();
    
    private MatlabTypeConverter processor;
    
    public MatlabAgent(RobotJointState[] leftJoints, RobotJointState[] rightJoints, 
            Gripper leftGripper, Gripper rightGripper, RobotLocTracker locTracker,
            Node rootNode, Factory factory) {
        this.leftJoints = leftJoints;
        this.rightJoints = rightJoints;
        this.leftGripper = leftGripper;
        this.rightGripper = rightGripper;
        this.locTracker = locTracker;
        this.rootNode = rootNode;
        this.factory = factory;
    }

    public void start() {        
        MatlabProxyFactoryOptions options = new MatlabProxyFactoryOptions.Builder()
                .setUsePreviouslyControlledSession(true).build();
        MatlabProxyFactory matlabFactory = new MatlabProxyFactory(options);
        try {
            matlab = matlabFactory.getProxy();
            if (debug) {
                matlabWithLogging = new LoggingMatlabProxy(matlabFactory.getProxy());
                LoggingMatlabProxy.showInConsoleHandler();
                matlab = matlabWithLogging;
            }
        
            this.processor = new MatlabTypeConverter(matlab);

            matlab.eval("clear java; clear import; clear all; close all; clc;");
            matlab.eval("addpath matlab");
            matlab.eval("[initFunc, callbackFunc] = agentBehavior;");
        } catch (MatlabConnectionException e) {
            logger.log(Level.SEVERE, "cannot connect matlab: {0}", e.getMessage());
            stop();
        } catch (MatlabInvocationException e) {
            logger.log(Level.SEVERE, "cannot call function agentBehavior: {0}", e.getMessage());
            stop();
        }

        if (matlab != null) {
            try {
                StringBuffer buf = new StringBuffer("aux = struct('path', 'matlab/', 'numLimbs', 2, 'numJoints', " 
                		+ Robot.DOF + ", 'minJointAngles', [");
                for (int i = 0; i < leftJoints.length; ++i) {
                	buf.append(leftJoints[i].getMinAngle());
                	if (i < leftJoints.length - 1) {
                		buf.append(", ");
                	}
                }
                buf.append("; ");
                for (int i = 0; i < rightJoints.length; ++i) {
                	buf.append(rightJoints[i].getMinAngle());
                	if (i < rightJoints.length - 1) {
                		buf.append(", ");
                	}
                }
                buf.append("], 'maxJointAngles', [");
                for (int i = 0; i < leftJoints.length; ++i) {
                	buf.append(leftJoints[i].getMaxAngle());
                	if (i < leftJoints.length - 1) {
                		buf.append(", ");
                	}
                }
                buf.append("; ");
                for (int i = 0; i < rightJoints.length; ++i) {
                	buf.append(rightJoints[i].getMaxAngle());
                	if (i < rightJoints.length - 1) {
                		buf.append(", ");
                	}
                }
                buf.append("]);");
                matlab.eval(buf.toString());
                matlab.eval("initFunc();");
                
                // draw spatial targets (markers) for arm reaching
                rootNode.attachChild(markerNode);                
                if (matlabStructVarExists("aux", "drawMarkers")) {
                    double[][] markers = matlabGetVarAsArray2D("aux.drawMarkers");
                    for (int i = 0; i < markers.length; ++i) {
                        if (markers[i].length != 3) {
                            throw new IllegalArgumentException("markers must have 3 components that are x, y, and z");
                        }
                        Geometry g = factory.makeSphere("", 1, ColorRGBA.Cyan.mult(0.5f));
                        g.setLocalTranslation((float) markers[i][0], (float) markers[i][2], (float) -markers[i][1]);
                        g.setQueueBucket(RenderQueue.Bucket.Translucent);
                        markerNode.attachChild(g);
                    }
                }
                
                // set initial joint states
                if (matlabStructVarExists("aux", "initJointAngles")) {
                    double[][] angles = matlabGetVarAsArray2D("aux.initJointAngles");
                    if (angles.length != 2) {
                        throw new IllegalArgumentException("initJointAngles must have 2 rows (for left and right arms)");
                    }
                    if (angles[0].length != Robot.DOF || angles[1].length != Robot.DOF) {
                        throw new IllegalArgumentException("initJointAngles must have " + Robot.DOF + " columns (number of joints per arm)");
                    }
                    for (int i = 0; i < leftJoints.length; ++i) {
                        leftJoints[i].setAngle((float) angles[0][i]);
                    }
                    for (int i = 0; i < leftJoints.length; ++i) {
                        rightJoints[i].setAngle((float) angles[1][i]);
                    }
                }                

                motorData.sendTemplateToMatlab(matlab); // so users don't have to fill out every field
                
            } catch (MatlabInvocationException e) {
                logger.log(Level.SEVERE, "cannot initialize agent: {0}", e.getMessage());
                stop();
            }
        }
    }
    
    private boolean matlabStructVarExists(String structName, String varName) 
            throws MatlabInvocationException {
        Object[] ret = matlab.returningEval("any(strcmp('" + varName + "', fieldnames(" + structName + ")))", 1);
        boolean exists = ((boolean[]) ret[0])[0];
        return exists;
    }

    private double[][] matlabGetVarAsArray2D(String varName) 
            throws MatlabInvocationException {
        MatlabNumericArray matlabArray = processor.getNumericArray(varName);
        double[][] var = matlabArray.getRealArray2D();
        return var;
    }

    public void stop() {
        if (matlab != null) {
            if (matlab.isConnected()) {
                cleanup();
                matlab.disconnect();
            }
            matlab = null;
        }
    }

    public boolean isAlive() {
        return matlab != null;
    }

    public void poll(float tpf, 
            final Vector3f leftEndEffPos, final Vector3f rightEndEffPos,
            final BufferedImage vision, boolean demoCue) {
        try {
            sensorData.populate(tpf, leftJoints, rightJoints,
                    leftGripper.getOpening(), rightGripper.getOpening(),
                    leftEndEffPos, rightEndEffPos, locTracker.getLocations(), vision, demoCue);
            sensorData.sendToMatlab(matlab);

            matlab.eval("callbackFunc();");
            
            motorData.recvFromMatlab(matlab);
            motorData.execute(leftJoints, rightJoints, leftGripper, rightGripper);
            
            if (matlabStructVarExists("aux", "exit")) {
                stop();
            }
        } catch (MatlabInvocationException ex) {
            logger.log(Level.SEVERE, null, ex);
            if (matlab.isConnected()) {
                matlab.disconnect();
            }
            matlab = null;
        }
    }
    
    private void cleanup() {
        motorData.reset();
        motorData.execute(leftJoints, rightJoints, leftGripper, rightGripper);        
        markerNode.detachAllChildren();
        if (markerNode.getParent() != null) {
            markerNode.getParent().detachChild(markerNode);
        }
    }
}
