/*
 * RoboWindows CPU correctness fixture v3 protected-mode stage.
 *
 * The boot sector loads this stage at physical 0x8000. It writes a bounded
 * result record to sector 18 of its own disposable image and never accesses
 * machine profiles or user media.
 */
.intel_syntax noprefix
.section .text
.code16
.global start

.equ RESULT, 0x0600
.equ MASK, RESULT + 12
.equ ASSERTION, RESULT + 14
.equ COMPLETE, RESULT + 16
.equ CHECKSUM, RESULT + 20
.equ EXPECTED_MASK, 0x3fff
.equ PAGE_DIRECTORY, 0x1000
.equ PAGE_TABLE, 0x2000
.equ CROSS_PAGE_CODE, 0x3ffd
.equ IDT, 0x5000
.equ FAULT_ADDRESS, 0x30000
.equ NESTED_FAULT_1, 0x31000
.equ NESTED_FAULT_2, 0x32000
.equ NESTED_FAULT_3, 0x33000
.equ NESTED_FAULT_4, 0x34000
.equ REP_PAGE, 0x36000
.equ REP_BEGIN, REP_PAGE - 16
.equ REP_END, REP_PAGE + 16
.equ REP_VALUE, 0x1234abcd
.equ REP_GUARD, 0x5aa5c33c
.equ RESULT_SECTOR, 18

start:
    cli
    xor ax, ax
    mov ss, ax
    mov sp, 0x7c00
    mov ds, ax
    mov es, ax
    sti
    mov byte ptr [boot_drive], dl

    mov di, RESULT
    mov cx, 256
    rep stosw
    mov dword ptr [RESULT], 0x50435752       /* RWCP */
    mov dword ptr [RESULT + 4], 0x33303055   /* U003 */
    mov word ptr [RESULT + 8], 3
    mov word ptr [RESULT + 10], EXPECTED_MASK

    /* 16-bit arithmetic and status flags. */
    mov ax, 0xffff
    add ax, 1
    pushf
    pop bx
    cmp ax, 0
    jne test_32
    test bx, 1
    jz test_32
    or word ptr [MASK], 0x0001

test_32:
    mov eax, 0x12345678
    rol eax, 8
    cmp eax, 0x34567812
    jne test_x87
    mov eax, 70001
    imul eax, eax, 97
    cmp eax, 6790097
    jne test_x87
    or word ptr [MASK], 0x0002

test_x87:
    fninit
    fild word ptr [six]
    fimul word ptr [seven]
    fistp word ptr [RESULT + 32]
    cmp word ptr [RESULT + 32], 42
    jne test_string
    or word ptr [MASK], 0x0004

test_string:
    mov si, offset string_source
    mov di, RESULT + 40
    mov cx, 4
    cld
    rep movsw
    mov si, offset string_source
    mov di, RESULT + 40
    mov cx, 4
string_compare:
    mov ax, word ptr [si]
    cmp ax, word ptr [di]
    jne test_smc
    add si, 2
    add di, 2
    loop string_compare
    or word ptr [MASK], 0x0008

test_smc:
    mov word ptr [smc_immediate + 1], 0x5aa5
    call smc_immediate
    cmp ax, 0x5aa5
    jne enter_protected
    or word ptr [MASK], 0x0010

enter_protected:
    cli
    lgdt [gdt_descriptor]
    mov eax, cr0
    or eax, 1
    mov cr0, eax
    ljmp 0x08, pm32

.code32
pm32:
    mov ax, 0x10
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov esp, 0x7000
    mov eax, 0x76543210
    xor eax, 0x55aa55aa
    cmp eax, 0x23fe67ba
    jne setup_idt
    or word ptr [MASK], 0x0020

setup_idt:
    mov edi, IDT
    xor eax, eax
    mov ecx, 512
    rep stosd
    mov eax, offset invalid_opcode_handler
    mov edi, IDT + 6 * 8
    call install_gate
    mov eax, offset page_fault_handler
    mov edi, IDT + 14 * 8
    call install_gate
    mov eax, offset software_interrupt_handler
    mov edi, IDT + 0x30 * 8
    call install_gate
    lidt [idt_descriptor]

    mov dword ptr [interrupt_seen], 0
    int 0x30
    cmp dword ptr [interrupt_seen], 1
    jne test_invalid_opcode
    or word ptr [MASK], 0x0040

test_invalid_opcode:
    mov dword ptr [invalid_opcode_seen], 0
    ud2
after_invalid_opcode:
    cmp dword ptr [invalid_opcode_seen], 1
    jne test_linked_flow
    or word ptr [MASK], 0x0080

test_linked_flow:
    xor eax, eax
    mov ecx, 4096
