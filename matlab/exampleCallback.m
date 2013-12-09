% This script is called every time the simulated environment is rendering a
% frame. The default refresh rate is 60Hz, whcih means this script is
% called approximately every 1/60 seconds.

%% sensor
% The variable 'sensor' transmits sensory information from the simulated 
% robot to this matlab agent. This variable is read-only, meaning any
% changes to it will be ignored. 

% Time elapsed in seconds since last call to this script (~1/60)
sensor.timeElapsed

% Current joint angles: a 'aux.numLimbs'-by-'aux.numJoints' matrix (in
% radius)
%   row index: limb (left, right)
%   col index: joint (indexed from the shoulder down)
sensor.jointAngles

% End effector positions: a 'aux.numLimbs'-by-3 matrix
%   row index: limb (left, right)
%   col index: x, y, z (XY: horizontal plane; Z: vertical)
sensor.endEffPos

% RGB visual image taken from the head-mounted camera of the robot: 
% a WIDTH-by-HEIGHT-by-3 matrix (3-dimensional)
% The third index specifies red, green, and blue intensities. Intensities
% are between 0 and 1.
% Note that visual image may not be available every time this script is
% called, because doing so may slow down the simulation significantly.
% Therefore, users must check the existence of sensor.rgbVision before
% accessing it.
if any(strcmp('rgbVision', fieldnames(sensor)))
    image(sensor.rgbVision); % draw the image whenever it is available
end

%% motor
% The variable 'motor' transmits motor commands from this matlab agent back
% to the simulated robot. This variable will be read by the simulated
% environment and therefore must be in the expected format. A template
% 'motor' variable is created for you at the first time this script is 
% called. It is recommended to make modifications to this existing 'motor'
% variable rather than creating a new copy. Ill-formatted 'motor' variable
% can cause this matlab agent to be terminated abruptly.

% Joint velocities: a 'aux.numLimbs'-by-'aux.numJoints' matrix (in radius
% per second)
%   row index: limb (left, right)
%   col index: joint (indexed from the shoulder down).
% Example below: change all joint velocities every 5 seconds
exampleVariable = exampleVariable + sensor.timeElapsed;
if exampleVariable > 5
    exampleVariable = 0;
    if motor.jointVelocities(1, 1) <= 0
        motor.jointVelocities(:) = 0.5;
    else
        motor.jointVelocities(:) = -0.5;
    end
end
    
