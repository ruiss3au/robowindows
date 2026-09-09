#pragma once
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <vector>
#include "frame_mailbox.h"

// Construct/use/destroy only on the presenter thread.
class GpuPresenter {
public:
    ~GpuPresenter() { reset(); }
    bool initialize(ANativeWindow* window);
    bool present(const PublishedFrame& frame, uint64_t& upload_draw_us, uint64_t& swap_us,
            bool verify_fixture = false);
    void reset();
private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface surface_ = EGL_NO_SURFACE;
    GLuint program_ = 0, texture_ = 0;
    unsigned width_ = 0, height_ = 0;
    std::vector<uint8_t> scratch_;
};
