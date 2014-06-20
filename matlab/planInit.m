show = false;

load('plan-3blocks.mat')
aux.initJointAngles = state0.jointAngles;

ops = State.getOps();

% vis = Vision.makeDefault();

state = state0;
p = 1;
timePassed = 0;
graspTime = 0;

profile on