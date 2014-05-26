%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
%%%%%%%%%%%%%% Learning of the IK %%%%%%%%%%%%%%  
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
clear all;close all;clc;
Alpha = 0.01;               % Learning rate
jointDim = 2;               % Dimention of the joint space
SpatialDim = 2;             % Dimention of the spatial space
L1 = 28; L2 = 20;           % Specifications for the lenght of the robot arm (cm)
MinRangeLimit=0;            % Lower limit of the range of motion for both Theta 1 (shoulder) and Theta 2 (elbow) - 0 degree
MaxRangeLimit=3*pi/4;       % Upper limit of the range of motion for both Theta 1 (shoulder) and Theta 2 (elbow) - 135 degree

%% Radial basis function network construction
%Parameters
nG=5;                               % The number of partitions on each dimention of the 'grid'
nR=nG^jointDim;                     % Total number of receptive field units for the RBF network
Nbf = nR;                           % Total number of receptive field units for the RBF network
Field=0:(3*pi/4)/(nG-1):3*pi/4;     % Sampling of the field

%Construction of the center of the basis functions
k=0; 
for i=1:length(Field)
    for j=1:length(Field)
        k=k+1;
        Centers(k,1)=Field(i);
        Centers(k,2)=Field(j);
    end
end

%Construction of the width of the (gaussian) basis functions
widths = 1.3*((3*pi/4)/nG);
Widths = widths*ones(Nbf,jointDim);

%Initialization of the weights and of the parameters of the transformation (aij, see Guenther et al. 1997) 
for i = 1:jointDim
    for j = 1:SpatialDim
        for k = 1:Nbf
            weights(i,j,k) = 0;     % See Guenther et al. 1997
            a(i,j,k) = 0;           % See Guenther et al. 1997
        end
    end
end

% Initialization of the paraemeters for learning/updating the parameters (see Guenther et al. 1997)
z = zeros(jointDim,jointDim*2); Del_Z=zeros(jointDim,jointDim*2); Del_wei=zeros(jointDim,jointDim);
for k = 1:Nbf 
    Z(:,:,k) = z; 
    D_Z(:,:,k) = Del_Z;
    D_weights(:,:,k) = Del_wei;
end 
D_Centers=zeros(Nbf,jointDim); D_Widths =zeros(Nbf,jointDim);



%% Initilization of the joints angles, spatial position and random joint angles
Nb_trial = 10000;                                                       %Number of trials for learning
Random_Joint_Mov = 0.1*(1 - 2*rand(jointDim,Nb_trial));                 %Random joint angles
Theta = zeros(1,2)*pi/180;                                              %Initilization of the joints angles

% %Initilization of the spatial position of end-effector using the forward kinematics
X_prev(1,1) = L1*cos(Theta(1)) + L2*cos(Theta(1)+Theta(2));
X_prev(2,1) = L1*sin(Theta(1)) + L2*sin(Theta(1)+Theta(2));


%% Implementation of the learning stage through babbling stage
for Nbt = 1:Nb_trial
    Nbt                                          % Display the current trial
    DThe_B = Random_Joint_Mov(:,Nbt);            % Select the small increment for the given trial
    Theta = Theta + DThe_B';                    % Compute the small Delta Theta increment
  
    Theta(1) = max(MinRangeLimit,min(MaxRangeLimit,Theta(1)));  % Limitation of the range of motion between 0 and 135 degree for Theta 1 (shoulder)
    Theta(2) = max(MinRangeLimit,min(MaxRangeLimit,Theta(2)));  % Limitation of the range of motion between 0 and 135 degree for Theta 2 (shoulder)

    % Spatial position of the end-effector using the forward kinematics
    X_curr(1,1) = L1*cos(Theta(1)) + L2*cos(Theta(1)+Theta(2));     % X position of end-effector
    X_curr(2,1) = L1*sin(Theta(1)) + L2*sin(Theta(1)+Theta(2));     % Y position of end-effector

    DX = (X_curr - X_prev);                     % Compute the small spatial increment of the end effector  
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

    DThe = IVM*DX;                              % Estimation of the small joint increment resulting from the IK                               
    Error = (DThe_B - DThe);                    % Computation of the error                       


    
    %% Weights and center and width adjustments through gradient descent
    for k = 1:Nbf

        % Learning and update of the weights (see corresponding papers)
        D_Z(:,1:2,k) = -2 * Alpha * Error * DX(1) * h(k) * c(k,:);
        D_Z(:,3:4,k) = -2 * Alpha * Error * DX(2) * h(k) * c(k,:);
        D_weights(:,:,k) = -2 * Alpha * Error * DX' * h(k);

        weights(:,:,k) = weights(:,:,k) - D_weights(:,:,k);
        Z(:,1:2,k) = Z(:,1:2,k) - D_Z(:,1:2,k);
        Z(:,3:4,k) = Z(:,3:4,k) - D_Z(:,3:4,k);


       % Learning  and update of the centers and the widths of the basis funrctions (see corresponding papers)
        Beta = Error'*Z(:,:,k);
        OMEGA(:,:,k) = reshape(Beta,jointDim,2);
        PSI(:,:,k) = weights(:,:,k) + LLAreshap(:,:,k);

        D_Centers(k,:) = -2 * Alpha * h(k) * (norm(c(k,:)) * (1 - h(k)) * (Error'*PSI(:,:,k)*DX) - OMEGA(:,:,k)*DX)'./Widths(k,:);
        D_Widths(k,:) = -2 * Alpha * h(k) * (norm(c(k,:)) * (1 - h(k)) * (Error'*PSI(:,:,k)*DX) - OMEGA(:,:,k)*DX)'.*(c(k,:)./(Widths(k,:)) );
    end

        Centers = Centers - D_Centers;
        Widths = Widths - D_Widths;
        X_prev = X_curr;
end