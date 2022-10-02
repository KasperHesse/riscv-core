package core

import chisel3._
import chisel3.util.RegEnable

class Writeback(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryWritebackIO)
    val out = Output(new ForwardingPort())
  })

  val mem = RegEnable(io.mem, true.B)
  val wdata = Mux(mem.memRead, io.mem.rdata, mem.res)
  io.out.we := mem.we
  io.out.wdata := wdata
  io.out.rd := mem.rd
}