#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

struct StereoFrame {
    int16_t left = 0;
    int16_t right = 0;
};

struct AudioPushResult {
    size_t written_frames = 0;
    size_t dropped_frames = 0;
};

class AudioRing {
public:
    explicit AudioRing(size_t capacity_frames);

    AudioPushResult push(const int16_t* interleaved_samples, size_t frames);
    size_t pop(int16_t* interleaved_samples, size_t frames);
    size_t size() const;
    size_t capacity() const;
    void clear();

private:
    std::vector<StereoFrame> frames_;
    alignas(64) std::atomic<uint64_t> read_{0};
    alignas(64) std::atomic<uint64_t> write_{0};
};
