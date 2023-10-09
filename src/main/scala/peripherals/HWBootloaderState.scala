package peripherals

import chisel3.ChiselEnum

object HWBootloaderState extends ChiselEnum {
  val IDLE, LOOP1, LOOP2, LOOP3, GO = Value
}
