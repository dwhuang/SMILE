% get effector position from the external world
% X_curr(1,1) = endEffectorPosition(1);
% X_curr(2,1) = endEffectorPosition(2);
% Theta(1) = jointAngles(1);
% Theta(2) = jointAngles(2);
X_curr(1, 1) = sensor.endEffPos(2, 1);
X_curr(2, 1) = sensor.endEffPos(2, 2);
Theta(1) = sensor.jointAngles(2, 1);
Theta(2) = sensor.jointAngles(2, 4);

fprintf('epoch = %d\n', Nbt);

if Nbt > 0
    DX = (X_curr - X_prev);                     % Compute the small spatial increment of the end effector
    Theta_rbf = ones(Nbf, 1) * Theta;              % Theta_rbf is a copy of the joint angles to do matrix operations
    c = (Theta_rbf - Centers) ./ (Widths);        % Distance from the center (Gaussian) (See Guenther et al. 1997)
    g = exp(-0.5 * (sum((c.^2)')'));              % Activation of the basis function (See Guenther et al. 1997)
    h = g / sum(g);                               % Normalization of the activation (See Guenther et al. 1997)
        
    % Computation of the coefficient (aij, from Guenther et al. 1997)
    IVM = zeros(jointDim,2); % Matrix including all the aij to compute the inverse mapping
    for k = 1 : Nbf
        LLA = c(k,:)*Z(:,:,k);                                  % Computation of the linear local adaptation (most right member of the equation in Guenther et al. 1997)
        LLAreshap(:,:,k)=reshape(LLA,jointDim,2);               % Reshape for computation
        a(:,:,k) = h(k) * (weights(:,:,k) + LLAreshap(:,:,k));  % Computation of the aij (element of the inverse transformation for each RBF, see Guenther et al. 1997)
        IVM = IVM + a(:,:,k);                                   % Computation of the aij for the tansformation (see Guenther et al. 1997)
    end
    
    DThe = IVM * DX;                              % Estimation of the small joint increment resulting from the IK
    Error = (DThe_B - DThe);                    % Computation of the error
    
    %% Weights and center and width adjustments through gradient descent
    for k = 1:Nbf
        
        % Learning and update of the weights (see corresponding papers)
        D_Z(:,1:2,k) = -2 * lr * Error * DX(1) * h(k) * c(k,:);
        D_Z(:,3:4,k) = -2 * lr * Error * DX(2) * h(k) * c(k,:);
        D_weights(:,:,k) = -2 * lr * Error * DX' * h(k);
        
        weights(:,:,k) = weights(:,:,k) - D_weights(:,:,k);
        Z(:,1:2,k) = Z(:,1:2,k) - D_Z(:,1:2,k);
        Z(:,3:4,k) = Z(:,3:4,k) - D_Z(:,3:4,k);
        
        
        % Learning  and update of the centers and the widths of the basis funrctions (see corresponding papers)
        Beta = Error'*Z(:,:,k);
        OMEGA(:,:,k) = reshape(Beta,jointDim,2);
        PSI(:,:,k) = weights(:,:,k) + LLAreshap(:,:,k);
        
        D_Centers(k,:) = -2 * lr * h(k) * (norm(c(k,:)) * (1 - h(k)) * (Error'*PSI(:,:,k)*DX) - OMEGA(:,:,k)*DX)'./Widths(k,:);
        D_Widths(k,:) = -2 * lr * h(k) * (norm(c(k,:)) * (1 - h(k)) * (Error'*PSI(:,:,k)*DX) - OMEGA(:,:,k)*DX)'.*(c(k,:)./(Widths(k,:)) );
    end
    
    Centers = Centers - D_Centers;
    Widths = Widths - D_Widths;
end

X_prev = X_curr;
Nbt = Nbt + 1;                                         % Display the current trial

if Nbt > Nb_trial
    fname = [aux.path, 'dof2.mat'];
    save(fname, 'Nbf', 'Centers', 'Widths', 'jointDim', 'weights', 'Z');
    aux.exit = true;
    return;
end

DThe_B = Random_Joint_Mov(:,Nbt);            % Select the small increment for the given trial
motor.jointVelocities(2, 1) = DThe_B(1);
motor.jointVelocities(2, 4) = DThe_B(2);
