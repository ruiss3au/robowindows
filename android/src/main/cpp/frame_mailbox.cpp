#include "frame_mailbox.h"

#include <cstring>
#include <limits>
#include "presentation_policy.h"

FramePublishResult FrameMailbox::publish(const void* pixels, unsigned width, unsigned height,
        size_t pitch) {
    if (!pixels || !presentation::valid_frame(width, height, pitch)) return {};
    if (height > std::numeric_limits<size_t>::max() / pitch) return {};
    const size_t byte_count = pitch * static_cast<size_t>(height);

    size_t selected = kSlotCount;
    uint64_t coalesced = 0;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopped_) return {};
        for (size_t i = 0; i < kSlotCount; ++i) {
            if (slots_[i].state == SlotState::Free) {
                selected = i;
                break;
            }
        }
        if (selected == kSlotCount) {
            uint64_t oldest = std::numeric_limits<uint64_t>::max();
            for (size_t i = 0; i < kSlotCount; ++i) {
                if (slots_[i].state == SlotState::Published && slots_[i].sequence < oldest) {
                    selected = i;
                    oldest = slots_[i].sequence;
                }
            }
            if (selected == kSlotCount) return {};
            coalesced = 1;
        }
        slots_[selected].state = SlotState::Writing;
    }

    Slot& slot = slots_[selected];
    slot.pixels.resize(byte_count);
    std::memcpy(slot.pixels.data(), pixels, byte_count);

    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopped_) {
            slot.state = SlotState::Free;
            return {};
        }
        slot.width = width;
        slot.height = height;
        slot.pitch = pitch;
        slot.sequence = next_sequence_++;
        slot.state = SlotState::Published;
    }
    ready_.notify_one();
    return {true, coalesced};
}

bool FrameMailbox::acquire_latest_locked(PublishedFrame& frame) {
    size_t newest = kSlotCount;
    uint64_t sequence = 0;
    for (size_t i = 0; i < kSlotCount; ++i) {
        if (slots_[i].state == SlotState::Published && slots_[i].sequence > sequence) {
            newest = i;
            sequence = slots_[i].sequence;
        }
    }
    if (newest == kSlotCount) return false;

    uint64_t coalesced = 0;
    for (size_t i = 0; i < kSlotCount; ++i) {
        if (i != newest && slots_[i].state == SlotState::Published) {
            slots_[i].state = SlotState::Free;
            ++coalesced;
        }
    }
    Slot& slot = slots_[newest];
    slot.state = SlotState::Reading;
    frame = {newest, slot.sequence, slot.width, slot.height, slot.pitch, slot.pixels.data(),
            coalesced};
    return true;
}

bool FrameMailbox::acquire_latest(PublishedFrame& frame) {
    std::lock_guard<std::mutex> lock(mutex_);
    return acquire_latest_locked(frame);
}

bool FrameMailbox::wait_acquire_latest(PublishedFrame& frame) {
    std::unique_lock<std::mutex> lock(mutex_);
    ready_.wait(lock, [this] {
        if (stopped_) return true;
        for (const Slot& slot : slots_) {
            if (slot.state == SlotState::Published) return true;
        }
        return false;
    });
    return !stopped_ && acquire_latest_locked(frame);
}

void FrameMailbox::release(const PublishedFrame& frame) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (frame.slot >= kSlotCount) return;
    Slot& slot = slots_[frame.slot];
    if (slot.state == SlotState::Reading && slot.sequence == frame.sequence) {
        slot.state = SlotState::Free;
    }
}

bool FrameMailbox::wait_acquire_latest_for(PublishedFrame& frame, std::chrono::milliseconds timeout) {
    std::unique_lock<std::mutex> lock(mutex_);
    ready_.wait_for(lock, timeout, [this] {
        if (stopped_) return true;
        for (const auto& slot : slots_) if (slot.state == SlotState::Published) return true;
        return false;
    });
    return !stopped_ && acquire_latest_locked(frame);
}

void FrameMailbox::stop() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        stopped_ = true;
    }
    ready_.notify_all();
}

void FrameMailbox::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    stopped_ = false;
    next_sequence_ = 1;
    for (Slot& slot : slots_) {
        slot.state = SlotState::Free;
        slot.sequence = 0;
    }
}
