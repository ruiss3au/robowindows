/*
 * RoboWindows expanded x86 gate, protocol v4.
 *
 * SUITE_ID and CASE_COUNT are build-time constants. Each variant is placed in
 * a fresh disposable floppy image and writes only its own bounded result.
 */
.intel_syntax noprefix
.section .text
.code16
.global start

.ifndef SUITE_ID
.error "SUITE_ID is required"
.endif
.ifndef CASE_COUNT
.error "CASE_COUNT is required"
.endif

.equ RESULT, 0x0600
.equ DONE_LO, RESULT + 24
.equ DONE_HI, RESULT + 28
.equ ASSERTION, RESULT + 40
.equ COMPLETE, RESULT + 44
.equ INTEGRITY, RESULT + 48
.equ RESULT_CYLINDER, 1
.equ RESULT_SECTOR, 5
.equ IDT, 0x5000
.equ DATA, 0x20000
.equ PAGE_DIRECTORY, 0x1000
.equ PAGE_TABLE, 0x2000
.equ FAULT_PAGE, 0x30000
.equ PROGRESS_PAGE_2, FAULT_PAGE + 0x2000
.equ PROGRESS_PAGE_3, FAULT_PAGE + 0x4000
.equ PROGRESS_PAGE_4, FAULT_PAGE + 0x6000

.if CASE_COUNT == 32
.equ EXPECTED_LO, 0xffffffff
.else
.equ EXPECTED_LO, (1 << CASE_COUNT) - 1
.endif
.equ EXPECTED_HI, 0
.equ SIGNATURE_LO, CASE_COUNT
.equ SIGNATURE_HI, 0x52570000 | SUITE_ID

.macro BEGIN_CASE number
    mov word ptr [ASSERTION], (SUITE_ID & 0xff) * 100 + \number
.endm

.macro PASS_CASE number
    or dword ptr [DONE_LO], (1 << (\number - 1))
    mov word ptr [ASSERTION], 0
.endm

.macro REQUIRE_EQ reg, value
    cmp \reg, \value
    jne finish_pm
.endm

.macro BEGIN_FAULT_CASE number
    BEGIN_CASE \number
    mov word ptr [fault_assertion_base], 2000 + \number * 10
.endm

start:
    cli
    xor ax, ax
    mov ss, ax
    mov sp, 0x7c00
    mov ds, ax
    mov es, ax
    mov byte ptr [boot_drive], dl

    mov di, RESULT
    mov cx, 256
    rep stosw
    mov dword ptr [RESULT], 0x38585752       /* RWX8 */
    mov dword ptr [RESULT + 4], 0x34304736  /* 6G04 */
    mov word ptr [RESULT + 8], 4
    mov word ptr [RESULT + 10], 52
    mov word ptr [RESULT + 12], SUITE_ID
    mov word ptr [RESULT + 14], CASE_COUNT
    mov dword ptr [RESULT + 16], EXPECTED_LO
    mov dword ptr [RESULT + 20], EXPECTED_HI
    mov dword ptr [RESULT + 32], SIGNATURE_LO
    mov dword ptr [RESULT + 36], SIGNATURE_HI

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
    cld
    call setup_idt

.if SUITE_ID == 0x0201
    call suite_integer_flags
.elseif SUITE_ID == 0x0202
    call suite_stack_control
.elseif SUITE_ID == 0x0301
    call suite_x87
.elseif SUITE_ID == 0x0302
    call suite_mixed_seeds
.elseif SUITE_ID == 0x0101
    call suite_strings
.elseif SUITE_ID == 0x0102
    call suite_fault_retry
.elseif SUITE_ID == 0x0103
    call suite_pagefault_progress
.elseif SUITE_ID == 0x0203
    call suite_paging_smc
.else
.error "unknown SUITE_ID"
.endif
    jmp finish_pm

setup_idt:
    mov edi, IDT
    xor eax, eax
    mov ecx, 512
    rep stosd
    mov eax, offset divide_handler
    mov edi, IDT
    call install_gate
    mov eax, offset invalid_handler
    mov edi, IDT + 6 * 8
    call install_gate
.if SUITE_ID == 0x0103
    mov eax, offset progress_page_fault_handler
.else
    mov eax, offset page_fault_handler
.endif
    mov edi, IDT + 14 * 8
    call install_gate
    mov eax, offset interrupt_handler
    mov edi, IDT + 0x30 * 8
    call install_gate
    mov eax, offset user_return_handler
    mov edi, IDT + 0x31 * 8
    call install_user_gate
    lidt [idt_descriptor]
    ret

install_gate:
    mov word ptr [edi], ax
    mov word ptr [edi + 2], 0x08
    mov word ptr [edi + 4], 0x8e00
    shr eax, 16
    mov word ptr [edi + 6], ax
    ret

install_user_gate:
    mov word ptr [edi], ax
    mov word ptr [edi + 2], 0x08
    mov word ptr [edi + 4], 0xee00
    shr eax, 16
    mov word ptr [edi + 6], ax
    ret

.if SUITE_ID == 0x0201
suite_integer_flags:
    BEGIN_CASE 1
    mov eax, 0xffffffff
    add eax, 1
    pushfd
    pop edx
    REQUIRE_EQ eax, 0
    test edx, 1
    jz finish_pm
    test edx, 0x40
    jz finish_pm
    PASS_CASE 1

    BEGIN_CASE 2
    stc
    mov eax, 0x7fffffff
    adc eax, 0
    pushfd
    pop edx
    REQUIRE_EQ eax, 0x80000000
    test edx, 0x800
    jz finish_pm
    PASS_CASE 2

    BEGIN_CASE 3
    xor eax, eax
    sub eax, 1
    pushfd
    pop edx
    REQUIRE_EQ eax, 0xffffffff
    test edx, 1
    jz finish_pm
    PASS_CASE 3

    BEGIN_CASE 4
    stc
    mov eax, 0x80000000
    sbb eax, 0x7fffffff
    pushfd
    pop edx
    REQUIRE_EQ eax, 0
    test edx, 0x800
    jz finish_pm
    PASS_CASE 4

    BEGIN_CASE 5
    stc
    mov eax, 0xf0f0aa55
    xor eax, 0x0f0f55aa
    pushfd
    pop edx
    REQUIRE_EQ eax, 0xffffffff
    test edx, 0x801
    jnz finish_pm
    PASS_CASE 5

    BEGIN_CASE 6
    stc
    mov eax, 0x7fffffff
    inc eax
    pushfd
    pop edx
    REQUIRE_EQ eax, 0x80000000
    and edx, 0x801
    REQUIRE_EQ edx, 0x801
    dec eax
    REQUIRE_EQ eax, 0x7fffffff
    PASS_CASE 6

    BEGIN_CASE 7
    mov eax, 0x80000001
    shl eax, 1
    pushfd
    pop edx
    REQUIRE_EQ eax, 2
    test edx, 1
    jz finish_pm
    mov eax, 0x80000001
    sar eax, 1
    REQUIRE_EQ eax, 0xc0000000
    PASS_CASE 7

    BEGIN_CASE 8
    mov eax, 0x80000001
    rol eax, 1
    REQUIRE_EQ eax, 3
    ror eax, 1
    REQUIRE_EQ eax, 0x80000001
    stc
    rcr eax, 1
    REQUIRE_EQ eax, 0xc0000000
    PASS_CASE 8

    BEGIN_CASE 9
    mov byte ptr [DATA], 0x80
    movsx eax, byte ptr [DATA]
    REQUIRE_EQ eax, 0xffffff80
    movzx edx, byte ptr [DATA]
    REQUIRE_EQ edx, 0x80
    mov word ptr [DATA], 0x8001
    movsx ecx, word ptr [DATA]
    REQUIRE_EQ ecx, 0xffff8001
    movzx ebx, word ptr [DATA]
    REQUIRE_EQ ebx, 0x8001
    mov eax, 0xffffffff
    mov ecx, 3
    mul ecx
    REQUIRE_EQ eax, 0xfffffffd
    REQUIRE_EQ edx, 2
    mov eax, -70001
    imul eax, eax, 97
    REQUIRE_EQ eax, -6790097
    PASS_CASE 9

    BEGIN_CASE 10
    mov edx, 1
    mov eax, 0
    mov ecx, 3
    div ecx
    REQUIRE_EQ eax, 0x55555555
    REQUIRE_EQ edx, 1
    mov eax, -70001
    cdq
    mov ecx, 97
    idiv ecx
    REQUIRE_EQ eax, -721
    REQUIRE_EQ edx, -64
    mov dword ptr [divide_seen], 0
    mov dword ptr [divide_resume], offset after_divide_trap
    xor edx, edx
    mov eax, 1
    xor ecx, ecx
