# SMILE: Simulator for Maryland Imitation Learning Environment #

SMILE is a simulated environment for studying robot imitation learning based on the hypothesis that, in many situations, procedural tasks can be learned more effectively by observing object behaviors while completely ignoring the demonstrator’s motions. In other words, the demonstrator in the environment is intentionally made invisible to the robot learner. 

SMILE is a part of our [imitation learning project](http://www.cs.umd.edu/~reggia/onrImitLearn/index.html).

Primary features include:
* Recording demonstrations of procedural tasks using mouse inputs and GUI. A recorded demonstration consists of a sequence of images (a "video") and an accompanying text log.
* Programming behaviors of a simulated robot in the environment via a Matlab interface.
* Creating various simulated objects and controls for custom task scenarios using XML.

Refer to this [technical report](https://hdl.handle.net/1903/18066) for detailed descriptions.

## Prerequisite ##
* [Java Runtime Environment](http://java.com/en/download/) version 1.7 or above, although 1.6 or 1.5 may work too.
* Matlab is required only if you are planning to run Matlab scripts to controll the simulated robot.

## Getting started ##

1. Download the [latest zip file](https://github.com/dwhuang/SMILE/releases) and extract it somewhere.
1. Double click `SMILE.jar`, or use command line `java -jar SMILE.jar` to start SMILE.
1. Select screen resolution and click continue.
1. If everything goes well, a main screen containing a 3D simulated environment will appear.
1. Hit `ESC` anytime to quit.

SMILE has been tested on OS X, Windows, and Linux.

Refer to this [technical report](https://hdl.handle.net/1903/18066) for how to use SMILE.

## Usage examples ##

Recording a demonstration that builds a structure (letters "UM") using a set of blocks.

[![Recording a "UM" demonstration](https://img.youtube.com/vi/0M-LACmy7Cc/0.jpg)](https://youtu.be/0M-LACmy7Cc)

---

Recording a demonstration of a disk-drive dock maintenance task. The goal is to replace a "bad" drive as indicated by a red light. A switch must be turned off before removing a drive, and turned back on after a new drive is inserted.

[![Recording a maintenance demonstration](https://img.youtube.com/vi/YNeTfFfvIoo/0.jpg)](https://youtu.be/YNeTfFfvIoo)

---

A recorded demonstration video of the "UM" block stacking task. Note that since the demonstrator is made invisible in SMILE, the blocks appear to move on their own.

[![recorded "UM"](https://img.youtube.com/vi/Ia-EabIX8Sk/0.jpg)](https://youtu.be/Ia-EabIX8Sk)

---

A robot simulation performing a "UM" block stacking task.

[![robot "UM"](https://img.youtube.com/vi/oRbdmR1QjPg/0.jpg)](https://youtu.be/oRbdmR1QjPg)


## More Screenshots ##

An overview of SMILE's 3D simulated world

![showcase.png](https://bitbucket.org/repo/jxdd7X/images/3758731750-exview.png)

---

A contraption designed using SMILE's XML interface

![dock.png](https://bitbucket.org/repo/jxdd7X/images/1459767673-dock.png)

---

Loading a custom 3D model in STL format

![custom.png](https://bitbucket.org/repo/jxdd7X/images/2939431961-objcusctr-eps-converted-to.png)

---

The four states of three mutually-connected custom controls, specified using SMILE's XML interface

![custom.png](https://bitbucket.org/repo/jxdd7X/images/2217614476-objcustom.png)