linked_loop:
    test ecx, 1
    jz linked_even
    add eax, 3
    jmp linked_join
linked_even:
    add eax, 5
linked_join:
    dec ecx
    jnz linked_loop
    cmp eax, 16384
    jne test_invlpg
    or word ptr [MASK], 0x0100

test_invlpg:
    /* Address bytes deliberately encode INC EAX if misread as instructions.
     * No operand is dereferenced; INVLPG must also leave EFLAGS unchanged. */
    mov eax, 0x12345678
    cmp eax, eax
    pushfd
    pop ebx
    mov word ptr [ASSERTION], 11
    invlpg [0x40404040]
    pushfd
    pop ecx
    cmp eax, 0x12345678
    jne leave_paging
    cmp ebx, ecx
    jne leave_paging
    mov word ptr [ASSERTION], 12
    invlpg [eax + 0x40]
    pushfd
    pop ecx
    cmp eax, 0x12345678
    jne leave_paging
    cmp ebx, ecx
    jne leave_paging
    mov word ptr [ASSERTION], 13
    invlpg [eax + eax * 2 + 0x40404040]
    pushfd
    pop ecx
    cmp eax, 0x12345678
    jne leave_paging
    cmp ebx, ecx
    jne leave_paging
    mov word ptr [ASSERTION], 14
    addr16 invlpg [0x4040]
    pushfd
    pop ecx
    cmp eax, 0x12345678
    jne leave_paging
    cmp ebx, ecx
    jne leave_paging
    mov word ptr [ASSERTION], 0
    or word ptr [MASK], 0x1000

setup_paging:
    /* Define readback values; do not depend on emulator RAM initialization. */
    mov dword ptr [FAULT_ADDRESS], 0
    mov dword ptr [NESTED_FAULT_1], 0
    mov dword ptr [NESTED_FAULT_2], 0
    mov dword ptr [NESTED_FAULT_3], 0
    mov dword ptr [NESTED_FAULT_4], 0
    mov edi, PAGE_DIRECTORY
    xor eax, eax
    mov ecx, 2048
    rep stosd
    mov dword ptr [PAGE_DIRECTORY], PAGE_TABLE + 3
    mov edi, PAGE_TABLE
    mov eax, 3
    mov ecx, 1024
page_table_loop:
    stosd
    add eax, 0x1000
    loop page_table_loop
    and dword ptr [PAGE_TABLE + (FAULT_ADDRESS >> 12) * 4], 0xfffffffe
    and dword ptr [PAGE_TABLE + (NESTED_FAULT_1 >> 12) * 4], 0xfffffffe
    and dword ptr [PAGE_TABLE + (NESTED_FAULT_2 >> 12) * 4], 0xfffffffe
    and dword ptr [PAGE_TABLE + (NESTED_FAULT_3 >> 12) * 4], 0xfffffffe
    and dword ptr [PAGE_TABLE + (NESTED_FAULT_4 >> 12) * 4], 0xfffffffe
    mov eax, PAGE_DIRECTORY
    mov cr3, eax
    mov eax, cr0
    or eax, 0x80000000
    mov cr0, eax
    jmp paging_enabled

paging_enabled:
    mov dword ptr [page_fault_seen], 0
    mov eax, dword ptr [FAULT_ADDRESS]
    cmp eax, 0
    jne test_cross_page_smc
    cmp dword ptr [page_fault_seen], 1
    jne test_nested_page_faults
    or word ptr [MASK], 0x0200

test_nested_page_faults:
    mov dword ptr [nested_enter_count], 0
    mov dword ptr [nested_return_count], 0
    mov dword ptr [nested_bad], 0
    mov eax, dword ptr [NESTED_FAULT_1]
    mov word ptr [ASSERTION], 1
    cmp eax, 0
    jne test_cross_page_smc
    mov word ptr [ASSERTION], 2
    cmp dword ptr [nested_enter_count], 4
    jne test_cross_page_smc
    mov word ptr [ASSERTION], 3
    cmp dword ptr [nested_return_count], 4
    jne test_cross_page_smc
    mov word ptr [ASSERTION], 4
    cmp dword ptr [nested_bad], 0
    jne test_cross_page_smc
    mov word ptr [ASSERTION], 0
    or word ptr [MASK], 0x0800

test_cross_page_smc:
    mov byte ptr [CROSS_PAGE_CODE], 0xb8
    mov dword ptr [CROSS_PAGE_CODE + 1], 0x11223344
    mov byte ptr [CROSS_PAGE_CODE + 5], 0xc3
    mov eax, CROSS_PAGE_CODE
    call eax
    cmp eax, 0x11223344
    jne leave_paging
    mov dword ptr [CROSS_PAGE_CODE + 1], 0xa55a5aa5
    mov eax, CROSS_PAGE_CODE
    call eax
    cmp eax, 0xa55a5aa5
    jne leave_paging
    or word ptr [MASK], 0x0400