divide_trap_instruction:
    div ecx
after_divide_trap:
    cmp dword ptr [divide_seen], 1
    jne finish_pm
    PASS_CASE 10
    ret
.endif

.if SUITE_ID == 0x0202
suite_stack_control:
    BEGIN_CASE 1
    mov eax, 0x11223344
    mov ebx, 0xa55a5aa5
    push eax
    push ebx
    pop ecx
    pop edx
    REQUIRE_EQ ecx, 0xa55a5aa5
    REQUIRE_EQ edx, 0x11223344
    pushw 0x7ace
    pop ax
    REQUIRE_EQ ax, 0x7ace
    PASS_CASE 1

    BEGIN_CASE 2
    mov eax, 1
    mov ecx, 2
    mov edx, 3
    mov ebx, 4
    mov ebp, 5
    mov esi, 6
    mov edi, 7
    pushad
    xor eax, eax
    xor ecx, ecx
    xor edx, edx
    xor ebx, ebx
    xor ebp, ebp
    xor esi, esi
    xor edi, edi
    popad
    REQUIRE_EQ eax, 1
    REQUIRE_EQ ecx, 2
    REQUIRE_EQ edx, 3
    REQUIRE_EQ ebx, 4
    REQUIRE_EQ ebp, 5
    REQUIRE_EQ esi, 6
    REQUIRE_EQ edi, 7
    PASS_CASE 2

    BEGIN_CASE 3
    mov eax, 0x12340000
    call near_call_target
    REQUIRE_EQ eax, 0x12345678
    mov ebx, esp
    push 0x55667788
    call near_call_cleanup_target
    REQUIRE_EQ esp, ebx
    REQUIRE_EQ ecx, 0x55667788
    PASS_CASE 3

    BEGIN_CASE 4
    mov eax, 0x13570000
    lcall 0x08, far_call_target
far_call_return:
    REQUIRE_EQ eax, 0x13572468
    PASS_CASE 4

    BEGIN_CASE 5
    mov ebx, esp
    mov esp, DATA + 4
    mov eax, 0x89abcdef
    push eax
    pop edx
    REQUIRE_EQ edx, 0x89abcdef
    REQUIRE_EQ esp, DATA + 4
    mov esp, ebx
    PASS_CASE 5

    BEGIN_CASE 6
    xor eax, eax
    mov ecx, 64
control_branch_loop:
    test ecx, 1
    jz control_even
    add eax, 3
    jmp control_join
control_even:
    add eax, 5
control_join:
    dec ecx
    jnz control_branch_loop
    REQUIRE_EQ eax, 256
    PASS_CASE 6

    BEGIN_CASE 7
    xor eax, eax
    mov ecx, 257
control_loop_instruction:
    inc eax
    loop control_loop_instruction
    REQUIRE_EQ eax, 257
    jecxz control_jecxz_ok
    jmp finish_pm
control_jecxz_ok:
    PASS_CASE 7

    BEGIN_CASE 8
    mov dword ptr [interrupt_seen], 0
    mov dword ptr [invalid_seen], 0
    int 0x30
    ud2
    cmp dword ptr [interrupt_seen], 1
    jne finish_pm
    cmp dword ptr [invalid_seen], 1
    jne finish_pm
    PASS_CASE 8
    ret

near_call_target:
    or eax, 0x5678
    ret
near_call_cleanup_target:
    mov ecx, dword ptr [esp + 4]
    ret 4
far_call_target:
    or eax, 0x2468
    retf
.endif

.if SUITE_ID == 0x0301
suite_x87:
    BEGIN_CASE 1
    fninit
    sub esp, 4
    fnstcw word ptr [esp]
    cmp word ptr [esp], 0x037f
    lea esp, [esp + 4]
    jne finish_pm
    PASS_CASE 1

    BEGIN_CASE 2
    fild dword ptr [x87_six]
    fiadd dword ptr [x87_seven]
    fistp dword ptr [DATA]
    cmp dword ptr [DATA], 13
    jne finish_pm
    PASS_CASE 2

    BEGIN_CASE 3
    fild dword ptr [x87_six]
    fimul dword ptr [x87_seven]
    fidiv dword ptr [x87_three]
    fistp dword ptr [DATA]
    cmp dword ptr [DATA], 14
    jne finish_pm
    PASS_CASE 3

    BEGIN_CASE 4
    fldcw word ptr [x87_round_down]
    fld dword ptr [x87_two_point_seven]
    fistp dword ptr [DATA]
    cmp dword ptr [DATA], 2
    jne finish_pm
    fldcw word ptr [x87_round_up]
    fld dword ptr [x87_two_point_seven]
    fistp dword ptr [DATA]
    cmp dword ptr [DATA], 3
    jne finish_pm
    fldcw word ptr [x87_default_cw]
    PASS_CASE 4

    BEGIN_CASE 5
    fninit
    fld1
    fldz
    fnstsw ax
    and ax, 0x3800
    cmp ax, 0x3000
    jne finish_pm
    fstp st(0)
    fstp st(0)
    PASS_CASE 5

    BEGIN_CASE 6
    fld1
    fld1
    fcompp
    fnstsw ax
    sahf
    jne finish_pm
    PASS_CASE 6

    BEGIN_CASE 7
    fninit
    fldz
    fld1
    fdiv st(0), st(1)
    fstp dword ptr [DATA]
    cmp dword ptr [DATA], 0x7f800000
    jne finish_pm
    fninit
    PASS_CASE 7

    BEGIN_CASE 8
    fild qword ptr [x87_large]
    fistp qword ptr [DATA]
    cmp dword ptr [DATA], 0x89abcdef
    jne finish_pm
    cmp dword ptr [DATA + 4], 0x00123456
    jne finish_pm
    PASS_CASE 8
    ret
