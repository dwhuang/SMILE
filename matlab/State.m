classdef State < handle
    methods(Static=true)
        function str = color2str(rgb)
            idx = [4 2 1]*round(rgb) + 1;
            strs = {'black','blue','green','cyan','red','magenta','yellow','white'};
            str = strs{idx};
        end
        function disp(state)
            blk_keys = keys(state.blocks);
            for k = 1:numel(blk_keys)
                blk = state.blocks(blk_keys{k});
                blk_color = blk.C(:,1);
                disp([blk_keys{k} ': ' State.color2str(blk_color) ' block on ' state.support(blk_keys{k})]); % '[' num2str(mean(blk.V,2)') ']']);
            end
            disp(['Left gripping ' state.gripping('L')]);
            disp(['Right gripping ' state.gripping('R')]);
        end
        function [I, prox] = render(state,vis)
            if isempty(state.table.I)
                state.table.spRender(vis);
            end
            I = state.table.I;
            prox = full(state.table.prox);
            blk_keys = keys(state.blocks);
            for k = 1:numel(blk_keys)
                b = blk_keys{k};
                if isempty(state.blocks(b).I)
                    state.blocks(b).spRender(vis);
                end
                idx = state.blocks(b).prox > prox;
                prox(idx) = state.blocks(b).prox(idx);
                I(idx,:) = state.blocks(b).I(idx,:);
            end
        end
        function show(state, vis)
            [I,~] = State.render(state, vis);
            state.arms.update(state.jointAngles,state.gripperOpenings);
            subplot(1,2,1);
            imshow(vis.wrap(full(I)));
            blk_keys = keys(state.blocks);
            hold on
            for k = 1:numel(blk_keys)
                b = blk_keys{k};
                blk = state.blocks(b);
                V = bsxfun(@plus, blk.M*blk.V, blk.x);
                Plotter.edges(vis.xyz2ij(V), state.blocks(b).E, true);
                cent = vis.xyz2ij(mean(V,2));
                text(cent(2),cent(1),b);
            end
            for i = 1:2
                Plotter.plot(vis.xyz2ij(squeeze(state.arms.X(:,i,:))),true,'-bo');
                Plotter.edges(vis.xyz2ij(state.arms.X_g{i}),state.arms.E_g,true,'-bo');
            end
            hold off
            subplot(1,2,2);
            cla
            hold on
            for i = 1:2
                Plotter.plot(squeeze(state.arms.X(:,i,:)),false,'-bo');
                Plotter.edges(state.arms.X_g{i},state.arms.E_g,false,'-bo');
            end
            [V,~,~,~] = state.table.transformed();
            Plotter.edges(V,state.table.E,false);
            for k = 1:numel(blk_keys)
                b = blk_keys{k};
                blk = state.blocks(b);
                V = bsxfun(@plus, blk.M*blk.V, blk.x);
                Plotter.edges(V, blk.E, false);
                cent = mean(V,2);
                text(cent(1),cent(2),cent(3),b);
            end
            Plotter.plot(state.X_TARGETS,false,'rx');
            hold off
        end
        function state = copy(orig)
            state.table = orig.table;
            if ~isempty(keys(orig.blocks))
                state.blocks = containers.Map;
                blk_ids = keys(orig.blocks);
                for i = 1:numel(blk_ids)
                    state.blocks(blk_ids{i}) = orig.blocks(blk_ids{i}).copy();
                end
                state.support = containers.Map(keys(orig.support),values(orig.support));
            else
                state.blocks = containers.Map;
                state.support = containers.Map;
            end
            state.gripping = containers.Map(keys(orig.gripping),values(orig.gripping));
            state.jointAngles = orig.jointAngles;
            state.gripperOpenings = orig.gripperOpenings;
            state.arms = Arms();
            state.arms.update(state.jointAngles,state.gripperOpenings);
            state.X_TARGETS = orig.X_TARGETS;
        end
        function [suppmin, amin] = findSupport(state, blk_id)
            blk = state.blocks(blk_id);
            [V,~,~,~] = blk.transformed();
            p = mean(V,2);
            u = [0 0 -1]';
            amin = p(3);
            suppmin = 'table';
            blk_keys = keys(state.blocks);
            for k = 1:numel(blk_keys)
                if strcmp(blk_keys{k},blk_id), continue; end;
                s = state.blocks(blk_keys{k});
                [~,N,F,B] = s.transformed();
                for f = 1:numel(s.B)
                    [a,inside] = Geom.ray2PlaneSegment(p,u,F(:,f),N(:,f),B{f});
                    if inside && a < amin
                        amin = a;
                        suppmin = blk_keys{k};
                    end
                end
            end
            amin = amin - (p(3) - min(V(3,:)));
        end
        %%% Primitive Operators (Demo)
        function state = move(state, blk_id, x, M)
            state = State.copy(state);
            blk = state.blocks(blk_id);
