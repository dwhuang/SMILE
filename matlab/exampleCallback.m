% This script is called every time when the simulated environment is rendering a
% frame. The default refresh rate is 60Hz, whcih means this script is
% called approximately every 1/60 seconds. Depending on your hardware
% capabilities, the refresh rate may be lower than 60Hz. The variable 
% sensor.timeEllapsed (see below) contains a measurement of the duration 
% since the last time this script was invokded.

%% NOTE Calling a Matlab script from the simulated environment is blocking
% (as in blocking I/O), meaning the simulated environment will always wait for
% the matlab script to finish before further execution (e.g., refreshing the
% scree). Therefore it is important to keep this script fast. 
% A sluggish script will impact the screen refresh rate. If the refresh rate 
% drops below ~12 fps (frames per second), noticeable visual latency and funny 
% physics effects may appear. 


%% sensor
% The variable 'sensor' transmits sensory information from the simulated 
% robot to this matlab agent. This variable is read-only, meaning any
% changes to it will be ignored. 
sensor

% Time elapsed in seconds since last call to this script (~1/60)
sensor.timeElapsed

% Current joint angles: a 'aux.numLimbs'-by-'aux.numJoints' matrix (in
% radius)
%   row index: limb (left, right)
%   col index: joint (indexed from the shoulder down)
sensor.jointAngles

% Current gripper opening: an array of length 'aux.numLimbs'. Each value
% is the distance of the opening between the two gripper fingers.
sensor.gripperOpening

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

% A boolean value indicating if the current visual image is a part of a
% demonstration. If the visual image is not available in this frame, this
% value will be false.
sensor.demoCue


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
%
% Gripper velocities: an array of length 'aux.numLimbs' specifying the
% intended velocities for the grippers. Positive values increase the
% gripper opening; negative values decrease it. The actual gripper velocity
% may be reduced if the gripper is interacting with other objects.
%
% Example below: invert all joint and gripper velocities every 5 seconds
duration = duration + sensor.timeElapsed;
if duration > 5
    rotationDir = -rotationDir;
    motor.jointVelocities(:) = 0.5 * rotationDir;
    motor.gripperVelocities(:) = rotationDir;
    duration = 0;
end

motor.jointVelocities    
motor.gripperVelocities