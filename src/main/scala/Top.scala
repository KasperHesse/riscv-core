import chisel3._
import core._
import peripherals._

import scala.collection.immutable.HashMap

object Top extends App {
  val wrapConf = scala.collection.immutable.HashMap(
    "numKeys" -> 12,
    "numLeds" -> 12,
    "coreFreq" -> 80_000_000,
    "uartBaud" -> 115200,
    "uartRxBufSize" -> 2,
    "uartTxBufSize" -> 2,
    "uartBaseAddr" -> 0x01,
    "ledsBaseAddr" -> 0x02,
    "keysBaseAddr" -> 0x03,
    "memSize" -> 2048
  )
  chisel3.emitVerilog(new CoreWrapper(wrapConf)(Config()))
}
