package peripherals

import chisel3._
import core.{Config, MemoryInterface, MemoryRequest, MemoryResponse}

class Leds(numLeds: Int)(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryInterface)
    val leds = Output(UInt(numLeds.W))
  })
  require(numLeds > 0 && numLeds <= conf.XLEN, s"Must numLeds>0 but <=${conf.XLEN}, was $numLeds")
  //Each bit set high in the input is used to set an LED
  val leds = RegInit(0.U(numLeds.W))
  val rdData = RegInit(0.U(conf.XLEN.W))
  val ack = RegNext(io.mem.req.req)


  //Handle reads
  when(io.mem.req.req && !io.mem.req.we) {
    rdData := leds
  }
  when(io.mem.req.req && io.mem.req.we) {
    leds := io.mem.req.wdata(numLeds-1, 0)
  }

  //Handle I/O
  io.mem.resp.ack := ack
  io.mem.resp.rdata := rdData
  io.leds := leds
}
