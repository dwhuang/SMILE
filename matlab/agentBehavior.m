function [init, callback] = agentBehavior()
init = @dof2TestingInit;
callback = @dof2TestingCallback;
end