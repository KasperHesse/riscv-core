package core.stages

import chiseltest._
import chisel3._
import core.{Config, Funct3, defaultConf}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random

class MemorySpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory stage"

  implicit val conf: Config = defaultConf

  it should "invalidate its output when stalled" in {
    test(new Memory) {dut =>
      dut.io.hzd.stall.poke(true.B)

      dut.io.ex.valid.poke(true.B)
      dut.clock.step()
      dut.io.wb.valid.expect(false.B)

      dut.io.ex.valid.poke(false.B)
      dut.clock.step()
      dut.io.wb.valid.expect(false.B)

      dut.io.hzd.stall.poke(true.B)
      dut.clock.step()
      dut.io.wb.valid.expect(false.B)

      dut.io.ex.valid.poke(true.B)
      dut.clock.step()
      dut.io.wb.valid.expect(true.B)
    }
  }

  it should "construct read data correctly" in {
    test(new Memory) { dut =>
      //When LB, LBU, LSB of address indicate which byte was requested
      val d = 0x1234fedc
      dut.io.mem.rdata.poke(d.U)
      dut.io.ex.ctrl.memRead.poke(true.B)

      //LB: Sign-extend correctly
      dut.io.ex.ctrl.memOp.poke(Funct3.LB.U)

      for (i <- Seq.fill(10)(Random.nextInt(1000))) {
        val lsb = i & 0x3
        dut.io.ex.res.poke(i.U)
        dut.clock.step()
        val rdata = (((d >> lsb * 8) << 56) >> 56) & 0xffff_ffffL
        dut.io.wb.res.expect(rdata)
      }

      //LBU: Don't sign extend
      dut.io.ex.ctrl.memOp.poke(Funct3.LBU.U)
      for (i <- Seq.fill(10)(Random.nextInt(1000))) {
        val lsb = i & 0x3
        dut.io.ex.res.poke(i.U)
        dut.clock.step()
        val rdata = (d >> lsb * 8) & 0xff
        dut.io.wb.res.expect(rdata)
      }

      //LH: Sign extend
      dut.io.ex.ctrl.memOp.poke(Funct3.LH.U)
      for (i <- Seq.fill(10)(Random.nextInt(1000)).map(_ * 2)) {
        val lsb = i & 0x3
        dut.io.ex.res.poke(i.U)
        dut.clock.step()
        val rdata = (((d >> lsb * 8) << 48) >> 48) & 0xffff_ffffL
        dut.io.wb.res.expect(rdata)
      }

      //LHU: Don't Sign extend
      dut.io.ex.ctrl.memOp.poke(Funct3.LH.U)
      for (i <- Seq.fill(10)(Random.nextInt(1000)).map(_ * 2)) {
        val lsb = i & 0x3
        dut.io.ex.res.poke(i.U)
        dut.clock.step()
        val rdata = (((d >> lsb * 8) << 48) >> 48) & 0xffff_ffffL
        dut.io.wb.res.expect(rdata)
      }

      //LW: No processing
      dut.io.ex.ctrl.memOp.poke(Funct3.LW.U)
      for (i <- Seq.fill(10)(Random.nextInt(1000)).map(_*4)) {
        dut.io.ex.res.poke(i.U)
        dut.clock.step()
        val p = Random.nextLong(1L << conf.XLEN)
        dut.io.mem.rdata.poke(p)
        dut.io.wb.res.expect(p)
      }
    }
  }
}
