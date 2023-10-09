package core.modules

import chisel3._
import chisel3.experimental.ChiselEnum

object CSROP extends ChiselEnum {
  val SWAP = Value("b01".U)
  val READSET = Value("b10".U)
  val READCLEAR = Value("b11".U)
}
