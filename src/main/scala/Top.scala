import chisel3._
import core._
import peripherals._

import scala.collection.immutable.HashMap

object Top extends App {
  val wrapConf = scala.collection.immutable.HashMap(
    "numKeys" -> 16,
    "numLeds" -> 16,
    "uartFreq" -> 100_000_000,
    "uartBaud" -> 9600,
    "uartRxBufSize" -> 2,
    "uartTxBufSize" -> 2,
    "uartBaseAddr" -> 0x01,
    "ledsBaseAddr" -> 0x02,
    "keysBaseAddr" -> 0x03,
    "memSize" -> 2048
  )
  chisel3.emitVerilog(new CoreWrapper(wrapConf)(Config()))
//  chisel3.emitVerilog(new HWBootloader(4, 2, 1, 1)(Config()))
}
