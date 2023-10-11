package core

import chisel3._
import chisel3.ChiselEnum

object AluOp extends ChiselEnum {
  val ADD = Value("b0000".U)
  val SLL = Value("b0001".U)
  val SLT = Value("b0010".U)
  val SLTU = Value("b0011".U)
  val XOR = Value("b0100".U)
  val SRL = Value("b0101".U)
  val OR  = Value("b0110".U)
  val AND = Value("b0111".U)
  val SUB = Value("b1000".U)
  val SRA = Value("b1101".U)


}
