X_curr(1, 1) = sensor.endEffPos(2, 1);
X_curr(2, 1) = sensor.endEffPos(2, 2);
Theta(1) = sensor.jointAngles(2, 1);
Theta(2) = sensor.jointAngles(2, 4);


if any(strcmp('rgbVision', fieldnames(sensor)))
    image(sensor.rgbVision);
end

s = mod(s, Nb_samples) + 1;
if s == 1
    X_TARGET_INDEX = mod(X_TARGET_INDEX, size(X_TARGETS, 2)) + 1;
    X_TARGET = X_TARGETS(:, X_TARGET_INDEX);
end

DX = (X_TARGET - X_curr);   % Compute the spatial increment between the end-effector and the target

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

% jointAngleDelta(1) = DThe_go(1);
% jointAngleDelta(2) = DThe_go(2);
motor.jointVelocities(2, 1) = DThe_go(1);
motor.jointVelocities(2, 4) = DThe_go(2);