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
    push 520
    push 660
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
    push 5
    push eax
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

run_cpu:
    call dword ptr [__imp__GetTickCount]
    mov dword ptr [cpu_start], eax
    mov dword ptr [cpu_work], 0
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
    call dword ptr [__imp__GetTickCount]
    sub eax, dword ptr [cpu_start]
    cmp eax, PHASE_MS
    jb cpu_outer
    mov dword ptr [cpu_elapsed], eax
    mov eax, dword ptr [cpu_work]
    xor edx, edx
    div dword ptr [cpu_elapsed]
    mov dword ptr [cpu_throughput], eax
    ret

run_memory:
    call dword ptr [__imp__GetTickCount]
    mov dword ptr [memory_start], eax
    mov dword ptr [memory_work], 0
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
    call dword ptr [__imp__GetTickCount]
    sub eax, dword ptr [memory_start]
    cmp eax, PHASE_MS
    jb memory_outer
    mov dword ptr [memory_elapsed], eax
    mov eax, dword ptr [memory_work]
    xor edx, edx
    div dword ptr [memory_elapsed]
    mov dword ptr [memory_throughput], eax
    ret

run_gdi:
    push dword ptr [window_handle]
    call dword ptr [__imp__GetDC]
    test eax, eax
    jz fatal
    mov dword ptr [device_context], eax

    push 13
    push 0
    push offset wave_data
    call dword ptr [__imp__PlaySoundA]
    test eax, eax
    jz fatal

    call dword ptr [__imp__GetTickCount]
    mov dword ptr [gdi_start], eax
    mov dword ptr [gdi_work], 0
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
    push 400
    push 640
    push 0
    push 0
    push dword ptr [device_context]
    call dword ptr [__imp__PatBlt]
    test eax, eax
    jz fatal
    inc dword ptr [gdi_work]
    xor dword ptr [gdi_integrity], ebx
    call pump_messages
    call dword ptr [__imp__GetTickCount]
    sub eax, dword ptr [gdi_start]
    cmp eax, PHASE_MS
    jb gdi_outer
    mov dword ptr [gdi_elapsed], eax

    push 0
    push 0
    push 0
    call dword ptr [__imp__PlaySoundA]
    push dword ptr [device_context]
    push dword ptr [window_handle]
    call dword ptr [__imp__ReleaseDC]

    mov eax, dword ptr [gdi_work]
    mov ecx, 1000
    mul ecx
    div dword ptr [gdi_elapsed]
    mov dword ptr [gdi_throughput], eax
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
    ret

_wndproc:
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    push dword ptr [esp + 16]
    call dword ptr [__imp__DefWindowProcA]
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
    push 0x40
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
window_title: .asciz "RoboWindows Windows 98 Benchmark"
fatal_title: .asciz "RoboWindows benchmark failed"
fatal_text: .asciz "The benchmark could not initialize a required Windows 98 service."
done_title: .asciz "RoboWindows benchmark complete"
result_path: .asciz "C:\\RWBENCH.TXT"
header: .asciz "RW98BENCH schema=1 workload=1 duration_ms=30000\n"
cpu_prefix: .asciz "phase=cpu unit=ops_per_ms elapsed_ms="
memory_prefix: .asciz "phase=memory unit=kib_per_ms elapsed_ms="
gdi_prefix: .asciz "phase=gdi unit=fills_per_s pixels_per_fill=256000 elapsed_ms="
work_key: .asciz " work="
throughput_key: .asciz " throughput="
integrity_key: .asciz " integrity="
complete_line: .asciz "\ncomplete=1\n"
newline: .asciz "\n"
hex_digits: .ascii "0123456789ABCDEF"

.align 4
wave_data:
    .ascii "RIFF"
    .long 11060
    .ascii "WAVEfmt "
    .long 16
    .short 1, 1
    .long 11025, 11025
    .short 1, 8
    .ascii "data"
    .long 11024
    .rept 1378
    .byte 144,144,144,144,112,112,112,112
    .endr

.section .bss
.align 4
instance: .space 4
window_handle: .space 4
device_context: .space 4
message: .space 28
cpu_start: .space 4
cpu_elapsed: .space 4
cpu_work: .space 4
cpu_throughput: .space 4
cpu_integrity: .space 4
memory_start: .space 4
memory_elapsed: .space 4
memory_work: .space 4
memory_throughput: .space 4
memory_integrity: .space 4
gdi_start: .space 4
gdi_elapsed: .space 4
gdi_work: .space 4
gdi_throughput: .space 4
gdi_integrity: .space 4
output_pointer: .space 4
bytes_written: .space 4
number_scratch: .space 16
result_buffer: .space 1024
memory_area: .space 65536
