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
  //Flag pulled high when PC value should be updated
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
  when(io.ctrl.loadPC && !io.mem.resp.ack) { //Set
    delayedLoadPC := true.B
    delayedNewPC := io.ctrl.newPC
  } .elsewhen(delayedLoadPC && io.mem.resp.ack) { //Reset
    delayedLoadPC := false.B
  }

  //HANDLE ACK WHILE STALLED
  //Storing the most recent instruction in case of stall
  val sampledInstr = Reg(UInt(conf.XLEN.W))
  val hasSampledInstr = RegInit(false.B)
  when(risingEdge(io.hzd.stall && io.mem.resp.ack) && !hasSampledInstr) { //Set
    hasSampledInstr := true.B
    sampledInstr := io.mem.resp.rdata
  } .elsewhen(hasSampledInstr && io.id.valid) { //Reset
    hasSampledInstr := false.B
  }

  //Next PC logic
  PCnext := MuxCase(PC + 4.U, Seq(
    (io.ctrl.loadPC && io.mem.resp.ack, io.ctrl.newPC),
    (delayedLoadPC && io.mem.resp.ack, delayedNewPC)
  ))

  //OUTPUT LOGIC
  //When acknowledged, issue a request for next instruction
  //When sampled instruction is stored while stalled, this also represents that we wish to pre-load next instruction
  val addr = Mux(io.mem.resp.ack || hasSampledInstr, PCnext, PC)
  io.id.instr := Mux(hasSampledInstr, sampledInstr, io.mem.resp.rdata)

  //updatePC has some logic in common with io.id.valid, but controls when to register new value of PC.
  //Sometimes, we wish to update value of PC without signalling valid (e.g. when flushing due to loadPC)
  updatePC := !(io.hzd.stall) && (io.mem.resp.ack || hasSampledInstr)
  io.id.valid := !(io.hzd.flush || io.hzd.stall || delayedLoadPC) && (io.mem.resp.ack || hasSampledInstr)

  //Does not follow addr exactly, since addr is allowed to increment if ack arrives while stalled.
  //In that case, PC value to ID stage should still be kept constant
  //Since IF->ID takes at least two clock cycles (one to req, one to ack), value of PC sent to ID stage is delayed by one CC.
  io.id.pc := RegNext(Mux(io.hzd.stall, PC, addr))
  io.mem.req.addr := addr
  io.mem.req.req := req

  //Fetch stage never writes to memory
  io.mem.req.wdata := 0.U
  io.mem.req.we := false.B
  io.mem.req.wmask := 0.U
}