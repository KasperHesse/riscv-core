#include "include/io.h"

void main() {
  while(1) {
    uart_write("Hello, world!\n\r", 15);
    //Stall a bit
    for(int i=0; i< 4000000; i++) {

    }
  }
}