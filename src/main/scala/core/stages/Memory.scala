package core.stages

import chisel3._
import chisel3.util.{Fill, RegEnable, is, switch}
import core._

class Memory(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    /** Inputs from EX stage */
    val ex = Flipped(new ExecuteMemoryIO)
    /** Outputs to WB stage */
    val wb = new MemoryWritebackIO
    /** Inputs from memory module */
    val mem = Input(new MemoryResponseInterface)
    /** Outputs to forwarding module */
    val fwd = Output(new ForwardingPort())

    val ctrl = Output(new Bundle {
      val missingAck = Bool()
    })
  })
  /*
  Memory stage.
  ex.res is the address to be accessed
  ex.v2 is the data to be written, if operation is a write
  LOAD operations set ctrl.memRead high
  STORE operations set ctrl.memWrite high
   */

  val ex = RegEnable(io.ex, true.B)

  //If a write or read was requested, we expect an ACK
  //Otherwise, we stall
  io.ctrl.missingAck := (ex.ctrl.memWrite || ex.ctrl.memRead) && !io.mem.ack

  //Sign-extend read-result, if available
  //Default to processing as LW
  val rdata = WireDefault(io.mem.rdata)
  switch(ex.ctrl.memOp) {
    is(Funct3.LH.U)  {rdata := Fill(conf.XLEN-16, io.mem.rdata(15)) ## io.mem.rdata(15,0)}
    is(Funct3.LB.U)  {rdata := Fill(conf.XLEN-8, io.mem.rdata(7)) ## io.mem.rdata(7,0)}
    is(Funct3.LHU.U) {rdata := 0.U((conf.XLEN-16).W) ## io.mem.rdata(15,0)}
    is(Funct3.LBU.U) {rdata := 0.U((conf.XLEN-8).W) ## io.mem.rdata(7,0)}
  }

  //Right now, just forward values
  io.wb.res := Mux(ex.ctrl.memRead, rdata, ex.res)
  io.wb.we := ex.ctrl.we
  io.wb.rd := ex.rd

  io.fwd.rd := ex.rd
  io.fwd.we := ex.ctrl.we
  io.fwd.wdata := ex.res
}
/*
two-stage processor
IF ID | EX MEM WB

When mem.ack, on the next clock cycle, data can be sampled in WB stage
Must add forwarding-logic in ID stage (always required)
 */