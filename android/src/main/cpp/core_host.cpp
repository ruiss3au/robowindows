#include "core_host.h"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <aaudio/AAudio.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdarg>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>

#include "libretro.h"
#include "audio_output.h"
#include "audio_ring.h"
#include "frame_mailbox.h"
#include "frame_presenter.h"
#include "runtime_telemetry.h"
#include "session_state.h"

namespace {
std::mutex lifecycle_mutex;
std::thread core_thread;
std::atomic<bool> running{false};
std::atomic<bool> paused{false};
std::atomic<bool> shutdown_requested{false};
std::atomic<bool> reset_requested{false};
std::atomic<int> session_state{robowindows::SESSION_STOPPED};
// DOSBox Pure is linked into this process. Its retro_deinit implementation
// releases frame buffers but does not reset all of its process-global runtime
// state, so a second retro_init after a completed guest is not a supported
// session boundary. Keep the frontend initialized and use unload/load between
// RoboWindows sessions instead.
bool core_initialized = false;
std::string content_path;
std::string content_directory;
std::string system_path;
std::string save_path;

std::atomic<bool> video_enabled{false};
std::atomic<uint64_t> submitted_frames{0};
std::atomic<double> requested_run_fps{60.0};

std::mutex input_mutex;
std::array<bool, RETROK_LAST> keys{};
float mouse_x = 0;
float mouse_y = 0;
int mouse_buttons = 0;
float wheel_v = 0;
float wheel_h = 0;
retro_keyboard_event_t keyboard_callback = nullptr;
retro_disk_control_ext_callback disk_control{};
bool has_disk_control = false;
std::mutex media_mutex;
std::string pending_media;

std::mutex audio_control_mutex;
// Keep enough decoded audio to absorb short Android scheduling stalls without
// allowing the real-time callback to underrun during guest timing changes.
AudioRing audio_ring(16384);
AudioOutputState audio_output_state;
AAudioStream* audio_stream = nullptr;
std::atomic<bool> audio_recovery_requested{false};
std::atomic<bool> logged_non_silent_audio{false};
int audio_sample_rate = 48000;
RuntimeTelemetry telemetry;
FrameMailbox frame_mailbox;
FramePresenter frame_presenter(frame_mailbox, telemetry);
std::chrono::steady_clock::time_point telemetry_report_time;

void report_telemetry_if_due() {
    const auto now = std::chrono::steady_clock::now();
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            now - telemetry_report_time).count();
    if (elapsed < 1000) return;
    RuntimeTelemetrySnapshot snapshot = telemetry.take_snapshot(static_cast<uint64_t>(elapsed));
    __android_log_print(ANDROID_LOG_INFO, "RoboWindowsTelemetry",
            "schema=1 interval_ms=%llu state=%s audio_state=%s run=%llu audio_produced=%llu "
            "audio_consumed=%llu queue_min=%llu queue_max=%llu underruns=%llu "
            "missing=%llu dropped=%llu saturated=%llu stream_errors=%llu submitted=%llu published=%llu "
            "presented=%llu coalesced=%llu post_failures=%llu",
            static_cast<unsigned long long>(snapshot.interval_ms),
            runtime_state_name(snapshot.runtime_state),
            audio_phase_name(audio_output_state.phase()),
            static_cast<unsigned long long>(snapshot.emulator_run_calls),
            static_cast<unsigned long long>(snapshot.audio_produced_frames),
            static_cast<unsigned long long>(snapshot.audio_consumed_frames),
            static_cast<unsigned long long>(snapshot.audio_queue_frames_min),
            static_cast<unsigned long long>(snapshot.audio_queue_frames_max),
            static_cast<unsigned long long>(snapshot.audio_underrun_callbacks),
            static_cast<unsigned long long>(snapshot.audio_missing_frames),
            static_cast<unsigned long long>(snapshot.audio_dropped_frames),
            static_cast<unsigned long long>(snapshot.audio_saturated_samples),
            static_cast<unsigned long long>(snapshot.audio_stream_errors),
            static_cast<unsigned long long>(snapshot.guest_frames_submitted),
            static_cast<unsigned long long>(snapshot.frames_published),
            static_cast<unsigned long long>(snapshot.frames_presented),
            static_cast<unsigned long long>(snapshot.frames_coalesced),
            static_cast<unsigned long long>(snapshot.surface_post_failures));
    telemetry_report_time = now;
}

