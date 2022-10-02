package core

import chisel3._
import chisel3.util._

class Execute(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val id = Flipped(new DecodeExecuteIO)
    val mem = new ExecuteMemoryIO
    val memFwd = Input(new ForwardingPort)
    val wbFwd = Input(new ForwardingPort)
    val ctrl = new Bundle {
      val fetch = new Bundle {
        val loadPC = Output(Bool())
        val newPC = Output(UInt(conf.XLEN.W))
      }
    }
  })

  val id = RegEnable(io.id, true.B)
  val fwd = Module(new ForwardingUnit)

  fwd.io.IDrs1 := id.rs1
  fwd.io.IDrs2 := id.rs2
  fwd.io.IDv1 := id.v1
  fwd.io.IDv2 := id.v2
  fwd.io.mem := io.memFwd
  fwd.io.wb := io.wbFwd

  val v1 = fwd.io.v1
  val v2 = fwd.io.v2

  //BRANCH EVALUATION LOGIC
  val eq = v1 === v2
  val lt = v1.asSInt < v2.asSInt
  val ltu = v1 < v2

  val beq = eq && (id.aluOp.asUInt(2,0) === 0.U(3.W))
  val bne = !eq && (id.aluOp.asUInt(2,0) === 1.U(3.W))
  val blt = lt && (id.aluOp.asUInt(2,0) === 4.U(3.W))
  val bge = !lt && (id.aluOp.asUInt(2,0) === 5.U(3.W))
  val bltu =ltu && (id.aluOp.asUInt(2,0) === 6.U(3.W))
  val bgeu = !ltu && (id.aluOp.asUInt(2,0) === 7.U(3.W))

  val loadPC = (id.ctrl.branch && (beq | bne | blt | bge | bltu | bgeu)) | id.ctrl.jump
  //We always set the LSB to 0 since JAL, branches already have 0's in the LSB and JALR requires a 0
  val newPC = Cat((Mux(id.pcNextSrc, v1, id.pc) + id.imm)(conf.XLEN-1,1), 0.U(1.W))


  //ALU FOR CALCULATING REGISTER RESULTS
  val aluOut = Wire(UInt(conf.XLEN.W))
  val op1 = v1
  val op2 = Mux(id.ctrl.op2src, v2, id.imm)
  val carry = id.aluOp === AluOp.SUB


  val arith = op1 + Mux(carry, (~op2).asUInt, op2) + carry

  //Shift logic
  val shifter = Module(new Shifter)
  shifter.io.in := op1
  shifter.io.shamt := op2(if(conf.XLEN == 32) 4 else 5, 0)
  shifter.io.mode := id.aluOp
  val shift = shifter.io.out

  //SLT, SLTU
  //if sign(a)=1 and sign(b)=0, a<b when signed, a>b when unsigned
  //if sign(a)=0 and sign(b)=1, a>b when signed, a<b when unsigned
  val sgn1 = op1(conf.XLEN-1)
  val sgn2 = op2(conf.XLEN-1)
  val comp = op1(conf.XLEN-2,0) < op2(conf.XLEN-2,0)
  val slt = Mux(sgn1 === sgn2, comp,  //MSBs are the same, perform unsigned comparison on remaining bits
    Mux(id.aluOp === AluOp.SLT, sgn1 & !sgn2, !sgn1 & sgn2)) //otherwise, use above logic

  //Bitwise logic expressions
  val logic = Mux(id.aluOp === AluOp.AND, op1 & op2, Mux(id.aluOp === AluOp.OR, op1 | op2, op1 ^ op2))

  aluOut := arith //Default clause
  switch(id.aluOp) {
    is(AluOp.SLL, AluOp.SRL, AluOp.SRA)  {aluOut := shift}
    is(AluOp.SLT, AluOp.SLTU)            {aluOut := slt}
    is(AluOp.AND, AluOp.OR, AluOp.XOR)  { aluOut := logic}
  }

  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
  io.mem.res := Mux(id.ctrl.jump, id.pc + 4.U(conf.XLEN.W), aluOut)
  io.mem.rd := id.rd
  io.mem.wdata := v2

  io.mem.ctrl.we := id.ctrl.we
  io.mem.ctrl.memOp := id.ctrl.memOp
  io.mem.ctrl.memWrite := id.ctrl.memWrite
  io.mem.ctrl.memRead := id.ctrl.memRead


  io.ctrl.fetch.loadPC := loadPC
  io.ctrl.fetch.newPC := newPC
}