#pragma once

#include <condition_variable>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <vector>

struct PublishedFrame {
    size_t slot = 0;
    uint64_t sequence = 0;
    unsigned width = 0;
    unsigned height = 0;
    size_t pitch = 0;
    const uint8_t* pixels = nullptr;
    uint64_t coalesced = 0;
};

struct FramePublishResult {
    bool published = false;
    uint64_t coalesced = 0;
};

class FrameMailbox {
public:
    static constexpr size_t kSlotCount = 3;

    FramePublishResult publish(const void* pixels, unsigned width, unsigned height, size_t pitch);
    bool acquire_latest(PublishedFrame& frame);
    bool wait_acquire_latest(PublishedFrame& frame);
    bool wait_acquire_latest_for(PublishedFrame& frame, std::chrono::milliseconds timeout);
    void release(const PublishedFrame& frame);
    void stop();
    void reset();

private:
    enum class SlotState : uint8_t { Free, Writing, Published, Reading };
    struct Slot {
        SlotState state = SlotState::Free;
        uint64_t sequence = 0;
        unsigned width = 0;
        unsigned height = 0;
        size_t pitch = 0;
        std::vector<uint8_t> pixels;
    };

    bool acquire_latest_locked(PublishedFrame& frame);

    std::mutex mutex_;
    std::condition_variable ready_;
    Slot slots_[kSlotCount];
    uint64_t next_sequence_ = 1;
    bool stopped_ = false;
};
