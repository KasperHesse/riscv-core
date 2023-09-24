package peripherals

import chisel3._
import chisel3.util._
import core.{Config, MemoryInterface, MemoryRequest, MemoryResponse}

/**
 * A word-addressable, byte-writeable memory block, for use as internal memory when no off-chip memory is available
 * @param numWords The number of full words that the memory should contain
 * @param conf
 */
class MemBlock(numWords: Int)(implicit conf: Config) extends Module {
  val io = IO(Flipped(new MemoryInterface))
  require(isPow2(numWords), "number of words in memory must be a power of 2")

  val mem = SyncReadMem(numWords, Vec(conf.WMASKLEN, UInt(8.W)))

  //It's long, but it maps wdata from UInt to vec of bytes
  val wdata = VecInit(io.req.wdata.asBools.grouped(8).map(x => VecInit(x).asUInt).toSeq)

  val addr = io.req.addr(log2Ceil(numWords)-1+2, 2)
  when(io.req.req && io.req.we) {
    mem.write(addr, wdata, io.req.wmask.asBools)
  }
  io.resp.rdata := mem.read(addr).asUInt //todo check for correctness
  io.resp.ack := RegNext(io.req.req)
}
