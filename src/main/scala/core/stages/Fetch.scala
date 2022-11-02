package core.stages

import chisel3._
import chisel3.util._
import core._
import core.modules.FetchHazardIO
import utils._

class Fetch(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = new MemoryInterface
    val id = new FetchDecodeIO
    val ctrl = new Bundle {
      val loadPC = Input(Bool())
      val newPC = Input(UInt(conf.XLEN.W))
    }
    val hzd = new FetchHazardIO
  })
  //Shorthand for NOP instruction if flushed/stalled
  val nop = ItypeInstruction(imm=0, rs1=0, rd=0, funct3=Funct3.ADDI, op=Opcode.OP_IMM).asUInt
  //By default, we always go to next instruction. When mem is not ack,
  //or we get a trap, we send a NOP instead
  /*
  When an instruction is present and decode is ready: send it right now
  When an instruction is present and decode is not ready: sample it, but don't update regs
  When an instruction is not present and decode is ready: assert !valid
  When stall/trap is high, also don't launch instructions, but that's not yet
   */

  /*
  On boot: Initialize PC to (0-4), pcNext becomes 0
  Index into imem with pcNext
  On ack, update PC

  0: PC=-4, PCnext=0, req=1, ack=0
  1: PC=-4, PCnext=0, req=1, ack=1 //error, fetched same value twice in a row
  //imem MUST be async read for this to work efficiently?
  //Assuming async check, sync read: Should not buffer instruction coming in...
  0: PC=-4, PCnext=0, req=1, ack=1
  1: PC=0, PCnext=4, req=1, ack=1 | rdata = imem(0)
   */


  val pc = RegInit(mkPos(conf.pcReset-4L).U(conf.XLEN.W))

  val req = WireDefault(true.B)

  //TODO: What happens if newPC arrives but we can't update PC because we're waiting on an instruction
    //Solution: It should be buffered. Implementing that later
  val pcNext = Mux(io.ctrl.loadPC,
      io.ctrl.newPC,
      Mux(RegNext(io.hzd.stall), pc, pc + 4.U))
      //On cycle after stall, we keep pcNext as PC to ensure correct PC values always follow instruction to ID stage

  when(/*!io.hzd.stall && */io.mem.in.ack) { //TODO probably needed on multi-cycle stall
    pc := pcNext
  }

  //Storing the most recently sampled instruction in case something goes wrong
  val sampledInstr = RegEnable(next=io.mem.in.rdata, init=nop, enable=io.mem.in.ack)

  io.id.instr := Mux(io.hzd.flush, nop, Mux(io.mem.in.ack, io.mem.in.rdata, sampledInstr))
  io.id.pc := Mux(io.hzd.stall, pc, pcNext)
  io.mem.out.addr := Mux(io.hzd.stall, pc, pcNext)
  io.mem.out.req := req //for now, always requesting new instructions

  //Fetch stage never writes to memory
  io.mem.out.wdata := 0.U
  io.mem.out.we := false.B
  io.mem.out.wmask := VecInit(0.U(conf.WMASKLEN.W).asBools)

}