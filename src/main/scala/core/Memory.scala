package core

import chisel3._

class Memory(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val ex = Flipped(new ExecuteMemoryIO)
    val wb = new MemoryWritebackIO
    val mem = new MemoryInterface
  })

  io.mem.req := false.B
  io.mem.addr := 0.U

  //Right now, just forward values
  io.wb.res := io.ex.res
  io.wb.we := io.ex.we
  io.wb.rd := io.ex.rd
}