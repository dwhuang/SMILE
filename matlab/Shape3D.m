classdef Shape3D < handle
    properties
        V; % Matrix of vertex column vectors
        E; % Edge adjacencies
        N; % Matrix of face normal column vectors
        F; % Matrix of face center column vectors
        B; % B{f}(:,:) define bounding polygon of face f
        C; % Matrix of rgb color column vectors (per face)
        I; % Sparse rgb rendering
        prox; % sparse proximity vector
        M; % orientation matrix
        x; % translation vector
    end
    methods
        function s = Shape3D(V,E,N,F,B,C)
            if nargin~=0 % empty constructor for array creation
                s.V = V; s.E = E; s.N = N; s.F = F; s.B = B; s.C = C;
                s.M = eye(3); s.x = [0 0 0]';
            end
        end
        function s = copy(orig)
            s = Shape3D(orig.V,orig.E,orig.N,orig.F,orig.B,orig.C);
            s.M = orig.M; s.x = orig.x;
            s.I = orig.I; s.prox = orig.prox;
        end
        function t = reput(s,M,x)
        % Change position/orientation without composing
            t = s.copy();
            t.M = M; t.x = x;
            % rendering must be redone
            t.I = []; t.prox = [];
        end
        function t = transform(s,M,x)
            % M 3D transformation matrix
            % x translation
            % Transform shape by t <- M*s+x
