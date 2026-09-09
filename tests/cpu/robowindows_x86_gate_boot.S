/* Protocol-v4 expanded-suite loader: 32 sectors, result at LBA 40. */
.intel_syntax noprefix
.section .text
.code16
.global start

.equ STAGE_ADDRESS, 0x8000

start:
    cli
    xor ax, ax
    mov ss, ax
    mov sp, 0x7c00
    mov ds, ax
    mov es, ax
    sti
    mov byte ptr [boot_drive], dl

    /* Sectors 2..18 on cylinder 0, head 0. */
    mov bx, STAGE_ADDRESS
    mov ah, 2
    mov al, 17
    xor ch, ch
    mov cl, 2
    xor dh, dh
    mov dl, byte ptr [boot_drive]
    int 0x13
    jc load_failed

    /* Sectors 1..15 on cylinder 0, head 1. */
    mov bx, STAGE_ADDRESS + 17 * 512
    mov ah, 2
    mov al, 15
    xor ch, ch
    mov cl, 1
    mov dh, 1
    mov dl, byte ptr [boot_drive]
    int 0x13
    jc load_failed
    ljmp 0x0000, STAGE_ADDRESS

load_failed:
    mov al, 'L'
    mov ah, 0x0e
    xor bx, bx
    int 0x10
halt:
    hlt
    jmp halt

boot_drive: .byte 0

.org 510
.word 0xaa55