test_rep_fault:
    cmp word ptr [ASSERTION], 0
    jne leave_paging
    mov dword ptr [rep_fault_seen], 0
    mov edi, REP_BEGIN - 4
    mov ecx, 10
    mov eax, REP_GUARD
    rep stosd
    and dword ptr [PAGE_TABLE + (REP_PAGE >> 12) * 4], 0xfffffffe
    invlpg [REP_PAGE]
    mov esi, 0x76543210
    mov edi, REP_BEGIN
    mov ecx, 8
    mov eax, REP_VALUE
    cld
rep_fault_instruction:
    rep stosd
    cmp word ptr [ASSERTION], 0
    jne leave_paging
    mov word ptr [ASSERTION], 28
    cmp ecx, 0
    jne leave_paging
    cmp edi, REP_END
    jne leave_paging
    cmp dword ptr [rep_fault_seen], 1
    jne leave_paging
    mov word ptr [ASSERTION], 29
    mov edi, REP_BEGIN
    mov ecx, 8
rep_verify:
    cmp dword ptr [edi], REP_VALUE
    jne leave_paging
    add edi, 4
    loop rep_verify
    mov word ptr [ASSERTION], 30
    cmp dword ptr [REP_BEGIN - 4], REP_GUARD
    jne leave_paging
    cmp dword ptr [REP_END], REP_GUARD
    jne leave_paging
    mov word ptr [ASSERTION], 0
    or word ptr [MASK], 0x2000

leave_paging:
    mov eax, cr0
    and eax, 0x7fffffff
    mov cr0, eax
    jmp paging_disabled
paging_disabled:
    ljmp 0x18, pm16

install_gate:
    mov word ptr [edi], ax
    mov word ptr [edi + 2], 0x08
    mov word ptr [edi + 4], 0x8e00
    shr eax, 16
    mov word ptr [edi + 6], ax
    ret

software_interrupt_handler:
    inc dword ptr [interrupt_seen]
    iretd

invalid_opcode_handler:
    inc dword ptr [invalid_opcode_seen]
    add dword ptr [esp], 2
    iretd

page_fault_handler:
    push eax
    mov eax, cr2
    cmp eax, REP_PAGE
    je page_fault_rep
    test dword ptr [esp + 4], 7
    jne page_fault_bad
    cmp eax, FAULT_ADDRESS
    je page_fault_single
    cmp eax, NESTED_FAULT_1
    je page_fault_nested_1
    cmp eax, NESTED_FAULT_2
    je page_fault_nested_2
    cmp eax, NESTED_FAULT_3
    je page_fault_nested_3
    cmp eax, NESTED_FAULT_4
    je page_fault_nested_4
    jmp page_fault_bad

page_fault_rep:
    inc dword ptr [rep_fault_seen]
    cmp word ptr [ASSERTION], 0
    jne rep_make_present
    mov word ptr [ASSERTION], 20
    cmp dword ptr [rep_fault_seen], 1
    jne rep_make_present
    cmp dword ptr [esp + 4], 2
    jne rep_make_present
    mov word ptr [ASSERTION], 21
    cmp dword ptr [esp + 8], offset rep_fault_instruction
    jne rep_make_present
    mov word ptr [ASSERTION], 22
    cmp ecx, 4
    jne rep_make_present
    mov word ptr [ASSERTION], 23
    cmp edi, REP_PAGE
    jne rep_make_present
    mov word ptr [ASSERTION], 24
    cmp dword ptr [esp], REP_VALUE
    jne rep_make_present
    cmp esi, 0x76543210
    jne rep_make_present
    mov word ptr [ASSERTION], 25
    cmp dword ptr [REP_BEGIN], REP_VALUE
    jne rep_make_present
    cmp dword ptr [REP_BEGIN + 4], REP_VALUE
    jne rep_make_present
    cmp dword ptr [REP_BEGIN + 8], REP_VALUE
    jne rep_make_present
    cmp dword ptr [REP_BEGIN + 12], REP_VALUE
    jne rep_make_present
    mov word ptr [ASSERTION], 0
rep_make_present:
    or dword ptr [PAGE_TABLE + (REP_PAGE >> 12) * 4], 1
    invlpg [REP_PAGE]
    cmp word ptr [ASSERTION], 0
    jne page_fault_return
    mov word ptr [ASSERTION], 26
    cmp dword ptr [REP_PAGE], REP_GUARD
    jne page_fault_return
    mov word ptr [ASSERTION], 0
    jmp page_fault_return

