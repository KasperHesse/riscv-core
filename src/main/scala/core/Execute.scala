package core

import chisel3._
import chisel3.util._

class Execute(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val id = Flipped(new DecodeExecuteIO)
    val mem = new ExecuteMemoryIO
    val ctrl = new Bundle {
      val fetch = new Bundle {
        val loadPC = Output(Bool())
        val newPC = Output(UInt(conf.XLEN.W))
      }
    }
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

  //BRANCH EVALUATION LOGIC
  val eq = io.id.v1 === io.id.v2
  val lt = io.id.v1.asSInt < io.id.v2.asSInt
  val ltu = io.id.v1 < io.id.v2

  val beq = eq && (io.id.aluOp.asUInt(2,0) === 0.U(3.W))
  val bne = !eq && (io.id.aluOp.asUInt(2,0) === 1.U(3.W))
  val blt = lt && (io.id.aluOp.asUInt(2,0) === 4.U(3.W))
  val bge = !lt && (io.id.aluOp.asUInt(2,0) === 5.U(3.W))
  val bltu =ltu && (io.id.aluOp.asUInt(2,0) === 6.U(3.W))
  val bgeu = !ltu && (io.id.aluOp.asUInt(2,0) === 7.U(3.W))

  val loadPC = (io.id.ctrl.branch && (beq | bne | blt | bge | bltu | bgeu)) | io.id.ctrl.jump
  //We always set the LSB to 0 since JAL, branches already have 0's in the LSB and JALR requires a 0
  val newPC = Cat((Mux(io.id.pcNextSrc, io.id.v1, io.id.pc) + io.id.imm)(conf.XLEN-1,1), 0.U(1.W))


  //ALU FOR CALCULATING REGISTER RESULTS
  val aluOut = Wire(UInt(conf.XLEN.W))
  val op1 = io.id.v1
  val op2 = Mux(io.id.ctrl.op2src, io.id.v2, io.id.imm)
  val carry = io.id.aluOp === AluOp.SUB


  val arith = op1 + Mux(carry, (~op2).asUInt, op2) + carry

  //Shift logic
  val shifter = Module(new Shifter)
  shifter.io.in := op1
  shifter.io.shamt := op2(if(conf.XLEN == 32) 4 else 5, 0)
  shifter.io.mode := io.id.aluOp
  val shift = shifter.io.out

  //SLT, SLTU
  //if sign(a)=1 and sign(b)=0, a<b when signed, a>b when unsigned
  //if sign(a)=0 and sign(b)=1, a>b when signed, a<b when unsigned
  val sgn1 = op1(conf.XLEN-1)
  val sgn2 = op2(conf.XLEN-1)
  val comp = op1(conf.XLEN-2,0) < op2(conf.XLEN-2,0)
  val slt = Mux(sgn1 === sgn2, comp,  //MSBs are the same, perform unsigned comparison on remaining bits
    Mux(io.id.aluOp === AluOp.SLT, sgn1 & !sgn2, !sgn1 & sgn2)) //otherwise, use above logic

  //Bitwise logic expressions
  val logic = Mux(io.id.aluOp === AluOp.AND, op1 & op2, Mux(io.id.aluOp === AluOp.OR, op1 | op2, op1 ^ op2))

  aluOut := arith //Default clause
  switch(io.id.aluOp) {
    is(AluOp.SLL, AluOp.SRL, AluOp.SRA)  {aluOut := shift}
    is(AluOp.SLT, AluOp.SLTU)            {aluOut := slt}
    is(AluOp.AND, AluOp.OR, AluOp.XOR)  { aluOut := logic}
  }

  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
  io.mem.res := Mux(io.id.ctrl.jump, io.id.pc + 4.U(conf.XLEN.W), aluOut)
  io.mem.rd := io.id.rd
  io.mem.we := io.id.ctrl.we

  io.ctrl.fetch.loadPC := loadPC
  io.ctrl.fetch.newPC := newPC
}