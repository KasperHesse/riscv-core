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
    test(new Fetch).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
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

      //Finally ack it: When ack-ing, this instruction should be invalidated
      in.rdata.poke(512)
      in.ack.poke(true.B)
      dut.io.id.instr.expect(512) //NOP
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(100) //because next instruction is fetched from PC 100

      dut.clock.step()
      dut.io.id.instr.expect(512)
      dut.io.id.valid.expect(true.B)
      out.addr.expect(104)
    }
  }

  //Common preamble for the tests verifying stall/ack behavior
  //Leaves the DUT in a state where ack=false, stall=true, in.rdata=3 and out.addr=12
  def stallPreamble(dut: Fetch): Unit = {
    //Setup
    dut.io.mem.in.ack.poke(false.B)
    dut.io.mem.in.rdata.poke(0.U)
    dut.io.mem.out.addr.expect(0.U)
    dut.clock.step()

    //Once ack is signalled, addr should increment
    dut.io.mem.in.ack.poke(true.B)
    for(i <- 1 until 4) {
      dut.io.mem.in.rdata.poke(i.U)
      dut.io.mem.out.addr.expect((4*i).U)
      dut.io.id.valid.expect(true.B)
      dut.io.id.instr.expect(i.U)
      dut.io.id.pc.expect((4*i).U)
      dut.clock.step()
    }

    //Once ack is deasserted, instruction should be invalid and address should not change
    //Since ack was not received, we're stll trying to access address 12
    dut.io.mem.in.ack.poke(false.B)
    for (_ <- 0 until 4) {
      dut.io.mem.out.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(3.U)
      dut.clock.step()
    }

    //If stalled, address should not change
    dut.io.hzd.stall.poke(true.B)
    for (_ <- 0 until 3) {
      dut.io.mem.out.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(3.U)
      dut.clock.step()
    }
  }

  //Scenario 1: Stall is asserted and de-asserted while ack is low
  it should "handle stall being toggled while waiting on ack" in {
    test(new Fetch) { dut =>
      stallPreamble(dut)
      //Once stall is deasserted, since ack hasn't been asserted, nothing changes
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.out.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.clock.step()

      //Toggling ack, valid should be high immediately
      dut.io.mem.in.ack.poke(true.B)
      dut.io.mem.in.rdata.poke(10.U)
      dut.io.id.valid.expect(true.B)
      dut.io.id.instr.expect(10.U)
    }
  }

    /*
    Scenario: Instruction addressed is not present in I$. While waiting on ack, a stall arrives
    Three outcomes
    1) Stall is de-asserted before ack
    2) Ack is asserted while stalling
    3) Ack is asserted on same CC as stall is deasserted
     */
    //Scenario 2: Ack is asserted while stalling
    it should "handle ack being asserted while stalling" in {
      test(new Fetch).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
        stallPreamble(dut)
        //Once ack is asserted, that instruction will be saved in sampledInstr
        //Will show up one CC later
        dut.io.mem.in.ack.poke(true.B)
        dut.io.mem.in.rdata.poke(10.U)
        dut.io.id.pc.expect(12.U)
        dut.io.id.valid.expect(false.B)
        dut.clock.step() //this sets hasSampledInstr high

        //Address should now be incremented because of ack since we don't have a sampled instruction
        dut.io.mem.in.ack.poke(false.B)
        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(false.B) //Because we're still stalled
        dut.io.id.instr.expect(10.U)
        dut.io.id.pc.expect(12.U)
        dut.clock.step()

        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(false.B)
        dut.io.id.instr.expect(10.U)
        dut.io.id.pc.expect(12.U)
        dut.clock.step()

        //Next instruction is acknowledged. This should keep addr constant, as we already have a sampled instruction
        dut.io.mem.in.ack.poke(true.B)
        dut.io.mem.in.rdata.poke(20.U)
        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(false.B)
        dut.io.id.pc.expect(12.U)
        dut.io.id.instr.expect(10.U)
        dut.clock.step()

        //Once acknowledged, should still keep memory address constant
        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(false.B)
        dut.io.id.pc.expect(12.U)
        dut.io.id.instr.expect(10.U)
        dut.clock.step()

        //Deasserting stall should make id.valid=1. Should still have pc=12 and instr=10 on this CC
        dut.io.hzd.stall.poke(false.B)
        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(true.B) //note: Indicates that current I$ cache (10, from  sampledInstr) is valid
        dut.io.id.pc.expect(12.U)
        dut.io.id.instr.expect(10.U)
        dut.clock.step()

        //On next CC, address should still be 16, and that PC value should also be sent to ID stage
        dut.io.mem.out.addr.expect(16.U)
        dut.io.id.valid.expect(true.B) //note: Indicates that current read (20, from I$) is valid
        dut.io.id.pc.expect(16.U)
        dut.io.id.instr.expect(20.U)
        dut.clock.step()

        //Should now be back to normal
        dut.io.mem.in.rdata.poke(30.U)
        dut.io.mem.out.addr.expect(20.U)
        dut.io.id.valid.expect(true.B)
        dut.io.id.pc.expect(20.U)
        dut.io.id.instr.expect(30.U)
      }
    }



  it should "keep PC constant while stalled" in {
    /*
    Scenario:
    Dmem is stalled waiting on load. This also stalls EX, ID and IF stages
    IF stage should keep PC constant, even if in.ack is true
     */
    test(new Fetch).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      //First cycle, ack is always low
      dut.io.mem.in.ack.poke(false.B)
      dut.io.mem.out.addr.expect(0.U)
      dut.clock.step()

      //Second-Fifth cycle, business as usual
      dut.io.mem.in.ack.poke(true.B)
      dut.io.mem.out.addr.expect(4.U)
      for(i <- 2 until 5) {
        dut.clock.step()
        dut.io.id.pc.expect((4*i).U)
      }

      //Before stalling, accessing address 16.
      dut.io.mem.out.addr.expect(16.U)
      dut.clock.step()
      dut.io.mem.in.rdata.poke(100.U)
      dut.io.hzd.stall.poke(true.B)

      //Because ack=1, rdata=100 is saved in sampledInstr and address is incremented to 20
      dut.io.mem.out.addr.expect(20.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(16.U) //PC should be that of previously requested instruction

      //On next cycles, access should still be to address 20, PC to ID should be 16 and access should be invalid
      for(_ <- 0 until 4) {
        dut.clock.step()
        dut.io.mem.in.rdata.poke(200.U)
        dut.io.mem.out.addr.expect(20.U)
        dut.io.id.instr.expect(100.U)
        dut.io.id.pc.expect(16.U) //
        dut.io.id.valid.expect(false.B)
      }

      //When deasserted, address should still be 20 on next CC,
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.out.addr.expect(20.U)
      dut.io.id.pc.expect(16.U)
      dut.io.id.instr.expect(100.U)
      dut.clock.step()

      //Should still access 20 now, but should also give the the proper result
      dut.io.mem.out.addr.expect(20.U) //Should correctly increment address now
      dut.io.id.pc.expect(20.U)
      dut.io.id.instr.expect(200.U)
    }
  }
}
