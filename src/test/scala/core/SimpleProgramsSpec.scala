package core

import chiseltest.{ChiselScalatestTester, WriteVcdAnnotation}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable.ListBuffer

class SimpleProgramsSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Simple programs"

  implicit val conf = defaultConf

  it should "compute the sum of 1..100" in {
    val asm =
      """
        |addi x1, x0, 1
        |add x2, x0, x0
        |addi x3, x0, 101
        |L1: add x2, x1, x2
        |addi x1, x1, 1
        |blt x1, x3, L1
        |addi x4, x4, 1
        |addi x5, x5, 1
        |addi x6, x6, 1
        |""".stripMargin


    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      //Each loop of add,addi,blt takes 5 clock cycles since branches are always mispredicted and pipeline must be flushed
      sh.setTimeout(600)
      sh.run()

      expectReg(dut, 1, 101)
      expectReg(dut, 2, 5050)
      expectReg(dut, 3, 101)
      expectReg(dut, 4, 1)
      expectReg(dut, 5, 1)
      expectReg(dut, 6, 1)
    }
  }

  it should "write Hello World to serial output" in {
    val asm =
      """
        |addi x1, x0, 72
        |addi x2, x0, 101
        |addi x3, x0, 108
        |addi x4, x0, 108
        |addi x5, x0, 111
        |addi x6, x0, 32
        |li x10, 0x10000
        |addi x7, x0, 87
        |addi x8, x0, 111
        |slli x8, x8, 8
        |or x7, x7, x8
        |add x8, x0, 114
        |slli x8, x8, 16
        |or x7, x7, x8
        |addi x8, x0, 108
        |slli x8, x8, 24
        |or x7, x7, x8
        |addi x8, x0, 100
        |addi x9, x0, 33
        |addi x11, x0, 10
        |sb x1, 0(x10)
        |sb x2, 0(x10)
        |sb x3, 0(x10)
        |sb x4, 0(x10)
        |sb x5, 0(x10)
        |sb x6, 0(x10)
        |sw x7, 0(x10)
        |sb x8, 0(x10)
        |sb x9, 0(x10)
        |sb x11, 0(x10)
        |""".stripMargin

    test(new Core) { dut =>
      val imem = MemAgent(dut.io.imem, Icache(dut.io.imem, dut.clock, assembleMap(asm)))
      val dmem = MemAgent(dut.io.dmem, Seq(new Dcache(dut.io.dmem, dut.clock, 0, 0xffff)()))
      dmem.register(new SoftwareSerialPort(dut.io.dmem, dut.clock, 0x10000, 0x10000))

      val sh = new SimulationHarness(dut, ListBuffer(imem, dmem))
      sh.run()
      //assert(uart.getBufString === "Hello World!\n")
    }
  }
}
