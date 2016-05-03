package tabletop2.util;

import java.util.HashMap;

import com.jme3.bullet.joints.SliderJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

public class MySliderJoint extends SliderJoint {
	public MySliderJoint(PhysicsRigidBody nodeA, PhysicsRigidBody nodeB, Vector3f pivotA, Vector3f pivotB, Matrix3f rotA, Matrix3f rotB, boolean useLinearReferenceFrameA) {
		super(nodeA, nodeB, pivotA, pivotB, rotA, rotB, useLinearReferenceFrameA);
	}
//	public Matrix3f getRotA() {
//		return rotA;
//	}
//	public Matrix3f getRotB() {
//		return rotB;
//	}
	
	/**
	 * Used by the memento for the undo function
	 * @param p
	 */
	public void saveParam(HashMap<String, Object> p) {
		p.clear();
		p.put("rotA", rotA);
		p.put("rotB", rotB);
		p.put("collisionBetweenLinkedBodys", collisionBetweenLinkedBodys);
		p.put("lowerAngLimit", getLowerAngLimit());
		p.put("lowerLinLimit", getLowerLinLimit());
		p.put("upperAngLimit", getUpperAngLimit());
		p.put("upperLinLimit", getUpperLinLimit());

		p.put("dampingDirAng", getDampingDirAng());
		p.put("dampingDirLin", getDampingDirLin());
		p.put("dampingLimAng", getDampingLimAng());
		p.put("dampingLimLin", getDampingLimLin());
		p.put("dampingOrthoAng", getDampingOrthoAng());
		p.put("dampingOrthoLin", getDampingOrthoLin());
		p.put("restitutionDirAng", getRestitutionDirAng());
		p.put("restitutionDirLin", getRestitutionDirLin());
		p.put("restitutionLimAng", getRestitutionLimAng());
		p.put("restitutionLimLin", getRestitutionLimLin());
		p.put("restitutionOrthoAng", getRestitutionOrthoAng());
		p.put("restitutionOrthoLin", getRestitutionOrthoLin());
		p.put("softnessDirAng", getSoftnessDirAng());
		p.put("softnessDirLin", getSoftnessDirLin());
		p.put("softnessLimAng", getSoftnessLimAng());
		p.put("softnessLimLin", getSoftnessLimLin());
		p.put("softnessOrthoAng", getSoftnessOrthoAng());
		p.put("softnessOrthoLin", getSoftnessOrthoLin());

		p.put("isPoweredAngMotor", isPoweredAngMotor());
		p.put("isPoweredLinMotor", isPoweredLinMotor());		
		p.put("maxAngMotorForce", getMaxAngMotorForce());
		p.put("maxLinMotorForce", getMaxLinMotorForce());
		p.put("targetAngMotorVelocity", getTargetAngMotorVelocity());
		p.put("targetLinMotorVelocity", getTargetLinMotorVelocity());
	}
	
	public void loadParam(HashMap<String, Object> p) {
		rotA = (Matrix3f) p.get("rotA");
		rotB = (Matrix3f) p.get("rotB");
		collisionBetweenLinkedBodys = (Boolean) p.get("collisionBetweenLinkedBodys");
		setLowerAngLimit((Float) p.get("lowerAngLimit"));
		setLowerLinLimit((Float) p.get("lowerLinLimit"));
		setUpperAngLimit((Float) p.get("upperAngLimit"));
		setUpperLinLimit((Float) p.get("upperLinLimit"));
		
		setDampingDirAng((Float) p.get("dampingDirAng"));
		setDampingDirLin((Float) p.get("dampingDirLin"));
		setDampingLimAng((Float) p.get("dampingLimAng"));
		setDampingLimLin((Float) p.get("dampingLimLin"));
		setDampingOrthoAng((Float) p.get("dampingOrthoAng"));
		setDampingOrthoLin((Float) p.get("dampingOrthoLin"));
		setRestitutionDirAng((Float) p.get("restitutionDirAng"));
		setRestitutionDirLin((Float) p.get("restitutionDirLin"));
		setRestitutionLimAng((Float) p.get("restitutionLimAng"));
		setRestitutionLimLin((Float) p.get("restitutionLimLin"));
		setRestitutionOrthoAng((Float) p.get("restitutionOrthoAng"));
		setRestitutionOrthoLin((Float) p.get("restitutionOrthoLin"));
		setSoftnessDirAng((Float) p.get("softnessDirAng"));
		setSoftnessDirLin((Float) p.get("softnessDirLin"));
		setSoftnessLimAng((Float) p.get("softnessLimAng"));
		setSoftnessLimLin((Float) p.get("softnessLimLin"));
		setSoftnessOrthoAng((Float) p.get("softnessOrthoAng"));
		setSoftnessOrthoLin((Float) p.get("softnessOrthoLin"));
		
		setPoweredAngMotor((Boolean) p.get("isPoweredAngMotor"));
		setPoweredLinMotor((Boolean) p.get("isPoweredLinMotor"));
		setMaxAngMotorForce((Float) p.get("maxAngMotorForce"));
		setMaxLinMotorForce((Float) p.get("maxLinMotorForce"));
		setTargetAngMotorVelocity((Float) p.get("targetAngMotorVelocity"));
		setTargetLinMotorVelocity((Float) p.get("targetLinMotorVelocity"));
	}
}
