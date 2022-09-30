package core

import chisel3._
import chisel3.util.RegEnable

class Memory(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val ex = Flipped(new ExecuteMemoryIO)
    val wb = new MemoryWritebackIO
    val mem = new MemoryInterface
  })
  /*
  Memory stage.
  ex.res is the address to be accessed
  ex.v2 is the data to be written, if operation is a write
  LOAD operations set ctrl.memRead high
  STORE operations set ctrl.memWrite high
   */

  val ex = RegEnable(io.ex, true.B)

  io.mem.req := false.B
  io.mem.addr := 0.U
  io.mem.wdata := ex.wdata
  io.mem.addr := ex.res
  io.mem.we := ex.ctrl.memWrite
  io.mem.req := ex.ctrl.memWrite || ex.ctrl.memRead

  //Right now, just forward values
  io.wb.res := ex.res
  io.wb.rdata := io.mem.rdata
  io.wb.we := ex.ctrl.we
  io.wb.rd := ex.rd
  io.wb.memRead := ex.ctrl.memRead
}
/*
two-stage processor
IF ID | EX MEM WB

When mem.ack, on the next clock cycle, data can be sampled in WB stage
Must add forwarding-logic in ID stage (always required)
 */