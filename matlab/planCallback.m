timePassed = timePassed + sensor.timeElapsed;
dt = timePassed/p;

if p < numel(plan)
    loadOp = true;
    
    if strcmp(plan(p).name, 'stepArms')
        motor.jointVelocities = (plan(p).args{1} - sensor.jointAngles)/dt;
% this line (below) may set a positive gripper velocity after something is
% grasped, which causes the object to be released
%         motor.gripperVelocities = (plan(p).args{2} - sensor.gripperOpening')/dt;
    elseif strcmp(plan(p).name, 'grasp') || strcmp(plan(p).name, 'release')
        if strcmp(plan(p).name, 'grasp')
            v = -3;
        else
            v = 3;
        end
        
        if strcmp(plan(p).args{1}, 'L')
            arm = 1;
        else
            arm = 2;
        end
        
        motor.gripperVelocities(arm) = v;
        graspTime = graspTime + sensor.timeElapsed;
        if graspTime < 2
            loadOp = false;
        else
            graspTime = 0;
            motor.gripperVelocities(arm) = 0;
        end
    end
    
    if loadOp
        op = ops(plan(p).name);
        next = op(state,plan(p).args{:});
        if islogical(next), break; end;
        state = next;
        disp(p);
        p = p + 1;
    end
    
else
    motor.jointVelocities(:) = 0;
    motor.gripperVelocities(:) = 0;
    aux.exit = true;
end