%             c = mean(blk.V,2);
%             blk = blk.transform(eye(3),-c);
%             blk = blk.transform(blk.N(:,1:3)', [0 0 0]');
%             blk.N = orth(blk

            % overwrite, don't compose
            blk.M = M; blk.x = x;
            blk.I = []; blk.prox = []; % recalc
            state.blocks(blk_id) = blk;
        end
        function state = grasp(state, LR, blk)
            if ~strcmp(state.gripping(LR), 'nothing')
            	state = false;
                return;
            end;
            state = State.copy(state);
            state.gripping(LR) = blk;
            state.support(blk) = LR;
        end
        function state = release(state, LR)
            state = State.copy(state);
            blk_name = state.gripping(LR);
            state.gripping(LR) = 'nothing';
            % Find support
            [suppmin,amin] = State.findSupport(state,blk_name);
            blk = state.blocks(blk_name);
            state.blocks(blk_name) = blk.transform(eye(3),[0 0 -amin]');
            state.support(blk_name) = suppmin;
        end
        %%% Primitive operators (Execution)
        function state = setX_TARGETS(state, X_TARGETS)
            state = State.copy(state);
            state.X_TARGETS = X_TARGETS;
        end
        function state = stepArms(state, J, G)
            % J,G can be stacks of waypoints to avoid htn search recursion
            % overflow
            state = State.copy(state);
            state.jointAngles = J(:,:,end);
            state.gripperOpenings = G(:,end);
            state.arms.update(state.jointAngles, state.gripperOpenings);
            T = state.arms.T(:,:,:,8);
            LR = 'LR';
            for i = 1:2
                id = state.gripping(LR(i));
                if ~strcmp(id,'nothing')
                    blk = state.blocks(id);
                    h = max(blk.V(3,:))-min(blk.V(3,:));
                    blk = blk.reput(T(1:3,1:3,i), T(1:3,4,i)-[0 0 .6*h + .7]');
                    state.blocks(id) = blk;
                end
            end
        end
        %%% Non-primitive methods
        function tasks = moveArms(state, X_TARG, M_TARG, X_OBST, R_OBST, delta, steps)
            % Default motion params
            if nargin < 6, delta = 0.5; end; % joint speed
            if nargin < 7, steps = 100; end;
            dt = 0.25; % length of time-step between updates
            tol = 0.05; % tolerance for target reached
            % Update targets
            tasks(1).name = 'setX_TARGETS';
            tasks(1).args = {X_TARG};
            state = State.setX_TARGETS(state,X_TARG);
            % Try reaching (must loop rather than recurse to avoid stack
            % overflow)
            J = zeros([size(state.jointAngles) steps]);
            G = repmat(state.gripperOpenings,[1 steps]);
            for s = 1:steps
                dJ = state.arms.seek(X_TARG, X_OBST, R_OBST, delta);
                dJ(:,5:7) = state.arms.postureWrists(M_TARG);
                J(:,:,s) = state.jointAngles+dJ*dt;
                state = State.stepArms(state, J(:,:,s), G(:,s));
                % Check for success
                diff = X_TARG - state.arms.X(:,:,6);
                disp('norm')
%                 disp(diff);
%                 disp(X_TARG);
%                 disp(state.arms.X(:,:,6));
                diff = diff(~isnan(diff));
                disp(norm(diff));
                if (norm(diff) < tol) && (norm(dJ(:,5:7).^2) < tol)
                    tasks(2).name = 'stepArms';
                    tasks(2).args = {J(:,:,1:s), G(:,1:s)};
                    return;
                end
            end
            % If this code is reached, seeking failed
            tasks = false;
        end
        function tasks = changeGrip(state, WIDTHS)
            steps = 4;
            J = repmat(state.jointAngles,[1 1 steps]);
            dG = WIDTHS-state.gripperOpenings;
            G = bsxfun(@plus, state.gripperOpenings, dG*(1:steps)/steps);
            tasks(1).name = 'stepArms';
            tasks(1).args = {J, G};
        end
        function tasks = pickup(state, LR, blk_id)
            % Get block params
            blk = state.blocks(blk_id);
            h = max(blk.V(3,:))-min(blk.V(3,:));
            w = max(blk.V(2,:))-min(blk.V(2,:));
            %X_TARG = state.arms.X(:,:,6);
            X_TARG = nan(3,2);
            X_TARG(:,LR == 'LR') = blk.x + [0 0 .6*h+5]';
            M_TARG = {NaN, NaN};
            M_TARG{:,LR == 'LR'} = blk.M;
            % Make obstacles
            X_OBST = {zeros(3,0), zeros(3,0)};
            R_OBST = {zeros(1,0), zeros(1,0)};
            blk_ids = keys(state.blocks);
            for i = 1:numel(blk_ids)
                x = state.blocks(blk_ids{i}).x;
                r = norm(state.blocks(blk_ids{i}).V(:,end)); % block diag
                % Currently held blocks are not obstacles
                chrs = 'LR';
                if ~strcmp(blk_ids{i}, state.gripping(chrs(LR ~= 'LR')))
                    X_OBST{LR ~= 'LR'}(:,end+1) = x;
                    R_OBST{LR ~= 'LR'}(:,end+1) = r;
                end
                if ~strcmp(blk_id, blk_ids{i})
                    X_OBST{LR == 'LR'}(:,end+1) = x;
                    R_OBST{LR == 'LR'}(:,end+1) = r;
                end
            end
            WIDTHS = state.gripperOpenings;
            WIDTHS(LR == 'LR') = w;
            % Set tasks
            tasks(1).name = 'moveArms';
            tasks(1).args = {X_TARG, M_TARG, X_OBST, R_OBST};
            X_TARG(:,LR == 'LR') = blk.x + [0 0 .6*h+3]';
            tasks(2).name = 'moveArms';
            tasks(2).args = {X_TARG, M_TARG, X_OBST, R_OBST, 0.25}; % lower slowly
            tasks(3).name = 'changeGrip';
            tasks(3).args = {WIDTHS};
            tasks(4).name = 'grasp';
            tasks(4).args = {LR, blk_id};
            X_TARG(:,LR == 'LR') = blk.x + [0 0 .6*h+5]';
            tasks(5).name = 'moveArms';
            tasks(5).args = {X_TARG, M_TARG, X_OBST, R_OBST};
        end
        function tasks = putdown(state, LR, blk_id, dest_id, dM, dx)
            if ~strcmp(state.gripping(LR),blk_id)
                tasks = false;
                return;
            end
            % Get block params
            blk = state.blocks(blk_id);
            h = max(blk.V(3,:))-min(blk.V(3,:));
            if strcmp(dest_id,'table')
                dest = state.table;
                dx = blk.x;
                dx(3) = 0.5*h;
                dx = dx-dest.x;
            else
                dest = state.blocks(dest_id);
            end
            X_TARG = nan(3,2);
            X_TARG(:,LR == 'LR') = dest.x + dx + [0 0 .6*h+5]';
            M_TARG = {NaN, NaN};
            M_TARG{LR == 'LR'} = dM*dest.M;
            % Make obstacles
            X_OBST = {zeros(3,0), zeros(3,0)};
            R_OBST = {zeros(1,0), zeros(1,0)};
            blk_ids = keys(state.blocks);
            for i = 1:numel(blk_ids)
                x = state.blocks(blk_ids{i}).x;
                r = norm(state.blocks(blk_ids{i}).V(:,end)); % block diag
                % Currently held blocks are not obstacles
                chrs = 'LR';
                if ~strcmp(blk_ids{i}, state.gripping(chrs(LR ~= 'LR')))
                    X_OBST{LR ~= 'LR'}(:,end+1) = x;
                    R_OBST{LR ~= 'LR'}(:,end+1) = r;
                end
                if ~strcmp(blk_id, blk_ids{i}) && ~strcmp(dest_id, blk_ids{i})
                    X_OBST{LR == 'LR'}(:,end+1) = x;
                    R_OBST{LR == 'LR'}(:,end+1) = r;
                end
            end
            WIDTHS = state.gripperOpenings;
            WIDTHS(LR == 'LR') = 2;
            % Set tasks
            tasks(1).name = 'moveArms';
            tasks(1).args = {X_TARG, M_TARG, X_OBST, R_OBST};
            X_TARG(:,LR == 'LR') = dest.x + dx + [0 0 .6*h+3]';
            tasks(2).name = 'moveArms';
            tasks(2).args = {X_TARG, M_TARG, X_OBST, R_OBST, 0.25}; % lower slowly
            tasks(3).name = 'changeGrip';
            tasks(3).args = {WIDTHS};
            tasks(4).name = 'release';
            tasks(4).args = {LR};
            X_TARG(:,LR == 'LR') = dest.x + dx + [0 0 .6*h+5]';
            tasks(5).name = 'moveArms';
            tasks(5).args = {X_TARG, M_TARG, X_OBST, R_OBST};
        end
        %%% Task handles
        function ops = getOps
            ops = containers.Map;
            ops('move') = @State.move;
            ops('grasp') = @State.grasp;
            ops('release') = @State.release;
            ops('setX_TARGETS') = @State.setX_TARGETS;
            ops('stepArms') = @State.stepArms;
%             ops('moveArms') = @State.moveArms;
%             ops('shiftBlock') = @State.shiftBlock;
%             ops('pickup') = @State.pickup;
%             ops('carryBlock') = @State.carryBlock;
%             ops('putDown') = @State.putDown;
        end
        function mets = getMethods
            mets = containers.Map;
            mets('moveArms') = {@State.moveArms};
            mets('changeGrip') = {@State.changeGrip};
            mets('pickup') = {@State.pickup};
            mets('putdown') = {@State.putdown};
        end
        %%% Constructors
        function state0 = empty()
            % Setup initial state
            state0.table = Shape3D.makeBlock([20 12 5]',[1 1 1]');
            % state0.table = state0.table.transform(eye(3),[-10 -6 -20]');
            state0.table = state0.table.transform(eye(3),[0 0 -2.5]');
            state0.blocks = containers.Map;
            state0.support = containers.Map;
            state0.gripping = containers.Map;
            state0.gripping('L') = 'nothing';
            state0.gripping('R') = 'nothing';
            state0.jointAngles = zeros(2,7);
            state0.jointAngles(:,1) = [1 -1] *  pi/4;
            state0.jointAngles(:,3) = [1 -1] * -pi/2;
            state0.jointAngles(:,5) = [1 -1] *  pi/2;
            state0.jointAngles(:,6) = [1  1] *  pi/2;
            state0.gripperOpenings = [2 2]';
            state0.arms = Arms();
            state0.arms.update(state0.jointAngles, state0.gripperOpenings);
        end
        function state0 = rgbInit()
            % Setup initial state
            state0.table = Shape3D.makeBlock([20 12 20]',[1 1 1]');
            state0.table = state0.table.transform(eye(3),[-10 -6 -20]');
            %state0.table.spRender(vis);
            state0.blocks = containers.Map;
            blockpos = [-5 -0.5 0; -1 -0.5 0; 3 -0.5 0]';
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[1 0 0]');
            state0.blocks('block_1') = blk.transform(eye(3),blockpos(:,1));
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[0 1 0]');
            state0.blocks('block_2') = blk.transform(eye(3),blockpos(:,2));
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[0 0 1]');
            state0.blocks('block_3') = blk.transform(eye(3),blockpos(:,3));
%             blk_keys = keys(state0.blocks);
%             for k = 1:numel(blk_keys)
%                 blk_k = state0.blocks(blk_keys{k});
%                 blk_k.spRender(vis);
%             end
            state0.gripping = containers.Map;
            state0.gripping('L') = 'nothing';
            state0.gripping('R') = 'nothing';
            state0.support = containers.Map;
            state0.support('block_1') = 'table';
            state0.support('block_2') = 'table';
            state0.support('block_3') = 'table';
            state0.jointAngles = zeros(2,7);
            state0.gripperOpenings = [2 2];
            state0.arms = Arms();
            state0.arms.update(state0.jointAngles, state0.gripperOpenings);

        end
        function state0 = binoc()
            % Setup initial state
            state0.table = Shape3D.makeBlock([40 32 20]',[1 1 1]');
            state0.table = state0.table.transform(eye(3),[0 0 -10]');
            %state0.table.spRender(vis);
            state0.blocks = containers.Map;
            blockpos = [-5 -0.5 .75; -1 -5.5 .75; 3 -2.5 .75]';
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[1 0 0]');
            state0.blocks('block_1') = blk.transform(eye(3),blockpos(:,1));
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[0 1 0]');
            state0.blocks('block_2') = blk.transform(eye(3),blockpos(:,2));
            blk = Shape3D.makeBlock([1.5 1.5 1.5]',[0 0 1]');
            state0.blocks('block_3') = blk.transform(eye(3),blockpos(:,3));
            blk = Shape3D.makeBlock([100 1 100]',[.1 .1 .1]');
            state0.blocks('block_4') = blk.transform(eye(3),[0 32 0]');
%             blk_keys = keys(state0.blocks);
%             for k = 1:numel(blk_keys)
%                 blk_k = state0.blocks(blk_keys{k});
%                 blk_k.spRender(vis);
%             end
            state0.gripping = containers.Map;
            state0.gripping('L') = 'nothing';
            state0.gripping('R') = 'nothing';
            state0.support = containers.Map;
            state0.support('block_1') = 'table';
            state0.support('block_2') = 'table';
            state0.support('block_3') = 'table';
            state0.jointAngles = zeros(2,7);
            state0.gripperOpenings = [2 2];
            state0.arms = Arms();
            state0.arms.update(state0.jointAngles, state0.gripperOpenings);

        end
    end
end