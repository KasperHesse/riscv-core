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
   *
   * @param dut The DUT
   * @param imm A function that generates a valid immediate
   * @param m a function taking the generated immediate and returning an Instruction
   */
  def testFun(dut: ImmediateGenerator, imm: => Int, m: Int => Instruction): Unit = {
    for(_ <- 0 until 10) {
      val i = imm
      println(f"Poking immediate $i")
      val inst = m(i)
      println(f"Instruction is $inst / ${inst.toUInt}")
      dut.io.instr.poke(inst.toUInt)
      dut.clock.step()
      println(f"Peeking immediate ${dut.io.imm.peek()}")
      dut.io.imm.expect(i.toLong & 0xffff_ffffL)
      dut.clock.step()
    }
  }

  it should "generate immediates for OP_IMM instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        x => ItypeInstruction(x, 0, 0, 0, Opcode.OP_IMM)
      )
    }
  }

  it should "generate immediates for LOAD instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        x => ItypeInstruction(x, 0, 0, 0, Opcode.LOAD)
      )
    }
  }

  it should "generate immediates for STORE instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2,
        x => StypeInstruction(x, 0, 0, 0, Opcode.STORE)
      )
    }
  }

  it should "generate immediates for BRANCH instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        (scala.util.Random.nextInt(MAX_12BIT)-MAX_12BIT/2) << 1,
        x => BtypeInstruction(x, 0, 0, 0, Opcode.BRANCH
        )
      )
    }
  }

  it should "generate immediates for LUI instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        scala.util.Random.nextInt() & 0xfffff000,
        x => UtypeInstruction(x, 0, Opcode.LUI))
    }
  }

  it should "generate immediates for AUIPC instructions" in {
    test(new ImmediateGenerator()(conf)) {dut =>
      testFun(dut,
        scala.util.Random.nextInt() & 0xfffff000,
        x => UtypeInstruction(x, 0, Opcode.AUIPC)
      )
    }
  }

  it should "generate immediates for JAL instructions" in {
    test(new ImmediateGenerator()(conf)) { dut =>
      testFun(dut,
        (scala.util.Random.nextInt(MAX_20BIT) - MAX_20BIT / 2) & ~1,
        x => JtypeInstruction(x, 0, Opcode.JAL)
      )
    }
  }

  it should "generate immediates for JALR instructions" in {
    test(new ImmediateGenerator()(conf)) { dut =>
      testFun(dut,
        scala.util.Random.nextInt(MAX_12BIT) - MAX_12BIT/2,
        x => ItypeInstruction(x, 0, 0, 0, Opcode.JALR)
      )
    }
  }
}
