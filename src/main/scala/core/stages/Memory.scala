package core.stages

import chisel3._
import chisel3.util.{Fill, RegEnable, is, switch}
import core._
import core.modules.MemoryHazardIO

class Memory(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    /** Inputs from EX stage */
    val ex = Flipped(new ExecuteMemoryIO)
    /** Outputs to WB stage */
    val wb = new MemoryWritebackIO
    /** Inputs from memory module */
    val mem = Input(new MemoryResponse)
    /** Outputs to forwarding module */
    val fwd = Output(new ForwardingPort())
    /** Connections to hazard detection module */
    val hzd = new MemoryHazardIO
  })

  /** Pipeline register */
  val ex = RegEnable(io.ex, 0.U(io.ex.getWidth.W).asTypeOf(io.ex), !io.hzd.stall)

  //Sign-extend read-result, if available
  //Default to processing as LW
  val rdata = WireDefault(io.mem.rdata)
  val sext = (ex.ctrl.memOp === Funct3.LH.U) || (ex.ctrl.memOp === Funct3.LB.U)

  //Depending on memOp and res, we generated load dat
  switch(ex.ctrl.memOp) {
    is(Funct3.LH.U, Funct3.LHU.U) {
      val s = ex.res(1) //if ex.res(1) is set, access was to upper halfword
      val sign = Fill(conf.XLEN - 16, Mux(s, io.mem.rdata(31), io.mem.rdata(15)) & sext)
      val data = Mux(s, io.mem.rdata(31, 16), io.mem.rdata(15, 0))
      rdata := sign ## data
    }
    is(Funct3.LB.U, Funct3.LBU.U)  {
      val s = ex.res(1,0)
      //Sign-values could be generated by an AND-OR structure. For now however, using a when/elsewhen for easy ready
      when(s === 0.U) {
        rdata := Fill(conf.XLEN-8, io.mem.rdata(7) & sext) ## io.mem.rdata(7,0)
      } .elsewhen(s === 1.U) {
        rdata := Fill(conf.XLEN-8, io.mem.rdata(15) & sext) ## io.mem.rdata(15,8)
      } .elsewhen(s === 2.U) {
        rdata := Fill(conf.XLEN-8, io.mem.rdata(23) & sext) ## io.mem.rdata(23,16)
      } .otherwise {
        rdata := Fill(conf.XLEN-8, io.mem.rdata(31) & sext) ## io.mem.rdata(31,24)
      }
    }
  }

  //TODO Raise stall if ack is not signalled when ex.ctrl.memRead==1
  //Should also not accept next instruction while stalled

  //OUTPUTS
  //Outputs to WB stage
  io.wb.res := Mux(ex.ctrl.memRead, rdata, ex.res)
  io.wb.we := ex.ctrl.we
  io.wb.rd := ex.rd
  io.wb.valid := ex.valid && !io.hzd.stall

  //Forwarding outputs
  io.fwd.rd := ex.rd
  io.fwd.we := ex.ctrl.we
  io.fwd.wdata := ex.res

  //Control signals
  io.hzd.memRead := ex.ctrl.memRead
  io.hzd.memWrite := ex.ctrl.memWrite
  io.hzd.ack := io.mem.ack
}