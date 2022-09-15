package core

import chisel3._
import chisel3.util._

class Fetch(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = new MemoryInterface
    val id = new FetchDecodeIO
    val ctrl = new FetchControlIO
  })


  val pc = RegInit(conf.pcReset.U(conf.MLEN.W))
  //pc can either be pc+4 or it can be the loaded value
  //When ack is true, we can update PC.
  val pcNext = Mux(io.ctrl.loadPC, io.ctrl.newPC, pc + 4.U)
  when(!io.ctrl.stall && io.mem.ack) {
    pc := pcNext
  } //otherwise, keep current value

  val nop = ItypeInstruction(imm=0, rs1=0, rd=0, funct3=Funct3.ADDI, op=Opcode.OP_IMM).asUInt //Shorthand for NOP instruction if flushed/stalled

  //Storing the most recently sampled instruction in case something goes wrong
  val sampledInstr = RegEnable(io.mem.data, nop, io.mem.ack)

  //when ack: Send that instruction. Otherwise, if flush, send a nop, otherwise send sampled instr
  io.id.instr := Mux(io.mem.ack, io.mem.data, Mux(io.ctrl.flush, nop, sampledInstr))
  io.id.pc := pc
  io.mem.addr := pc
  io.mem.req := true.B //for now, always requesting new instructions

}