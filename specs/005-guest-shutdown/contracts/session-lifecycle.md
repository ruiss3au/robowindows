# Session Lifecycle Contract

Native session states are stable at the Java boundary:

| Value | State | Meaning |
|---:|---|---|
| 0 | stopped | No active or unacknowledged session |
| 1 | starting | Core thread is loading the guest |
| 2 | running | Guest is executing |
| 3 | failed | Guest could not load |
| 4 | guest shutdown | Guest requested power-off; Android has not acknowledged it |

`RETRO_ENVIRONMENT_SHUTDOWN` transitions the host to `guest shutdown` and asks
the core loop to finish. Core cleanup MUST preserve that state. Android observes
it, navigates to Machines, and calls `stopSession`; that join/acknowledgement
transitions the state to `stopped`.

Explicit RoboWindows Restart uses the reset request and never enters `guest
shutdown`. A BIOS reboot remains inside the generated configuration boot loop.

