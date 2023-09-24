package peripherals

import chisel3._
import core.{Config, MemoryInterface, MemoryRequest, MemoryResponse}

class Keys(numKeys: Int)(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val mem = Flipped(new MemoryInterface)
    val keys = Input(UInt(numKeys.W))
  })

  io.mem.resp.rdata := RegNext(RegNext(io.keys)) //Double-registering to synchronize. TODO, probably also needs debouncing
  io.mem.resp.ack := RegNext(io.mem.req.req)

}
