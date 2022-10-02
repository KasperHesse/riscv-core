package core

import chisel3._

class Core(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val imem = new MemoryInterface
    val dmem = new MemoryInterface
    val dbg = if(!conf.debug) None else Some(new Bundle {
      val reg = Output(Vec(32, UInt(conf.XLEN.W)))
    })
  })

  val fetch = Module(new Fetch)
  val decode = Module(new Decode)
  val execute = Module(new Execute)
  val memory = Module(new Memory)
  val writeback = Module(new Writeback)

  fetch.io.id <> decode.io.fetch
  decode.io.ex <> execute.io.id
  execute.io.mem <> memory.io.ex
  memory.io.wb <> writeback.io.mem
  writeback.io.out <> decode.io.wb

  execute.io.memFwd := memory.io.fwd
  execute.io.wbFwd := writeback.io.out

  io.imem <> fetch.io.mem
  io.dmem <> memory.io.mem

  //CONTROL SIGNALS NOT YET CONNECTED
  fetch.io.ctrl.stall := false.B
  fetch.io.ctrl.flush := false.B
  fetch.io.ctrl.loadPC := execute.io.ctrl.fetch.loadPC
  fetch.io.ctrl.newPC := execute.io.ctrl.fetch.newPC

  if(conf.debug) {
    io.dbg.get.reg := decode.io.dbg.get.reg
  }
}