.endif

.macro MIXED_CASE number, seed
    BEGIN_CASE \number
    mov eax, \seed
    mov ebx, eax
    rol eax, 5
    xor eax, 0xa5c39e71
    add eax, 0x1020304
    push eax
    call mixed_call_target
    pop eax
    sub eax, 0x1020304
    xor eax, 0xa5c39e71
    ror eax, 5
    cmp eax, ebx
    jne finish_pm
    mov dword ptr [DATA + 0x100], eax
    mov dword ptr [DATA + 0x104], ebx
    mov esi, DATA + 0x100
    mov edi, DATA + 0x180
    mov ecx, 2
    rep movsd
    cmp dword ptr [DATA + 0x180], ebx
    jne finish_pm
    ud2
    cmp dword ptr [invalid_seen], \number
    jne finish_pm
    mov dword ptr [mixed_immediate + 1], \seed
    call mixed_immediate
    cmp eax, \seed
    jne finish_pm
    PASS_CASE \number
.endm

.if SUITE_ID == 0x0302
suite_mixed_seeds:
    mov dword ptr [invalid_seen], 0
    MIXED_CASE 1,  0x6d2b79f5
    MIXED_CASE 2,  0x1b56c4e9
    MIXED_CASE 3,  0x9e3779b9
    MIXED_CASE 4,  0x243f6a88
    MIXED_CASE 5,  0xb7e15162
    MIXED_CASE 6,  0xdeadbeef
    MIXED_CASE 7,  0x8badf00d
    MIXED_CASE 8,  0xc001d00d
    MIXED_CASE 9,  0x31415926
    MIXED_CASE 10, 0x27182818
    MIXED_CASE 11, 0x5a17c9e3
    MIXED_CASE 12, 0x0f1e2d3c
    MIXED_CASE 13, 0x89abcdef
    MIXED_CASE 14, 0x76543210
    MIXED_CASE 15, 0xaaaaaaaa
    MIXED_CASE 16, 0x55555555
    MIXED_CASE 17, 0x80000001
    MIXED_CASE 18, 0x7ffffffe
    MIXED_CASE 19, 0x01010101
    MIXED_CASE 20, 0xfefefefe
    MIXED_CASE 21, 0x13579bdf
    MIXED_CASE 22, 0x2468ace0
    MIXED_CASE 23, 0xca11ab1e
    MIXED_CASE 24, 0x0ddba11a
    MIXED_CASE 25, 0xfeedface
    MIXED_CASE 26, 0x600d5eed
    MIXED_CASE 27, 0x10293847
    MIXED_CASE 28, 0x56473829
    MIXED_CASE 29, 0xabcdef01
    MIXED_CASE 30, 0x10fedcba
    MIXED_CASE 31, 0x42424242
    MIXED_CASE 32, 0xbd1e9955
    ret

mixed_call_target:
    mov edx, dword ptr [esp]
    xor edx, 0x55aa55aa
    xor edx, 0x55aa55aa
    ret
mixed_immediate:
    mov eax, 0
    ret
.endif

/* Each string case checks non-REP, zero-count REP, multiple-count REP and
 * address-size-16 execution. The dword cases also force 16-bit index wrap. */
.macro INIT_STRING_DATA
    mov dword ptr [DATA], 0x44332211
    mov dword ptr [DATA + 4], 0x88776655
    mov dword ptr [DATA + 8], 0xccbbaa99
    mov dword ptr [DATA + 12], 0x10ffeedd
    mov dword ptr [DATA + 0x100], 0
    mov dword ptr [DATA + 0x104], 0
    mov dword ptr [DATA + 0x108], 0
    mov dword ptr [DATA + 0x10c], 0
    mov dword ptr [0x7400], 0x44332211
    mov dword ptr [0x7404], 0x88776655
    mov dword ptr [0x7408], 0xccbbaa99
    mov dword ptr [0x740c], 0x10ffeedd
    mov dword ptr [0x7500], 0
    mov dword ptr [0x7504], 0
    mov dword ptr [0x7508], 0
    mov dword ptr [0x750c], 0
.endm

.macro SET_DIRECTION back, source, destination, step
.if \back
    std
    mov esi, \source + 3 * \step
    mov edi, \destination + 3 * \step
.else
    cld
    mov esi, \source
    mov edi, \destination
.endif
.endm

.macro CHECK_INDEX reg, base, step, back
.if \back
    cmp \reg, \base - \step
.else
    cmp \reg, \base + 4 * \step
.endif
    jne finish_pm
.endm

.macro MOVS_CASE number, instruction, step, back
    BEGIN_CASE \number
    INIT_STRING_DATA
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ebx, esi
    mov edx, edi
    mov ecx, 0
    rep \instruction
    cmp esi, ebx
    jne finish_pm
    cmp edi, edx
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    \instruction
    mov ecx, 3
    rep \instruction
    CHECK_INDEX esi, DATA, \step, \back
    CHECK_INDEX edi, DATA + 0x100, \step, \back
    mov eax, dword ptr [DATA]
    cmp dword ptr [DATA + 0x100], eax
    jne finish_pm
    mov eax, dword ptr [DATA + 4 * \step - 4]
    cmp dword ptr [DATA + 0x100 + 4 * \step - 4], eax
    jne finish_pm
    SET_DIRECTION \back, 0x7400, 0x7500, \step
    mov cx, 4
    addr16 rep \instruction
    CHECK_INDEX si, 0x7400, \step, \back
    CHECK_INDEX di, 0x7500, \step, \back
.if \step == 4
.if \back
    mov dword ptr [0xfffc], 0x11223344
    mov dword ptr [0x0000], 0x55667788
    mov si, 0x0000
    mov di, 0x7300
.else
    mov dword ptr [0xfffc], 0x11223344
    mov dword ptr [0x0000], 0x55667788
    mov si, 0xfffc
    mov di, 0x7300
.endif
    mov cx, 2
    addr16 rep movsd
.if \back
    cmp si, 0xfff8
.else
    cmp si, 0x0004
.endif
    jne finish_pm
.endif
    PASS_CASE \number
.endm

.macro STOS_CASE number, instruction, step, back
    BEGIN_CASE \number
    INIT_STRING_DATA
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov edx, edi
    mov eax, 0xa1b2c3d4
    mov ecx, 0
    rep \instruction
    cmp edi, edx
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    \instruction
    mov ecx, 3
    rep \instruction
    CHECK_INDEX edi, DATA + 0x100, \step, \back
.if \step == 1
    cmp byte ptr [DATA + 0x100], 0xd4
.elseif \step == 2
    cmp word ptr [DATA + 0x100], 0xc3d4
.else
    cmp dword ptr [DATA + 0x100], 0xa1b2c3d4
.endif
    jne finish_pm
    SET_DIRECTION \back, 0x7400, 0x7500, \step
    mov cx, 4
    addr16 rep \instruction
    CHECK_INDEX di, 0x7500, \step, \back
.if \step == 4
.if \back
    mov di, 0
.else
    mov di, 0xfffc
.endif
    mov cx, 2
    addr16 rep stosd