void log_line(enum retro_log_level level, const char* format, ...) {
    int priority = level == RETRO_LOG_ERROR ? ANDROID_LOG_ERROR :
            level == RETRO_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO;
    va_list args;
    va_start(args, format);
    __android_log_vprint(priority, "RoboWindowsCore", format, args);
    va_end(args);
}

const char* option_value(const char* key) {
    if (!std::strcmp(key, "dosbox_pure_on_screen_keyboard")) return "false";
    if (!std::strcmp(key, "dosbox_pure_mouse_input")) return "true";
    if (!std::strcmp(key, "dosbox_pure_menu_time")) return "0";
    if (!std::strcmp(key, "dosbox_pure_voodoo")) return "false";
    return nullptr;
}

bool environment(unsigned command, void* data) {
    switch (command) {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            static_cast<retro_log_callback*>(data)->log = log_line;
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char**>(data) = system_path.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char**>(data) = save_path.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CONTENT_DIRECTORY:
            *static_cast<const char**>(data) = content_directory.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *static_cast<unsigned*>(data) = 2;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* variable = static_cast<retro_variable*>(data);
            variable->value = option_value(variable->key);
            return variable->value != nullptr;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            return *static_cast<retro_pixel_format*>(data) == RETRO_PIXEL_FORMAT_XRGB8888;
        case RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK:
            keyboard_callback = static_cast<retro_keyboard_callback*>(data)->callback;
            return true;
        case RETRO_ENVIRONMENT_SHUTDOWN:
            session_state = robowindows::SESSION_GUEST_SHUTDOWN;
            shutdown_requested = true;
            __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                    "guest requested shutdown");
            return true;
        case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
            *static_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_THROTTLE_STATE: {
            // Returning false asks DOSBox Pure to use its current advertised guest frame
            // rate. Reporting a fabricated 60 Hz for a 70.087 Hz VGA mode makes the core
            // submit 800 frames per run and therefore about 56 kHz into a 48 kHz stream.
            return false;
        }
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            const auto* av = static_cast<const retro_system_av_info*>(data);
            if (av && av->timing.fps > 1.0) {
                requested_run_fps.store(av->timing.fps, std::memory_order_relaxed);
                __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                        "guest timing update fps=%.6f audio=%.0f",
                        av->timing.fps, av->timing.sample_rate);
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            return true;
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE:
            disk_control = *static_cast<retro_disk_control_ext_callback*>(data);
            has_disk_control = true;
            return true;
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            auto* basic = static_cast<retro_disk_control_callback*>(data);
            disk_control = {basic->set_eject_state, basic->get_eject_state,
                    basic->get_image_index, basic->set_image_index, basic->get_num_images,
                    basic->replace_image_index, basic->add_image_index, nullptr, nullptr, nullptr};
            has_disk_control = true;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
            return false;
        default:
            return false;
    }
}

void video_refresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !video_enabled || width == 0 || height == 0) return;
    telemetry.add_guest_frames_submitted();
    uint64_t frame_number = ++submitted_frames;
    if (frame_number == 1) {
        const auto* pixels = static_cast<const uint32_t*>(data);
        size_t pixel_count = static_cast<size_t>(width) * height;
        uint32_t sample = 2166136261u;
        size_t stride = std::max<size_t>(1, pixel_count / 1024);
        for (size_t i = 0; i < pixel_count; i += stride) sample = (sample ^ pixels[i]) * 16777619u;
        __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                "first guest frame %ux%u pitch=%zu sample=%08x", width, height, pitch, sample);
    }
    const FramePublishResult result = frame_mailbox.publish(data, width, height, pitch);
    if (result.published) telemetry.add_frames_published();
    telemetry.add_frames_coalesced(result.coalesced);
}

