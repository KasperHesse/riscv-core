package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable

class ControlTransferSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "control transfer instructions"

  implicit val conf: Config = defaultConf

  it should "perform a JAL" in {
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
    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 2, 15)
      expectReg(dut, 1, 8)
      expectReg(dut, 3, 0)
      expectReg(dut, 4, 40)
    }
  }

  it should "perform a JALR" in {
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
    test(new Core()(defaultConf)) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 1, 12)
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 0)
      expectReg(dut, 4, 40)
    }
  }

  it should "perform a BEQ" in {
    val asm =
      """
        |addi x2, x0, 15
        |beq x2, x2, L1
        |addi x4, x0, 1
        |jal L2
        |L1: addi x3, x0, 2
        |L2: nop
        |""".stripMargin
    val instrs = mutable.ListBuffer.empty[Instruction]
    instrs += ItypeInstruction(15, 0, 2, Funct3.ADDI, Opcode.OP_IMM) //0
    instrs += BtypeInstruction(12, 2, 2, Funct3.BEQ, Opcode.BRANCH)  //4
    instrs += ItypeInstruction(1, 0, 4, Funct3.ADDI, Opcode.OP_IMM)  //8, should not be executed
    instrs += JtypeInstruction(8, 4, Opcode.JAL)                     //12, jump to 20
    instrs += ItypeInstruction(2, 0, 3, Funct3.ADDI, Opcode.OP_IMM)  //16

    test(new Core()(defaultConf)) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm))
      sh.run()
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 2)
      expectReg(dut, 4, 0)
    }
  }
  //TODO More randomized tests here
}
