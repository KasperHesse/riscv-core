package core

import chisel3._
import chisel3.util._

class Execute(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val id = Flipped(new DecodeExecuteIO)
    val mem = new ExecuteMemoryIO
  })
  /* Operations that the ordinary ALU can perform. Operates on rs1/pc and rs2/imm/4
  * Add
  * Sub
  * shift left (if shift, only operates on 5 LSB of immediate)
  * shift right logic
  * shift right arithmetic
  * and
  * or
  * xor
  * PC + 4
  * */

  /* Using a second ALU for branch evaluation. This always takes rs1,rs2 values as inputs
  * Only computes BEQ, BNE, BLT, BGE, BLTU, BGEU
  * BNE = !BNE
  * BLT = !BGE
  * BLTU = !BGEU //todo: Check if this is correct*/

  /* Tertiary ALU for branch target calculation. Either rs1 + imm (jalr) or pc+imm (jal, branch)*/

  //PRIMARY ALU
  val op1 = io.id.v1
  val op2 = Mux(io.id.ctrl.op2src, io.id.v2, io.id.imm)
  val aluOut = Wire(UInt(conf.XLEN.W))
  aluOut := op1 + op2
  switch(io.id.aluOp) {
    is(AluOp.ADD) { aluOut := op1 + op2 }
    is(AluOp.SUB) { aluOut := op1 - op2 }
    is(AluOp.SLL) { aluOut := op1 << op2(4,0) }
    is(AluOp.SLT) { aluOut := op1.asSInt < op2.asSInt}
    is(AluOp.SLTU) { aluOut := op1 < op2}
    is(AluOp.XOR) { aluOut := op1 ^ op2}
    is(AluOp.SRL) { aluOut := op1 >> op2(4,0)}
    is(AluOp.SRA) {aluOut := (op1.asSInt >> op2(4,0)).asUInt}
    is(AluOp.OR) { aluOut := op1 | op2 }
    is(AluOp.AND) { aluOut := op1 & op2 }
  }
  io.mem.res := aluOut
  io.mem.rd := io.id.rd
  io.mem.we := io.id.ctrl.we
}