.if \back
    cmp di, 0xfff8
.else
    cmp di, 4
.endif
    jne finish_pm
.endif
    PASS_CASE \number
.endm

.macro LODS_CASE number, instruction, step, back
    BEGIN_CASE \number
    INIT_STRING_DATA
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ebx, esi
    mov eax, 0xa5a5a5a5
    mov ecx, 0
    rep \instruction
    cmp esi, ebx
    jne finish_pm
    cmp eax, 0xa5a5a5a5
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    \instruction
    mov ecx, 3
    rep \instruction
    CHECK_INDEX esi, DATA, \step, \back
.if \step == 1
.if \back
    cmp al, 0x11
.else
    cmp al, byte ptr [DATA + 3]
.endif
.elseif \step == 2
.if \back
    cmp ax, 0x2211
.else
    cmp ax, word ptr [DATA + 6]
.endif
.else
.if \back
    cmp eax, 0x44332211
.else
    cmp eax, dword ptr [DATA + 12]
.endif
.endif
    jne finish_pm
    SET_DIRECTION \back, 0x7400, 0x7500, \step
    mov cx, 4
    addr16 rep \instruction
    CHECK_INDEX si, 0x7400, \step, \back
    PASS_CASE \number
.endm

.macro CMPS_CASE number, instruction, step, back
    BEGIN_CASE \number
    INIT_STRING_DATA
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov eax, dword ptr [DATA]
    mov dword ptr [DATA + 0x100], eax
    mov eax, dword ptr [DATA + 4]
    mov dword ptr [DATA + 0x104], eax
    mov eax, dword ptr [DATA + 8]
    mov dword ptr [DATA + 0x108], eax
    mov eax, dword ptr [DATA + 12]
    mov dword ptr [DATA + 0x10c], eax
    mov ebx, esi
    mov edx, edi
    mov ecx, 0
    repe \instruction
    cmp esi, ebx
    jne finish_pm
    cmp edi, edx
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ecx, 4
    repe \instruction
    cmp ecx, 0
    jne finish_pm
    jne finish_pm
    /* REPNE stops on the first equal element. */
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ecx, 4
    repne \instruction
    cmp ecx, 3
    jne finish_pm
    SET_DIRECTION \back, 0x7400, 0x7500, \step
    mov eax, dword ptr [0x7400]
    mov dword ptr [0x7500], eax
    mov eax, dword ptr [0x7404]
    mov dword ptr [0x7504], eax
    mov eax, dword ptr [0x7408]
    mov dword ptr [0x7508], eax
    mov eax, dword ptr [0x740c]
    mov dword ptr [0x750c], eax
    mov cx, 4
    addr16 repe \instruction
    cmp cx, 0
    jne finish_pm
    PASS_CASE \number
.endm

.macro SCAS_CASE number, instruction, step, back
    BEGIN_CASE \number
    INIT_STRING_DATA
.if \step == 1
    mov dword ptr [DATA + 0x100], 0xd4d4d4d4
    mov dword ptr [DATA + 0x104], 0xd4d4d4d4
    mov dword ptr [DATA + 0x108], 0xd4d4d4d4
    mov dword ptr [DATA + 0x10c], 0xd4d4d4d4
    mov dword ptr [0x7500], 0xd4d4d4d4
    mov dword ptr [0x7504], 0xd4d4d4d4
    mov dword ptr [0x7508], 0xd4d4d4d4
    mov dword ptr [0x750c], 0xd4d4d4d4
.elseif \step == 2
    mov dword ptr [DATA + 0x100], 0xc3d4c3d4
    mov dword ptr [DATA + 0x104], 0xc3d4c3d4
    mov dword ptr [DATA + 0x108], 0xc3d4c3d4
    mov dword ptr [DATA + 0x10c], 0xc3d4c3d4
    mov dword ptr [0x7500], 0xc3d4c3d4
    mov dword ptr [0x7504], 0xc3d4c3d4
    mov dword ptr [0x7508], 0xc3d4c3d4
    mov dword ptr [0x750c], 0xc3d4c3d4
.else
    mov dword ptr [DATA + 0x100], 0xa1b2c3d4
    mov dword ptr [DATA + 0x104], 0xa1b2c3d4
    mov dword ptr [DATA + 0x108], 0xa1b2c3d4
    mov dword ptr [DATA + 0x10c], 0xa1b2c3d4
    mov dword ptr [0x7500], 0xa1b2c3d4
    mov dword ptr [0x7504], 0xa1b2c3d4
    mov dword ptr [0x7508], 0xa1b2c3d4
    mov dword ptr [0x750c], 0xa1b2c3d4
.endif
    mov eax, 0xa1b2c3d4
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov edx, edi
    mov ecx, 0
    repe \instruction
    cmp edi, edx
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ecx, 4
    repe \instruction
    cmp ecx, 0
    jne finish_pm
    SET_DIRECTION \back, DATA, DATA + 0x100, \step
    mov ecx, 4
    repne \instruction
    cmp ecx, 3
    jne finish_pm
    SET_DIRECTION \back, 0x7400, 0x7500, \step
    mov cx, 4
    addr16 repe \instruction
    cmp cx, 0
    jne finish_pm
    PASS_CASE \number
.endm

.if SUITE_ID == 0x0101
suite_strings:
    MOVS_CASE 1,  movsb, 1, 0
    MOVS_CASE 2,  movsb, 1, 1
    MOVS_CASE 3,  movsw, 2, 0
    MOVS_CASE 4,  movsw, 2, 1
    MOVS_CASE 5,  movsd, 4, 0
    MOVS_CASE 6,  movsd, 4, 1
    STOS_CASE 7,  stosb, 1, 0
    STOS_CASE 8,  stosb, 1, 1
    STOS_CASE 9,  stosw, 2, 0
    STOS_CASE 10, stosw, 2, 1
    STOS_CASE 11, stosd, 4, 0
    STOS_CASE 12, stosd, 4, 1
    LODS_CASE 13, lodsb, 1, 0
    LODS_CASE 14, lodsb, 1, 1
    LODS_CASE 15, lodsw, 2, 0
    LODS_CASE 16, lodsw, 2, 1
    LODS_CASE 17, lodsd, 4, 0
    LODS_CASE 18, lodsd, 4, 1
    CMPS_CASE 19, cmpsb, 1, 0
    CMPS_CASE 20, cmpsb, 1, 1
    CMPS_CASE 21, cmpsw, 2, 0
    CMPS_CASE 22, cmpsw, 2, 1
    CMPS_CASE 23, cmpsd, 4, 0
    CMPS_CASE 24, cmpsd, 4, 1
    SCAS_CASE 25, scasb, 1, 0
    SCAS_CASE 26, scasb, 1, 1
    SCAS_CASE 27, scasw, 2, 0
    SCAS_CASE 28, scasw, 2, 1
    SCAS_CASE 29, scasd, 4, 0
    SCAS_CASE 30, scasd, 4, 1
    cld
    ret
.endif

/* P0 fault/retry and P1 paging remain fail-closed until their case code is
 * added. */
