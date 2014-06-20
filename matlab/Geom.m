classdef Geom
    methods(Static=true)
        function [X, E] = cube(d)
        % cube constructs vertex and edge data for the d-dimensional unit
        % hypercube with binary (0/1) vertices.
        %   Parameters
        %   d : dimension of the cube (defaults to 3).
        %   Returns
        %   X : X(:,d) is the d^th vertex vector.
        %   E : E(e,:) are the [src, dest] vertex indices of the e^th edge.
        
            if nargin < 1, d = 3; end;
            % Form vertices
            X = mod(floor(bsxfun(@rdivide, 0:2^d-1, 2.^(0:d-1)')),2); 
            % List edges
            [ei, ej] = find(tril((2*X-1)'*(2*X-1) == d-2,-1));
            E = [ei, ej];
            
        end
        function R = axisRotation(axis, ang)
        % axisRotation produces a 3d rotation matrix about a given axis.
        %   Parameters
        %   axis : The axis of rotation (column vector)
        %   ang : The angle of rotation, in radians
        %   Returns
        %   R : The rotation matrix
        
            % Construct cross product matrix
            ax = zeros(3);
            ax([8 3 4 6 7 2]) = [-axis; axis];
            % Construct rotation matrix (from Rodriguez formula)
            R = eye(3)*cos(ang)+ax*sin(ang)+(1-cos(ang))*(axis*axis');
            
        end
        function R = planeRotation(ang, i, j, d)
        % planeRotation produces a d-dimensional rotation matrix inside a
        % given plane.
        %   Parameters
        %   ang : The angle of rotation, in radians
        %   i, j : The two axis indices specifying the plane of rotation
        %   d : the dimension of the matrix
        
            % Construct rotation matrix (identity except for i,j)
            R = eye(d);
            R([i j], [i j]) = [cos(ang) -sin(ang); sin(ang) cos(ang)];
            
        end
        function [a, inside] = ray2PlaneSegment(c,u,p,n,B)
        % ray2PlaneSegment extends one or more rays to a convex polygonal
        % region of a plane in 3d space.
        %   Parameters
        %   c : The source point of the rays (column vector)
        %   u : A matrix of ray directions (unit column vectors)
        %   p : A point in the polygonal plane region (column vector)
        %   n : The normal to the plane (unit column vector)
        %   B : A matrix of column vectors specifying the polygon.  Each
        %       vector lies in the plane and is perpendicular to a
        %       corresponding edge. A given point x in the polygon
        %       satisfies (x-p)'*B(:,k) <= B(:,k)'*B(:,k) for each k.
        %   Returns
        %   a : a(k) is the distance from c to the plane along the k^th ray
        %   inside : inside(k) is true if the k^th ray extends through the
        %            polygonal region, false otherwise.
        
            % Get distances along each ray
            a = (n'*(p-c))./(n'*u);
            % Get vectors from p to intersection points of each ray
            v = bsxfun(@plus, c-p, bsxfun(@times,a,u));
            % Test each vector for interior of polygonal region
            % inside = all(B'*v < 1,1);
            inside = all(bsxfun(@le, B'*v, dot(B,B)'),1);
%             [a, inside] = geomRay2PlaneSegment(c,u,p,n,B);
            
        end
    end
end