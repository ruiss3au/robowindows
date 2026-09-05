package org.robowindows.app;

import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class InputDeviceRecord {
    final int handle;
    final String name;
    final int vendorId;
    final int productId;
    final int sources;
    final int keyboardType;
    final List<String> axes;

    private InputDeviceRecord(int handle, String name, int vendorId, int productId,
            int sources, int keyboardType, List<String> axes) {
        this.handle = handle;
        this.name = name;
        this.vendorId = vendorId;
        this.productId = productId;
        this.sources = sources;
        this.keyboardType = keyboardType;
        this.axes = axes;
    }

    static InputDeviceRecord from(InputDevice device, int handle) {
        ArrayList<String> axes = new ArrayList<>();
        for (InputDevice.MotionRange range : device.getMotionRanges()) {
            String label = MotionEvent.axisToString(range.getAxis());
            if (!axes.contains(label)) axes.add(label);
        }
        return new InputDeviceRecord(handle, device.getName(), device.getVendorId(),
                device.getProductId(), device.getSources(), device.getKeyboardType(), axes);
    }

    String capabilitySummary(String type) {
        String keyboard = keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                ? "alphabetic" : keyboardType == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
                ? "non-alphabetic" : "none";
        String axisText = axes.isEmpty() ? "none" : android.text.TextUtils.join(", ", axes);
        return String.format(Locale.ROOT, "%s\n%s · %04x:%04x\nKeyboard: %s · Sources: 0x%08x\nAxes: %s",
                name, type, vendorId, productId, keyboard, sources, axisText);
    }
}