.if SUITE_ID == 0x0102
suite_fault_retry:
    call enable_identity_paging

    /* 1: REP MOVSB, non-present source on the first iteration. */
    BEGIN_FAULT_CASE 1
    mov dword ptr [FAULT_PAGE], 0x44332211
    mov dword ptr [DATA + 0x400], 0xa5a5a5a5
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_movsb_first
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 4
    mov dword ptr [fault_expected_esi], FAULT_PAGE
    mov dword ptr [fault_expected_edi], DATA + 0x400
    mov dword ptr [fault_expected_eax], 0x13579bdf
    mov dword ptr [fault_seen], 0
    mov eax, 0x13579bdf
    mov esi, FAULT_PAGE
    mov edi, DATA + 0x400
    mov ecx, 4
fault_movsb_first:
    rep movsb
    call require_fault_complete
    cmp dword ptr [DATA + 0x400], 0x44332211
    jne finish_pm
    PASS_CASE 1

    /* 2: REP MOVSD, non-present source after two completed iterations. */
    BEGIN_FAULT_CASE 2
    mov dword ptr [FAULT_PAGE - 8], 0x11223344
    mov dword ptr [FAULT_PAGE - 4], 0x55667788
    mov dword ptr [FAULT_PAGE], 0x99aabbcc
    mov dword ptr [FAULT_PAGE + 4], 0xddeeff00
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_movsd_middle
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 2
    mov dword ptr [fault_expected_esi], FAULT_PAGE
    mov dword ptr [fault_expected_edi], DATA + 0x508
    mov dword ptr [fault_expected_eax], 0x2468ace0
    mov dword ptr [fault_seen], 0
    mov eax, 0x2468ace0
    mov esi, FAULT_PAGE - 8
    mov edi, DATA + 0x500
    mov ecx, 4
fault_movsd_middle:
    rep movsd
    call require_fault_complete
    cmp dword ptr [DATA + 0x500], 0x11223344
    jne finish_pm
    cmp dword ptr [DATA + 0x50c], 0xddeeff00
    jne finish_pm
    PASS_CASE 2

    /* 3: REP MOVSD, non-present destination on the last iteration. */
    BEGIN_FAULT_CASE 3
    mov dword ptr [DATA + 0x600], 0x10203040
    mov dword ptr [DATA + 0x604], 0x50607080
    mov dword ptr [DATA + 0x608], 0x90a0b0c0
    mov dword ptr [DATA + 0x60c], 0xd0e0f000
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_movsd_last
    mov dword ptr [fault_expected_error], 2
    mov dword ptr [fault_expected_ecx], 1
    mov dword ptr [fault_expected_esi], DATA + 0x60c
    mov dword ptr [fault_expected_edi], FAULT_PAGE
    mov dword ptr [fault_expected_eax], 0x0badcafe
    mov dword ptr [fault_seen], 0
    mov eax, 0x0badcafe
    mov esi, DATA + 0x600
    mov edi, FAULT_PAGE - 12
    mov ecx, 4
fault_movsd_last:
    rep movsd
    call require_fault_complete
    cmp dword ptr [FAULT_PAGE], 0xd0e0f000
    jne finish_pm
    PASS_CASE 3

    /* 4: supervisor REP STOSB write-protection fault. */
    BEGIN_FAULT_CASE 4
    mov eax, FAULT_PAGE
    mov edx, 1
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_stosb_wp
    mov dword ptr [fault_expected_error], 3
    mov dword ptr [fault_expected_ecx], 4
    mov dword ptr [fault_expected_esi], 0x76543210
    mov dword ptr [fault_expected_edi], FAULT_PAGE
    mov dword ptr [fault_expected_eax], 0x55aa55d4
    mov dword ptr [fault_seen], 0
    mov eax, 0x55aa55d4
    mov esi, 0x76543210
    mov edi, FAULT_PAGE
    mov ecx, 4
fault_stosb_wp:
    rep stosb
    call require_fault_complete
    cmp dword ptr [FAULT_PAGE], 0xd4d4d4d4
    jne finish_pm
    PASS_CASE 4

    /* 5: REP STOSD middle fault, then backward middle fault. */
    BEGIN_FAULT_CASE 5
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_stosd_middle
    mov dword ptr [fault_expected_error], 2
    mov dword ptr [fault_expected_ecx], 2
    mov dword ptr [fault_expected_esi], 0x76543210
    mov dword ptr [fault_expected_edi], FAULT_PAGE
    mov dword ptr [fault_expected_eax], 0x1234abcd
    mov dword ptr [fault_seen], 0
    mov eax, 0x1234abcd
    mov esi, 0x76543210
    mov edi, FAULT_PAGE - 8
    mov ecx, 4
    cld
fault_stosd_middle:
    rep stosd
    call require_fault_complete
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE + 0xffc
    mov dword ptr [fault_expected_eip], offset fault_stosd_backward
    mov dword ptr [fault_expected_ecx], 2
    mov dword ptr [fault_expected_edi], FAULT_PAGE + 0xffc
    mov dword ptr [fault_seen], 0
    mov eax, 0x1234abcd
    mov esi, 0x76543210
    mov edi, FAULT_PAGE + 0x1004
    mov ecx, 4
    std
fault_stosd_backward:
    rep stosd
    cld
    call require_fault_complete
    PASS_CASE 5

    /* 6: REP LODSD split-page source operand. */
    BEGIN_FAULT_CASE 6
    mov word ptr [FAULT_PAGE - 2], 0x2211
    mov word ptr [FAULT_PAGE], 0x4433
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_lodsd_split
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 1
    mov dword ptr [fault_expected_esi], FAULT_PAGE - 2
    mov dword ptr [fault_expected_edi], 0x23456789
    mov dword ptr [fault_expected_eax], 0xa5a5a5a5
    mov dword ptr [fault_seen], 0
    mov eax, 0xa5a5a5a5
    mov esi, FAULT_PAGE - 2
    mov edi, 0x23456789
    mov ecx, 1
fault_lodsd_split:
    rep lodsd
    call require_fault_complete
    REQUIRE_EQ eax, 0x44332211
    PASS_CASE 6

    /* 7: REPE CMPSD source fault after one completed comparison. */
    BEGIN_FAULT_CASE 7
    mov dword ptr [FAULT_PAGE - 4], 0x11112222
    mov dword ptr [FAULT_PAGE], 0x33334444
    mov dword ptr [DATA + 0x700], 0x11112222
    mov dword ptr [DATA + 0x704], 0x33334444
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_cmpsd_middle
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 1
    mov dword ptr [fault_expected_esi], FAULT_PAGE
    mov dword ptr [fault_expected_edi], DATA + 0x704
    mov dword ptr [fault_expected_eax], 0x1122aabb
    mov dword ptr [fault_seen], 0
    mov eax, 0x1122aabb
    mov esi, FAULT_PAGE - 4
    mov edi, DATA + 0x700
    mov ecx, 2
