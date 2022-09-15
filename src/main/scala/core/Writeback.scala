package core

import chisel3._

class Writeback(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryWritebackIO)
    val id = new WritebackDecodeIO
  })
  io.id.we := io.mem.we
  io.id.wdata := io.mem.res
  io.id.rd := io.mem.rd
}