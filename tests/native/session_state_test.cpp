#include <cstdlib>
#include <iostream>

#include "session_state.h"

static void require(bool value, const char* message) {
    if (!value) { std::cerr << message << '\n'; std::exit(1); }
}

int main() {
    using namespace robowindows;
    require(SessionStateAfterCoreCleanup(SESSION_GUEST_SHUTDOWN) == SESSION_GUEST_SHUTDOWN,
            "guest shutdown was lost during core cleanup");
    require(SessionStateAfterCoreCleanup(SESSION_RUNNING) == SESSION_STOPPED,
            "ordinary core completion did not become stopped");
    require(SessionStateAfterCoreCleanup(SESSION_FAILED) == SESSION_STOPPED,
            "failed core cleanup did not become stopped");
}
