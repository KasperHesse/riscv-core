package core.stages

import chisel3._
import chisel3.util.RegEnable
import core.{Config, ForwardingPort, MemoryWritebackIO}

class Writeback(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryWritebackIO)
    val out = Output(new ForwardingPort())
  })

  val mem = RegEnable(io.mem, 0.U(io.mem.getWidth.W).asTypeOf(io.mem), true.B)
  io.out.we := mem.we
  io.out.wdata := mem.res
  io.out.rd := mem.rd
}