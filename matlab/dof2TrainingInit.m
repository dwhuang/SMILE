%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
%%%%%%%%%%%%%% Learning of the IK %%%%%%%%%%%%%%  
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% 
aux.initJointAngles = zeros(aux.numLimbs, aux.numJoints);
aux.initJointAngles(2, 3) = pi / 2;


lr = 0.01;               % Learning rate
jointDim = 2;               % Dimention of the joint space
SpatialDim = 2;             % Dimention of the spatial space
% L1 = 28; L2 = 20;           % Specifications for the lenght of the robot arm (cm)
% MinRangeLimit=0;            % Lower limit of the range of motion for both Theta 1 (shoulder) and Theta 2 (elbow) - 0 degree
% MaxRangeLimit=3*pi/4;       % Upper limit of the range of motion for both Theta 1 (shoulder) and Theta 2 (elbow) - 135 degree

%% Radial basis function network construction
%Parameters
nG = 5;                             % The number of partitions on each dimention of the 'grid'
nR = nG^jointDim;                   % Total number of receptive field units for the RBF network
Nbf = nR;                           % Total number of receptive field units for the RBF network
Field1 = -1.7 : 3.4/(nG-1) : 1.7;
Field2 = -0.05 : 2.668/(nG-1) : 2.618;     % Sampling of the field

%Construction of the center of the basis functions
[CX, CY] = ndgrid(Field1, Field2);
Centers = [CX(:), CY(:)];

% k=0;
% for i=1:length(Field1)
%     for j=1:length(Field2)
%         k=k+1;
%         Centers(k,1)=Field1(i);
%         Centers(k,2)=Field2(j);
%     end
% end

%Construction of the width of the (gaussian) basis functions
widths1 = 1.3 * 3.4 / nG;
widths2 = 1.3 * 2.668 / nG;
Widths = [widths1 * ones(Nbf, 1), widths2 * ones(Nbf, 1)];

% widths = 1.3*((3*pi/4)/nG);
% Widths = widths*ones(Nbf,jointDim);

%Initialization of the weights and of the parameters of the transformation (aij, see Guenther et al. 1997) 
weights = zeros(jointDim, SpatialDim, Nbf);
a = zeros(jointDim, SpatialDim, Nbf);
% for i = 1 : jointDim
%     for j = 1 : SpatialDim
%         for k = 1 : Nbf
%             weights(i,j,k) = 0;     % See Guenther et al. 1997
%             a(i,j,k) = 0;           % See Guenther et al. 1997
%         end
%     end
% end

% Initialization of the paraemeters for learning/updating the parameters (see Guenther et al. 1997)
z = zeros(jointDim, jointDim * 2); 
Del_Z = zeros(jointDim, jointDim * 2); 
Del_wei = zeros(jointDim, jointDim);

Z = repmat(z, 1, 1, Nbf);
D_Z = repmat(Del_Z, 1, 1, Nbf);
D_weights = repmat(Del_wei, 1, 1, Nbf);
% for k = 1 : Nbf 
%     Z(:,:,k) = z; 
%     D_Z(:,:,k) = Del_Z;
%     D_weights(:,:,k) = Del_wei;
% end

D_Centers = zeros(Nbf, jointDim); 
D_Widths = zeros(Nbf, jointDim);



%% Initilization of the joints angles, spatial position and random joint angles
Nb_trial = 10000;                                                       %Number of trials for learning
Random_Joint_Mov = 0.1 * 50 * (1 - 2 * rand(jointDim, Nb_trial));                 %Random joint angles
Theta = zeros(1, 2);                                              %Initilization of the joints angles


Nbt = 0;

% jointAngleDelta = [0; 0];
