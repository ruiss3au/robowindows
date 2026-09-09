.intel_syntax noprefix

.equ PHASE_MS, 10000
.equ CPU_BATCH, 4096
.equ MEMORY_DWORDS, 16384
.equ MEMORY_KIB, 64

.section .text
.globl _start
_start:
    cld
    push 0
    call dword ptr [__imp__GetModuleHandleA]
    mov dword ptr [instance], eax

    push 32512
    push 0
    call dword ptr [__imp__LoadCursorA]
    mov dword ptr [window_class + 24], eax
    mov eax, dword ptr [instance]
    mov dword ptr [window_class + 16], eax
    push offset window_class
    call dword ptr [__imp__RegisterClassA]
    test eax, eax
    jz fatal

    push 0
    push dword ptr [instance]
    push 0
    push 0
    push 380
    push 520
    push 80
    push 80
    push 0x10cf0000
    push offset window_title
    push offset class_name
    push 0
    call dword ptr [__imp__CreateWindowExA]
    test eax, eax
    jz fatal
    mov dword ptr [window_handle], eax

    push 0
    push dword ptr [instance]
    push 0
    push eax
    push 28
    push 470
    push 24
    push 20
    push 0x50000000
    push offset status_cpu
    push offset static_class
    push 0
    call dword ptr [__imp__CreateWindowExA]
    test eax, eax
    jz fatal
    mov dword ptr [status_handle], eax

    push 0
    push dword ptr [instance]
    push 0
    push dword ptr [window_handle]
    push 42
    push 470
    push 58
    push 20
    push 0x50000000
    push offset detail_text
    push offset static_class
    push 0
    call dword ptr [__imp__CreateWindowExA]
    test eax, eax
    jz fatal
    mov dword ptr [detail_handle], eax
    push 5
    push dword ptr [window_handle]
    call dword ptr [__imp__ShowWindow]
    push dword ptr [window_handle]
    call dword ptr [__imp__UpdateWindow]

    call run_cpu
    call run_memory
    call run_gdi
    call build_result
    call save_and_show

    push dword ptr [window_handle]
    call dword ptr [__imp__DestroyWindow]
    push 0
    call dword ptr [__imp__ExitProcess]

fatal:
    push 0x10
    push offset fatal_title
    push offset fatal_text
    push 0
    call dword ptr [__imp__MessageBoxA]
    push 1
    call dword ptr [__imp__ExitProcess]

aborted:
    push dword ptr [window_handle]
    call dword ptr [__imp__DestroyWindow]
    push 2
    call dword ptr [__imp__ExitProcess]

run_cpu:
    call dword ptr [__imp__GetTickCount]
    mov dword ptr [cpu_start], eax
    mov dword ptr [cpu_work], 0
    mov dword ptr [cpu_last_ui], 0
    mov ebx, 0x13579bdf
cpu_outer:
    mov ecx, CPU_BATCH
cpu_inner:
    imul ebx, ebx, 1664525
    add ebx, 1013904223
    rol ebx, 7
    xor ebx, 0xa5a5a5a5
    dec ecx
    jnz cpu_inner
    add dword ptr [cpu_work], CPU_BATCH
    mov dword ptr [cpu_integrity], ebx
    call pump_messages
    test eax, eax
    jnz aborted
    call dword ptr [__imp__GetTickCount]
    sub eax, dword ptr [cpu_start]
    push eax
    call update_cpu_status
    pop eax
    cmp eax, PHASE_MS
    jb cpu_outer
    mov dword ptr [cpu_elapsed], eax
    mov eax, dword ptr [cpu_work]
    xor edx, edx
    div dword ptr [cpu_elapsed]
    mov dword ptr [cpu_throughput], eax
    ret

run_memory:
    push offset status_memory
    push dword ptr [status_handle]
    call dword ptr [__imp__SetWindowTextA]
    push offset live_wait
    push dword ptr [detail_handle]
    call dword ptr [__imp__SetWindowTextA]
    call dword ptr [__imp__GetTickCount]
    mov dword ptr [memory_start], eax
    mov dword ptr [memory_work], 0
    mov dword ptr [memory_last_ui], 0
    mov ebx, 0x2468ace1
