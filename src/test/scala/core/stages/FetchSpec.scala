package core.stages

import chisel3._
import chiseltest._
import core.{Config, defaultConf}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class FetchSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Fetch stage"

  implicit val conf: Config = defaultConf

  it should "update addr when ack is signalled" in {
    test(new Fetch).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val in = dut.io.mem.in
      val out = dut.io.mem.out
      in.ack.poke(false.B)
      in.rdata.poke(0.U)

      //Wait until fetch stage is requesting
      while(!out.req.peekBoolean()) {
        dut.clock.step()
      }
      for(i <- 0 until 5) {
        val addr = out.addr.peekInt().toInt
        assert (addr === conf.pcReset + 4*i, s"i=$i")
        dut.io.id.pc.expect(conf.pcReset + 4*i, s"i=$i")
        dut.clock.step()
        in.ack.poke(true.B)
        in.rdata.poke((i+1).U)
        dut.io.id.instr.expect((i+1).U)
        dut.io.id.pc.expect(conf.pcReset + 4*(i+1))
      }
    }
  }

  it should "keep addr constant when ack is not signalled" in {
    test(new Fetch) {dut =>
      val in = dut.io.mem.in
      val out = dut.io.mem.out
      in.ack.poke(false.B)
      in.rdata.poke(0.U)

      //Wait until fetch stage is requesting
      while(!out.req.peekBoolean()) {
        dut.clock.step()
      }
      //step 2 cc more, we expect addr being requested to stay constant
      for(_ <- 0 until 3) {
        out.addr.expect(conf.pcReset)
        dut.clock.step()
      }
      //On poking ack, we expect the address to change immediately
      in.ack.poke(true.B)
      out.addr.expect(conf.pcReset + 4)
      dut.clock.step()

      in.ack.poke(false.B)
      out.addr.expect(conf.pcReset + 4)

      //Another couple CC before updating
      for(_ <- 0 until 5) {
        out.addr.expect(conf.pcReset + 4)
        dut.clock.step()
      }
      in.ack.poke(true.B)
      dut.clock.step()
      in.ack.poke(false.B)
      out.addr.expect(conf.pcReset + 8)
    }
  }

  it should "handle a loadPC while waiting on acknowledge" in {
    /*
    Scenario:
    beq x0, x0, L2 (0)
    addi x1, x0, 1 (4)
    addi x2, x0, 2 (8)
    addi x3, x0, 3 (12)
    L2:
    addi x4, x0, 4 (16)
    addi x5, x0, 5 (20)
    addi x6, x0, 6 (24)

    When (8) is being fetched, (0) is being processed in EX stage and triggers loadPC, causing us to access (16) instead
    If this happens while ack=0, we cannot update PC as the protocol does not support
    de-asserting req / changing addr once req is high.
    - When not ack, addr is driven by PC
    - When ack, addr is driven by pcNext
     */
    test(new Fetch) {dut =>
      val in = dut.io.mem.in
      val out = dut.io.mem.out
      in.ack.poke(false.B)
      in.rdata.poke(0.U)

      //Wait for request
      while(!out.req.peekBoolean()) {
        dut.clock.step()
      }
      dut.clock.step()
      out.addr.expect(conf.pcReset)

      //Attempt to load new PC
      dut.io.ctrl.loadPC.poke(true.B)
      dut.io.ctrl.newPC.poke(100)
      dut.io.hzd.flush.poke(true.B)
      dut.clock.step()

      //Address being accessed shouldn't have changed
      dut.io.ctrl.loadPC.poke(false.B)
      dut.io.hzd.flush.poke(false.B)
      out.addr.expect(conf.pcReset)
      for(_ <- 0 until 3) {
        dut.clock.step()
        out.addr.expect(conf.pcReset)
      }

      //Finally ack it: When ack-ing, this instruction should be turned into a NOP
      in.rdata.poke(512)
      in.ack.poke(true.B)
      dut.io.id.instr.expect(0x13) //NOP
      dut.io.id.pc.expect(100) //because next instruction is fetched from PC 100

      dut.clock.step()
      dut.io.id.instr.expect(512)
      out.addr.expect(104)
    }
  }
}
