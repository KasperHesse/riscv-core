package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ExecuteSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Execute stage"

  it should "AND values" in {
    test(new Execute()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Generate instructions
      //Generate list of expected outputs for mem.res, pcNext and branch-output
      dut.io.id.v1.poke(0xfff)
      dut.io.id.v2.poke(0xeee)
      dut.io.id.ctrl.op2src.poke(true.B)
      dut.io.id.aluOp.poke(AluOp.AND)
      dut.clock.step()
      dut.io.mem.res.expect(0xfff & 0xeee)
    }
  }

  it should "evaluate a branch" in {
    test(new Execute()(defaultConf))
  }
}
