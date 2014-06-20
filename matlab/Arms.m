classdef Arms < handle
    properties
        % Number of links, limb radii
        N; R;
        % d, r, alpha, theta0
        DH_d; DH_r; DH_a; DH_t0;
        % left/right shoulder transforms
        lsh; rsh;
        % Successive transformations (and deriv), joint positions
        ZX; Xj, DZX; T; X;
        % relative gripper positions, gripper edges
        X_g; E_g;
        % joints+openings
        jointAngles;
        gripperOpenings;
    end
    methods
        function a = Arms()
            a.N = 7;
            a.R = 1*ones(2,8);
            arm_scale = 0.01;
            a.DH_d = [270.35 0 364.35 0 374.29 0 229.525]*arm_scale; % last dummy joint translates along wrist
            a.DH_r = [69 0 69 0 10 0 0]*arm_scale;
            a.DH_a = [-pi/2 pi/2 -pi/2 pi/2 -pi/2 pi/2 0];
            a.DH_t0 = [0 pi/2 0 0 0 0 0];
            a.lsh = [Geom.planeRotation(3*pi/4,1,2,3), [-2.5 -8.5 3]'; [0 0 0 1]];
            a.rsh = [Geom.planeRotation(1*pi/4,1,2,3), [ 2.5 -8.5 3]'; [0 0 0 1]];
            a.E_g = reshape(1:6,2,3)';
            a.Xj = zeros(4,4,2,7);
            for i = 1:2
                for j = 1:7
                    a.Xj(:,:,i,j) = ...
                         [1, 0,               0,              a.DH_r(j);...
                          0, cos(a.DH_a(j)), -sin(a.DH_a(j)), 0; ...
                          0, sin(a.DH_a(j)),  cos(a.DH_a(j)), 0; ...
                          0, 0,               0,              1];
                end
            end
        end
        function update(a, jointAngles, gripperOpenings)
            a.jointAngles = jointAngles;
            a.gripperOpenings = gripperOpenings;
            T = zeros(4,4,2,8);
            T(:,:,1,1) = a.lsh;
            T(:,:,2,1) = a.rsh;
            ZX = T;
            DZX = zeros(4,4,2,8);
            for i = 1:2
                DH_t = a.DH_t0 + jointAngles(i,:);
                Zj = eye(4); DZj = zeros(4);
                for j = 1:a.N
                    s = sin(DH_t(j)); c = cos(DH_t(j));
                    Zj([1 2 5 6 15]) = [c s -s c a.DH_d(j)];
                    DZj([1 2 5 6]) = [-s c -c -s];
                    Xj = a.Xj(:,:,i,j);
                    ZX(:,:,i,j+1) = Zj*Xj;
                    DZX(:,:,i,j+1) = DZj*Xj;
                    T(:,:,i,j+1) = T(:,:,i,j)*ZX(:,:,i,j+1);
                end
            end
            X = squeeze(T(1:3,4,:,:));

            for i = 2:-1:1
                G = [zeros(1,6); ...
                    [-1.1 -1.1 1.1 1.1]*gripperOpenings(i)/2+0.1*(2-i), [-1.1 1.1]+0.1*(2-i);...
                    0 1.5 0 1.5 0 0;...
                    ones(1,6)];
                X_g{i} = T(:,:,i,end)*G;
                X_g{i} = X_g{i}(1:3,:);
            end
            a.T = T; a.ZX = ZX; a.DZX = DZX; a.X = X; a.X_g = X_g;
        end
        function jointVelocities = reach(a, X_TARGETS, delta, alpha)
            if nargin < 4, alpha = 10; end;
            if nargin < 3, delta = 0.005; end;
            J = {zeros(3,7), zeros(3,7)}; % Jacobians
            jointVelocities = zeros(2,7);
            for i = 1:2
                U = eye(4); % Backwards cumulative products of ZX
                for j = 7:-1:1
                    DT = a.T{i,j}*a.DZX{i,j+1}*U;
                    J{i}(:,j) = DT(1:3,4);
                    U = a.ZX{i,j+1}*U;
                end
                dX = X_TARGETS-a.X{i}(:,end);
                r = norm(dX);
                dX = delta*dX/r*(1-1/(alpha*r+1));
                jointVelocities(i,:) = dX'*J{i}; % zipser gradient
            end
        end
        function jointVelocities = seek(a, X_TARGETS, X_OBST, R_OBST,delta,tlink)
            if nargin < 6, tlink = 6; end;
            if nargin < 5, delta = 0.05; end;
            jointVelocities = zeros(2,7);
            
            % Unvectorized, unnested
            for i = 1:2
%                 if isnan(X_TARGETS(1,i))
%                     X_TARGETS(:,i) = a.X(:,i,tlink);
%                 end
                %for j = 3:2:7
                X_OBSTi = [X_OBST{i} squeeze(a.X(:,3-i,:))]; % avoid other arm
                R_OBSTi = [R_OBST{i} a.R(3-i,:)];
                U = zeros(4,0);
                dX = zeros(3,0);
                for j = 8:-1:1
                    if j==3 || j==5 || j==7
                        % Compute avoidance vectors on limb j
                        t = a.T(1:3,3,i,j)'*bsxfun(@minus, X_OBSTi, a.X(:,i,j));
                        t(t<0) = 0;
                        t(t>a.DH_d(j)) = a.DH_d(j);
                        X_avoid = bsxfun(@plus, a.X(:,i,j), a.T(1:3,3,i,j)*t);
                        V_avoid = X_avoid - X_OBSTi;
                        % Include seeker vectors from wrists (if not NaN)
                        if j == 5 && ~isnan(X_TARGETS(1,i))
                            dX(:,end+1) = X_TARGETS(:,i)-a.X(:,i,tlink);
                            U(:,end+1) = [0 0 a.DH_d(5) 1]';
                        end
                        % Scale avoidance vectors
%                         r = sum(V_avoid.*V_avoid,1); % squared vector lengths
%                         d = a.R(i,j) + R_OBSTi; % minimum distance
%                         dX = cat(2,dX,bsxfun(@times, d./r.^3, V_avoid));
                        r = sqrt(sum(V_avoid.*V_avoid,1)); % actual distances
                        d = a.R(i,j) + R_OBSTi; % minimum distances
                        %V_avoid = bsxfun(@times, V_avoid, 0.01./(r.*(r-d).^2));
                        V_avoid = bsxfun(@times, V_avoid, 1./r.*exp(-5*(r-d)));
                        dX = cat(2, dX, V_avoid);
                        % Accumulate influences
                        U = cat(2,U,[zeros(2,size(X_OBSTi,2)); t; ones(1,size(X_OBSTi,2))]);
                    end
                    if j > 1
                        DT = a.T(:,:,i,j-1)*a.DZX(:,:,i,j)*U;
                        J = sum(sum(dX.*DT(1:3,:))); % contribution at i,j-1
                        jointVelocities(i,j-1) = jointVelocities(i,j-1) + J;
                        % Accumulate tail factors in Jacobian
                        U = a.ZX(:,:,i,j)*U;
                    end
                end
                % Move at roughly constant joint speed until target is
                % near, or speed proportional to avoidance gradient when no target
                % jointVelocities(i,4) = 10*jointVelocities(i,4); % boost elbow
                if isnan(X_TARGETS(1,i))
                    speed = delta*norm(jointVelocities(i,:));
                else
                    diff = X_TARGETS(:,i)-a.X(:,i,tlink);
                    %diff(isnan(diff)) = 0;
                    r = norm(diff);
                    speed = delta*(1-1/(0.5*r+1));
                end
                jointVelocities(i,:) = speed*jointVelocities(i,:)/norm(jointVelocities(i,:));
            end
        end
        function dWr = postureWrists(a, M_TARG)
            % M_TARG{i}: basis matrix for i^th gripper
            w = [0 0 1 0]';
%             xbm = [-xb(1:2); xb(3:4)];
            dWr = zeros(2,3);
            for i = 1:2
                dWr(i,1) = -w'*(a.T(:,:,i,5)*a.DZX(:,:,i,6)*a.ZX(:,:,i,7)*a.ZX(:,:,i,8))*w;
                dWr(i,2) = -w'*(a.T(:,:,i,6)*a.DZX(:,:,i,7)*a.ZX(:,:,i,8))*w;
%                 if xb'*a.T(:,1,i,8) > xbm'*a.T(:,1,i,8)
%                     dWr(i,3) = xb'*(a.T(:,:,i,7)*a.DZX(:,:,i,8))*[1 0 0 0]';
%                 else
%                     dWr(i,3) = xbm'*(a.T(:,:,i,7)*a.DZX(:,:,i,8))*[1 0 0 0]';
%                 end
                if ~any(isnan(M_TARG{i}))
                    xb = M_TARG{i}(:,1);
                    Txb = a.T(1:3,1:3,i,8)'*xb;
                    alpha = atan2(Txb(2),Txb(1));
                    t = a.jointAngles(i,7);
                    if alpha+t > pi, alpha = alpha - 2*pi; end;
                    if alpha+t < -pi, alpha = alpha + 2*pi; end;
                    dWr(i,3) = alpha;
                end
            end
        end
    end
    methods(Static = true)
        function [ arms, T, grips, E ] = fix( jointAngles, gripperOpening )
        %UNTITLED Summary of this function goes here
        %   Detailed explanation goes here

            arm_scale = 0.01;
            DH_d = [270.35 0 364.35 0 374.29 0 229.525; 270.35 0 364.35 0 374.29 0 229.525]*arm_scale; % last dummy joint translates along wrist
            DH_r = [69 0 69 0 10 0 0; 69 0 69 0 10 0 0]*arm_scale;
            DH_alpha = [-pi/2 pi/2 -pi/2 pi/2 -pi/2 pi/2 0; -pi/2 pi/2 -pi/2 pi/2 -pi/2 pi/2 0];
            DH_theta0 = [0 pi/2 0 0 0 0 0; 0 pi/2 0 0 0 0 0];

            DH_theta = DH_theta0 + jointAngles;
            lsh = [Geom.prot(3*pi/4,1,2,3), [-2.5 -8.5 3]'; [0 0 0 1]];
            rsh = [Geom.prot(pi/4,1,2,3), [2.5 -8.5 3]'; [0 0 0 1]];
            T = cat(2, {lsh; rsh}, cell(size(DH_theta)));
            arms = cell(2,1);
            upto = 7;
            for i = 1:2
                arms{i} = [T{i,1}(1:3,4), zeros(3,size(DH_d,2))];
                for j = 1:upto%numel(DH_d)
                    Z = [cos(DH_theta(i,j)), -sin(DH_theta(i,j)), 0, 0; ...
                         sin(DH_theta(i,j)),  cos(DH_theta(i,j)), 0, 0; ...
                         0,                 0,                1, DH_d(i,j); ...
                         0,                 0,                0, 1];
                    X = [1, 0,                 0,                DH_r(i,j);...
                         0, cos(DH_alpha(i,j)), -sin(DH_alpha(i,j)), 0; ...
                         0, sin(DH_alpha(i,j)),  cos(DH_alpha(i,j)), 0; ...
                         0, 0,                 0,                1];
                    T{i,j+1} = T{i,j}*Z*X;
                    arms{i}(:,j+1) = T{i,j+1}(1:3,4);
                end
            end

            E = reshape(1:6,2,3)';
            for i = 2:-1:1
                G = [zeros(1,6); ...
                    [-1.1 -1.1 1.1 1.1]*gripperOpening(i)/2+0.1*(2-i), [-1.1 1.1]+0.1*(2-i);...
                    0 1.5 0 1.5 0 0;...
                    ones(1,6)];
                grips{i} = T{i,end}*G;
                grips{i} = grips{i}(1:3,:);
            end
        end
        function DThe_go = motion(sensor, arms, X_TARGET, Nbf, Centers, Widths, Zp, W, Time_step, Go)
            X_curr = arms{2}(:,6);
            Theta = sensor.jointAngles(2, 1:4);

            DX = (X_TARGET - X_curr);   % Compute the spatial increment between the end-effector and the target

            Theta_rbf = ones(Nbf,1)*Theta;              % Theta_rbf is a copy of the joint angles to do matrix operations
            c = (Theta_rbf - Centers)./(Widths);        % Distance from the center (Gaussian) (See Guenther et al. 1997)
            g2 = exp(-0.5*sum(c.^2, 2));              % Activation of the basis function (See Guenther et al. 1997)
            h2 = g2/sum(g2);

            % Computation of the coefficient (aij, from Guenther et al. 1997)
            LLA = sum(bsxfun(@times, c, Zp),2);
            LLA = squeeze(LLA);
            a2 = bsxfun(@times, h2, LLA + W);
            jointDim = 4;               % Dimention of the joint space
            SpatialDim = 3;             % Dimention of the spatial space
            IVM = reshape(sum(a2,1), jointDim, SpatialDim);

            % See Bullock et al. 1993; Guenther et al. 1997
            DThe = IVM*DX;                      % Estimation of the small joint increment resulting from the IK

            DThe_go = Time_step*DThe*Go;     % Go signal to trigger the motion (see Bullock et al. 1993; Guenther et al. 1997)

        end
    end
end

%% Alternate Seek code
%             % vectorized, nested
%             diffs = bsxfun(@minus,reshape(X_OBST,3,1,1,[]),a.X(:,:,3:2:7));
%             dots = sum(bsxfun(@times,squeeze(a.T(1:3,3,:,3:2:7)),diffs),1);
%             dots = permute(dots,[1 4 2 3]);
%             dots(dots<0) = 0;
%             mx = repmat(reshape(a.DH_d(3:2:7),1,1,1,3),1,size(X_OBST,2),2,1);
%             dots = max(dots, mx);
%             X_avoid = bsxfun(@plus, reshape(a.X(:,:,3:2:7),3,1,2,3), bsxfun(@times, a.T(1:3,3,:,3:2:7), dots));
%             V_avoid = bsxfun(@minus, X_avoid, X_OBST);
%             vr = sum(V_avoid.*V_avoid,1);
%             d = bsxfun(@plus, R_OBST, reshape(a.R(:,3:2:7),1,1,2,3));
%             dXall = bsxfun(@times, d./vr.^3, V_avoid);
%             for i = 1:2
%                 for j = 3:2:7
%                     t = dots(1,:,i,(j-1)/2);
%                     dX = dXall(:,:,i,(j-1)/2);
%                     U = [zeros(2,size(X_OBST,2)); t; ones(1,size(X_OBST,2))];
%                     % Include seeker vectors from wrists
%                     if j == 5
%                         dX(:,end+1) = X_TARGETS(:,i)-a.X(:,i,6);
%                         U(:,end+1) = [0 0 a.DH_d(5) 1]';
%                     end
%                     % Accumulate velocities for previous joints
%                     J = zeros(1,j-1); % Jacobian contributions
%                     DT = zeros(4,size(U,2),j-1);
%                     for j2 = j-1:-1:1
%                         DT(:,:,j2) = a.T(:,:,i,j2)*a.DZX(:,:,i,j2+1)*U;
%                         % Accumulate tail factors in Jacobian
%                         U = a.ZX(:,:,i,j2+1)*U;
%                     end
%                     J = bsxfun(@times, dX, DT(1:3,:,:));
%                     J = sum(reshape(J,[],j-1));
%                     jointVelocities(i,1:j-1) = jointVelocities(i,1:j-1) + J;
%                 end
%                 % Move at roughly constant joint speed until target is near
%                 r = norm(X_TARGETS(:,i)-a.X(:,i,6));
%                 speed = delta*(1-1/(5*r+1));
%                 jointVelocities(i,:) = speed*jointVelocities(i,:)/norm(jointVelocities(i,:));
%             end
% 
%             % Unvectorized, nested
%             for i = 1:2
%                 for j = 3:2:7
%                     % Compute avoidance vectors on limb j
%                     t = a.T(1:3,3,i,j)'*bsxfun(@minus, X_OBST, a.X(:,i,j));
%                     t(t<0) = 0;
%                     t(t>a.DH_d(j)) = a.DH_d(j);
%                     X_avoid = bsxfun(@plus, a.X(:,i,j), a.T(1:3,3,i,j)*t);
%                     V_avoid = X_avoid - X_OBST;
%                     % Scale avoidance vectors
%                     r = sum(V_avoid.*V_avoid,1); % squared vector lengths
%                     d = a.R(i,j) + R_OBST; % minimum distance
%                     dX = bsxfun(@times, d./r.^3, V_avoid);
%                     U = [zeros(2,size(X_OBST,2)); t; ones(1,size(X_OBST,2))];
%                     % Include seeker vectors from wrists
%                     if j == 5
%                         dX(:,end+1) = X_TARGETS(:,i)-a.X(:,i,6);
%                         U(:,end+1) = [0 0 a.DH_d(5) 1]';
%                     end
%                     % Accumulate velocities for previous joints
%                     J = zeros(1,j-1); % Jacobian contributions
%                     for j2 = j-1:-1:1
%                         DT = a.T(:,:,i,j2)*a.DZX(:,:,i,j2+1)*U;
%                         J(j2) = sum(sum(dX.*DT(1:3,:)));
%                         % Accumulate tail factors in Jacobian
%                         U = a.ZX(:,:,i,j2+1)*U;
%                     end
%                     jointVelocities(i,1:j-1) = jointVelocities(i,1:j-1) + J;
%                 end
%                 % Move at roughly constant joint speed until target is near
%                 r = norm(X_TARGETS(:,i)-a.X(:,i,6));
%                 speed = delta*(1-1/(5*r+1));
%                 jointVelocities(i,:) = speed*jointVelocities(i,:)/norm(jointVelocities(i,:));
%             end

%             % vectorized, unnested
%             N_OBST = size(X_OBST,2);
%             diffs = bsxfun(@minus,reshape(X_OBST,3,1,1,[]),a.X(:,:,3:2:7));
%             dots = sum(bsxfun(@times,squeeze(a.T(1:3,3,:,3:2:7)),diffs),1);
%             dots = permute(dots,[1 4 2 3]);
%             dots(dots<0) = 0;
%             mx = repmat(reshape(a.DH_d(3:2:7),1,1,1,3),1,N_OBST,2,1);
%             dots = max(dots, mx);
%             X_avoid = bsxfun(@plus, reshape(a.X(:,:,3:2:7),3,1,2,3), bsxfun(@times, a.T(1:3,3,:,3:2:7), dots));
%             V_avoid = bsxfun(@minus, X_avoid, X_OBST);
%             vr = sum(V_avoid.*V_avoid,1);
%             d = bsxfun(@plus, R_OBST, reshape(a.R(:,3:2:7),1,1,2,3));
%             dXall = bsxfun(@times, d./vr.^3, V_avoid);
%             for i = 1:2
%                 dX = zeros(3,0);
%                 U = zeros(4,0);
%                 for j = 8:-1:1
%                     if j==3 || j==5 || j==7
%                         if j == 5
%                             dX(:,end+1) = X_TARGETS(:,i)-a.X(:,i,6);
%                             U(:,end+1) = [0 0 a.DH_d(5) 1]';
%                         end
%                         % Accumulate influences
%                         t = dots(1,:,i,(j-1)/2);
%                         dX = cat(2, dX, dXall(:,:,i,(j-1)/2));
%                         U = cat(2, U, [zeros(2,N_OBST); t; ones(1,N_OBST)]);
%                     end
%                     if j > 1
%                         DT = (a.T(:,:,i,j-1)*a.DZX(:,:,i,j))*U;
%                         J = sum(sum(dX.*DT(1:3,:))); % contribution at i,j-1
%                         jointVelocities(i,j-1) = jointVelocities(i,j-1) + J;
%                         % Accumulate tail factors in Jacobian
%                         U = a.ZX(:,:,i,j)*U;
%                     end
%                 end
%                 % Move at roughly constant joint speed until target is near
%                 r = norm(X_TARGETS(:,i)-a.X(:,i,6));
%                 speed = delta*(1-1/(5*r+1));
%                 jointVelocities(i,:) = speed*jointVelocities(i,:)/norm(jointVelocities(i,:));
%             end            
