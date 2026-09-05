#include "audio_ring.h"

#include <array>
#include <atomic>
#include <cassert>
#include <thread>
#include <vector>

int main() {
    AudioRing ring(4);
    const std::array<int16_t, 6> input{1, -1, 2, -2, 3, -3};
    AudioPushResult push = ring.push(input.data(), 3);
    assert(push.written_frames == 3 && push.dropped_frames == 0);
    assert(ring.size() == 3 && ring.capacity() == 4);

    std::array<int16_t, 4> output{};
    assert(ring.pop(output.data(), 2) == 2);
    assert((output == std::array<int16_t, 4>{1, -1, 2, -2}));

    const std::array<int16_t, 6> wrapped{4, -4, 5, -5, 6, -6};
    push = ring.push(wrapped.data(), 3);
    assert(push.written_frames == 3 && push.dropped_frames == 0);
    push = ring.push(wrapped.data(), 1);
    assert(push.written_frames == 0 && push.dropped_frames == 1);

    std::array<int16_t, 8> all{};
    assert(ring.pop(all.data(), 4) == 4);
    assert((all == std::array<int16_t, 8>{3, -3, 4, -4, 5, -5, 6, -6}));
    assert(ring.pop(output.data(), 2) == 0);

    assert(ring.push(input.data(), 3).written_frames == 3);
    ring.clear();
    assert(ring.size() == 0);

    constexpr size_t kCount = 100000;
    AudioRing concurrent(1024);
    std::atomic<bool> producer_done{false};
    std::atomic<size_t> consumed{0};
    std::thread producer([&] {
        size_t value = 0;
        while (value < kCount) {
            const int16_t sample[2]{static_cast<int16_t>(value),
                    static_cast<int16_t>(~value)};
            if (concurrent.push(sample, 1).written_frames == 1) ++value;
        }
        producer_done = true;
    });
    std::thread consumer([&] {
        size_t expected = 0;
        int16_t sample[2]{};
        while (!producer_done || concurrent.size() != 0) {
            if (concurrent.pop(sample, 1) == 0) continue;
            assert(sample[0] == static_cast<int16_t>(expected));
            assert(sample[1] == static_cast<int16_t>(~expected));
            ++expected;
        }
        consumed = expected;
    });
    producer.join();
    consumer.join();
    assert(consumed == kCount);
    return 0;
}
