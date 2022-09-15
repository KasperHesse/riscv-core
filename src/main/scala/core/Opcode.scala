package core

import chisel3._
import chisel3.experimental.ChiselEnum

object Funct3 {
  val ADDI, JALR, BEQ, LB, SB, ADD, SUB, FENCE, ECALL, EBREAK = 0
  val BNE, LH, SH, SLLI, SLL = 1
  val LW, SW, SLTI, SLT = 2
  val SLTIU, SLTU = 3
  val BLT, LBU, XORI, XOR = 4
  val BGE, LHU, SRLI, SRAI, SRL, SRA = 5
  val BLTU, ORI, OR = 6
  val BGEU, ANDI, AND = 7
}

object Funct7 {
  val SRAI, SUB, SRA = 0x20
  val OTHERS = 0
}

object Opcode extends ChiselEnum {
  val LOAD =     Value("b0000011".U)
  val MISC_MEM = Value("b0001111".U)
  val OP_IMM =   Value("b0010011".U)
  val AUIPC =    Value("b0010111".U)
  val STORE =    Value("b0100011".U)
  val OP =       Value("b0110011".U)
  val LUI =      Value("b0110111".U)
  val BRANCH =   Value("b1100011".U)
  val JALR =     Value("b1100111".U)
  val JAL =      Value("b1101111".U)
  val SYSTEM =   Value("b1110011".U)
}