page_fault_single:
    mov dword ptr [page_fault_seen], 1
    or dword ptr [PAGE_TABLE + (FAULT_ADDRESS >> 12) * 4], 1
    invlpg [FAULT_ADDRESS]
    jmp page_fault_return

page_fault_nested_1:
    cmp dword ptr [nested_enter_count], 0
    jne page_fault_bad
    inc dword ptr [nested_enter_count]
    mov eax, dword ptr [NESTED_FAULT_2]
    cmp dword ptr [nested_return_count], 3
    jne page_fault_bad
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_1 >> 12) * 4], 1
    invlpg [NESTED_FAULT_1]
    inc dword ptr [nested_return_count]
    jmp page_fault_return

page_fault_nested_2:
    cmp dword ptr [nested_enter_count], 1
    jne page_fault_bad
    inc dword ptr [nested_enter_count]
    mov eax, dword ptr [NESTED_FAULT_3]
    cmp dword ptr [nested_return_count], 2
    jne page_fault_bad
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_2 >> 12) * 4], 1
    invlpg [NESTED_FAULT_2]
    inc dword ptr [nested_return_count]
    jmp page_fault_return

page_fault_nested_3:
    cmp dword ptr [nested_enter_count], 2
    jne page_fault_bad
    inc dword ptr [nested_enter_count]
    mov eax, dword ptr [NESTED_FAULT_4]
    cmp dword ptr [nested_return_count], 1
    jne page_fault_bad
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_3 >> 12) * 4], 1
    invlpg [NESTED_FAULT_3]
    inc dword ptr [nested_return_count]
    jmp page_fault_return

page_fault_nested_4:
    cmp dword ptr [nested_enter_count], 3
    jne page_fault_bad
    inc dword ptr [nested_enter_count]
    cmp dword ptr [nested_return_count], 0
    jne page_fault_bad
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_4 >> 12) * 4], 1
    invlpg [NESTED_FAULT_4]
    inc dword ptr [nested_return_count]
    jmp page_fault_return

page_fault_bad:
    mov dword ptr [page_fault_seen], 0xffffffff
    mov dword ptr [nested_bad], 0xffffffff
    or dword ptr [PAGE_TABLE + (FAULT_ADDRESS >> 12) * 4], 1
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_1 >> 12) * 4], 1
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_2 >> 12) * 4], 1
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_3 >> 12) * 4], 1
    or dword ptr [PAGE_TABLE + (NESTED_FAULT_4 >> 12) * 4], 1
    invlpg [FAULT_ADDRESS]
    invlpg [NESTED_FAULT_1]
    invlpg [NESTED_FAULT_2]
    invlpg [NESTED_FAULT_3]
    invlpg [NESTED_FAULT_4]
page_fault_return:
    pop eax
    add esp, 4
    iretd

.code16
pm16:
    mov eax, cr0
    and eax, 0xfffffffe
    mov cr0, eax
    ljmp 0x0000, real_mode

real_mode:
    xor ax, ax
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov sp, 0x7c00
    lidt [real_idt_descriptor]
    sti
    mov dword ptr [COMPLETE], 0xc0dec0de

    xor bx, bx
    mov si, RESULT
    mov cx, 20
checksum_loop:
    lodsb
    xor ah, ah
    add bx, ax
    loop checksum_loop
    mov word ptr [CHECKSUM], bx

    mov bx, RESULT
    mov ah, 3
    mov al, 1
    xor ch, ch
    mov cl, RESULT_SECTOR
    xor dh, dh
    mov dl, byte ptr [boot_drive]
    int 0x13

    mov al, 'F'
    cmp word ptr [MASK], EXPECTED_MASK
    jne show_result
    mov al, 'P'
show_result:
    mov ah, 0x0e
    xor bx, bx
    int 0x10
halt:
    hlt
    jmp halt

smc_immediate:
    mov ax, 0x1111
    ret

six: .word 6
seven: .word 7
string_source: .ascii "RWSTR003"
boot_drive: .byte 0
interrupt_seen: .long 0
invalid_opcode_seen: .long 0
page_fault_seen: .long 0
nested_enter_count: .long 0
nested_return_count: .long 0
nested_bad: .long 0
rep_fault_seen: .long 0

.align 8
gdt:
    .quad 0
    .word 0xffff, 0x0000
    .byte 0x00, 0x9a, 0xcf, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0x92, 0xcf, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0x9a, 0x00, 0x00
gdt_end:
gdt_descriptor:
    .word gdt_end - gdt - 1
    .long gdt
idt_descriptor:
    .word 256 * 8 - 1
    .long IDT
real_idt_descriptor:
    .word 0x03ff
    .long 0
