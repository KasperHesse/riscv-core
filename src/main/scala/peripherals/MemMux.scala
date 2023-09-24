package peripherals

import chisel3._
import chisel3.util._
import core.{Config, MemoryInterface}

/**
 * The memory mux is used to select whether the Imem or Dmem should access memory.
 * Dmem is always prioritized, as it is assumed that it is less active than Imem
 * @param conf
 */
class MemMux(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new MemoryInterface)
    val dmem = Flipped(new MemoryInterface)
    val mem = new MemoryInterface
  })
  val sDMEM :: sIMEM :: Nil = Enum(2)
  val state = RegInit(sDMEM)

  //Default drives
  Seq(io.dmem, io.imem).foreach {mi =>
    mi.resp.rdata := DontCare
    mi.resp.ack := false.B
  }
  io.mem.req := DontCare
  io.mem.req.req := false.B

  /*
  When neither is active => give Imem priority for faster instruction fetch
  If dmem is activated, give it priority to avoid pipeline stall as Imem always fetches
 */
  switch(state) {
    is(sDMEM) {
      io.mem.req := io.dmem.req
      io.dmem.resp := io.mem.resp
      when(io.imem.req.req && !io.dmem.req.req) {
        io.mem.req := io.imem.req
        state := sIMEM
      }
    }
    is(sIMEM) {
      io.mem.req := io.imem.req
      io.imem.resp := io.mem.resp
      when(io.dmem.req.req && (!io.imem.req.req || io.imem.resp.ack)) {
        io.mem.req := io.dmem.req
        state := sDMEM
      }
    }
  }
}
