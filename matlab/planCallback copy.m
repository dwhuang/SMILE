timePassed = timePassed + sensor.timeElapsed;
dt = timePassed/p;

if p < numel(plan)
    loadNextOp = true;
    
    if strcmp(plan(p).name, 'stepArms')
        motor.jointVelocities = (plan(p).args{1} - sensor.jointAngles)/dt;
%         motor.gripperVelocities = (plan(p).args{2} - sensor.gripperOpening')/dt;
    else
        motor.jointVelocities(:) = 0;
%         motor.gripperVelocities(:) = 0;
    end
    if strcmp(plan(p).name,'grasp') && graspTime < 2
        graspTime = graspTime + sensor.timeElapsed;
    else
        graspTime = 0;

        op = ops(plan(p).name);
        next = op(state,plan(p).args{:});
        if islogical(next), break; end;
        state = next;
        disp(p);
%         if show
%             State.disp(state);
%             State.show(state,vis);
%             pause(0.01);
%         end
        p = p + 1;
        
    end
else
    motor.jointVelocities(:) = 0;
    motor.gripperVelocities(:) = 0;
end