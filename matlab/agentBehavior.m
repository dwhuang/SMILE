%% This file should provide two function pointers indicating the "init" and 
% "callback" functions for the agent.

function [init, callback] = agentBehavior()

init = @exampleInit;
callback = @exampleCallback;

end
