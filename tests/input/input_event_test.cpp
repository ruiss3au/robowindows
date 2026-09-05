#include <cstdlib>
#include <iostream>
#include <string>

#include "input_event.h"

static void require(bool value, const char* message) {
    if (!value) { std::cerr << message << '\n'; std::exit(1); }
}

static std::string format(const robowindows::InputEvent& event) {
    char output[512];
    robowindows::FormatInputEvent(output, sizeof(output), event);
    return output;
}

int main() {
    robowindows::InputEvent key{};
    key.type = robowindows::EventType::Key;
    key.values[0] = 0; key.values[1] = 29; key.values[2] = 30; key.values[3] = 2;
    key.values[4] = 65; key.values[5] = 257; key.values[6] = 7; key.time_nanos = 123;
    std::string encoded = format(key);
    require(encoded.find("\"scanCode\":30") != std::string::npos, "scan code missing");
    require(encoded.find("\"repeat\":2") != std::string::npos, "repeat missing");
    require(encoded.find("\"meta\":65") != std::string::npos, "meta missing");

    robowindows::InputEvent mouse{};
    mouse.type = robowindows::EventType::Mouse;
    mouse.values[0] = 2; mouse.values[1] = 7; mouse.values[2] = 2;
    mouse.values[3] = 8194; mouse.values[4] = 8;
    mouse.axes[0] = 1.25f; mouse.axes[1] = -2.5f; mouse.axes[2] = 100;
    mouse.axes[3] = 200; mouse.axes[4] = -1; mouse.axes[5] = .5f;
    mouse.time_nanos = 456; mouse.captured = true;
    encoded = format(mouse);
    require(encoded.find("\"relativeX\":1.250") != std::string::npos, "relative X missing");
    require(encoded.find("\"relativeY\":-2.500") != std::string::npos, "relative Y missing");
    require(encoded.find("\"buttons\":7") != std::string::npos, "three-button state missing");
    require(encoded.find("\"verticalScroll\":-1.000") != std::string::npos, "vertical wheel missing");
    require(encoded.find("\"horizontalScroll\":0.500") != std::string::npos, "horizontal wheel missing");
    require(encoded.find("\"captured\":true") != std::string::npos, "capture missing");
    require(robowindows::ShouldForwardGuestMouse(mouse), "captured mouse was not forwarded");

    mouse.captured = false;
    require(!robowindows::ShouldForwardGuestMouse(mouse), "uncaptured mouse reached guest");

    robowindows::InputEvent touch{};
    touch.type = robowindows::EventType::Touch;
    touch.values[0] = 0; touch.values[1] = 2; touch.values[2] = 4098; touch.values[3] = 9;
    touch.axes[0] = 12; touch.axes[1] = 34; touch.axes[2] = .75f; touch.time_nanos = 789;
    encoded = format(touch);
    require(encoded.find("\"pointerCount\":2") != std::string::npos, "touch count missing");
    require(encoded.find("\"pressure\":0.750") != std::string::npos, "pressure missing");

    robowindows::InputEvent cancel{};
    cancel.type = robowindows::EventType::Cancel;
    require(format(cancel) == "{\"type\":\"cancel\"}", "cancel malformed");
}
