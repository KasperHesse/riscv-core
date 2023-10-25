package core

import chisel3._
import chisel3.ChiselEnum

/**
 * Enum encoding of the operations that the ALU can perform.
 * Obtained by concatenating funct7(5) and funct3 of RR instructions
 */
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

/**
 * Enum used to encode where ALU operands should be taken from
 */
object OpSrc extends ChiselEnum {
  /** Operand should be taken from register file */
  val REG   = Value(0.U)
  /** Operand should be taken from immediate */
  val IMM   = Value(1.U)
  /** Operand should be taken from instruction PC */
  val PC    = Value(2.U)
  /** Operand should be a constant (0 when rs1, 4 when rs2) */
  val CONST = Value(3.U)
}
