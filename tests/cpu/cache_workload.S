/* Source-owned fixed-work translation controls. No OS or user media.
 * CASE_ID: 1 warm, 2 cold, 3 reuse, 4 data write, 5 code rewrite.
 * Loaded at 0x8000; sector 18 is exclusively this fixture's result.
 */
.intel_syntax noprefix
.section .text
.code16
.global start
.ifndef CASE_ID
.error "CASE_ID required"
.endif
.if CASE_ID < 1 || CASE_ID > 5
.error "invalid CASE_ID"
.endif
.equ RESULT, 0x600
.equ IDT, 0x5000
.equ CODE, 0x20000
.equ DATA, 0x61000
.equ CALLS, 4096
.equ ITERATIONS, 512
.equ EXPECTED_VALUE, 0x48cd306a
start:
    cli
    xor ax, ax
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov sp, 0x7c00
    cld
    mov byte ptr [drive], dl
    in al, 0x21
    mov byte ptr [pic1], al
    in al, 0xa1
    mov byte ptr [pic2], al
    xor ax, ax
    mov di, RESULT
    mov cx, 256
    rep stosw
    mov dword ptr [RESULT], 0x41435752 /* RWCACHE1 */
    mov dword ptr [RESULT+4], 0x31454843
    mov word ptr [RESULT+8], 1
    mov word ptr [RESULT+10], 64
    mov dword ptr [RESULT+12], CASE_ID
    mov dword ptr [RESULT+16], CALLS
    mov dword ptr [RESULT+28], 262144
    mov dword ptr [RESULT+52], ITERATIONS
    mov dword ptr [RESULT+56], 64
    /* Identical video/tone configuration for every case. */
    mov ax, 0x13
    int 0x10
    cli
    mov al, 0xb6
    out 0x43, al
    mov ax, 2712
    out 0x42, al
    mov al, ah
    out 0x42, al
    in al, 0x61
    or al, 3
    out 0x61, al
    lgdt [gdt_descriptor]
    mov eax, cr0
    or eax, 1
    mov cr0, eax
    ljmp 8, pm32
.code32
pm32:
    mov ax, 16
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov esp, 0x7000
    mov edi, IDT
    mov ecx, 256
idt_loop:
    mov eax, offset exception
    call gate
    add edi, 8
    loop idt_loop
    mov edi, IDT+0x20*8
    mov eax, offset tick
    call gate
    lidt [idt_descriptor]
    mov al, 0x11
    out 0x20, al
    out 0xa0, al
    mov al, 0x20
    out 0x21, al
    mov al, 0x28
    out 0xa1, al
    mov al, 4
    out 0x21, al
    mov al, 2
    out 0xa1, al
    mov al, 1
    out 0x21, al
    out 0xa1, al
    mov al, 0xfe
    out 0x21, al
    mov al, 0xff
    out 0xa1, al
    mov al, 0x34
    out 0x43, al
    mov ax, 1193
    out 0x40, al
    mov al, ah
    out 0x40, al
    sti
    /* Build all slots before timing, regardless of the selected case. */
    mov edi, CODE
    mov ebp, CALLS
populate:
    mov esi, offset routine
    mov ecx, 64
    rep movsb
    dec ebp
    jnz populate
    mov eax, dword ptr [ticks]
    mov dword ptr [RESULT+32], eax
    /* Prime one slot except cold; reuse primes the entire footprint. */
.if CASE_ID != 2
    mov ebp, 1
.if CASE_ID == 3
    mov ebp, CALLS
.endif
    mov edi, CODE
prime:
    call edi
    cmp eax, EXPECTED_VALUE
    jne bad_value
    add edi, 64
    dec ebp
    jnz prime
.endif
    mov eax, dword ptr [ticks]
    mov dword ptr [RESULT+36], eax
    mov ebp, CALLS
    mov edi, CODE
    xor esi, esi
work:
.if CASE_ID == 4 || CASE_ID == 5
    mov ebx, ebp
    and ebx, 1
.if CASE_ID == 4
    mov dword ptr [DATA], ebx
.else
    mov dword ptr [CODE+xor_value-routine], ebx
.endif
    jmp serialize
