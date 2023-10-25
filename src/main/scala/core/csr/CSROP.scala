package core.csr

import chisel3._
import chisel3.ChiselEnum

object CSROP extends ChiselEnum {
  val UNUSED = Value("b00".U)
  val SWAP = Value("b01".U)
  val READSET = Value("b10".U)
  val READCLEAR = Value("b11".U)
}
