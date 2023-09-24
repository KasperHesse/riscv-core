#include <stdint.h>

#define UART_BASE  0x01000000
#define INSTR_BASE (uint8_t*) 0x00001000
#define prog ((void (*)(void))0x00001000)

/*
Uart functionality:
Read data
read data available flag
write data
write data buffer full flag
*/
typedef struct {
  uint32_t rd_data;     //Read data word. Undefined if rd_flag is 0
  uint32_t rd_flag;     //Read data available flag. 1 if data is available, 0 otherwise
  uint32_t rd_buf_cnt;  //Number of data words available in read buffer
  uint32_t not_used;
  uint32_t wr_data;     //Write data to write buffer
  uint32_t wr_buf_full; //Write buffer full flag. 1 if buffer is full, 0 otherwise. If data is written while full, that data is lot
  uint32_t wr_buf_cnt;  //Number of items in write buffer
} uart_t;

/**
General structure:
Wait until a byte arrives. That byte encodes the number of following data bytes
Load all of those data bytes into memory starting at INSTR_BASE
Repeat until M != 255, then jump to INSTR_BASE

https://stackoverflow.com/questions/31390127/how-can-i-compile-c-code-to-get-a-bare-metal-skeleton-of-a-minimal-risc-v-assemb#31393890
*/

void main() {
  uint8_t num_bytes;
  uint16_t tot_bytes;
  uint8_t num_bytes_read;
  volatile uart_t* uart = (uart_t*) UART_BASE;

  num_bytes = 255;
  tot_bytes = 0;

  do {
    while (!uart->rd_flag) {}; //spin
    num_bytes = uart->rd_data;

    for(num_bytes_read = 0; num_bytes_read < num_bytes; num_bytes++) {
      *(INSTR_BASE + tot_bytes) = uart->rd_data;
    };
  } while(num_bytes == 255);
  //Finished fetching: Jump to INSTR_BASE
  prog();

}