package core

import chisel3._
import chiseltest._
import core.stages.Execute
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ExecuteSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Execute stage"

  def branchTest(v1: Seq[Long], v2: Seq[Long], taken: Seq[Boolean], op: AluOp.Type): Unit = {
    test(new Execute()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val r = scala.util.Random
      val pc = Seq.fill(20)((r.nextInt()/4).toLong &  0xffff_ffffL)
      val imm = Seq.fill(20)((r.nextInt()/8).toLong & 0xffff_ffffL)
      val res = (pc zip imm).map{case (x,y) => (x+y) & 0xffff_fffeL}

      for(i <- 0 until 20) {
        dut.io.id.aluOp.poke(op)
        dut.io.id.ctrl.branch.poke(true.B)
        dut.io.id.v1.poke(v1(i))
        dut.io.id.v2.poke(v2(i))
        dut.io.id.pc.poke(pc(i))
        dut.io.id.imm.poke(imm(i))
        dut.clock.step()


        dut.io.ctrl.fetch.loadPC.expect(taken(i).B, s"v1=${v1(i)}, v2=${v2(i)}, taken=${taken(i)}")
        dut.io.ctrl.fetch.newPC.expect(res(i), s"v1=${v1(i)}, v2=${v2(i)}, taken=${taken(i)}")
      }
    }
  }

  it should "evaluate BEQ taken" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextLong() & 0xffff_ffffL)
    branchTest(v1, v1, Seq.fill(20)(true), AluOp.ADD) //funct3(add) = funct3(beq)
  }

  it should "evaluate BEQ not taken" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt().toLong & 0xffff_ffffL)
    val v2 = v1.map(_+1)
    branchTest(v1, v2, Seq.fill(20)(false), AluOp.ADD)
  }

  it should "evaluate BNE taken" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt())
    val v2 = Seq.fill(20)(r.nextInt())
    branchTest(v1.map(_.toLong & 0xffffffffL), v2.map(_.toLong & 0xffffffffL), (v1 zip v2).map(x => x._1 != x._2), AluOp.SLL) //funct3(sll)=funct3(bne)
  }

  it should "evaluate BNE not taken" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt())
    branchTest(v1.map(_.toLong & 0xffffffffL), v1.map(_.toLong & 0xffffffffL), Seq.fill(20)(false), AluOp.SLL)
  }

  it should "evaluate BLT" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt())
    val v2 = Seq.fill(20)(r.nextInt())
    branchTest(v1.map(_.toLong & 0xffffffffL), v2.map(_.toLong & 0xffffffffL), (v1 zip v2).map(x => x._1 < x._2), AluOp.XOR) //funct3(xor)=funct3(blt)
  }

  it should "evaluate BGE" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt())
    val v2 = Seq.fill(20)(r.nextInt())
    branchTest(v1.map(_.toLong & 0xffffffffL), v2.map(_.toLong & 0xffffffffL), (v1 zip v2).map(x => x._1 >= x._2), AluOp.SRL) //funct3(srl)=funct3(bge)
  }

  it should "evaluate BLTU" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt().toLong & 0xffffffffL)
    val v2 = Seq.fill(20)(r.nextInt().toLong & 0xffffffffL)
    branchTest(v1, v2, (v1 zip v2).map(x => x._1 < x._2), AluOp.OR) //funct3(or)=funct3(blt)
  }

  it should "evaluate BGEU" in {
    val r = scala.util.Random
    val v1 = Seq.fill(20)(r.nextInt().toLong & 0xffffffffL)
    val v2 = Seq.fill(20)(r.nextInt().toLong & 0xffffffffL)
    branchTest(v1, v2, (v1 zip v2).map(x => x._1 >= x._2), AluOp.AND) //funct3(and)=funct3(bgeu)
  }

}
