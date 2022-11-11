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

  /** Next value of PC*/
  val PCnext = Wire(UInt(conf.XLEN.W))
  /** Program counter */
  val PC = RegEnable(next=PCnext, init=mkPos(conf.pcReset).U(conf.XLEN.W), enable=io.mem.in.ack) //may also need !io.hzd.stall for multi-cycle stalls?
  /** Memory request flag */
  val req = WireDefault(true.B)

  //PC UPDATE LOGIC
  //when ack && load -> set to newPC
  //when ack && delayed -> set to delayedPC
  //otherwise -> pc + 4
  PCnext := Mux(io.ctrl.loadPC && io.mem.in.ack,
    io.ctrl.newPC,
    Mux(delayedLoadPC && io.mem.in.ack,
      delayedNewPC, PC + 4.U))

  //HANDLE LOAD PC WHILE WAITING ON ACK
  //If loadPC arrives while waiting on ack, these regs hold flag and new PC to update to
  val delayedLoadPC = RegInit(false.B)
  val delayedNewPC = RegInit(0.U(conf.XLEN.W))
  when(io.ctrl.loadPC && !io.mem.in.ack) { //Set
    delayedLoadPC := true.B
    delayedNewPC := io.ctrl.newPC
  } .elsewhen(delayedLoadPC && io.mem.in.ack) { //Reset
    delayedLoadPC := false.B
  }

  //OUTPUT LOGIC
  //Storing the most recently sampled instruction in case something goes wrong
  val sampledInstr = RegNext(io.mem.in.rdata)
  val addr = Mux(io.hzd.stall || !io.mem.in.ack, PC, PCnext)
  io.id.instr := Mux(io.hzd.flush || !io.mem.in.ack || delayedLoadPC, nop, io.mem.in.rdata)
  io.id.pc := addr
  io.mem.out.addr := addr
  io.mem.out.req := req //for now, always requesting new instructions

  //Fetch stage never writes to memory
  io.mem.out.wdata := 0.U
  io.mem.out.we := false.B
  io.mem.out.wmask := VecInit(0.U(conf.WMASKLEN.W).asBools)

}