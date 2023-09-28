.equ UART_BASE,    0x01000000
.equ UART_TX,      0x01000010
.equ UART_TX_FULL, 0x01000014
.globl main

.text
main:
la x1, HELLO_WORLD
lb x2, 0(x1) #Initial fetch to simplify loop
lui x3, %hi(UART_TX)
addi x3, x3, %lo(UART_TX)      #x3 = UART_TX
lui x4, %hi(UART_TX_FULL)
addi x4, x4, %lo(UART_TX_FULL) # x4 = UART_TX_FULL
ECHO:
beqz x2, PAUSE           #End of string
addi x1, x1, 1          #incr char index
CHECK_TX_FULL:
lw x5, (x4)     #check if buffer is full
bnez x5, CHECK_TX_FULL
sb x2, (x3)          #write to UART
lbu x2, 0(x1)        #Fetch new value
j ECHO
PAUSE: #end of string. Count up before going back
li x1, 0
li x2, 8000000
PAUSE_LOOP: addi x1, x1, 1
blt x1, x2, PAUSE_LOOP #count up
j _start

.data
HELLO_WORLD: .string "Hello World!\n\r"
.align(4) #Make sure a full byte is taken up