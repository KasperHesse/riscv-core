.globl main

main:
li x1, 5
li x2, 0xab
csrw fcsr, x2
li x3, 0x02
li x4, 0x5
sll x5, x3, x4
or x6, x5, x4
csrr x7, fflags
slli x8, x7, 1