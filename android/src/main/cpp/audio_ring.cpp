#include "audio_ring.h"

#include <algorithm>

AudioRing::AudioRing(size_t capacity_frames) : frames_(std::max<size_t>(1, capacity_frames)) {}

AudioPushResult AudioRing::push(const int16_t* samples, size_t frames) {
    if (!samples || frames == 0) return {};
    const uint64_t write = write_.load(std::memory_order_relaxed);
    const uint64_t read = read_.load(std::memory_order_acquire);
    const size_t used = static_cast<size_t>(write - read);
    const size_t available = frames_.size() - std::min(used, frames_.size());
    const size_t written = std::min(frames, available);
    for (size_t i = 0; i < written; ++i) {
        StereoFrame& target = frames_[(write + i) % frames_.size()];
        target.left = samples[i * 2];
        target.right = samples[i * 2 + 1];
    }
    write_.store(write + written, std::memory_order_release);
    return {written, frames - written};
}

size_t AudioRing::pop(int16_t* samples, size_t frames) {
    if (!samples || frames == 0) return 0;
    const uint64_t read = read_.load(std::memory_order_relaxed);
    const uint64_t write = write_.load(std::memory_order_acquire);
    const size_t available = static_cast<size_t>(write - read);
    const size_t popped = std::min(frames, available);
    for (size_t i = 0; i < popped; ++i) {
        const StereoFrame& source = frames_[(read + i) % frames_.size()];
        samples[i * 2] = source.left;
        samples[i * 2 + 1] = source.right;
    }
    read_.store(read + popped, std::memory_order_release);
    return popped;
}

size_t AudioRing::size() const {
    const uint64_t write = write_.load(std::memory_order_acquire);
    const uint64_t read = read_.load(std::memory_order_acquire);
    return std::min(static_cast<size_t>(write - read), frames_.size());
}

size_t AudioRing::capacity() const {
    return frames_.size();
}

void AudioRing::clear() {
    read_.store(write_.load(std::memory_order_acquire), std::memory_order_release);
}