fault_cmpsd_middle:
    repe cmpsd
    call require_fault_complete
    cmp ecx, 0
    jne finish_pm
    PASS_CASE 7

    /* 8: REPNE SCASD destination fault on the last iteration. */
    BEGIN_FAULT_CASE 8
    mov dword ptr [FAULT_PAGE - 12], 1
    mov dword ptr [FAULT_PAGE - 8], 2
    mov dword ptr [FAULT_PAGE - 4], 3
    mov dword ptr [FAULT_PAGE], 0x778899aa
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset fault_scasd_last
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 1
    mov dword ptr [fault_expected_esi], 0x3456789a
    mov dword ptr [fault_expected_edi], FAULT_PAGE
    mov dword ptr [fault_expected_eax], 0x778899aa
    mov dword ptr [fault_seen], 0
    mov eax, 0x778899aa
    mov esi, 0x3456789a
    mov edi, FAULT_PAGE - 12
    mov ecx, 4
fault_scasd_last:
    repne scasd
    call require_fault_complete
    cmp ecx, 0
    jne finish_pm
    PASS_CASE 8
    ret
.endif
.if SUITE_ID == 0x0103
/*
 * PageFaultCore progress characterization. The result asserts only x86-visible
 * behavior; device-dependent host call and duration aggregates remain in the
 * bounded liveness record.
 */
suite_pagefault_progress:
    call enable_identity_paging

    /* 1: one deliberately long handler, followed by successful retry. */
    BEGIN_CASE 1
    mov dword ptr [FAULT_PAGE], 0x13579bdf
    mov dword ptr [progress_mode], 1
    mov dword ptr [progress_delay_iterations], 262144
    call reset_progress_counts
    mov dword ptr [progress_expected_eip], offset progress_single_fault
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [progress_saved_esp], esp
    mov esp, FAULT_PAGE
    jmp progress_single_fault
progress_single_fault:
    pop ebx
    mov esp, dword ptr [progress_saved_esp]
    REQUIRE_EQ ebx, 0x13579bdf
    mov eax, 1
    call require_progress_counts
    PASS_CASE 1

    /* 2: repeated sequential handlers must each finish before the next fault. */
    BEGIN_CASE 2
    mov dword ptr [progress_mode], 2
    mov dword ptr [progress_delay_iterations], 8192
    call reset_progress_counts
    mov dword ptr [progress_expected_eip], offset progress_sequential_fault
    mov esi, 16
progress_sequential_loop:
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov esp, FAULT_PAGE
    jmp progress_sequential_fault
progress_sequential_fault:
    pop ebx
    mov esp, dword ptr [progress_saved_esp]
    cmp ebx, 0x13579bdf
    jne finish_pm
    dec esi
    jnz progress_sequential_loop
    mov eax, 16
    call require_progress_counts
    PASS_CASE 2

    /* 3: four handlers enter in order and return in reverse order. */
    BEGIN_CASE 3
    mov dword ptr [FAULT_PAGE], 0x10203040
    mov dword ptr [PROGRESS_PAGE_2], 0x21314151
    mov dword ptr [PROGRESS_PAGE_3], 0x32425262
    mov dword ptr [PROGRESS_PAGE_4], 0x43536373
    mov dword ptr [progress_mode], 3
    mov dword ptr [progress_delay_iterations], 32768
    call reset_progress_counts
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov eax, PROGRESS_PAGE_2
    call map_page_flags
    mov eax, PROGRESS_PAGE_3
    call map_page_flags
    mov eax, PROGRESS_PAGE_4
    call map_page_flags
    mov esp, FAULT_PAGE
    jmp progress_nested_fault_1
progress_nested_fault_1:
    pop ebx
    mov esp, dword ptr [progress_saved_esp]
    REQUIRE_EQ ebx, 0x10203040
    mov eax, 4
    call require_progress_counts
    PASS_CASE 3
    ret

reset_progress_counts:
    mov dword ptr [progress_entries], 0
    mov dword ptr [progress_returns], 0
    mov dword ptr [progress_work], 0
    mov dword ptr [progress_bad], 0
    ret

/* EAX is the exact expected entry, return and bounded-work count. */
require_progress_counts:
    cmp dword ptr [progress_entries], eax
    jne finish_pm
    cmp dword ptr [progress_returns], eax
    jne finish_pm
    cmp dword ptr [progress_work], eax
    jne finish_pm
    cmp dword ptr [progress_bad], 0
    jne finish_pm
    ret

/* Deliberate guest work: bounded, deterministic, and free of host timing. */
progress_delay:
    push eax
    push ecx
    xor eax, eax
    mov ecx, dword ptr [progress_delay_iterations]
progress_delay_loop:
    inc eax
    loop progress_delay_loop
    cmp eax, dword ptr [progress_delay_iterations]
    je progress_delay_complete
    mov dword ptr [progress_bad], 1
progress_delay_complete:
    inc dword ptr [progress_work]
    pop ecx
    pop eax
    ret
.endif
.if SUITE_ID == 0x0203
suite_paging_smc:
    call enable_identity_paging

    BEGIN_CASE 1
    mov eax, 0x12345678
    cmp eax, eax
    pushfd
    pop ebx
    invlpg [0x40404040]
    invlpg [eax + 0x40]
    invlpg [eax + eax * 2 + 0x40404040]
    addr16 invlpg [0x4040]
    pushfd
    pop ecx
    REQUIRE_EQ eax, 0x12345678
    cmp ebx, ecx
    jne finish_pm
    PASS_CASE 1

    BEGIN_CASE 2
    mov dword ptr [FAULT_PAGE], 0x55aa33cc
    mov eax, FAULT_PAGE
    xor edx, edx
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset paging_read_fault
    mov dword ptr [fault_expected_error], 0
    mov dword ptr [fault_expected_ecx], 0x12345678
    mov dword ptr [fault_expected_esi], 0x23456789
    mov dword ptr [fault_expected_edi], 0x3456789a
    mov dword ptr [fault_expected_eax], 0x456789ab
    mov dword ptr [fault_seen], 0
    mov eax, 0x456789ab
    mov ecx, 0x12345678
    mov esi, 0x23456789
    mov edi, 0x3456789a
paging_read_fault:
    mov ebx, dword ptr [FAULT_PAGE]
    call require_fault_complete
    REQUIRE_EQ ebx, 0x55aa33cc
    PASS_CASE 2

    BEGIN_CASE 3
    mov eax, FAULT_PAGE
    mov edx, 1
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset paging_write_fault
    mov dword ptr [fault_expected_error], 3
    mov dword ptr [fault_expected_ecx], 0x12345678
    mov dword ptr [fault_expected_esi], 0x23456789
    mov dword ptr [fault_expected_edi], 0x3456789a
    mov dword ptr [fault_expected_eax], 0x1122aabb
    mov dword ptr [fault_seen], 0
    mov eax, 0x1122aabb
    mov ecx, 0x12345678
    mov esi, 0x23456789
    mov edi, 0x3456789a
