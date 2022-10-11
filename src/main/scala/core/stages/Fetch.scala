package core.stages

import chisel3._
import chisel3.util._
import core._

class Fetch(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = new MemoryInterface
    val id = new FetchDecodeIO
    val ctrl = new FetchControlIO
  })
  //By default, we always go to next instruction. When mem is not ack,
  //or we get a trap, we send a NOP instead
  /*
  When an instruction is present and decode is ready: send it right now
  When an instruction is present and decode is not ready: sample it, but don't update regs
  When an instruction is not present and decode is ready: assert !valid
  When stall/trap is high, also don't launch instructions, but that's not yet
   */


  val pc = RegInit(conf.pcReset.U(conf.XLEN.W))

  val req = WireDefault(true.B)

  //TODO: What happens if newPC arrives but we can't update PC because we're waiting on an instruction
    //Solution: It should be buffered. Implementing that later
  val pcNext = Mux(io.ctrl.loadPC, io.ctrl.newPC, pc + 4.U)

    when(!io.ctrl.stall && io.mem.in.ack) {
    pc := pcNext
  } //otherwise, keep current value of PC

  val nop = ItypeInstruction(imm=0, rs1=0, rd=0, funct3=Funct3.ADDI, op=Opcode.OP_IMM).asUInt //Shorthand for NOP instruction if flushed/stalled

  //Storing the most recently sampled instruction in case something goes wrong
  val sampledInstr = RegEnable(io.mem.in.rdata, nop, io.mem.in.ack)

  //when ack: Send that instruction. Otherwise, if flush, send a nop, otherwise send sampled instr
  //TODO: Should drive addr with nextPC instead of PC
  io.id.instr := Mux(io.mem.in.ack, io.mem.in.rdata, Mux(io.ctrl.flush, nop, sampledInstr))
  io.id.pc := pc
  io.mem.out.addr := pc
  io.mem.out.req := req //for now, always requesting new instructions

  //Fetch stage never writes to memory
  io.mem.out.wdata := 0.U
  io.mem.out.we := false.B
  io.mem.out.wmask := VecInit(0.U(conf.WMASKLEN.W).asBools)

}