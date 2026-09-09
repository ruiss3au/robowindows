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
    mov dword ptr [RESULT], 0x50475752
    mov dword ptr [RESULT+4], 0x31303055
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
