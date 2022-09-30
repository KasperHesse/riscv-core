package core

import chisel3._
import chisel3.util.RegEnable

class Writeback(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryWritebackIO)
    val id = new WritebackDecodeIO
  })

  val mem = RegEnable(io.mem, true.B)
  val wdata = Mux(mem.memRead, io.mem.rdata, mem.res)
  io.id.we := mem.we
  io.id.wdata := wdata
  io.id.rd := mem.rd
}