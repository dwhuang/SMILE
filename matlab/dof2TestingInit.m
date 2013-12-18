%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
%%%%%%%%%%%%%%%%% Performance %%%%%%%%%%%%%%%%%%  
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
fname = [aux.path, 'dof2.mat'];
load(fname);

aux.initJointAngles = zeros(aux.numLimbs, aux.numJoints);
aux.initJointAngles(2, 3) = pi / 2;

%% Simulation parameters
TotalDuration=2;                            % Define the total duration of the movement
Fs=100;                                     % Sampling frequency
Time_step=1/Fs;                             % Time step
Nb_samples = TotalDuration*Fs;              % Calculation of the total number of samples
Time = (0:Nb_samples - 1) / Fs;             % Calculation of time vector
G0=50;                                      % Gain of the GO signal 
Go = G0*(Time.^2)./(1 + Time.^2);           % Computation of Go signal as time series 

%% Initial condition and targets setting
% Theta = [0 0]*pi/180;

% Spatial position of the end-effector using the forward kinematics
% X_curr(1,1) = L1*cos(Theta(1)) + L2*cos(Theta(1)+Theta(2));     % X position of end-effector
% X_curr(2,1) = L1*sin(Theta(1)) + L2*sin(Theta(1)+Theta(2));     % Y position of end-effector
% X_TARGETS = [-4, 2; 2, -2; 5, 3.5; -2, -2]';
% X_TARGETS = [7, 2; 0, -3; -2, 5; 13, -10]';
% X_TARGETS = [7, 2; 0, -3; -2, 5; 10, -5]';
X_TARGETS = [7, 2; 0, -3; -2, 5; 6, -3]';
X_TARGET_INDEX = 0;

aux.drawMarkers = [X_TARGETS; ones(1, size(X_TARGETS, 2)) * 4]';

s = 0;