memory_outer:
    mov edi, offset memory_area
    mov ecx, MEMORY_DWORDS
memory_write:
    imul ebx, ebx, 1103515245
    add ebx, 12345
    mov eax, ebx
    stosd
    dec ecx
    jnz memory_write
    add dword ptr [memory_work], MEMORY_KIB
    mov eax, dword ptr [memory_area]
    xor eax, dword ptr [memory_area + 32768]
    xor eax, dword ptr [memory_area + 65532]
    xor ebx, eax
    mov dword ptr [memory_integrity], ebx
    call pump_messages
    test eax, eax
    jnz aborted
    call dword ptr [__imp__GetTickCount]
    sub eax, dword ptr [memory_start]
    push eax
    call update_memory_status
    pop eax
    cmp eax, PHASE_MS
    jb memory_outer
    mov dword ptr [memory_elapsed], eax
    mov eax, dword ptr [memory_work]
    xor edx, edx
    div dword ptr [memory_elapsed]
    mov dword ptr [memory_throughput], eax
    ret

run_gdi:
    push offset status_gdi
    push dword ptr [status_handle]
    call dword ptr [__imp__SetWindowTextA]
    push offset live_wait
    push dword ptr [detail_handle]
    call dword ptr [__imp__SetWindowTextA]
    push dword ptr [window_handle]
    call dword ptr [__imp__GetDC]
    test eax, eax
    jz fatal
    mov dword ptr [device_context], eax

    push eax
    call dword ptr [__imp__CreateCompatibleDC]
    test eax, eax
    jz fatal
    mov dword ptr [memory_dc], eax

    push 200
    push 320
    push dword ptr [device_context]
    call dword ptr [__imp__CreateCompatibleBitmap]
    test eax, eax
    jz fatal
    mov dword ptr [preview_bitmap], eax

    push eax
    push dword ptr [memory_dc]
    call dword ptr [__imp__SelectObject]
    test eax, eax
    jz fatal
    mov dword ptr [previous_bitmap], eax

    push 0x00000042
    push 200
    push 320
    push 0
    push 0
    push dword ptr [memory_dc]
    call dword ptr [__imp__PatBlt]
    test eax, eax
    jz fatal

    push 0x00ff0062
    push 24
    push 320
    push 108
    push 20
    push dword ptr [device_context]
    call dword ptr [__imp__PatBlt]
    test eax, eax
    jz fatal

    call dword ptr [__imp__GetTickCount]
    mov dword ptr [gdi_start], eax
    mov dword ptr [gdi_last_preview], eax
    mov dword ptr [gdi_work], 0
    mov dword ptr [gdi_frames], 0
    mov dword ptr [gdi_last_ui], 0
    mov ebx, 0x9e3779b9
gdi_outer:
    imul ebx, ebx, 1664525
    add ebx, 1013904223
    test ebx, 1
    jz gdi_black
    mov eax, 0x00ff0062
    jmp gdi_draw
gdi_black:
    mov eax, 0x00000042
gdi_draw:
    push eax
    push 64
    push 64
    mov ecx, ebx
    shr ecx, 8
    and ecx, 127
    push ecx
    mov ecx, ebx
    and ecx, 255
    push ecx
    push dword ptr [memory_dc]
    call dword ptr [__imp__PatBlt]
    test eax, eax
    jz fatal
    inc dword ptr [gdi_work]
    xor dword ptr [gdi_integrity], ebx
    call pump_messages
    test eax, eax
    jnz aborted
    call dword ptr [__imp__GetTickCount]
    mov edx, eax
    sub eax, dword ptr [gdi_start]
    push edx
    push eax
    call update_gdi_status
    pop eax
    pop edx
    cmp eax, PHASE_MS
    jae gdi_done
    sub edx, dword ptr [gdi_last_preview]
    cmp edx, 100
    jb gdi_outer
    mov edx, eax
    add edx, dword ptr [gdi_start]
    mov dword ptr [gdi_last_preview], edx
    mov ecx, dword ptr [gdi_frames]
    imul ecx, ecx, 3
    add ecx, 20
    push 0x00000042
    push 24
    push 3
    push 108
    push ecx
    push dword ptr [device_context]
    call dword ptr [__imp__PatBlt]
    test eax, eax
    jz fatal
    inc dword ptr [gdi_frames]
    jmp gdi_outer
