
% n = 0;
% jointInd = [1, 2, 4];
% % aux.initJointAngles(2, :) = zeros(1, aux.numJoints);
% 
% f1 = fopen('j3drand.csv', 'w');
% f2 = fopen('j3drand-spatial.csv', 'w');

load matlab/trans.mat

pt = [0, 0, 0, 1]';

ang = -0.25;
s0 = [cos(ang), 0, sin(ang), 0;...
      0,        1,        0, 0;...
      -sin(ang),0, cos(ang), 0;...
      0         0,        0, 1];

ang = -1;
s1 = [cos(ang), 0, sin(ang), 0;...
      0,        1,        0, 0;...
      -sin(ang),0, cos(ang), 0;...
      0         0,        0, 1];
  
ang = 2.618;
e1 = [cos(ang), 0, sin(ang), 0;...
      0,        1,        0, 0;...
      -sin(ang),0, cos(ang), 0;...
      0         0,        0, 1];
  

res = t11 * s0 * t9 * s1 * t7 * t6 * e1 * t4 * t3 * t2 * t1 * pt;
res([2, 3]) = [-res(3), res(2)];
res = res';

res2 = t11 * s0 * t9 * s1 * t7 * t6 * pt;
res2([2, 3]) = [-res2(3), res2(2)];
res2 = res2';

res3 = t11 * s0 * t9 * pt;
res3([2, 3]) = [-res3(3), res3(2)];
res3 = res3';

res4 = t11 * pt;
res4([2, 3]) = [-res4(3), res4(2)];
res4 = res4';

aux.drawMarkers = [res(1:3); res2(1:3); res3(1:3); res4(1:3)];