aaudio_data_callback_result_t audio_callback(AAudioStream*, void*, void* output,
        int32_t frames) {
    auto* samples = static_cast<int16_t*>(output);
    const size_t requested_frames = static_cast<size_t>(frames);
    const size_t requested_samples = requested_frames * 2;
    if (audio_output_state.phase() != AudioPhase::Playing) {
        std::fill(samples, samples + requested_samples, 0);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    const size_t popped_frames = audio_ring.pop(samples, requested_frames);
    std::fill(samples + popped_frames * 2, samples + requested_samples, 0);
    telemetry.add_audio_consumed_frames(popped_frames);
    telemetry.observe_audio_queue_frames(audio_ring.size());
    if (popped_frames < requested_frames) {
        telemetry.add_audio_underrun(requested_frames - popped_frames);
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void audio_error_callback(AAudioStream*, void*, aaudio_result_t) {
    telemetry.add_audio_stream_errors();
    audio_output_state.begin_recovery();
    audio_recovery_requested.store(true, std::memory_order_release);
}

size_t audio_batch(const int16_t* samples, size_t frames) {
    if (!samples) return frames;
    telemetry.add_audio_produced_frames(frames);
    size_t count = frames * 2;
    uint64_t saturated = 0;
    for (size_t i = 0; i < count; ++i) {
        if (samples[i] == INT16_MIN || samples[i] == INT16_MAX) ++saturated;
    }
    telemetry.add_audio_saturated_samples(saturated);
    if (!logged_non_silent_audio.load(std::memory_order_relaxed)) {
        int peak = 0;
        for (size_t i = 0; i < count; ++i) peak = std::max(peak, std::abs((int)samples[i]));
        if (peak && !logged_non_silent_audio.exchange(true)) {
            __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                    "first non-silent guest audio peak=%d frames=%zu", peak, frames);
        }
    }
    const AudioPushResult pushed = audio_ring.push(samples, frames);
    telemetry.add_audio_dropped_frames(pushed.dropped_frames);
    const size_t queued_frames = audio_ring.size();
    telemetry.observe_audio_queue_frames(queued_frames);
    const size_t prebuffer_frames = static_cast<size_t>(audio_sample_rate) / 5;
    const bool should_start = audio_stream && queued_frames >= prebuffer_frames &&
            audio_output_state.mark_playing();
    if (should_start) {
        aaudio_result_t result = AAudioStream_requestStart(audio_stream);
        __android_log_print(result == AAUDIO_OK ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
                "RoboWindowsCore", "audio prebuffered start result=%d frames=%zu",
                result, prebuffer_frames);
        if (result != AAUDIO_OK) {
            audio_output_state.begin_recovery();
            audio_ring.clear();
            telemetry.add_audio_stream_errors();
            audio_recovery_requested.store(true, std::memory_order_release);
        }
    }
    return frames;
}

bool open_audio_stream_locked(int sample_rate) {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "RoboWindowsCore",
                "audio builder failed result=%d", result);
        return false;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, sample_rate);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(builder, audio_callback, nullptr);
    AAudioStreamBuilder_setErrorCallback(builder, audio_error_callback, nullptr);
    result = AAudioStreamBuilder_openStream(builder, &audio_stream);
    if (result == AAUDIO_OK) {
        audio_sample_rate = AAudioStream_getSampleRate(audio_stream);
        __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                "audio stream open requested_rate=%d actual_rate=%d burst=%d",
                sample_rate, audio_sample_rate, AAudioStream_getFramesPerBurst(audio_stream));
    } else {
        audio_stream = nullptr;
    }
    AAudioStreamBuilder_delete(builder);
    return result == AAUDIO_OK;
}

void start_audio(int sample_rate) {
    std::lock_guard<std::mutex> control_lock(audio_control_mutex);
    audio_sample_rate = sample_rate;
    audio_recovery_requested = false;
    audio_ring.clear();
    audio_output_state.initialize();
    if (!open_audio_stream_locked(sample_rate)) {
        audio_output_state.begin_recovery();
        telemetry.add_audio_stream_errors();
        audio_recovery_requested = true;
    }
}

void suspend_audio() {
    audio_output_state.suspend();
    std::lock_guard<std::mutex> control_lock(audio_control_mutex);
    if (audio_stream) {
        const aaudio_stream_state_t before = AAudioStream_getState(audio_stream);
        if (before == AAUDIO_STREAM_STATE_STARTED || before == AAUDIO_STREAM_STATE_STARTING) {
            AAudioStream_requestPause(audio_stream);
            aaudio_stream_state_t after = before;
            AAudioStream_waitForStateChange(audio_stream, before, &after, 100000000);
        }
    }
    audio_ring.clear();
}

void resume_audio() {
    if (audio_output_state.phase() != AudioPhase::Suspended) return;
    std::lock_guard<std::mutex> control_lock(audio_control_mutex);
    audio_ring.clear();
    audio_output_state.resume();
}

void recover_audio_if_needed() {
    if (paused || !audio_recovery_requested.exchange(false, std::memory_order_acq_rel)) return;
    std::lock_guard<std::mutex> control_lock(audio_control_mutex);
    if (audio_output_state.phase() == AudioPhase::Stopped) return;
    if (audio_stream) {
        AAudioStream_close(audio_stream);
        audio_stream = nullptr;
    }
    audio_ring.clear();
    if (open_audio_stream_locked(audio_sample_rate)) {
        audio_output_state.recovered();
    }
}