paging_write_fault:
    mov dword ptr [FAULT_PAGE], eax
    call require_fault_complete
    cmp dword ptr [FAULT_PAGE], 0x1122aabb
    jne finish_pm
    PASS_CASE 3

    /* Case 4 is the ring-3 user/supervisor mapping check below. */
    call paging_user_case

    BEGIN_CASE 5
    mov dword ptr [0x38000], 0x11112222
    mov dword ptr [0x39000], 0x33334444
    mov dword ptr [PAGE_TABLE + (0x37000 >> 12) * 4], 0x38003
    invlpg [0x37000]
    cmp dword ptr [0x37000], 0x11112222
    jne finish_pm
    mov dword ptr [PAGE_TABLE + (0x37000 >> 12) * 4], 0x39003
    invlpg [0x37000]
    invlpg [0x37000]
    cmp dword ptr [0x37000], 0x33334444
    jne finish_pm
    PASS_CASE 5

    BEGIN_CASE 6
    mov byte ptr [0x38000], 0xb8
    mov dword ptr [0x38001], 0x11223344
    mov byte ptr [0x38005], 0xc3
    call 0x38000
    REQUIRE_EQ eax, 0x11223344
    mov dword ptr [0x38001], 0xa55a5aa5
    call 0x38000
    REQUIRE_EQ eax, 0xa55a5aa5
    PASS_CASE 6

    BEGIN_CASE 7
    mov byte ptr [0x3fffd], 0xb8
    mov dword ptr [0x3fffe], 0x1234abcd
    mov byte ptr [0x40002], 0xc3
    call 0x3fffd
    REQUIRE_EQ eax, 0x1234abcd
    mov dword ptr [0x3fffe], 0x7654fedc
    call 0x3fffd
    REQUIRE_EQ eax, 0x7654fedc
    PASS_CASE 7

    BEGIN_CASE 8
    xor eax, eax
    mov ecx, 8192
paging_linked_loop:
    add eax, ecx
    xor eax, 0x9e3779b9
    xor eax, 0x9e3779b9
    loop paging_linked_loop
    cmp eax, 0x02001000
    jne finish_pm
    PASS_CASE 8
    ret

paging_user_case:
    BEGIN_CASE 4
    or dword ptr [PAGE_DIRECTORY], 4
    mov edi, PAGE_TABLE
    mov ecx, 1024
paging_user_pte_loop:
    or dword ptr [edi], 4
    add edi, 4
    loop paging_user_pte_loop
    mov dword ptr [FAULT_PAGE], 0x66778899
    mov eax, FAULT_PAGE
    mov edx, 3
    call map_page_flags
    mov dword ptr [fault_expected_cr2], FAULT_PAGE
    mov dword ptr [fault_expected_eip], offset paging_user_fault
    mov dword ptr [fault_expected_error], 5
    mov dword ptr [fault_expected_ecx], 0x10293847
    mov dword ptr [fault_expected_esi], 0x56473829
    mov dword ptr [fault_expected_edi], 0x76543210
    mov dword ptr [fault_expected_eax], 0x13579bdf
    mov dword ptr [fault_map_flags], 7
    mov dword ptr [fault_seen], 0
    mov edi, 0x5800
    xor eax, eax
    mov ecx, 26
    rep stosd
    mov dword ptr [0x5804], 0x6f00
    mov word ptr [0x5808], 0x10
    mov word ptr [0x5866], 104
    mov ax, 0x30
    ltr ax
    mov dword ptr [ring0_esp], esp
    push 0x2b
    push DATA + 0x2000
    pushfd
    push 0x23
    push offset paging_user_entry
    iretd
paging_user_entry:
    mov ax, 0x2b
    mov ds, ax
    mov es, ax
    mov eax, 0x13579bdf
    mov ecx, 0x10293847
    mov esi, 0x56473829
    mov edi, 0x76543210
paging_user_fault:
    mov ebx, dword ptr [FAULT_PAGE]
    cmp ebx, 0x66778899
    jne paging_user_failed
    int 0x31
paging_user_failed:
    ud2
paging_user_return:
    mov dword ptr [fault_map_flags], 3
    call require_fault_complete
    PASS_CASE 4
    ret
.endif

enable_identity_paging:
    mov edi, PAGE_DIRECTORY
    xor eax, eax
    mov ecx, 2048
    rep stosd
    mov dword ptr [PAGE_DIRECTORY], PAGE_TABLE + 3
    mov edi, PAGE_TABLE
    mov eax, 3
    mov ecx, 1024
identity_page_loop:
    stosd
    add eax, 0x1000
    loop identity_page_loop
    mov eax, PAGE_DIRECTORY
    mov cr3, eax
    mov eax, cr0
    or eax, 0x80010000
    mov cr0, eax
    jmp paging_enabled_return
paging_enabled_return:
    mov dword ptr [fault_bad], 0
    mov dword ptr [fault_map_flags], 3
    ret

/* EAX is a linear address and EDX contains the desired low PTE flags. */
map_page_flags:
    push eax
    push ebx
    mov ebx, eax
    and eax, 0xfffff000
    and ebx, 0x003ff000
    shr ebx, 10
    or eax, edx
    mov dword ptr [PAGE_TABLE + ebx], eax
    pop ebx
    pop eax
    invlpg [eax]
    ret

require_fault_complete:
    cmp dword ptr [fault_seen], 1
    jne finish_pm
    cmp dword ptr [fault_bad], 0
    jne finish_pm
    ret

divide_handler:
    inc dword ptr [divide_seen]
    push eax
    mov eax, dword ptr [divide_resume]
    mov dword ptr [esp + 4], eax
    pop eax
    iretd

invalid_handler:
    inc dword ptr [invalid_seen]
    add dword ptr [esp], 2
    iretd

interrupt_handler:
    inc dword ptr [interrupt_seen]
    iretd

user_return_handler:
.if SUITE_ID == 0x0203
    mov ax, 0x10
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov esp, dword ptr [ring0_esp]
    jmp paging_user_return
.else
    iretd
.endif

page_fault_handler:
    pushad
    mov eax, cr2
    cmp eax, dword ptr [fault_expected_cr2]
    jne page_fault_bad_cr2
    cmp dword ptr [esp + 32], 0
    jl page_fault_bad_error
    mov ebx, dword ptr [fault_expected_error]
    cmp dword ptr [esp + 32], ebx
    jne page_fault_bad_error
    mov ebx, dword ptr [fault_expected_eip]
    cmp dword ptr [esp + 36], ebx
    jne page_fault_bad_eip
    mov ebx, dword ptr [fault_expected_ecx]
    cmp dword ptr [esp + 24], ebx
    jne page_fault_bad_ecx
    mov ebx, dword ptr [fault_expected_esi]
    cmp dword ptr [esp + 4], ebx
    jne page_fault_bad_esi
    mov ebx, dword ptr [fault_expected_edi]
    cmp dword ptr [esp], ebx
    jne page_fault_bad_edi
    mov ebx, dword ptr [fault_expected_eax]
    cmp dword ptr [esp + 28], ebx
    jne page_fault_bad_eax
    inc dword ptr [fault_seen]
    mov edx, dword ptr [fault_map_flags]
    call map_page_flags
    jmp page_fault_return
page_fault_bad_cr2:
    mov bx, word ptr [fault_assertion_base]
    add bx, 1
    jmp page_fault_record_bad
page_fault_bad_error:
    mov bx, word ptr [fault_assertion_base]
    add bx, 2
    jmp page_fault_record_bad
page_fault_bad_eip:
    mov bx, word ptr [fault_assertion_base]
    add bx, 3
    jmp page_fault_record_bad
page_fault_bad_ecx:
    mov bx, word ptr [fault_assertion_base]
    add bx, 4
    jmp page_fault_record_bad
