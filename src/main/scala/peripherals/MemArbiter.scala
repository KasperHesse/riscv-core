package peripherals

import chisel3._
import core._

/**
 * The memory arbiter is used to arbitrate between the Core master and a number of 
 * peripherals connected to it. 
 * @param N The number of peripherals connected to the memory arbiter
 * @param baseAddresses The base addresses of the connected peripherals. Used to activate and route the data signals
 * @param conf
 */
class MemArbiter(N: Int, baseAddresses: Seq[Int])(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val master = Flipped(new MemoryInterface)
    val periph = Vec(N, new MemoryInterface)
  })
  
  //TODO: Check timing. Is it better to drive all output busses, or to 
  // mux between inputs and 0's
  // Remember to update tests if behavioru is changed
  /*
  Each peripheral should be driven when req is high and addressing is correct
  Each peripherals response should be driven when ack && RegNext(req) && RegNext(correct addr)
   */
  //Default response to master should have ack low and rdata DontCare
//  io.master.resp.ack := false.B
  io.master.resp.getElements.foreach(_ := 0.U)
  for((periph, baseAddr) <- io.periph zip baseAddresses) {
    //Drive from master to peripheral when correctly addressed
    val periphActivated = io.master.req.req && io.master.req.addr(31, 24) === baseAddr.U(8.W)
    when(periphActivated) {
      periph.req := io.master.req
    } .otherwise {
      periph.req.getElements.foreach(_ := 0.U)
//      periph.req.req := DontCare
//      periph.req.req := false.B
    }
    
    //Drive response from peripheral to master
    //TODO: Alternative implementation, perhaps with shorter path
    // Take each response channel, AND with RegNext(periphActivated) for that channel.
    // Drive response as OR of all AND'ed channels, mutex is ensured
    when(RegNext(periphActivated)) {
      io.master.resp := periph.resp
    }
  }
}
