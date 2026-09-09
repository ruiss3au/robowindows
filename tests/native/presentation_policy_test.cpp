#include "presentation_policy.h"
#include <cassert>
#include <array>

int main() {
    using namespace presentation;
    assert(allowed(Software) && allowed(Gpu) && !allowed(-1) && !allowed(2));
    const auto rect = fit(640, 480, 2000, 1200);
    assert(rect.x == 200 && rect.y == 0 && rect.width == 1600 && rect.height == 1200);
    const auto portrait = fit(640, 480, 600, 1000);
    assert(portrait.x == 0 && portrait.y == 275 && portrait.height == 450);
    assert(fit(0, 480, 100, 100).width == 0);
    std::array<uint8_t, 24> rows{1,2,3,4,5,6,7,8,99,99,99,99,9,10,11,12,13,14,15,16};
    std::vector<uint8_t> scratch;
    const auto* packed = packed_rows(rows.data(), 2, 2, 12, scratch);
    assert(packed == scratch.data() && scratch.size() == 16);
    for (int i = 0; i < 16; ++i) assert(packed[i] == i + 1);
    assert(packed_rows(rows.data(), 2, 2, 8, scratch) == rows.data());
    assert(!packed_rows(rows.data(), 2, 2, 7, scratch));
    assert(!valid_frame(4097, 1, 16388) && !valid_frame(1, 4097, 4));
    assert(interval_ns(Software) == 66000000);
    assert(next_deadline(1000000000, Gpu) == 1033333333);
    // A long stall creates one fresh deadline, never a catch-up burst.
    assert(next_deadline(9000000000, Gpu) == 9033333333);
}
