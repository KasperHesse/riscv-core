package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ShifterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "shifting module"


  def test(mode: AluOp.Type, op: (Int, Int) => Int): Unit = {
    val r = scala.util.Random
    test(new Shifter()(defaultConf)) { dut =>
      val in = Seq.fill(20)(r.nextInt())
      val shamt = Seq.fill(20)(r.nextInt(32))

      for ((i,s) <- in zip shamt) {
        dut.io.in.poke((i.toLong & 0xffffffffL).U)
        dut.io.shamt.poke(s.U)
        dut.io.mode.poke(mode)
        dut.clock.step()
        dut.io.out.expect(op(i,s).toLong & 0xffffffffL, s"Shifting i=$i (${i.toHexString}) with shamt=$s did not yield ${i << s}")
      }
    }
  }

  it should "perform SLL" in {
    test(AluOp.SLL, _<<_)
  }

  it should "perform SRL" in {
    test(AluOp.SRL, _>>>_)
  }

  it should "perform SRA" in {
    test(AluOp.SRA, _>>_)
  }
}
