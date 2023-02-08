package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class MemorySpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory instructions"
  
  implicit val conf: Config = defaultConf

  it should "perform a SW/LW" in {
    val asm =
      """
        |li x1, 4
        |sw x1, 20(x0)
        |lw x2, 20(x0)
        |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core()).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 4)
      expectReg(dut, 2, 4)
    }
  }

  it should "perform a SH/LH" in {
    val asm = """
      |li x1, 25
      |sh x1, 20(x0)
      |lh x2, 20(x0)
      |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core()) { dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 25)
      expectReg(dut, 2, 25)
    }
  }

  it should "perform a SB/LB" in {
    val asm = """
      |li x1, 25
      |sb x1, 20(x0)
      |lb x2, 20(x0)
      |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core()) { dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 25)
      expectReg(dut, 2, 25)
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
    val instrs = assembleMap(asm)
    test(new Core()) { dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

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
    test(new Core()) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

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
    test(new Core()) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

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
    test(new Core()) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 0x7fff)
      expectReg(dut, 2, 0x8000)
      expectReg(dut, 3, 0x7fff)
      expectReg(dut, 4, 0x8000)
    }
  }

  it should "perform LB and LBU from any offset" in {
    val asm =
      """
        |li x1, 0xabcd9876
        |sw x1, 0(x0)
        |lb x2, 0(x0)
        |lb x3, 1(x0)
        |lb x4, 2(x0)
        |lb x5, 3(x0)
        |lbu x6, 0(x0)
        |lbu x7, 1(x0)
        |lbu x8, 2(x0)
        |lbu x9, 3(x0)
        |""".stripMargin
    val instr = assembleMap(asm)
    test(new Core()) {dut =>
      val sh = SimulationHarness(dut, instr)
      sh.run()

      expectReg(dut, 1, 0xabcd9876L)
      expectReg(dut, 2, 0x76)
      expectReg(dut, 3, 0xffffff98)
      expectReg(dut, 4, 0xffffffcd)
      expectReg(dut, 5, 0xffffffab)
      expectReg(dut, 6, 0x76)
      expectReg(dut, 7, 0x98)
      expectReg(dut, 8, 0xcd)
      expectReg(dut, 9, 0xab)
    }
  }

  it should "perform SB to any offset" in {
    val asm =
      """
        |li x1, 0x12345678
        |li x2, 0xff
        |li x3, 0xee
        |li x4, 0xdd
        |li x5, 0xcc
        |sw x1, 0(x0)
        |lw x10, 0(x0)
        |sb x2, 0(x0)
        |sb x3, 1(x0)
        |sb x4, 2(x0)
        |sb x5, 3(x0)
        |lw x6, 0(x0)
        |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core()) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 0x12345678L)
      expectReg(dut, 2, 0xff)
      expectReg(dut, 3, 0xee)
      expectReg(dut, 4, 0xdd)
      expectReg(dut, 5, 0xcc)
      expectReg(dut, 6, 0xccddeeffL)
      expectReg(dut, 10, 0x12345678L)
    }
  }

  it should "perform SH to any even offset" in {
    val asm =
      """
        |li x1, 0x12345678
        |li x2, 0xaabb
        |li x3, 0xccdd
        |li x4, 0xeeff
        |li x5, 0x5555
        |sw x1, 0(x0)
        |sh x2, 0(x0)
        |sh x3, 2(x0)
        |sh x4, 4(x0)
        |sh x5, 6(x0)
        |lw x6, 0(x0)
        |lw x7, 4(x0)
        |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 0x12345678L)
      expectReg(dut, 2, 0xaabb)
      expectReg(dut, 3, 0xccdd)
      expectReg(dut, 4, 0xeeff)
      expectReg(dut, 5, 0x5555)
      expectReg(dut, 6, 0xccddaabbL)
      expectReg(dut, 7, 0x5555eeffL)
    }
  }

  /*
  0: li
  4: sw
  8: lw
  12: addi, load-use hazard
  16: addi, extra
   */

  it should "avoid load-use hazards" in {
    val asm =
      """
        |li x1, 42
        |sw x1, 0(x0)
        |lw x2, 0(x0)
        |addi x2, x2, 5
        |addi x3, x0, 10
        |""".stripMargin
    val instrs = assembleMap(asm)
    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val sh = SimulationHarness(dut, instrs)
      sh.run()

      expectReg(dut, 1, 42)
      expectReg(dut, 2, 47)
      expectReg(dut, 3, 10)
    }
  }
}
