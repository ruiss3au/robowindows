/* Project-owned BIOS timer, animated VGA and PIT speaker workload. No OS needed.
 * Boot loader loads at 0x8000. Writes only sector 18 of its disposable floppy.
 * Result: "RWGPU001", uint32 elapsed BIOS ticks, uint16 status (1 running).
 * Run continuously; host stops/unloads before reading buffered floppy writes.
 */
.intel_syntax noprefix
.section .text
.code16
.global start
.equ RESULT, 0x600
start:
    cli
    xor ax, ax
    mov ss, ax
    mov sp, 0x7c00
    mov ds, ax
    mov es, ax
    cld
    mov byte ptr [drive], dl
    mov di, RESULT
    mov cx, 256
    rep stosw
.ifdef STRESS_WORKLOAD
    mov dword ptr [RESULT], 0x54535752  /* RWSTR001 */
    mov dword ptr [RESULT+4], 0x31303052
.else
    mov dword ptr [RESULT], 0x50475752
    mov dword ptr [RESULT+4], 0x31303055
.endif
    mov word ptr [RESULT+12], 1
    sti
    mov ax, 0x13
    int 0x10
    /* PIT channel 2, square wave, approximately 440 Hz. */
    mov al, 0xb6
    out 0x43, al
    mov ax, 2712
    out 0x42, al
    mov al, ah
    out 0x42, al
    in al, 0x61
    or al, 3
    out 0x61, al
    xor ah, ah
    int 0x1a
    mov word ptr [initial], dx
    call write_result
animate:
.ifdef STRESS_WORKLOAD
    call stress_work
.endif
    xor ah, ah
    int 0x1a
    sub dx, word ptr [initial]
    cmp dx, word ptr [RESULT+8]
    je animate
    mov word ptr [RESULT+8], dx
    mov ax, 0xa000
    mov es, ax
    xor di, di
    mov ax, dx
    mov ah, al
    mov cx, 32000
    rep stosw
    xor ax, ax
    mov es, ax
    /* One progress record per approximately guest second, not per frame. */
    mov ax, dx
    xor dx, dx
    mov bx, 18
    div bx
    test dx, dx
    jnz animate
    call write_result
    jmp animate
write_result:
    pusha
    push es
    xor ax, ax
    mov es, ax
    mov bx, RESULT
    mov ax, 0x0301
    xor ch, ch
    mov cl, 18
    xor dh, dh
    mov dl, byte ptr [drive]
    int 0x13
    pop es
    popa
    ret
drive: .byte 0
initial: .word 0

.ifdef STRESS_WORKLOAD
/* Real mode with 32-bit operands, not a Windows/game reproduction. IRQs stay
 * enabled throughout bounded work; the BIOS PIT tick is still the guest clock.
 * Each phase lasts 144 BIOS ticks (~7.91 s), repeating for the full host run.
 * Sector-18 result adds uint16 phase mask at 14 and four uint32 batch counts.
 */
stress_work:
    pushad
    push ds
    push es
    mov ax, word ptr [RESULT+8]
    xor dx, dx
    mov bx, 144
    div bx
    and ax, 3
    mov bx, ax
    bts word ptr [RESULT+14], ax
    shl bx, 2
    inc dword ptr [RESULT+16+bx]
    test ax, ax
    jz stress_idle
    cmp ax, 1
    je stress_integer
    cmp ax, 2
    je stress_memory
    /* Continuous VGA writes, separately from RAM copying. */
    mov ax, 0xa000
    mov es, ax
    xor di, di
    mov eax, 0x03030303
    mov cx, 16000
    rep stosd
    jmp stress_done
stress_integer:
    mov eax, 0x12345678
    mov ebx, 0x9e3779b9
    mov cx, 512
stress_integer_loop:
    imul eax, eax, 1664525
    add eax, 1013904223
    rol eax, 7
    xor ebx, eax
    dec cx
    jnz stress_integer_loop
    jmp stress_done
stress_memory:
    /* Separate 16 KiB RAM ranges, outside code, stack, BIOS and video memory. */
    mov ax, 0x2000
    mov es, ax
    xor di, di
    mov eax, 0x11223344
    mov cx, 4096
    rep stosd
    mov ax, 0x2000
    mov ds, ax
    mov ax, 0x3000
    mov es, ax
    xor si, si
    xor di, di
    mov cx, 4096
    rep movsd
    jmp stress_done
stress_idle:
    hlt
stress_done:
    pop es
    pop ds
    popad
    ret
.endif
