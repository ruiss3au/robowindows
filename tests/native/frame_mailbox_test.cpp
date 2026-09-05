#include "frame_mailbox.h"

#include <array>
#include <atomic>
#include <cassert>
#include <thread>

int main() {
    FrameMailbox mailbox;
    std::array<uint32_t, 8> first{};
    first.fill(0x11223344u);
    auto result = mailbox.publish(first.data(), 4, 2, 4 * sizeof(uint32_t));
    assert(result.published && result.coalesced == 0);

    PublishedFrame frame{};
    assert(mailbox.acquire_latest(frame));
    assert(frame.width == 4 && frame.height == 2);
    assert(reinterpret_cast<const uint32_t*>(frame.pixels)[7] == 0x11223344u);
    mailbox.release(frame);
    assert(!mailbox.acquire_latest(frame));

    std::array<uint32_t, 4> values{};
    for (uint32_t value = 1; value <= 3; ++value) {
        values.fill(value);
        assert(mailbox.publish(values.data(), 2, 2, 8).published);
    }
    assert(mailbox.acquire_latest(frame));
    assert(reinterpret_cast<const uint32_t*>(frame.pixels)[0] == 3);
    assert(frame.sequence == 4);
    assert(frame.coalesced == 2);
    mailbox.release(frame);

    values.fill(4);
    assert(mailbox.publish(values.data(), 2, 2, 8).published);
    assert(mailbox.acquire_latest(frame));
    values.fill(5);
    assert(mailbox.publish(values.data(), 2, 2, 8).published);
    values.fill(6);
    assert(mailbox.publish(values.data(), 2, 2, 8).published);
    values.fill(7);
    result = mailbox.publish(values.data(), 2, 2, 8);
    assert(result.published && result.coalesced == 1);
    mailbox.release(frame);
    assert(mailbox.acquire_latest(frame));
    assert(reinterpret_cast<const uint32_t*>(frame.pixels)[0] == 7);
    mailbox.release(frame);

    mailbox.stop();
    assert(!mailbox.publish(values.data(), 2, 2, 8).published);
    assert(!mailbox.wait_acquire_latest(frame));
    mailbox.reset();

    std::atomic<bool> acquired{false};
    std::thread consumer([&] {
        PublishedFrame waited{};
        acquired = mailbox.wait_acquire_latest(waited);
        assert(waited.width == 3 && waited.height == 1);
        mailbox.release(waited);
    });
    std::array<uint32_t, 3> resized{9, 9, 9};
    assert(mailbox.publish(resized.data(), 3, 1, 12).published);
    consumer.join();
    assert(acquired);

    mailbox.stop();
    return 0;
}
