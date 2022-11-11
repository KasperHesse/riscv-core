package core

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable.ListBuffer

class ImemSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  behavior of "Instruction memory interface"

  implicit val conf: Config = defaultConf

  it should "function when ack is delayed" in {
    val asm =
      """
        |addi x1, x0, 5
        |addi x2, x0, 10
        |add x3, x1, x2
        |or x4, x1, x3
        |""".stripMargin

    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val imem = new ImemDriverWithDelay(dut.io.imem, assembleMap(asm), 3)
      val sh = new SimulationHarness(dut, ListBuffer(imem))
      sh.run()

      expectReg(dut, 1, 5)
      expectReg(dut, 2, 10)
      expectReg(dut, 3, 15)
      expectReg(dut, 4, 15)
    }
  }

  it should "handle branches and jumps when ack is delayed" in {
    val asm =
      """
        |beq x0, x0, L1
        |li x1, 1
        |li x2, 2
        |li x3, 3
        |L1: li x4, 4
        |li x5, 5
        |li x6, 6
        |""".stripMargin

    test(new Core) {dut =>
      val imem = new ImemDriverWithDelay(dut.io.imem, assembleMap(asm), 5)
      val sh = new SimulationHarness(dut, ListBuffer(imem))
      sh.run()

      expectReg(dut, 1, 0)
      expectReg(dut, 2, 0)
      expectReg(dut, 3, 0)
      expectReg(dut, 4, 4)
      expectReg(dut, 5, 5)
      expectReg(dut, 6, 6)
    }
  }
}
