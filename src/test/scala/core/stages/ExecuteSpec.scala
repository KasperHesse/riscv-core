package core.stages

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import core.{AluOp, Config, Funct3, MemoryRequest, defaultConf}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random

class ExecuteSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Execute stage"

  implicit val conf: Config = defaultConf

  def branchTest(v1: Seq[Long], v2: Seq[Long], taken: Seq[Boolean], op: AluOp.Type): Unit = {
    test(new Execute()(defaultConf)) {dut =>
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


        dut.io.fetch.loadPC.expect(taken(i).B, s"v1=${v1(i)}, v2=${v2(i)}, taken=${taken(i)}")
        dut.io.fetch.newPC.expect(res(i), s"v1=${v1(i)}, v2=${v2(i)}, taken=${taken(i)}")
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

  it should "issue a memory read when memRead=1" in {
    test(new Execute) { dut =>
      dut.io.id.ctrl.memRead.poke(true.B)
      dut.io.id.valid.poke(true.B)
      for(_ <- 0 until 10) {
        val v1 = Random.nextInt(10000)
        val imm = Random.nextInt(10000)
        val func = Seq(Funct3.LW, Funct3.LH, Funct3.LHU, Funct3.LB, Funct3.LBU)(Random.nextInt(5))
        val addr = (v1 + imm) & ~0x3
        dut.io.id.v1.poke(v1)
        dut.io.id.v2.poke(v1)
        dut.io.id.imm.poke(imm)
        dut.io.id.ctrl.memOp.poke(func.U)
        dut.clock.step()
        println(s"addr: Peek ${dut.io.mem.addr.peekInt()}, expect $addr")
        dut.io.mem.addr.expect(addr.U)
        dut.io.mem.req.expect(true.B)
        dut.io.mem.we.expect(false.B)
      }
      //When not valid, should not generate request
      dut.io.id.valid.poke(false.B)
      for(_ <- 0 until 10) {
        dut.io.id.v1.poke(10.U)
        dut.io.id.imm.poke(0.U)
        dut.clock.step()
        dut.io.mem.req.expect(false.B)
        dut.io.mem.addr.expect(8.U)
        dut.io.mem.we.expect(false.B)
      }
    }
  }

  it should "issue a memory write when memWrite=1" in {
    test(new Execute) { dut =>
      dut.io.id.ctrl.memWrite.poke(true.B)
      dut.io.id.valid.poke(true.B)
      for (_ <- 0 until 10) {
        val v1 = Random.nextInt(10000)
        val v2 = Random.nextLong(1L << 32L)
        val imm = Random.nextInt(10000)
        val func = Seq(Funct3.SB, Funct3.SH, Funct3.SW)(Random.nextInt(3))
        val addr = (v1 + imm) & ~0x3
        val wdata = if (func == Funct3.SW) {
          v2
        } else if (func == Funct3.SH) {
          (v2 & 0xffffL) | ((v2 & 0xffffL) << 16)
        } else { //SB
          (v2 & 0xffL) | ((v2 & 0xffL) << 8) | ((v2 & 0xffL) << 16) | ((v2 & 0xffL) << 24)
        }
        dut.io.id.v1.poke(v1)
        dut.io.id.v2.poke(v2)
        dut.io.id.imm.poke(imm)
        dut.io.id.ctrl.memOp.poke(func.U)
        dut.clock.step()
        println(s"addr: Peek ${dut.io.mem.addr.peekInt()}, expect $addr")
        println(s"write: Peek ${dut.io.mem.wdata.peekInt()}, expect $wdata (from v2=$v2)")
        dut.io.mem.addr.expect(addr.U)
        dut.io.mem.req.expect(true.B)
        dut.io.mem.we.expect(true.B)
        dut.io.mem.wdata.expect(wdata.U)
      }
      //When not valid, should not generate request
      dut.io.id.valid.poke(false.B)
      for (_ <- 0 until 10) {
        dut.io.id.v1.poke(10.U)
        dut.io.id.imm.poke(0.U)
        dut.clock.step()
        dut.io.mem.req.expect(false.B)
        dut.io.mem.addr.expect(8.U)
        dut.io.mem.we.expect(true.B)
      }
    }
  }

  it should "keep memory request constant if not acknowledged" in {
    test (new Execute).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.id.v1.poke(20.U)
      dut.io.id.imm.poke(2.U)
      dut.io.id.ctrl.memRead.poke(true.B)
      dut.io.id.ctrl.memOp.poke(Funct3.LW.U)
      dut.io.id.valid.poke(true.B)
      dut.clock.step()

      //Memory request should be activated
      dut.io.mem.addr.expect(20.U) //22 & ~3 == 20
      dut.io.mem.req.expect(true.B)
      dut.io.mem.we.expect(false.B)
      //Poke next instruction
      dut.io.id.v1.poke(40.U)
      dut.clock.step()

      //Ack did not arrive for first instruction. Raise stall, input from ID becomes invalid
      dut.io.hzd.stall.poke(true.B)
      dut.io.id.valid.poke(false.B)
      dut.io.id.v1.poke(40.U)
      for (_ <- 0 until 3) {
        dut.clock.step()
        println("x")
        dut.io.mem.addr.expect(20.U)
        dut.io.mem.req.expect(true.B)
        dut.io.mem.we.expect(false.B)
      }

      //Ack arrives: Lower stall, should immediately start tracking instruction in pipeline register (addr 40)
      //Next instruction is invalid, should disable req on next CC
      dut.io.hzd.stall.poke(false.B)
      dut.io.id.v1.poke(60.U)
      dut.io.id.ctrl.memWrite.poke(true.B)
      dut.io.id.valid.poke(false.B)
      dut.io.mem.expectPartial((new MemoryRequest).Lit(_.req -> true.B, _.addr -> 40.U, _.we -> false.B))
      dut.clock.step()
      dut.io.mem.expectPartial((new MemoryRequest).Lit(_.req -> false.B, _.addr -> 60.U, _.we -> true.B))
    }
  }
}