void stop_audio() {
    audio_output_state.stop();
    audio_recovery_requested = false;
    std::lock_guard<std::mutex> control_lock(audio_control_mutex);
    if (audio_stream) {
        AAudioStream_requestStop(audio_stream);
        AAudioStream_close(audio_stream);
        audio_stream = nullptr;
    }
    audio_ring.clear();
}
void input_poll() {}

void apply_pending_media() {
    std::string path;
    {
        std::lock_guard<std::mutex> lock(media_mutex);
        path.swap(pending_media);
    }
    if (path.empty() || !has_disk_control || !disk_control.replace_image_index) return;
    if (disk_control.set_eject_state) disk_control.set_eject_state(true);
    unsigned index = disk_control.get_image_index ? disk_control.get_image_index() : 0;
    unsigned count = disk_control.get_num_images ? disk_control.get_num_images() : 0;
    if (index >= count && disk_control.add_image_index && disk_control.add_image_index()) index = count;
    retro_game_info media{path.c_str(), nullptr, 0, nullptr};
    bool replaced = disk_control.replace_image_index(index, &media);
    if (replaced && disk_control.set_image_index) disk_control.set_image_index(index);
    if (disk_control.set_eject_state) disk_control.set_eject_state(false);
    __android_log_print(replaced ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, "RoboWindowsCore",
            "media change %s", replaced ? "completed" : "failed");
}

int16_t input_state(unsigned, unsigned device, unsigned, unsigned id) {
    std::lock_guard<std::mutex> lock(input_mutex);
    if (device == RETRO_DEVICE_KEYBOARD) return id < keys.size() && keys[id] ? 1 : 0;
    if (device != RETRO_DEVICE_MOUSE) return 0;
    switch (id) {
        case RETRO_DEVICE_ID_MOUSE_X: { int value = static_cast<int>(std::lround(mouse_x)); mouse_x -= value; return static_cast<int16_t>(std::clamp(value, -32768, 32767)); }
        case RETRO_DEVICE_ID_MOUSE_Y: { int value = static_cast<int>(std::lround(mouse_y)); mouse_y -= value; return static_cast<int16_t>(std::clamp(value, -32768, 32767)); }
        case RETRO_DEVICE_ID_MOUSE_LEFT: return (mouse_buttons & 1) != 0;
        case RETRO_DEVICE_ID_MOUSE_RIGHT: return (mouse_buttons & 2) != 0;
        case RETRO_DEVICE_ID_MOUSE_MIDDLE: return (mouse_buttons & 4) != 0;
        case RETRO_DEVICE_ID_MOUSE_WHEELUP: if (wheel_v >= 1) { wheel_v -= 1; return 1; } return 0;
        case RETRO_DEVICE_ID_MOUSE_WHEELDOWN: if (wheel_v <= -1) { wheel_v += 1; return 1; } return 0;
        case RETRO_DEVICE_ID_MOUSE_HORIZ_WHEELUP: if (wheel_h >= 1) { wheel_h -= 1; return 1; } return 0;
        case RETRO_DEVICE_ID_MOUSE_HORIZ_WHEELDOWN: if (wheel_h <= -1) { wheel_h += 1; return 1; } return 0;
        default: return 0;
    }
}

unsigned android_to_retro(int key) {
    if (key >= 29 && key <= 54) return RETROK_a + static_cast<unsigned>(key - 29);
    if (key >= 7 && key <= 16) return RETROK_0 + static_cast<unsigned>(key - 7);
    if (key >= 131 && key <= 142) return RETROK_F1 + static_cast<unsigned>(key - 131);
    switch (key) {
        case 19: return RETROK_UP; case 20: return RETROK_DOWN;
        case 21: return RETROK_LEFT; case 22: return RETROK_RIGHT;
        case 55: return RETROK_COMMA; case 56: return RETROK_PERIOD;
        case 57: return RETROK_LALT; case 58: return RETROK_RALT;
        case 59: return RETROK_LSHIFT; case 60: return RETROK_RSHIFT;
        case 61: return RETROK_TAB; case 62: return RETROK_SPACE;
        case 66: return RETROK_RETURN; case 67: return RETROK_BACKSPACE;
        case 68: return RETROK_BACKQUOTE; case 69: return RETROK_MINUS;
        case 70: return RETROK_EQUALS; case 71: return RETROK_LEFTBRACKET;
        case 72: return RETROK_RIGHTBRACKET; case 73: return RETROK_BACKSLASH;
        case 74: return RETROK_SEMICOLON; case 75: return RETROK_QUOTE;
        case 76: return RETROK_SLASH; case 111: return RETROK_ESCAPE;
        case 112: return RETROK_DELETE; case 113: return RETROK_LCTRL;
        case 114: return RETROK_RCTRL; case 115: return RETROK_CAPSLOCK;
        case 116: return RETROK_SCROLLOCK; case 117: return RETROK_LMETA;
        case 118: return RETROK_RMETA; case 120: return RETROK_PRINT;
        case 121: return RETROK_PAUSE; case 122: return RETROK_HOME;
        case 123: return RETROK_END; case 124: return RETROK_INSERT;
        case 92: return RETROK_PAGEUP; case 93: return RETROK_PAGEDOWN;
        default: return RETROK_UNKNOWN;
    }
}

