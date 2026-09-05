# Native Input Bridge Contract

The Android boundary emits typed events. It never synthesizes gamepad input for
a physical keyboard or mouse.

## KeyEventRecord

Required fields: action (down/up), Android key code, hardware scan code, repeat
count, meta-state, source bits, sanitized device handle, event time. Left/right
modifiers remain distinguishable. Text composition is a separate optional event
and cannot replace physical key transitions.

## MouseEventRecord

Required fields: action, captured relative X/Y, button-state bitset, action
button, vertical/horizontal scroll, source bits, sanitized device handle, event
time, and capture state. Absolute X/Y may be retained for diagnostics, but MUST
NOT be converted to guest motion. During a running session only captured records
are guest mouse input; the first uncaptured click inside the guest requests
capture and is not forwarded. See feature 004's session input contract.

## TouchEventRecord

Touch has its own record and cannot enter the physical mouse path implicitly.
The policy layer selects physical mouse, touch emulation, or neither.

## DeviceRecord

Contains display name, vendor/product IDs, source bitset, keyboard type, axes,
and a session-scoped handle. Persistent/public output must hash or omit Android
descriptors and must omit Bluetooth addresses and unique IDs.

## Lifecycle rules

- Losing focus or pausing releases pointer capture and emits input cancellation.
- Session exit and activity destruction release pointer capture and emit input
  cancellation.
- Disconnect cancels held keys/buttons belonging to that device.
- Reconnect creates or refreshes a device record without duplicating events.
- Native event queues have a bounded size and log overflow in debug builds.
