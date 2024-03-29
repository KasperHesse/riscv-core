package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable.ListBuffer

class DmemSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Data memory interface"

  implicit val conf: Config = defaultConf

  it should "handle delayed memory responses" in {
    val asm =
      """|lui x1, 0x12345
         |addi x1, x1, 0x678
         |sw x1, 0(x0)
         |auipc x10, 5
         |auipc x11, 5
         |add x1, x1, x1
         |lw x2, 0(x0)
         |lh x3, 0(x0)
         |lb x4, 0(x0)
         |""".stripMargin

    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val imem = new MemAgent(dut.io.imem)
      imem.register(new Icache(dut.io.imem, dut.clock, 0, 0xffff)(assembleMap(asm, this.getTestName)))
      val dmem = new MemAgent(dut.io.dmem)
      dmem.register(new DcacheWithDelay(dut.io.dmem, dut.clock, 0, 0xffff, 2, 4)())
      val sh = new SimulationHarness(dut, ListBuffer(imem, dmem))
      sh.run()

      expectReg(dut, 2, 0x12345678)
      expectReg(dut, 3, 0x5678)
      expectReg(dut, 4, 0x78)
      expectReg(dut, 10, (5 << 12) + 12)
      expectReg(dut, 11, (5 << 12) + 16)
      expectReg(dut, 1, 0x12345678*2)
    }
  }
}