uint16_t android_modifiers(int meta) {
    uint16_t result = RETROKMOD_NONE;
    if (meta & (1 | 64 | 128)) result |= RETROKMOD_SHIFT;
    if (meta & (4096 | 8192 | 16384)) result |= RETROKMOD_CTRL;
    if (meta & (2 | 16 | 32)) result |= RETROKMOD_ALT;
    if (meta & (65536 | 131072 | 262144)) result |= RETROKMOD_META;
    if (meta & 2097152) result |= RETROKMOD_NUMLOCK;
    if (meta & 1048576) result |= RETROKMOD_CAPSLOCK;
    if (meta & 4194304) result |= RETROKMOD_SCROLLOCK;
    return result;
}

void run_core() {
    if (!core_initialized) {
        retro_set_environment(environment);
        retro_set_video_refresh(video_refresh);
        retro_set_audio_sample_batch(audio_batch);
        retro_set_input_poll(input_poll);
        retro_set_input_state(input_state);
        retro_init();
        core_initialized = true;
    }
    retro_game_info game{content_path.c_str(), nullptr, 0, nullptr};
    if (!retro_load_game(&game)) {
        __android_log_print(ANDROID_LOG_ERROR, "RoboWindowsCore", "guest load failed");
        running = false;
        session_state = robowindows::SESSION_FAILED;
        frame_presenter.stop();
        return;
    }
    retro_system_av_info av{};
    retro_get_system_av_info(&av);
    __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
            "guest started fps=%.2f audio=%.0f", av.timing.fps, av.timing.sample_rate);
    session_state = robowindows::SESSION_RUNNING;
    telemetry.set_state(RuntimeState::Foreground);
    start_audio(static_cast<int>(av.timing.sample_rate > 1.0 ? av.timing.sample_rate : 44100.0));
    double fps = av.timing.fps > 1.0 ? av.timing.fps : 60.0;
    requested_run_fps.store(fps, std::memory_order_relaxed);
    auto frame_time = std::chrono::duration_cast<std::chrono::steady_clock::duration>(
            std::chrono::duration<double>(1.0 / fps));
    auto next_frame = std::chrono::steady_clock::now();
    while (running && !shutdown_requested) {
        if (reset_requested.exchange(false)) {
            {
                std::lock_guard<std::mutex> lock(input_mutex);
                keys.fill(false);
                mouse_buttons = mouse_x = mouse_y = wheel_v = wheel_h = 0;
            }
            retro_reset();
            __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                    "guest restart completed");
        }
        if (paused) {
            report_telemetry_if_due();
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            next_frame = std::chrono::steady_clock::now();
            continue;
        }
        apply_pending_media();
        recover_audio_if_needed();
        const double updated_fps = requested_run_fps.load(std::memory_order_relaxed);
        if (std::abs(updated_fps - fps) > 0.001) {
            fps = updated_fps;
            frame_time = std::chrono::duration_cast<std::chrono::steady_clock::duration>(
                    std::chrono::duration<double>(1.0 / fps));
            next_frame = std::chrono::steady_clock::now();
            __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore",
                    "frontend cadence updated fps=%.6f", fps);
        }
        retro_run();
        telemetry.add_emulator_run_calls();
        report_telemetry_if_due();
        next_frame += frame_time;
        const auto now = std::chrono::steady_clock::now();
        if (next_frame + frame_time < now) next_frame = now;
        std::this_thread::sleep_until(next_frame);
    }
    video_enabled = false;
    telemetry.set_state(RuntimeState::Stopping);
    frame_presenter.stop();
    stop_audio();
    retro_unload_game();
    running = false;
    session_state = robowindows::SessionStateAfterCoreCleanup(session_state.load());
    telemetry.set_state(RuntimeState::Stopped);
    __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore", "guest stopped cleanly");
}
}

