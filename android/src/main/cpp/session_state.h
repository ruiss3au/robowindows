#pragma once

namespace robowindows {

enum SessionState {
    SESSION_STOPPED = 0,
    SESSION_STARTING = 1,
    SESSION_RUNNING = 2,
    SESSION_FAILED = 3,
    SESSION_GUEST_SHUTDOWN = 4,
};

inline int SessionStateAfterCoreCleanup(int state) {
    return state == SESSION_GUEST_SHUTDOWN ? SESSION_GUEST_SHUTDOWN : SESSION_STOPPED;
}

}
