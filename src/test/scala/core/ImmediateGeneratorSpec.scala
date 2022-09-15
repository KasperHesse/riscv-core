package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ImmediateGeneratorSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  behavior of "Immediate generator"

  val MAX_12BIT = math.pow(2,12).toInt
  val MAX_20BIT = math.pow(2,20).toInt
  val conf = Config()


  /**
   * Testing function for immediate generation
   * @param dut The DUT
   * @param imm A function which generates an imm of the right size
   * @param immMask A function transforming that imm into a mask for the instruction
   * @param op The opcode to check against
   */
  def testing(dut: ImmediateGenerator, imm: => Long, immMask: Long => Long, op: Opcode.Type): Unit = {
    for(_ <- 0 until 10) {
      val i = imm & 0xffffffffL
      def ss(x: String) = x.grouped(4).fold("")((a,b) => a + "_" + b)
//      println(f"Poking immediate $i(${ss(i.toBinaryString)}) (mask=${ss(("00000000000000000000000000000" + immMask(i).toBinaryString).takeRight(32))})")
      dut.io.instr.poke((immMask(i) | op.litValue).U)
//      println(f"Peeking immediate ${dut.io.imm.peek().litValue.toLong.toBinaryString}")
      dut.io.imm.expect(i.U)
      dut.clock.step()
    }
  }

  it should "generate immediates for OP_IMM instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        x => x << 20,
        Opcode.OP_IMM)
    }
  }


  it should "generate immediates for LOAD instructions" in {
    def immMask(imm: Long): Long = {
      imm << 20
    }
    def imm: Long = scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2
    test(new ImmediateGenerator()(conf)) {dut => testing(dut, imm, immMask, Opcode.LOAD)}
  }

  it should "generate immediates for STORE instructions" in {
    def immMask(imm: Long): Long = {
      (imm >> 5) << 25 | (imm & 0x1f) << 7
    }
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        immMask,
        Opcode.STORE)
    }
  }

  it should "generate immediates for BRANCH instructions" in {
    def immMask(imm: Long): Long = {
      ((imm >> 12) << 31) | (((imm >> 5) & 0x3f) << 25) | (((imm >> 1) & 0xf) << 8) | ((imm >> 11) & 1) << 7
    }
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        (scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2) << 1,
        immMask,
        Opcode.BRANCH)
    }
  }

  it should "generate immediates for LUI instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt() & 0xfffff000L,
        identity,
        Opcode.LUI)
    }
  }

  it should "generate immediates for AUIPC instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt() & 0xfffff000L,
        identity,
        Opcode.AUIPC)
    }
  }

  it should "generate immediates for JAL instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt(MAX_20BIT)-MAX_20BIT/2 & ~1L,
        x => (x >> 20) << 31 | ((x >> 1) & 0x3ff) << 21 | ((x >> 11) & 1) << 20 | x & 0xff000,
        Opcode.JAL)
    }
  }

  it should "generate immediates for JALR instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testing(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        x => x << 20,
        Opcode.JALR)
    }
  }
}
