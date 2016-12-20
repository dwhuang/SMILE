package edu.umd.smile.util;

import java.util.HashMap;

import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

public class MySixDofJoint extends SixDofJoint implements MyJoint {
    protected Matrix3f rotA;
    protected Matrix3f rotB;
    protected Vector3f angularLowerLimit = new Vector3f();
    protected Vector3f angularUpperLimit = new Vector3f();
    protected Vector3f linearLowerLimit = new Vector3f();
    protected Vector3f linearUpperLimit = new Vector3f();

    public MySixDofJoint(PhysicsRigidBody nodeA, PhysicsRigidBody nodeB, Vector3f pivotA, Vector3f pivotB, Matrix3f rotA, Matrix3f rotB, boolean useLinearReferenceFrameA) {
        super(nodeA, nodeB, pivotA, pivotB, rotA, rotB, useLinearReferenceFrameA);
        this.rotA = rotA;
        this.rotB = rotB;
    }
    
    public Type getType() {
        return Type.SixDof;
    }
    
    public Matrix3f getRotA() {
        return rotA;
    }
    
    public Matrix3f getRotB() {
        return rotB;
    }
    
    @Override
    public void setLinearUpperLimit(Vector3f vector) {
        super.setLinearUpperLimit(vector);
        linearUpperLimit.set(vector);
    }

    @Override
    public void setLinearLowerLimit(Vector3f vector) {
        super.setLinearLowerLimit(vector);
        linearLowerLimit.set(vector);
    }

    @Override
    public void setAngularUpperLimit(Vector3f vector) {
        super.setAngularUpperLimit(vector);
        angularUpperLimit.set(vector);
    }

    @Override
    public void setAngularLowerLimit(Vector3f vector) {
        super.setAngularLowerLimit(vector);
        angularLowerLimit.set(vector);
    }

    /**
     * Used by the memento for the undo function
     * @param p
     */
    public void saveParam(HashMap<String, Object> p) {
        p.put("collisionBetweenLinkedBodys", collisionBetweenLinkedBodys);
        p.put("lowerAngLimit", angularLowerLimit);
        p.put("lowerLinLimit", linearLowerLimit);
        p.put("upperAngLimit", angularUpperLimit);
        p.put("upperLinLimit", linearUpperLimit);
    }

    public void loadParam(HashMap<String, Object> p) {
        collisionBetweenLinkedBodys = (Boolean) p.get("collisionBetweenLinkedBodys");
        setAngularLowerLimit((Vector3f) p.get("lowerAngLimit"));
        setLinearLowerLimit((Vector3f) p.get("lowerLinLimit"));
        setAngularUpperLimit((Vector3f) p.get("upperAngLimit"));
        setLinearUpperLimit((Vector3f) p.get("upperLinLimit"));
    }

    public String toParamString() {
        StringBuffer buf = new StringBuffer();
        buf.append("collisionBetweenLinkedBodys" + ": " + collisionBetweenLinkedBodys + "\n");
        buf.append("lowerAngLimit" + ": " + angularLowerLimit + "\n");
        buf.append("lowerLinLimit" + ": " + linearLowerLimit + "\n");
        buf.append("upperAngLimit" + ": " + angularUpperLimit + "\n");
        buf.append("upperLinLimit" + ": " + linearUpperLimit + "\n");
        return buf.toString();
    }
}
