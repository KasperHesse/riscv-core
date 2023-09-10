package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable

class ControlTransferSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "control transfer instructions"

  implicit val conf: Config = defaultConf

  it should "perform a JAL forward" in {
    val asm =
      """
        |addi x2, x0, 15
        |jal L1
        |addi x3, x0, 30
        |nop
        |nop
        |beq x0, x0, L2
        |L1: addi x4, x0, 40
        |L2: nop
        |""".stripMargin
    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 2, 15)
      expectReg(dut, 1, 8)
      expectReg(dut, 3, 0)
      expectReg(dut, 4, 40)
    }
  }

  it should "perform a JAL backward" in {
    val asm =
      """
        |beq x0, x0, L1
        |addi x10, x0, 10
        |L2: addi x2, x0, 2
        |addi x3, x0, 3
        |and x4, x2, x3
        |beq x0, x0, L3
        |L1: jal L2
        |L3: addi x5, x0 ,5
        |""".stripMargin

    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 1, 28)
      expectReg(dut, 2, 2)
      expectReg(dut, 3, 3)
      expectReg(dut, 4, 3 & 2)
      expectReg(dut, 5, 5)
      expectReg(dut, 10, 0)
    }
  }

  it should "perform a JALR forward" in {
    //Addi x1, 29 followed by JALR tests that the LSB of new PC is reset
    //29=11101 -> 28=11100
    val asm =
      """
        |addi x2, x0, 15
        |addi x1, x0, 29
        |jalr 0(x1)
        |addi x3, x0, 30
        |nop
        |nop
        |beq x0, x0, L2
        |addi x4, x0, 40
        |L2: nop
        |""".stripMargin
    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 1, 12)
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 0)
      expectReg(dut, 4, 40)
    }
  }

  it should "perform a JALR backward with immediate" in {
    val asm =
      """
        |addi x6, x0, 28
        |beq x0, x0, L1
        |addi x10, x0, 10
        |addi x2, x0, 2
        |addi x3, x0, 3
        |and x4, x2, x3
        |beq x0, x0, L3
        |L1: jalr -16(x6)
        |L3: addi x5, x0 ,5
        |""".stripMargin
    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 1, 32)
      expectReg(dut, 2, 2)
      expectReg(dut, 3, 3)
      expectReg(dut, 4, 3 & 2)
      expectReg(dut, 5, 5)
      expectReg(dut, 6, 28)
      expectReg(dut, 10, 0)
    }
  }

  it should "take a BEQ" in {
    val asm =
      """
        |addi x2, x0, 15
        |beq x2, x2, L1
        |addi x4, x0, 1
        |jal L2
        |L1: addi x3, x0, 2
        |addi x5, x0, 5
        |addi x6, x0, 6
        |L2: addi x7, x0, 7
        |addi x8, x0, 8
        |""".stripMargin

    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 2)
      expectReg(dut, 4, 0)
      expectReg(dut, 5, 5)
      expectReg(dut, 6, 6)
      expectReg(dut, 7, 7)
      expectReg(dut, 8, 8)
    }
  }

  it should "not take a BEQ" in {
    val asm =
      """
        |addi x2, x0, 15
        |beq x2, x0, L1
        |addi x3, x0, 30
        |beq x0, x0, L2
        |L1: addi x4, x0, 40
        |L2: nop
        |""".stripMargin

    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 30)
      expectReg(dut, 4, 0)
    }
  }

  it should "perform a BNE" in {
    var r = scala.util.Random.nextInt(math.pow(2,12).toInt)-2048
    if (r == 0) {
      r = 1
    }
    val asm =
      s"""
        |li x2 $r
        |bne x2, x0, L1""".stripMargin
  }
  //TODO: More randomized tests
}
