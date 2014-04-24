% This script is called once every time the matlab agent is enabled

% Print aux structure
aux

% Set initial joint angles (optional)
%   row index: limb (left, right)
%   col index: joint (indexed from the shoulder down)
aux.initJointAngles = zeros(aux.numLimbs, aux.numJoints);
aux.initJointAngles(2, 3) = pi / 2;

% Draw visual markers in the simulated environment (optional)
% Markers are semi-transparent spheres that do not interact with the environment
% Specify one marker per row, which contain 3 columns that are x, y, and z.
% From the robot's perspective, (0, 0, 0) is at the center of the table.
% XY coordinates form a horizontal plane, where X increases from left to right, 
% and Y increases from near to far. Z increases vertically from low to high.
aux.drawMarkers = [-2, 2, 2; 2, 2, 2; 2, -2, 2; -2, -2, 2]; % draw 4 markers

% Load any necessary *.mat files here (optional)
% Any files accessed by user scripts should be stored in the 'matlab/' 
% subdirectory. Do so by prepending aux.path to any filenames appear in the
% user script.  For example:
fname = strcat(aux.path, 'myfile.mat')

% Initialize variables here (optional)
% Variables defined here are global, and thus accessible in the callback script
exampleVariable = 0;
duration = 5;
rotationDir = 1;

% Setting aux.exit will disable this matlab agent (optional)
% Example: aux.exit = true;
