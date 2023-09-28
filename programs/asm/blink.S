.globl main

.text
main:
li x1, 0        # Blink value
li x2, 0        # Counter value
li x3, 4000000  #Max count value
li x4, 0x02000000

LOOP: addi x2, x2, 1 #Takes 4.4cc per increment of the counter. Max count 4_000_000 = 17_200_000 cc = 1/4 of a second
blt x2, x3, LOOP #count up
addi x1, x1, 1
sw x1, 0(x4)
li x2, 0
j LOOP