void CoreInputKey(int action, int android_key_code, int meta_state) {
    if (action != 0 && action != 1) return;
    unsigned key = android_to_retro(android_key_code);
    if (key == RETROK_UNKNOWN) return;
    bool down = action == 0;
    {
        std::lock_guard<std::mutex> lock(input_mutex);
        keys[key] = down;
    }
    retro_keyboard_event_t callback = keyboard_callback;
    if (callback) callback(down, key, 0, android_modifiers(meta_state));
}

void CoreInputMouse(float relative_x, float relative_y, int button_state,
                    float vertical_scroll, float horizontal_scroll) {
    std::lock_guard<std::mutex> lock(input_mutex);
    mouse_x += relative_x;
    mouse_y += relative_y;
    mouse_buttons = button_state;
    wheel_v += vertical_scroll;
    wheel_h += horizontal_scroll;
}

void CoreInputCancel() {
    std::lock_guard<std::mutex> lock(input_mutex);
    keys.fill(false);
    mouse_buttons = mouse_x = mouse_y = wheel_v = wheel_h = 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_robowindows_app_NativeHost_startSession(JNIEnv* env, jclass, jstring content,
        jstring files) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex);
    if (running) return JNI_FALSE;
    if (core_thread.joinable()) core_thread.join();
    const char* content_chars = env->GetStringUTFChars(content, nullptr);
    const char* files_chars = env->GetStringUTFChars(files, nullptr);
    content_path = content_chars;
    size_t slash = content_path.find_last_of('/');
    content_directory = slash == std::string::npos ? "." : content_path.substr(0, slash);
    system_path = std::string(files_chars) + "/system";
    save_path = std::string(files_chars) + "/saves";
    env->ReleaseStringUTFChars(content, content_chars);
    env->ReleaseStringUTFChars(files, files_chars);
    shutdown_requested = false;
    reset_requested = false;
    paused = false;
    running = true;
    session_state = robowindows::SESSION_STARTING;
    submitted_frames = 0;
    telemetry.reset(RuntimeState::Starting);
    telemetry_report_time = std::chrono::steady_clock::now();
    logged_non_silent_audio = false;
    // RoboWindows only passes generated .conf content, which bypasses Pure's menu.
    video_enabled = content_path.size() >= 5 &&
            content_path.compare(content_path.size() - 5, 5, ".conf") == 0;
    frame_mailbox.reset();
    frame_presenter.start();
    core_thread = std::thread(run_core);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_setPaused(JNIEnv*, jclass, jboolean value) {
    paused = value;
    telemetry.set_state(value ? RuntimeState::Paused : RuntimeState::Foreground);
    if (value) {
        suspend_audio();
    } else {
        resume_audio();
    }
    __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore", "guest %s",
            value ? "paused" : "resumed");
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_stopSession(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex);
    telemetry.set_state(RuntimeState::Stopping);
    running = false;
    if (core_thread.joinable()) core_thread.join();
    session_state = robowindows::SESSION_STOPPED;
    CoreInputCancel();
}

extern "C" JNIEXPORT jint JNICALL
Java_org_robowindows_app_NativeHost_sessionStatus(JNIEnv*, jclass) {
    return session_state.load();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_robowindows_app_NativeHost_restartSession(JNIEnv*, jclass) {
    if (!running || session_state != robowindows::SESSION_RUNNING) return JNI_FALSE;
    paused = false;
    reset_requested = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_setSurface(JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* replacement = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    frame_presenter.set_window(replacement);
    if (!replacement) {
        telemetry.set_state(RuntimeState::SurfaceLost);
    } else if (running && !paused) {
        telemetry.set_state(RuntimeState::Foreground);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_robowindows_app_NativeHost_changeMedia(JNIEnv* env, jclass, jstring path) {
    if (!running || !path) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    {
        std::lock_guard<std::mutex> lock(media_mutex);
        pending_media = chars;
    }
    env->ReleaseStringUTFChars(path, chars);
    __android_log_print(ANDROID_LOG_INFO, "RoboWindowsCore", "media change queued");
    return JNI_TRUE;
}
