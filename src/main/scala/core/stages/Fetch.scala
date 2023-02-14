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

  val updatePC = Wire(Bool())
  /** Next value of PC*/
  val PCnext = Wire(UInt(conf.XLEN.W))
  /** Program counter */
  val PC = RegEnable(PCnext, mkPos(conf.pcReset).U(conf.XLEN.W), updatePC) //may also need !io.hzd.stall for multi-cycle stalls?
  /** Memory request signal */
  val req = WireDefault(true.B)

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

  //HANDLE ACK WHILE STALLED
  //Storing the most recently sampled instruction in case of stall
  val sampledInstr = Reg(UInt(conf.XLEN.W))
  val hasSampledInstr = RegInit(false.B)
  when(risingEdge(io.hzd.stall && io.mem.in.ack) && !hasSampledInstr) { //Set
    hasSampledInstr := true.B
    sampledInstr := io.mem.in.rdata
  } .elsewhen(hasSampledInstr && io.id.valid) { //Reset
    hasSampledInstr := false.B
  }

  //PC UPDATE LOGIC
  //when ack && load -> set to newPC
  //when ack && delayed -> set to delayedPC
  //on falling edge of hasSampledInstr (coming out of stall) -> keep PC constant
  //default -> pc + 4
  PCnext := MuxCase(PC + 4.U, Seq(
    (io.ctrl.loadPC && io.mem.in.ack, io.ctrl.newPC),
    (delayedLoadPC && io.mem.in.ack, delayedNewPC),
    (fallingEdge(hasSampledInstr), PC)
  ))

  //OUTPUT LOGIC
  val addr = Mux(io.mem.in.ack || hasSampledInstr, PCnext, PC)
  io.id.instr := Mux(hasSampledInstr, sampledInstr, io.mem.in.rdata)
  updatePC := !(io.hzd.stall) && (io.mem.in.ack || hasSampledInstr)
  io.id.valid := !(io.hzd.flush || io.hzd.stall || delayedLoadPC) && (io.mem.in.ack || hasSampledInstr)

  io.id.pc := Mux(io.hzd.stall || hasSampledInstr, PC, addr)
  io.mem.out.addr := addr
  io.mem.out.req := req

  //Fetch stage never writes to memory
  io.mem.out.wdata := 0.U
  io.mem.out.we := false.B
  io.mem.out.wmask := 0.U

}