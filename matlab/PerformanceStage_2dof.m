%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
%%%%%%%%%%%%%%%%% Performance %%%%%%%%%%%%%%%%%%  
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
clear all;close all;clc;
load Test8.mat;

%% Simulation parameters
TotalDuration=2;                            % Define the total duration of the movement
Fs=100;                                     % Sampling frequency
Time_step=1/Fs;                             % Time step
Nb_samples = TotalDuration*Fs;              % Calculation of the total number of samples
Time = (0:Nb_samples - 1) / Fs;             % Calculation of time vector
G0=50;                                      % Gain of the GO signal 
Go = G0*(Time.^2)./(1 + Time.^2);           % Computation of Go signal as time series 

%% Initial condition and targets setting
Theta = [0 0]*pi/180;

% Spatial position of the end-effector using the forward kinematics
X_curr(1,1) = L1*cos(Theta(1)) + L2*cos(Theta(1)+Theta(2));     % X position of end-effector
X_curr(2,1) = L1*sin(Theta(1)) + L2*sin(Theta(1)+Theta(2));     % Y position of end-effector
X_TARGET = [20 40]';  


%% Computation of the reaching movement
for s =1:Nb_samples      
    DX = (X_TARGET - X_curr);   % Compute the spatial increment between the end-effector and the target    
   
    % Configuracion actual del manipulador (Theta)
    Theta(1) = max(MinRangeLimit,min(MaxRangeLimit,Theta(1)));  % Limitation of the range of motion between 0 and 135 degree for Theta 1 (shoulder)
    Theta(2) = max(MinRangeLimit,min(MaxRangeLimit,Theta(2)));  % Limitation of the range of motion between 0 and 135 degree for Theta 2 (shoulder)

    Theta_rbf = ones(Nbf,1)*Theta;              % Theta_rbf is a copy of the joint angles to do matrix operations
    c = (Theta_rbf - Centers)./(Widths);        % Distance from the center (Gaussian) (See Guenther et al. 1997)
    g = exp(-0.5*(sum((c.^2)')'));              % Activation of the basis function (See Guenther et al. 1997)
    h = g/sum(g);                               % Normalization of the activation (See Guenther et al. 1997)

    % Computation of the coefficient (aij, from Guenther et al. 1997)
    IVM = zeros(jointDim,2); % Matrix including all the aij to compute the inverse mapping
    for k = 1:Nbf
        LLA = c(k,:)*Z(:,:,k);                                  % Computation of the linear local adaptation (most right member of the equation in Guenther et al. 1997)   
        LLAreshap(:,:,k)=reshape(LLA,jointDim,2);               % Reshape for computation
        a(:,:,k) = h(k) * (weights(:,:,k) + LLAreshap(:,:,k));  % Computation of the aij (element of the inverse transformation for each RBF, see Guenther et al. 1997)
        IVM = IVM + a(:,:,k);                                   % Computation of the aij for the tansformation (see Guenther et al. 1997)
    end

    % See Bullock et al. 1993; Guenther et al. 1997 
    DThe = IVM*DX;                      % Estimation of the small joint increment resulting from the IK                    
    DThe_go = Time_step*DThe*Go(s);     % Go signal to trigger the motion (see Bullock et al. 1993; Guenther et al. 1997)
    Theta = Theta + DThe_go';           % Integration of the small displacement
 
    % Spatial position of the end-effector using the forward kinematics
    X_curr(1,1) = L1*cos(Theta(1)) + L2*cos(Theta(1)+Theta(2));     % X position of end-effector
    X_curr(2,1) = L1*sin(Theta(1)) + L2*sin(Theta(1)+Theta(2));     % Y position of end-effector

    X_Disp(s) = X_curr(1,1); % Save the current spatial position in a vector to store the displacement on X
    Y_Disp(s) = X_curr(2,1); % Save the current spatial position in a vector to store the displacement on Y
    Ang_Disp(s,:)=Theta;
end

%% Plot of the figures

% Plot of the end-effector trajectory and the spatial location of the target 
figure(1);
plot(X_Disp,Y_Disp,'.');hold on;
plot(X_TARGET(1,1),X_TARGET(2,1),'r*','MarkerEdgeColor',[0 0 0],'MarkerFaceColor',[0 0 0],'MarkerSize',10), hold on,

% Display some snapshot of the entire arm position
for k=1:(Nb_samples/10)-2:Nb_samples
     The_Disp = Ang_Disp(k,:);
     X0 = [0; 0];                                                   % X and Y position of the shoulder
	 Xe  = L1*cos(The_Disp(1));                                     % X position of the elbow
     Ye  = L1*sin(The_Disp(1));                                     % Y position of the elbow   
     Xw  = L1*cos(The_Disp(1)) + L2*cos(The_Disp(1)+The_Disp(2));   % X position of the wrist
     Yw =  L1*sin(The_Disp(1)) + L2*sin(The_Disp(1)+The_Disp(2));   % Y position of the wrist               

     X = [X0(1); Xe; Xw]; 
     Y = [X0(2); Ye; Yw]; 
     plot(X,Y,'k', 'LineWidth',2), hold on,
     plot(X,Y,'o','MarkerEdgeColor',[1 0 0],'MarkerFaceColor',[1 0 0],'MarkerSize',6), hold on,
end
plot(X0(1),X0(2),'o','MarkerEdgeColor',[0 0 1],'MarkerFaceColor',[0 0 1],'MarkerSize',10), hold on,
grid on
axis([-60 60 -60 60])