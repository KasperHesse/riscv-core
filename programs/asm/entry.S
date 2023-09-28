.globl _start

.section .text.start
_start:
# Reset registers. Maybe not necessary on FPGA?

# Setup stack
lui sp, %hi(stack_top)
addi sp, sp, %lo(stack_top)
#li sp, stack_top

# Jump to main, hoping to never return
j main

#If we exit main, we halt here
halt:
j halt