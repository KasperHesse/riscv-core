package core
import chisel3._
import chisel3.util.switch
import peripherals.{HWBootloader, Keys, Leds, MemArbiter, MemBlock, MemMux, Uart, UartWrapper}

/**
 * A wrapper around the core, wrapping a UART interface and an interface to some switches and LEDs on an FPGA board
 * @param wrapConf
 * @param conf
 */
class CoreWrapper(wrapConf: Map[String,Int])(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val uart = new Bundle {
      val tx = Output(Bool())
      val rx = Input(Bool())
    }
    val keys = Input(UInt(wrapConf("numKeys").W))
    val leds = Output(UInt(wrapConf("numLeds").W))
  })

  //Modules
  val core = Module(new Core)
  val memblock = Module(new MemBlock(wrapConf("memSize")))
  val uart = Module(new UartWrapper(
    wrapConf("coreFreq"),
    wrapConf("uartBaud"),
    wrapConf("uartRxBufSize"),
    wrapConf("uartTxBufSize"))
  )
  val keys = Module(new Keys(wrapConf("numKeys")))
  val leds = Module(new Leds(wrapConf("numLeds")))
  val memArb = Module(new MemArbiter(4, Seq(0x00, wrapConf("uartBaseAddr"), wrapConf("keysBaseAddr"), wrapConf("ledsBaseAddr"))))
  val memMux = Module(new MemMux)
  //Bad implementation right now, but we do what works. Duplicate UART for bootloader and core
  val bootloader = Module(new HWBootloader(wrapConf("coreFreq"), wrapConf("uartBaud"), 2, 2))

  //When !go, hwbootloader should write directly into memblock. When go, memblock should be connected to memMux instead
  /* TODO Failing timing from writeback_rd_reg
      To ex-stage, through forwarding unit to value "v1", through ALU and ARITH logic, to waddr, to memblock

      Long net delays = we either need more pipelining or a way of reducing num. logic levels
   */
  when(!bootloader.io.go) {
    memblock.io <> bootloader.io.mem
    memMux.io.mem.resp.ack := false.B
    memMux.io.mem.resp.rdata := 0.U
  } .otherwise {
    memblock.io <> memMux.io.mem
    bootloader.io.mem.resp.ack := false.B
    bootloader.io.mem.resp.rdata := 0.U
  }

  memMux.io.imem <> core.io.imem

  memArb.io.master <> core.io.dmem
  memArb.io.periph(0) <> memMux.io.dmem
  memArb.io.periph(1) <> uart.io.mem
  memArb.io.periph(2) <> keys.io.mem
  memArb.io.periph(3) <> leds.io.mem

  //Connect to world
  uart.io.rxd := io.uart.rx
  bootloader.io.rxd := io.uart.rx
  io.uart.tx := uart.io.txd
  io.leds := leds.io.leds
  keys.io.keys := io.keys
}

case class WrapperConfig(
                        uartAddr: Int,
                        keysAddr: Int,
                        numKeys: Int,
                        ledsAddr: Int,
                        numLeds: Int
                        )