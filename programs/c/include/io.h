#ifndef _IO_H
#define _IO_H

#include "stdint.h"

/* PERIPHERAL STRUCTS */
typedef struct {
  uint32_t rx_data;
  uint32_t rx_buf_full;
  uint32_t rx_buf_cnt;
  uint32_t rx_buf_avail;
  uint32_t tx_data;
  uint32_t tx_buf_full;
  uint32_t tx_buf_cnt;
} uart_t;

typedef struct {
  int state;
} leds_t;

typedef struct {
  int state;
} keys_t;

/* PERIPHERAL FUNCTIONS */

/**
 * Write data on the UART. Terminates early if a null byte is encountered
 * @param data The data to write out on the UART.
 * @param len Length of the data array
*/
void uart_write(char* data, int len);

/**
 * Reads a number of bytes from the UART. Does not return until the required num. bytes has been read
 * @param buf Pre-allocated buffer for the UART read data
 * @param N The number of bytes to read
*/
void uart_read(char* buf, int N);

#endif /* _IO_H */