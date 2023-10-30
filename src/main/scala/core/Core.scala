package core

import chisel3._
import core.csr.CSR
import core.modules.{ForwardingUnit, HazardDetection}
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

  val csr = Module(new CSR)
  val hazard = Module(new HazardDetection)

  //PIPELINE CONNECTIONS
  fetch.io.id <> decode.io.fetch
  decode.io.ex <> execute.io.id
  execute.io.memstage <> memory.io.ex
  csr.io.in <> decode.io.csr
  csr.io.fwd <> writeback.io.out

  writeback.io.mem <> memory.io.wb
  writeback.io.csr <> csr.io.out
  writeback.io.out <> decode.io.wb

  //FORWARDING CONNECTIONS
  execute.io.memFwd := memory.io.fwd
  execute.io.wbFwd := writeback.io.out

  //MEMORY CONNECTIONS
  io.imem <> fetch.io.mem
  io.dmem.req <> execute.io.mem
  io.dmem.resp <> memory.io.mem

  //CONTROL SIGNALS
  fetch.io.ctrl.loadPC := execute.io.fetch.loadPC
  fetch.io.ctrl.newPC := execute.io.fetch.newPC

  //HAZARD MODULE CONNECTIONS
  hazard.io.IF <> fetch.io.hzd
  hazard.io.ID <> decode.io.hzd
  hazard.io.EX <> execute.io.hzd
  hazard.io.MEM <> memory.io.hzd
  hazard.io.CSR <> csr.io.hzd

  //CSR TRIGGERS
  csr.io.triggers.instret := writeback.io.instret
  csr.io.triggers.NX := false.B
  csr.io.triggers.UF := false.B
  csr.io.triggers.OF := false.B
  csr.io.triggers.NV := false.B
  csr.io.triggers.DVZ := false.B


  if(conf.debug) {
    io.dbg.get.reg := decode.io.dbg.get.reg
  }
}
