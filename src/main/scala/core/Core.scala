package core

import chisel3._
import core.modules.HazardDetection
import core.stages.{Decode, Execute, Fetch, Memory, Writeback}

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
  val hazard = Module(new HazardDetection)

  //Pipeline connections
  fetch.io.id <> decode.io.fetch
  decode.io.ex <> execute.io.id
  execute.io.memstage <> memory.io.ex
  memory.io.wb <> writeback.io.mem
  writeback.io.out <> decode.io.wb

  //Forwarding connections
  execute.io.memFwd := memory.io.fwd
  execute.io.wbFwd := writeback.io.out

  //Memory connections
  io.imem <> fetch.io.mem
  io.dmem.out <> execute.io.mem
  io.dmem.in <> memory.io.mem

  //Control signals and hazard detection
  fetch.io.ctrl.loadPC := execute.io.fetch.loadPC
  fetch.io.ctrl.newPC := execute.io.fetch.newPC

  fetch.io.hzd <> hazard.io.IF
  decode.io.hzd <> hazard.io.ID
  execute.io.hzd <> hazard.io.EX
  memory.io.hzd <> hazard.io.MEM


  if(conf.debug) {
    io.dbg.get.reg := decode.io.dbg.get.reg
  }
}