%             MVx = bsxfun(@plus,M*s.V,t);
%             MN = M*s.N;
%             MN = bsxfun(@rdivide, MN, sqrt(sum(MN.^2,1)));
%             MFx = bsxfun(@plus,M*s.F,t);
%             MB = cell(size(s.B));
%             for f = 1:numel(s.B)
%                 MB{f} = M*s.B{f};
%             end
%             t = Shape3D(MVx, s.E, MN, MFx, MB, s.C);
            
            % compose transformations
            t = s.copy();
            t.M = M*t.M;
            t.x = x + t.x;
            % rendering must be redone
            t.I = []; t.prox = [];
        end
        function [V,N,F,B] = transformed(s)
            V = bsxfun(@plus, s.M*s.V, s.x);
            N = s.M*s.N;
            N = bsxfun(@rdivide, N, sqrt(sum(N.^2,1)));
            F = bsxfun(@plus,s.M*s.F,s.x);
            B = cell(size(s.B));
            for f = 1:numel(s.B)
                B{f} = s.M*s.B{f};
            end
        end
        function wire(s, img, varargin)
            Plotter.edges(bsxfun(@plus, s.M*s.V, s.x), s.E, img, varargin{:});
        end
        function m = mask(s,vis)
            m = false(vis.N,1);
            [~,N,F,B] = transformed(s)
            for f = 1:size(s.F,2)
                [~,inside] = Geom.ray2PlaneSegment(vis.c,vis.U,F(:,f),N(:,f),B{f});
                m(inside) = true;
            end
        end
        function [I, dep] = render(s,vis,I,dep, lumfun)
            % render will add the current shape to an existing scene.
            % Any pixels where the current shape is in the fore-front
            % will be updated.  If I and dep are not provided as input, a
            % new image and depth map are created just for the current
            % shape. lumfun is an optional function handle for computing
            % luminosity.
            if nargin < 4
                lumfun = @(lm,lexp) 0.5 + 0.5*lm^lexp;
            end
            if nargin < 3
                I = zeros(vis.N,3);
                dep = Inf*ones(vis.R,vis.C);
            end
            
            [~,N,F,B] = s.transformed();

            for f = 1:size(s.F,2)
                % Get depths and intersection for each ray with face f
                [a,inside] = Geom.ray2PlaneSegment(vis.c,vis.U,F(:,f),N(:,f),B{f});
                % Pixels only need to change if they are inside the face.  The face
                % depth should also be closest found so far at each pixel
                % (otherwise this face is hidden by another).
                change = inside & (a < dep(1:end));
                % Simplified luminosities for image: sides should be brighter if
                % they face the viewer more directly.  So use dot product of ray
                % with face normal.  The negation and max with 0 is to cull the
                % sides which don't face the viewer.
                %%%lum = max(-vis.U(:,change)'*s.N(:,f),0);
                %%lm = max(-vis.ldir'*s.N(:,f),0);
                %%lum = lumfun(lm,vis.lexp); %0.5 + 0.5*lm^vis.lexp;
                inters = bsxfun(@times,a(change),vis.U(:,change));
                inters(1,:) = inters(1,:) + vis.c(1);
                inters = bsxfun(@rdivide,inters,sqrt(sum(inters.^2,1)));
                lum = max(-inters'*N(:,f),0);
                % Change image to new luminosity, depth map to new depth
                I(change,:) = bsxfun(@times,lum,s.C(:,f)');
                %%I(change,:) = repmat(lum*s.C(:,f)',nnz(change),1);
                dep(change) = a(change);
            end
        end
        function [I,prox] = spRender(s,vis)
            [I,dep] = s.render(vis);
            prox = 1./dep;
            I = sparse(I);
            prox = sparse(vis.unwrap(prox));
            s.I = I;
            s.prox = prox;
        end
    end
    methods(Static=true);
        function cube = makeCube(color)
            
            % Start with cube vertex and edges
            [V, E] = Geom.cube(3);
            % Form normals to each surface
            N = [eye(3) -eye(3)];
            % Form centers of each face
            F = 0.5*N + 0.5;
            % Bound polygonal regions for each face
            B = cell(1,6);
            for n = 1:6
                Bn = N(:,mod([n n+1],3)+1);
                B{n} = 0.5*[Bn -Bn];
            end
            C = repmat(color,1,6);
            % Shift center of mass to origin
            x = mean(V,2);
            V = bsxfun(@minus,V,x);
            F = bsxfun(@minus,F,x);
            cube = Shape3D(V,E,N,F,B,C);

        end
        function cube = makeBlock(sl,color)
        % makeBlock constructs a block object
        %   Parameters
        %   sl : vector of side lengths
        %   x : vector offset describing block translation from origin
        %   c : rgb vector describing color

            cube = Shape3D.makeCube(color);
            D = diag(sl);
            cube.V = D*cube.V;
            cube.F = D*cube.F;
            for f = 1:numel(cube.B)
                cube.B{f} = D*cube.B{f};
            end
            % cube = cube.transform(diag(sl), [0 0 0]');
        
        end
        function bx = makeBox(color)
        % makeBox constructs a box object
        %   Parameters
        %   color : rgb vector describing color
        
            % Start with cube vertex and edges
            ex = Shape3D.makeBlock([5 5 3],color);
            in = Shape3D.makeBlock([4 4 2.35],color);
            in = in.transform(eye(3),[0.5 0.5 0.65]');
            keep = [1 2 4 5 6];
            V = [ex.V, in.V];
            E = [ex.E; in.E+8];
            N = [ex.N(:,keep), -in.N(:,keep), [zeros(2,4); ones(1,4)]];
            F = [ex.F(:,keep), in.F(:,keep), [4.75 2.5 0.25 2.5; 2.5 4.75 2.5 0.25; 3 3 3 3]];
            B = cat(3, ex.B(keep), in.B(keep));
            topBs = [0.25 0 0; 0 2.5 0; -0.25 0 0; 0 -2.5 0]';
            for n = 0:3
                B{end+1} = Geom.planeRotation(n*pi/2,1,2,3)*topBs;
            end
            bx = Shape3D(V,E,N,F,B,repmat(color,1,14));
        end
        function cyl = makeCyl(k,r,h)
            % k: # of sides
            % r: radius
            % h: height
            
%             % Single ring
%             angs = (1:k)*2*pi/k;
%             V = [cos(angs); sin(angs) zeros(size(angs))];
%             E = [1:k; 2:k 1]';
%             F = (V + V(:,[2:k 1]))/2;
%             N = bsxfun(@rdivide, F, sqrt(sum(F.^2,1)));
%             B = zeros(3,cat(3,V-F,V(:,[2:k 1])-F);
%             % Full sets
%             V = [V, [V(1:2,:); ones(size(angs))]];
%             F = [F, [0 0 0; 0 0 h]'];
%             N = [F, [0 0 -1; 0 0 1]'];
%             B(:,:,3:4) = 0;
            % Issue: different faces have different size B
            cyl = [];
        end
    end
end