gdi_done:
    mov dword ptr [gdi_elapsed], eax

    push dword ptr [previous_bitmap]
    push dword ptr [memory_dc]
    call dword ptr [__imp__SelectObject]
    push dword ptr [preview_bitmap]
    call dword ptr [__imp__DeleteObject]
    push dword ptr [memory_dc]
    call dword ptr [__imp__DeleteDC]
    push dword ptr [device_context]
    push dword ptr [window_handle]
    call dword ptr [__imp__ReleaseDC]

    mov eax, dword ptr [gdi_work]
    mov ecx, 1000
    mul ecx
    div dword ptr [gdi_elapsed]
    mov dword ptr [gdi_throughput], eax
    mov eax, dword ptr [gdi_frames]
    mov ecx, 1000
    mul ecx
    div dword ptr [gdi_elapsed]
    mov dword ptr [gdi_fps], eax
    ret

update_cpu_status:
    mov eax, dword ptr [esp + 4]
    mov edx, eax
    sub edx, dword ptr [cpu_last_ui]
    cmp edx, 250
    jb update_cpu_done
    mov dword ptr [cpu_last_ui], eax
    mov dword ptr [output_pointer], offset live_buffer
    mov esi, offset live_elapsed
    call append_string
    mov eax, dword ptr [esp + 4]
    call append_decimal
    mov esi, offset live_cpu_work
    call append_string
    mov eax, dword ptr [cpu_work]
    call append_decimal
    mov esi, offset live_cpu_rate
    call append_string
    mov eax, dword ptr [cpu_work]
    xor edx, edx
    div dword ptr [esp + 4]
    call append_decimal
    call finish_live_status
update_cpu_done:
    ret

update_memory_status:
    mov eax, dword ptr [esp + 4]
    mov edx, eax
    sub edx, dword ptr [memory_last_ui]
    cmp edx, 250
    jb update_memory_done
    mov dword ptr [memory_last_ui], eax
    mov dword ptr [output_pointer], offset live_buffer
    mov esi, offset live_elapsed
    call append_string
    mov eax, dword ptr [esp + 4]
    call append_decimal
    mov esi, offset live_memory_work
    call append_string
    mov eax, dword ptr [memory_work]
    call append_decimal
    mov esi, offset live_memory_rate
    call append_string
    mov eax, dword ptr [memory_work]
    xor edx, edx
    div dword ptr [esp + 4]
    call append_decimal
    call finish_live_status
update_memory_done:
    ret

update_gdi_status:
    mov eax, dword ptr [esp + 4]
    mov edx, eax
    sub edx, dword ptr [gdi_last_ui]
    cmp edx, 250
    jb update_gdi_done
    mov dword ptr [gdi_last_ui], eax
    mov dword ptr [output_pointer], offset live_buffer
    mov esi, offset live_elapsed
    call append_string
    mov eax, dword ptr [esp + 4]
    call append_decimal
    mov esi, offset live_gdi_rate
    call append_string
    mov eax, dword ptr [gdi_work]
    mov ecx, 1000
    mul ecx
    div dword ptr [esp + 4]
    call append_decimal
    mov esi, offset live_gdi_frames
    call append_string
    mov eax, dword ptr [gdi_frames]
    call append_decimal
    mov esi, offset live_gdi_fps
    call append_string
    mov eax, dword ptr [gdi_frames]
    mov ecx, 1000
    mul ecx
    div dword ptr [esp + 4]
    call append_decimal
    call finish_live_status
update_gdi_done:
    ret

finish_live_status:
    mov edi, dword ptr [output_pointer]
    mov byte ptr [edi], 0
    push offset live_buffer
    push dword ptr [detail_handle]
    call dword ptr [__imp__SetWindowTextA]
    ret

