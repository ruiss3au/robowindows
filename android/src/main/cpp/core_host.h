#pragma once

#include <cstdint>

void CoreInputKey(int action, int android_key_code, int meta_state);
void CoreInputMouse(float relative_x, float relative_y, int button_state,
                    float vertical_scroll, float horizontal_scroll);
void CoreInputCancel();
