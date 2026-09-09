#include "gpu_presenter.h"
#include "presentation_policy.h"
#include <chrono>

namespace {
using Clock = std::chrono::steady_clock;
uint64_t elapsed_us(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start).count();
}
GLuint shader(GLenum type, const char* source) {
    GLuint result = glCreateShader(type);
    glShaderSource(result, 1, &source, nullptr);
    glCompileShader(result);
    GLint ok = 0;
    glGetShaderiv(result, GL_COMPILE_STATUS, &ok);
    if (!ok) { glDeleteShader(result); return 0; }
    return result;
}
}

bool GpuPresenter::initialize(ANativeWindow* window) {
    reset();
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY || !eglInitialize(display_, nullptr, nullptr) ||
            !eglBindAPI(EGL_OPENGL_ES_API)) return false;
    const EGLint attributes[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig config;
    EGLint count = 0, format = 0;
    if (!eglChooseConfig(display_, attributes, &config, 1, &count) || count != 1 ||
            !eglGetConfigAttrib(display_, config, EGL_NATIVE_VISUAL_ID, &format)) return false;
    // Reset any previous software buffer dimensions to the surface's native size.
    if (ANativeWindow_setBuffersGeometry(window, 0, 0, format) != 0) return false;
    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    context_ = eglCreateContext(display_, config, EGL_NO_CONTEXT, context_attributes);
    if (context_ == EGL_NO_CONTEXT) return false;
    surface_ = eglCreateWindowSurface(display_, config, window, nullptr);
    if (surface_ == EGL_NO_SURFACE ||
            !eglMakeCurrent(display_, surface_, surface_, context_) || !eglSwapInterval(display_, 1)) return false;
    const char* vertex = "attribute vec2 position; attribute vec2 texcoord; varying vec2 uv;"
            "void main(){gl_Position=vec4(position,0.0,1.0);uv=texcoord;}";
    // Android ARM64 is little endian: XRGB8888 memory is B,G,R,X.
    const char* fragment = "precision mediump float; uniform sampler2D frame; varying vec2 uv;"
            "void main(){gl_FragColor=vec4(texture2D(frame,uv).bgr,1.0);}";
    GLuint vs = shader(GL_VERTEX_SHADER, vertex), fs = shader(GL_FRAGMENT_SHADER, fragment);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return false; }
    program_ = glCreateProgram();
    glAttachShader(program_, vs); glAttachShader(program_, fs);
    glBindAttribLocation(program_, 0, "position"); glBindAttribLocation(program_, 1, "texcoord");
    glLinkProgram(program_); glDeleteShader(vs); glDeleteShader(fs);
    GLint linked = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &linked);
    if (!linked) return false;
    glUseProgram(program_);
    glUniform1i(glGetUniformLocation(program_, "frame"), 0);
    glGenTextures(1, &texture_); glBindTexture(GL_TEXTURE_2D, texture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return glGetError() == GL_NO_ERROR;
}

bool GpuPresenter::present(const PublishedFrame& frame, uint64_t& upload_draw_us, uint64_t& swap_us,
        bool verify_fixture) {
    const auto start = Clock::now();
    const auto* pixels = presentation::packed_rows(frame.pixels, frame.width, frame.height, frame.pitch, scratch_);
    if (!pixels) return false;
    EGLint w = 0, h = 0;
    if (!eglQuerySurface(display_, surface_, EGL_WIDTH, &w) ||
            !eglQuerySurface(display_, surface_, EGL_HEIGHT, &h) || w <= 0 || h <= 0) return false;
    GLint max_texture = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max_texture);
    if (frame.width > unsigned(max_texture) || frame.height > unsigned(max_texture)) return false;
    glBindTexture(GL_TEXTURE_2D, texture_);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    if (width_ != frame.width || height_ != frame.height) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, frame.width, frame.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        width_ = frame.width; height_ = frame.height;
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }
    glViewport(0, 0, w, h); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    const auto rect = presentation::fit(frame.width, frame.height, w, h);
    glViewport(rect.x, h - rect.y - rect.height, rect.width, rect.height);
    // Texture row zero is the guest's top row, not OpenGL's bottom row.
    const GLfloat vertices[] = {-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0};
    glUseProgram(program_);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), vertices);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), vertices + 2);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    if (verify_fixture) {
        // Disposable synthetic patterns only. Never enabled for guest rendering.
        for (int row = 0; row < 3; ++row) for (int column = 0; column < 3; ++column) {
            const int x = rect.x + rect.width * (column * 2 + 1) / 6;
            const int y = rect.y + rect.height * (row * 2 + 1) / 6;
            const unsigned sx = unsigned((x - rect.x + 0.5) * frame.width / rect.width);
            const unsigned sy = unsigned((y - rect.y + 0.5) * frame.height / rect.height);
            uint32_t expected;
            std::memcpy(&expected, frame.pixels + sy * frame.pitch + sx * 4, 4);
            GLubyte pixel[4]{};
            glReadPixels(x, h - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            if (pixel[0] != ((expected >> 16) & 255) || pixel[1] != ((expected >> 8) & 255) ||
                    pixel[2] != (expected & 255) || pixel[3] != 255) return false;
        }
        if (rect.x || rect.y) {
            GLubyte black[4]{};
            glReadPixels(0, h - 1, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, black);
            if (black[0] || black[1] || black[2] || black[3] != 255) return false;
        }
    }
    upload_draw_us = elapsed_us(start);
    if (glGetError() != GL_NO_ERROR) return false;
    const auto swap_start = Clock::now();
    const bool ok = eglSwapBuffers(display_, surface_) == EGL_TRUE;
    swap_us = elapsed_us(swap_start);
    return ok;
}

void GpuPresenter::reset() {
    if (display_ != EGL_NO_DISPLAY) {
        if (context_ != EGL_NO_CONTEXT && eglGetCurrentContext() == context_) {
            if (texture_) glDeleteTextures(1, &texture_);
            if (program_) glDeleteProgram(program_);
        }
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) eglDestroySurface(display_, surface_);
        if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
        eglTerminate(display_);
    }
    display_ = EGL_NO_DISPLAY; surface_ = EGL_NO_SURFACE; context_ = EGL_NO_CONTEXT;
    program_ = texture_ = width_ = height_ = 0;
}