page_fault_bad_esi:
    mov bx, word ptr [fault_assertion_base]
    add bx, 5
    jmp page_fault_record_bad
page_fault_bad_edi:
    mov bx, word ptr [fault_assertion_base]
    add bx, 6
    jmp page_fault_record_bad
page_fault_bad_eax:
    mov bx, word ptr [fault_assertion_base]
    add bx, 7
page_fault_record_bad:
    mov word ptr [ASSERTION], bx
page_fault_bad:
    mov dword ptr [fault_bad], 1
    mov edx, dword ptr [fault_map_flags]
    call map_page_flags
page_fault_return:
    popad
    add esp, 4
    iretd

.if SUITE_ID == 0x0103
progress_page_fault_handler:
    pushad
    mov eax, cr2
    cmp dword ptr [esp + 32], 0
    jne progress_fault_bad
    cmp dword ptr [progress_mode], 3
    je progress_nested_dispatch
    cmp eax, FAULT_PAGE
    jne progress_fault_bad
    mov ebx, dword ptr [progress_expected_eip]
    cmp dword ptr [esp + 36], ebx
    jne progress_fault_bad
    cmp dword ptr [progress_entries], 0
    jl progress_fault_bad
    inc dword ptr [progress_entries]
    call progress_delay
    mov edx, 3
    call map_page_flags
    inc dword ptr [progress_returns]
    jmp progress_fault_return

progress_nested_dispatch:
    mov ebx, dword ptr [progress_entries]
    cmp ebx, 0
    je progress_fault_level_1
    cmp ebx, 1
    je progress_fault_level_2
    cmp ebx, 2
    je progress_fault_level_3
    cmp ebx, 3
    je progress_fault_level_4
    jmp progress_fault_bad

progress_fault_level_1:
    cmp eax, FAULT_PAGE
    jne progress_fault_bad
    cmp dword ptr [esp + 36], offset progress_nested_fault_1
    jne progress_fault_bad
    inc dword ptr [progress_entries]
    call progress_delay
    mov ebp, esp
    mov esp, PROGRESS_PAGE_2
progress_nested_fault_2:
    pop ebx
    mov esp, ebp
    cmp ebx, 0x21314151
    jne progress_fault_bad
    cmp dword ptr [progress_returns], 3
    jne progress_fault_bad
    mov eax, FAULT_PAGE
    jmp progress_fault_complete

progress_fault_level_2:
    cmp eax, PROGRESS_PAGE_2
    jne progress_fault_bad
    cmp dword ptr [esp + 36], offset progress_nested_fault_2
    jne progress_fault_bad
    inc dword ptr [progress_entries]
    call progress_delay
    mov ebp, esp
    mov esp, PROGRESS_PAGE_3
progress_nested_fault_3:
    pop ebx
    mov esp, ebp
    cmp ebx, 0x32425262
    jne progress_fault_bad
    cmp dword ptr [progress_returns], 2
    jne progress_fault_bad
    mov eax, PROGRESS_PAGE_2
    jmp progress_fault_complete

progress_fault_level_3:
    cmp eax, PROGRESS_PAGE_3
    jne progress_fault_bad
    cmp dword ptr [esp + 36], offset progress_nested_fault_3
    jne progress_fault_bad
    inc dword ptr [progress_entries]
    call progress_delay
    mov ebp, esp
    mov esp, PROGRESS_PAGE_4
progress_nested_fault_4:
    pop ebx
    mov esp, ebp
    cmp ebx, 0x43536373
    jne progress_fault_bad
    cmp dword ptr [progress_returns], 1
    jne progress_fault_bad
    mov eax, PROGRESS_PAGE_3
    jmp progress_fault_complete

progress_fault_level_4:
    cmp eax, PROGRESS_PAGE_4
    jne progress_fault_bad
    cmp dword ptr [esp + 36], offset progress_nested_fault_4
    jne progress_fault_bad
    inc dword ptr [progress_entries]
    call progress_delay
    cmp dword ptr [progress_returns], 0
    jne progress_fault_bad
    mov eax, PROGRESS_PAGE_4

progress_fault_complete:
    mov edx, 3
    call map_page_flags
    inc dword ptr [progress_returns]
    jmp progress_fault_return

/* Map only the faulting page so a bad fixture unwinds and records failure. */
progress_fault_bad:
    mov dword ptr [progress_bad], 1
    mov eax, cr2
    mov edx, 3
    call map_page_flags
progress_fault_return:
    popad
    add esp, 4
    iretd
.endif

finish_pm:
    cli
    mov eax, cr0
    and eax, 0x7ffeffff
    mov cr0, eax
    jmp paging_off
paging_off:
    ljmp 0x18, pm16

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
    mov dword ptr [COMPLETE], 0xc04ec04e

    mov esi, RESULT
    mov ecx, 48
    mov eax, 0x811c9dc5
fnv_loop:
    xor al, byte ptr [esi]
    imul eax, eax, 0x01000193
    inc esi
    loop fnv_loop
    mov dword ptr [INTEGRITY], eax

    mov bx, RESULT
    mov ah, 3
    mov al, 1
    mov ch, RESULT_CYLINDER
    mov cl, RESULT_SECTOR
    xor dh, dh
    mov dl, byte ptr [boot_drive]
    int 0x13

    mov al, 'F'
    cmp dword ptr [DONE_LO], EXPECTED_LO
    jne show_result
    cmp dword ptr [DONE_HI], EXPECTED_HI
    jne show_result
    cmp word ptr [ASSERTION], 0
    jne show_result
    mov al, 'P'
show_result:
    mov ah, 0x0e
    xor bx, bx
    int 0x10
halt:
    hlt
    jmp halt

boot_drive: .byte 0
divide_seen: .long 0
divide_resume: .long 0
invalid_seen: .long 0
interrupt_seen: .long 0
fault_seen: .long 0
fault_bad: .long 0
fault_expected_cr2: .long 0
fault_expected_eip: .long 0
fault_expected_error: .long 0
fault_expected_ecx: .long 0
fault_expected_esi: .long 0
fault_expected_edi: .long 0
fault_expected_eax: .long 0
fault_assertion_base: .word 2090
fault_map_flags: .long 3
.if SUITE_ID == 0x0103
progress_mode: .long 0
progress_delay_iterations: .long 0
progress_expected_eip: .long 0
progress_saved_esp: .long 0
progress_entries: .long 0
progress_returns: .long 0
progress_work: .long 0
progress_bad: .long 0
.endif
ring0_esp: .long 0
x87_six: .long 6
x87_seven: .long 7
x87_three: .long 3
x87_default_cw: .word 0x037f
x87_round_down: .word 0x077f
x87_round_up: .word 0x0b7f
x87_two_point_seven: .long 0x402ccccd
x87_large: .quad 0x0012345689abcdef

.align 8
gdt:
    .quad 0
    .word 0xffff, 0x0000
    .byte 0x00, 0x9a, 0xcf, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0x92, 0xcf, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0x9a, 0x00, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0xfa, 0xcf, 0x00
    .word 0xffff, 0x0000
    .byte 0x00, 0xf2, 0xcf, 0x00
    .word 103, 0x5800
    .byte 0x00, 0x89, 0x00, 0x00
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
