package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class MemorySpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory instructions"

  it should "perform a SW/LW" in {
    val asm =
      """
        |li x1, 4
        |sw x1, 20(x0)
        |lw x2, 20(x0)
        |""".stripMargin
    val instrs = assemble(asm).zipWithIndex.map{case (instr, i) => (i*4,instr)}.toMap
    test(new Core()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val imem = new ImemDriver(dut.io.imem, instrs)
      val dmem = new DmemDriver(dut.io.dmem, None)
      val sh = new SimulationHarness(dut, Seq(imem, dmem))
      sh.run

      expectReg(dut, 1, 4)
      expectReg(dut, 2, 4)
      assert(dmem.getData(20) == 4)
    }
  }

  it should "perform a SH/LH" in {
    val asm = """
      |li x1, 25
      |sh x1, 20(x0)
      |lh x2, 20(x0)
      |""".stripMargin
    val instrs = assemble(asm)
    val insn = instrs.zipWithIndex.map{case (instr,i) => (i*4, instr)}.toMap
    test(new Core()(defaultConf)) { dut =>
      val imem = new ImemDriver(dut.io.imem, insn)
      val dmem = new DmemDriver(dut.io.dmem, None)
      val sh = new SimulationHarness(dut, Seq(imem, dmem))
      sh.run

      expectReg(dut, 1, 25)
      expectReg(dut, 2, 25)
      assert(dmem.getData(20) == 25)
    }
  }

  it should "perform a SB/LB" in {
    val asm = """
                |li x1, 25
                |sb x1, 20(x0)
                |lb x2, 20(x0)
                |""".stripMargin
    val instrs = assemble(asm)
    val insn = instrs.zipWithIndex.map{case (instr,i) => (i*4, instr)}.toMap
    test(new Core()(defaultConf)) { dut =>
      val imem = new ImemDriver(dut.io.imem, insn)
      val dmem = new DmemDriver(dut.io.dmem, None)
      val sh = new SimulationHarness(dut, Seq(imem, dmem))
      sh.run

      expectReg(dut, 1, 25)
      expectReg(dut, 2, 25)
      assert(dmem.getData(20) == 25)
    }
  }

  it should "sign-extend data retrieved with LB" in {
    val asm =
      """
        | li x1, 300
        | li x2, 428
        | sw x1, 0(x0)
        | sw x2, 4(x0)
        | lb x3, 0(x0)
        | lb x4, 4(x0)
        |""".stripMargin
    val instrs = assemble(asm).zipWithIndex.map{case (instr,i) => (i*4, instr)}.toMap
    test(new Core()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val imem = new ImemDriver(dut.io.imem, instrs)
      val dmem = new DmemDriver(dut.io.dmem, None)
      val sh = new SimulationHarness(dut, Seq(imem, dmem))
      sh.run

      expectReg(dut, 1, 300)
      expectReg(dut, 2, 428)
      expectReg(dut, 3, 44)
      expectReg(dut, 4, -84)
    }
  }

  it should "not sign-extend data retrieved with LBU" in {
    val asm =
      """
        | li x1, 0x7f
        | li x2, 0x80
        | sw x1, 0(x0)
        | sw x2, 4(x0)
        | lbu x3, 0(x0)
        | lbu x4, 4(x0)
        | """.stripMargin
    val instrs = assembleMap(asm)
    test(new Core()(defaultConf)) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run

      expectReg(dut, 1, 0x7f)
      expectReg(dut, 2, 0x80)
      expectReg(dut, 3, 0x7f)
      expectReg(dut, 4, 0x80)
    }
  }

  it should "sign-extend data retrieved with LH" in {
    val asm =
      """
        | li x1, 0x7fff
        | li x2, 0x8000
        | sw x1, 0(x0)
        | sw x2, 4(x0)
        | lh x3, 0(x0)
        | lh x4, 4(x0)
        | """.stripMargin
    val instrs = assembleMap(asm)
    test(new Core()(defaultConf)) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run

      expectReg(dut, 1, 0x7fff)
      expectReg(dut, 2, 0x8000)
      expectReg(dut, 3, 0x7fff)
      expectReg(dut, 4, 0xffff8000)
    }
  }

  it should "not sign-extend data retrieved with LHU" in {
    val asm =
      """
        | li x1, 0x7fff
        | li x2, 0x8000
        | sw x1, 0(x0)
        | sw x2, 4(x0)
        | lhu x3, 0(x0)
        | lhu x4, 4(x0)
        | """.stripMargin
    val instrs = assembleMap(asm)
    test(new Core()(defaultConf)) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run

      expectReg(dut, 1, 0x7fff)
      expectReg(dut, 2, 0x8000)
      expectReg(dut, 3, 0x7fff)
      expectReg(dut, 4, 0x8000)
    }
  }
}
