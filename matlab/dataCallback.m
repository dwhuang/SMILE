% j = sensor.jointAngles(2, jointInd);


% fprintf(f1, '%d,0,%.6f,%.6f,%.6f\n', n, sensor.jointAngles(2, jointInd));
% fprintf(f2, '%d,0,%.6f,%.6f,%.6f\n', n, sensor.endEffPos(2, :));
% if mod(n, 100) == 0
%     fprintf('%d,0,%.6f,%.6f,%.6f\n', n, sensor.endEffPos(2, :));
% end
% 
% n = n + 1;

% motor.jointAngles = zeros(size(aux.initJointAngles));
% motor.jointAngles(2, jointInd) = rand(1, size(jointInd, 2)) ...
%     .* (aux.maxJointAngles(2, jointInd) - aux.minJointAngles(2, jointInd)) ...
%     + aux.minJointAngles(2, jointInd);

sensor.jointAngles(2, [1, 2, 4])
sensor.endEffPos(2, :)
aux.exit = 1;

% if n > 10000
%     aux.exit = 1;
%     fclose(f1);
%     fclose(f2);
% end
    