serialize:
.endif
    call edi
    mov edx, EXPECTED_VALUE
.if CASE_ID == 5
    xor edx, ebx
.endif
    cmp eax, edx
    jne bad_value
    add esi, eax
    inc dword ptr [RESULT+20]
.if CASE_ID == 2 || CASE_ID == 3
    add edi, 64
.endif
    cmp dword ptr [ticks], 30000
    ja timed_out
    dec ebp
    jnz work
    mov dword ptr [RESULT+24], esi
    mov dword ptr [RESULT+44], 0xc04ec04e
    jmp finish
bad_value:
    mov dword ptr [RESULT+48], 1
    jmp finish
timed_out:
    mov dword ptr [RESULT+48], 2
    jmp finish
exception:
    cli
    mov dword ptr [RESULT+48], 3
finish:
    cli
    mov eax, dword ptr [ticks]
    mov dword ptr [RESULT+40], eax
    mov esp, 0x7000
    ljmp 24, pm16
gate:
    mov word ptr [edi], ax
    mov word ptr [edi+2], 8
    mov word ptr [edi+4], 0x8e00
    shr eax, 16
    mov word ptr [edi+6], ax
    ret
tick:
    push eax
    inc dword ptr [ticks]
    mov al, 0x20
    out 0x20, al
    pop eax
    iretd
/* Fixed 64-byte slot. Only the trailing XOR immediate is rewritten. */
routine:
    mov eax, 0x12345678
    mov ecx, ITERATIONS
integer_loop:
    imul eax, eax, 1664525
    add eax, 1013904223
    rol eax, 7
    dec ecx
    jnz integer_loop
    .byte 0x35 /* XOR EAX, imm32 */
xor_value:
.ifdef TEST_BAD_VALUE
    .long 1
.else
    .long 0
.endif
    ret
    .fill 64-(.-routine), 1, 0x90
.code16
pm16:
    mov ax, 32
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov eax, cr0
    and eax, 0xfffffffe
    mov cr0, eax
    ljmp 0, real_mode
real_mode:
    xor ax, ax
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov sp, 0x7c00
    lidt [real_idt_descriptor]
    /* Restore BIOS PIC vectors and default PIT divisor before disk I/O. */
    mov al, 0x11
    out 0x20, al
    out 0xa0, al
    mov al, 8
    out 0x21, al
    mov al, 0x70
    out 0xa1, al
    mov al, 4
    out 0x21, al
    mov al, 2
    out 0xa1, al
    mov al, 1
    out 0x21, al
    out 0xa1, al
    mov al, byte ptr [pic1]
    out 0x21, al
    mov al, byte ptr [pic2]
    out 0xa1, al
    mov al, 0x36
    out 0x43, al
    xor al, al
    out 0x40, al
    out 0x40, al
    mov esi, RESULT
    mov cx, 60
    mov eax, 0x811c9dc5
hash:
    xor al, byte ptr [esi]
    imul eax, eax, 0x01000193
    inc esi
    loop hash
    mov dword ptr [RESULT+60], eax
    sti
    mov bx, RESULT
    mov ax, 0x0301
    xor ch, ch
    mov cl, 18
    xor dh, dh
    mov dl, byte ptr [drive]
    int 0x13
    jc halt
    /* Only the QEMU reference config supplies this debug-exit port. */
    mov dx, 0xf4
    mov al, 0x10
    out dx, al
    mov ax, 0x5301
    xor bx, bx
    int 0x15
    mov ax, 0x530e
    xor bx, bx
    mov cx, 0x0102
    int 0x15
    mov ax, 0x5307
    mov bx, 1
    mov cx, 3
    int 0x15
halt:
    hlt
    jmp halt
.align 8
gdt:
    .quad 0, 0x00cf9a000000ffff, 0x00cf92000000ffff
    .quad 0x00009a000000ffff, 0x000092000000ffff
gdt_end:
gdt_descriptor: .word gdt_end-gdt-1
    .long gdt
idt_descriptor: .word 2047
    .long IDT
real_idt_descriptor: .word 1023
    .long 0
ticks: .long 0
drive: .byte 0
pic1: .byte 0
pic2: .byte 0