pump_messages:
pump_again:
    push 1
    push 0
    push 0
    push 0
    push offset message
    call dword ptr [__imp__PeekMessageA]
    test eax, eax
    jz pump_done
    push offset message
    call dword ptr [__imp__TranslateMessage]
    push offset message
    call dword ptr [__imp__DispatchMessageA]
    jmp pump_again
pump_done:
    mov eax, dword ptr [abort_requested]
    ret

_wndproc:
    cmp dword ptr [esp + 8], 0x10
    je request_abort
    cmp dword ptr [esp + 8], 0x100
    jne default_window_proc
    cmp dword ptr [esp + 12], 27
    je request_abort
default_window_proc:
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    call dword ptr [__imp__DefWindowProcA]
    ret 16
request_abort:
    mov dword ptr [abort_requested], 1
    xor eax, eax
    ret 16

build_result:
    mov dword ptr [output_pointer], offset result_buffer
    mov esi, offset header
    call append_string

    mov esi, offset cpu_prefix
    call append_string
    mov eax, dword ptr [cpu_elapsed]
    call append_decimal
    mov esi, offset work_key
    call append_string
    mov eax, dword ptr [cpu_work]
    call append_decimal
    mov esi, offset throughput_key
    call append_string
    mov eax, dword ptr [cpu_throughput]
    call append_decimal
    mov esi, offset integrity_key
    call append_string
    mov eax, dword ptr [cpu_integrity]
    call append_hex
    mov esi, offset newline
    call append_string

    mov esi, offset memory_prefix
    call append_string
    mov eax, dword ptr [memory_elapsed]
    call append_decimal
    mov esi, offset work_key
    call append_string
    mov eax, dword ptr [memory_work]
    call append_decimal
    mov esi, offset throughput_key
    call append_string
    mov eax, dword ptr [memory_throughput]
    call append_decimal
    mov esi, offset integrity_key
    call append_string
    mov eax, dword ptr [memory_integrity]
    call append_hex
    mov esi, offset newline
    call append_string

    mov esi, offset gdi_prefix
    call append_string
    mov eax, dword ptr [gdi_elapsed]
    call append_decimal
    mov esi, offset work_key
    call append_string
    mov eax, dword ptr [gdi_work]
    call append_decimal
    mov esi, offset throughput_key
    call append_string
    mov eax, dword ptr [gdi_throughput]
    call append_decimal
    mov esi, offset frames_key
    call append_string
    mov eax, dword ptr [gdi_frames]
    call append_decimal
    mov esi, offset fps_key
    call append_string
    mov eax, dword ptr [gdi_fps]
    call append_decimal
    mov esi, offset integrity_key
    call append_string
    mov eax, dword ptr [gdi_integrity]
    call append_hex
    mov esi, offset complete_line
    call append_string
    mov edi, dword ptr [output_pointer]
    mov byte ptr [edi], 0
    ret

append_string:
    mov edi, dword ptr [output_pointer]
append_string_loop:
    lodsb
    test al, al
    jz append_string_done
    stosb
    jmp append_string_loop
append_string_done:
    mov dword ptr [output_pointer], edi
    ret

append_decimal:
    mov edi, offset number_scratch + 15
    mov byte ptr [edi], 0
    mov ecx, 10
    test eax, eax
    jnz append_decimal_loop
    dec edi
    mov byte ptr [edi], '0'
    jmp append_decimal_copy
append_decimal_loop:
    xor edx, edx
    div ecx
    add dl, '0'
    dec edi
    mov byte ptr [edi], dl
    test eax, eax
    jnz append_decimal_loop
append_decimal_copy:
    mov esi, edi
    call append_string
    ret

append_hex:
    mov edi, dword ptr [output_pointer]
    mov ecx, 8
append_hex_loop:
    rol eax, 4
    mov edx, eax
    and edx, 15
    mov dl, byte ptr [hex_digits + edx]
    mov byte ptr [edi], dl
    inc edi
    dec ecx
    jnz append_hex_loop
    mov dword ptr [output_pointer], edi
    ret

