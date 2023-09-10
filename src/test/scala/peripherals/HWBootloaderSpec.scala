package peripherals

import chisel3._
import scala.collection.mutable
import chisel3.util.OHToUInt
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class HWBootloaderSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  behavior of "Hardware bootloader"

  implicit val conf: Config = Config()

  /*
  3 behaviours:
  - first iteration, numBytes < 255, should go straight to GO
  - first iteration, numBytes == 255, should wait for more
  - n'th iteration, numBytes == 255. n+1'th iteration, numBytes < 255, should GO
   */

  /**
   * Write a value onto the UART receiver
   * @param freq Uart core frequency
   * @param baud Uart baud rate
   * @param value Value to write out. Must in range 0x00 - 0xff
   * @param rxd Uart rxd pin
   * @param clock DUT clock signal
   */
  def writeToUart(dut: HWBootloader, value: Int): Unit = {
    //All writes to UART are double-registered. Should wait +2 cc afterwards before we can expect output
    val cyclesPerBaud = dut.frequency/dut.baudRate
    val txData = 0x600 | ((value & 0xFF) << 1) | 0
    for(i <- 0 until 11) {
      val tx = (txData >> i) & 1
      dut.io.rxd.poke(tx.B)
      dut.clock.step(cyclesPerBaud)
    }
    dut.io.rxd.poke(true.B)
  }

  /**
   * Read a value coming off the memory bus, storing it into the passed map
   * @param mem Mutable map to store all received bytes and their address
   * @param dut The DUT
   * @param go Array holding received go-signal, to indicate when testing is finished
   */
  def readFromMem(mem: mutable.Map[Int, Byte], dut: HWBootloader, go: Array[Boolean]): Unit = {
    while(!go(0)) {
      while(!dut.io.memOut.req.peekBoolean() && !go(0)) {
        dut.clock.step()
      }
      if (go(0)) {
        return
      }
      val offset = dut.io.memOut.wmask.peekInt().toInt match {
        case 1 => 0
        case 2 => 1
        case 4 => 2
        case 8 => 3
        case _ => fail(s"offset was not decodeable from one-hot, was ${dut.io.memOut.wmask.peek()}")
      }
      val addr = (dut.io.memOut.addr.peekInt() + offset).toInt
      mem.update(addr, dut.io.memOut.wdata.peekInt().toByte)
      dut.clock.step()
      timescope {
        dut.io.memIn.ack.poke(true.B)
        dut.clock.step()
      }
    }
  }

  /**
   * Do the HW Bootloader test. Transmits a number of bytes onto the UART receiver, then samples these
   * off the memory output channel
   * @param dut The DUT
   * @param bytes The bytes to write onto the UART receiver
   * @return
   */
  def doHWBootloader(dut: HWBootloader, bytes: Seq[Byte]): mutable.Map[Int, Byte] = {
    val go = Array(x = false) //Using array for go to pass into readFromMem
    val mem = scala.collection.mutable.Map[Int, Byte]()
    fork {
      for(bp <- bytes.grouped(255)) {
        //Number of bytes coming
        writeToUart(dut, bp.length)
        //Following bytes
        bp.foreach(b => writeToUart(dut, b))
      }
      //If exactly a multiple of 255, should send 0 afterwards to move into GO state
      if (bytes.length % 255 == 0) {
        writeToUart(dut, 0)
      }
      while(!go(0)) {
        dut.clock.step()
      }
    } .fork {
      readFromMem(mem, dut, go)
    } .fork {
      while(!dut.io.go.peekBoolean()) {
        dut.clock.step()
      }
      go(0) = true
    } .joinAndStep(dut.clock)
    mem
  }

  it should "accept 2 bytes and then activate GO" in {
    test(new HWBootloader(2, 1, 1, 1)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val mem = doHWBootloader(dut, Seq(0xab, 0x3f).map(_.toByte))
      assert(mem(0) == 0xab.toByte)
      assert(mem(1) == 0x3f.toByte)
    }
  }

  it should "accept more than 255 bytes and then activate GO" in {
    test(new HWBootloader(2, 1, 1, 1)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.clock.setTimeout(5000)
      val n = scala.util.Random.nextInt(253) + 256
      val wdata = scala.util.Random.nextBytes(n).toSeq
      val mem = doHWBootloader(dut, wdata)
      for ((v,i) <- wdata.zipWithIndex) {
        assert(mem(i) == v, f"Mem[$i] was not $v as expected, was ${mem(i)}")
      }
    }
  }

  it should "accept more than 2 full packets and then activate GO" in {
    test(new HWBootloader(2, 1, 1, 1)) { dut =>
      dut.clock.setTimeout(5000)
      val n = scala.util.Random.nextInt(70) + 530
      val wdata = scala.util.Random.nextBytes(n).toSeq
      val mem = doHWBootloader(dut, wdata)
      for ((v,i) <- wdata.zipWithIndex) {
        assert(mem(i) == v, f"Mem[$i] was not $v as expected, was ${mem(i)}")
      }
    }
  }

  it should "accept exactly 255 bytes and then activate GO" in {
    test(new HWBootloader(2, 1, 1, 1)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.clock.setTimeout(5000)
      val wdata = scala.util.Random.nextBytes(255).toSeq
      val mem = doHWBootloader(dut, wdata)
      for ((v,i) <- wdata.zipWithIndex) {
        assert(mem(i) == v, f"Mem[$i] was not $v as expected, was ${mem(i)}")
      }
    }
  }
}
