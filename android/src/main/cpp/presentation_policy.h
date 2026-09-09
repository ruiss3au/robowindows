#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace presentation {
constexpr int Software = 0;
constexpr int Gpu = 1;
inline bool allowed(int policy) { return policy == Software || policy == Gpu; }
inline int64_t interval_ns(int policy) { return policy == Gpu ? 33333333 : 66000000; }

struct Rect { int x, y, width, height; };
inline Rect fit(unsigned width, unsigned height, int output_width, int output_height) {
    if (!width || !height || output_width <= 0 || output_height <= 0) return {};
    const double scale = std::min(double(output_width) / width, double(output_height) / height);
    const int w = std::max(1, int(width * scale)), h = std::max(1, int(height * scale));
    return {(output_width - w) / 2, (output_height - h) / 2, w, h};
}
// Bound untrusted core dimensions before allocating/copying or uploading.
inline bool valid_frame(unsigned width, unsigned height, size_t pitch) {
    return width && height && width <= 4096 && height <= 4096 &&
            pitch >= size_t(width) * 4 && pitch <= 4096 * 4;
}
inline const uint8_t* packed_rows(const uint8_t* pixels, unsigned width, unsigned height,
        size_t pitch, std::vector<uint8_t>& scratch) {
    if (!pixels || !valid_frame(width, height, pitch)) return nullptr;
    const size_t row = size_t(width) * 4;
    if (pitch == row) return pixels;
    scratch.resize(row * height);
    for (unsigned y = 0; y < height; ++y) {
        std::memcpy(scratch.data() + y * row, pixels + y * pitch, row);
    }
    return scratch.data();
}
// Next deadline is based on actual presentation start: no accumulated catch-up debt.
inline int64_t next_deadline(int64_t start, int policy) { return start + interval_ns(policy); }
}
