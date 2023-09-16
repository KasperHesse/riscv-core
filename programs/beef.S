.globl _start

.text
_start:
li x1, 0xbeef
li x2, 0x02000000
sw x1, 0(x2)
LOOP: addi x0, x0, 0
j LOOP