save_and_show:
    push offset status_complete
    push dword ptr [status_handle]
    call dword ptr [__imp__SetWindowTextA]
    push offset complete_detail
    push dword ptr [detail_handle]
    call dword ptr [__imp__SetWindowTextA]
    push 0
    push 0x80
    push 2
    push 0
    push 0
    push 0x40000000
    push offset result_path
    call dword ptr [__imp__CreateFileA]
    cmp eax, -1
    je show_result
    mov ebx, eax
    mov ecx, dword ptr [output_pointer]
    sub ecx, offset result_buffer
    push 0
    push offset bytes_written
    push ecx
    push offset result_buffer
    push ebx
    call dword ptr [__imp__WriteFile]
    push ebx
    call dword ptr [__imp__CloseHandle]
show_result:
    push 0
    push offset done_title
    push offset result_buffer
    push dword ptr [window_handle]
    call dword ptr [__imp__MessageBoxA]
    ret

.section .data
.align 4
window_class:
    .long 3, _wndproc, 0, 0, 0, 0, 0, 6, 0, class_name
class_name: .asciz "RoboWindowsBenchmarkWindow"
static_class: .asciz "STATIC"
window_title: .asciz "RoboWindows Bench 2"
fatal_title: .asciz "RoboWindows benchmark failed"
fatal_text: .asciz "The benchmark could not initialize a required Windows 98 service."
done_title: .asciz "RoboWindows benchmark complete"
result_path: .asciz "C:\\RWBENCH.TXT"
status_cpu: .asciz "Test 1 of 3 - CPU integer performance"
status_memory: .asciz "Test 2 of 3 - memory throughput"
status_gdi: .asciz "Test 3 of 3 - off-screen GDI performance"
status_complete: .asciz "Benchmark complete"
detail_text: .asciz "Live metrics will update four times per guest second."
live_wait: .asciz "Preparing phase..."
complete_detail: .asciz "Results saved to C:\\RWBENCH.TXT"
live_elapsed: .asciz "Elapsed ms: "
live_cpu_work: .asciz "   Operations: "
live_cpu_rate: .asciz "   Ops/ms: "
live_memory_work: .asciz "   KiB: "
live_memory_rate: .asciz "   KiB/ms: "
live_gdi_rate: .asciz "   Rects/s: "
live_gdi_frames: .asciz "   Preview frames: "
live_gdi_fps: .asciz "   Preview FPS: "
header: .asciz "RW98BENCH schema=2 workload=2 duration_ms=30000\n"
cpu_prefix: .asciz "phase=cpu unit=ops_per_ms elapsed_ms="
memory_prefix: .asciz "phase=memory unit=kib_per_ms elapsed_ms="
gdi_prefix: .asciz "phase=gdi pixels_per_rect=4096 elapsed_ms="
work_key: .asciz " work="
throughput_key: .asciz " throughput="
frames_key: .asciz " frames="
fps_key: .asciz " fps="
integrity_key: .asciz " integrity="
complete_line: .asciz "\ncomplete=1\n"
newline: .asciz "\n"
hex_digits: .ascii "0123456789ABCDEF"

.section .bss
.align 4
instance: .space 4
window_handle: .space 4
status_handle: .space 4
detail_handle: .space 4
device_context: .space 4
memory_dc: .space 4
preview_bitmap: .space 4
previous_bitmap: .space 4
message: .space 28
abort_requested: .space 4
cpu_start: .space 4
cpu_last_ui: .space 4
cpu_elapsed: .space 4
cpu_work: .space 4
cpu_throughput: .space 4
cpu_integrity: .space 4
memory_start: .space 4
memory_last_ui: .space 4
memory_elapsed: .space 4
memory_work: .space 4
memory_throughput: .space 4
memory_integrity: .space 4
gdi_start: .space 4
gdi_last_preview: .space 4
gdi_last_ui: .space 4
gdi_elapsed: .space 4
gdi_work: .space 4
gdi_throughput: .space 4
gdi_frames: .space 4
gdi_fps: .space 4
gdi_integrity: .space 4
output_pointer: .space 4
bytes_written: .space 4
number_scratch: .space 16
result_buffer: .space 1024
live_buffer: .space 256
memory_area: .space 65536
