package core.stages

import chisel3._
import chisel3.util.{MuxCase, RegEnable}
import core.{Config, ForwardingPort, WritebackInputs}

class Writeback(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val mem = Input(new WritebackInputs)
    val csr = Input(new WritebackInputs)
    val out = Output(new ForwardingPort())
    val instret = Output(Bool())
  })

  val mem = RegEnable(io.mem, 0.U(io.mem.getWidth.W).asTypeOf(io.mem), true.B)
  val csr = RegEnable(io.csr, 0.U(io.csr.getWidth.W).asTypeOf(io.csr), true.B)

  //Defaults: connect memory subsystem
  io.out.we := mem.valid && mem.we
  io.out.wdata := mem.res
  io.out.rd := mem.rd

  //Change output selection based on active input
  when(mem.valid) {
    io.out.we := mem.we
    io.out.wdata := mem.res
    io.out.rd := mem.rd
  } .elsewhen(csr.valid) {
    io.out.we := csr.we
    io.out.wdata := csr.res
    io.out.rd := csr.rd
  }

  io.instret := mem.valid | csr.valid
}
