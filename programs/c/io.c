#include "include/io.h"

/* PERIPHERAL DEFINITIONS */
#define UART_BASE 0x01000000
#define LEDS_BASE 0x02000000
#define KEYS_BASE 0x03000000

volatile uart_t* uart = (volatile uart_t*) UART_BASE;
volatile leds_t* leds = (volatile leds_t*) LEDS_BASE;
volatile keys_t* keys = (volatile keys_t*) KEYS_BASE;

void uart_write(char* data, int len) {
  int ptr;
  for(ptr = 0; ptr < len && data[ptr]; ptr++) {
    while(uart->tx_buf_full) {};
    uart->tx_data = data[ptr];
  }
}


void uart_read(char* buf, int N) {
  for(int i=0; i < N; i++) {
    while(!uart->rx_buf_avail) {};
    buf[i] = uart->rx_data;
  }
}