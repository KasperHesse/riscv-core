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
      val in = dut.io.mem.resp
      val out = dut.io.mem.req
      in.ack.poke(false.B)
      in.rdata.poke(0.U)

      //Wait until fetch stage is requesting
      while(!out.req.peekBoolean()) {
        dut.clock.step()
      }
      for(i <- 0 until 5) {
        val addr = out.addr.peekInt().toInt
        out.addr.expect(conf.pcReset + 4*i, s"i=$i")
        dut.clock.step()
        in.ack.poke(true.B)
        in.rdata.poke((i+1).U)
        dut.io.id.instr.expect((i+1).U)
        dut.io.id.pc.expect(conf.pcReset + 4*i)
      }
    }
  }

  it should "keep addr constant when ack is not signalled" in {
    test(new Fetch) {dut =>
      val in = dut.io.mem.resp
      val out = dut.io.mem.req
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
      val in = dut.io.mem.resp
      val out = dut.io.mem.req
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
      dut.io.ctrl.newPC.poke(16)
      dut.io.hzd.flush.poke(true.B)
      dut.clock.step()

      //Address being accessed shouldn't have changed since we didn't ack the req
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
      dut.io.id.pc.expect(0) //Flushed instruction was from pc 0
      out.addr.expect(16.U)

      dut.clock.step()
      in.rdata.poke(1024)
      dut.io.id.instr.expect(1024)
      dut.io.id.valid.expect(true.B)
      dut.io.id.pc.expect(16.U)
      out.addr.expect(20) //New instruction is from PC 16
    }
  }

  /*
    Scenario: Instruction addressed is not present in I$. While waiting on ack, a stall arrives
    Three outcomes
    1) Stall is de-asserted before ack
    2) Ack is asserted while stalling
    3) Ack is asserted on same CC as stall is deasserted
 */
  /** Common preamble for the tests verifying stall/ack behavior
  Leaves the DUT in a state where ack=false, stall=true, in.rdata=9, out.addr=12, id.pc=12 and no sampled instruction
  */
  def stallPreamble(dut: Fetch): Unit = {
    //Setup
    dut.io.mem.resp.ack.poke(false.B)
    dut.io.mem.resp.rdata.poke(0.U)
    dut.io.mem.req.addr.expect(conf.pcReset.U)
    dut.clock.step()

    //Once ack is signalled, addr should increment
    dut.io.mem.resp.ack.poke(true.B)
    for(i <- 0 until 12 by 4) {
      dut.io.mem.resp.rdata.poke((i+1).U)
      dut.io.mem.req.addr.expect((4+i).U)
      dut.io.id.valid.expect(true.B)
      dut.io.id.instr.expect((i+1).U)
      dut.io.id.pc.expect(i.U)
      dut.clock.step()
    }
    //At this point
    //rdata = 9
    //addr = 12
    //pc = 12

    //Once ack is deasserted, instruction should be invalid and address should not change
    //Since ack was not received, we're stll trying to access address 12
    dut.io.mem.resp.ack.poke(false.B)
    for (_ <- 0 until 4) {
      dut.io.mem.req.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(9.U)
      dut.clock.step()
    }

    //If stalled, address should not change
    dut.io.hzd.stall.poke(true.B)
    for (_ <- 0 until 3) {
      dut.io.mem.req.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(9.U)
      dut.clock.step()
    }
  }

  //Scenario 1: Stall is asserted and de-asserted while ack is low
  it should "handle stall being toggled while waiting on ack" in {
    test(new Fetch) { dut =>
      stallPreamble(dut)
      //Once stall is deasserted, since ack hasn't been asserted, nothing changes
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.req.addr.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.clock.step()

      for(_ <- 0 until 4) {
        //Nothing changes as we're still waiting on ack
        dut.io.mem.req.addr.expect(12.U)
        dut.io.id.valid.expect(false.B)
        dut.io.id.pc.expect(12.U)
        dut.io.id.valid.expect(false.B)
        dut.clock.step()
      }

      //Re-toggling stall: Nothing changes
      dut.io.hzd.stall.poke(true.B)
      for (_ <- 0 until 4) {
        //Nothing changes as we're still waiting on ack
        dut.io.mem.req.addr.expect(12.U)
        dut.io.id.valid.expect(false.B)
        dut.io.id.pc.expect(12.U)
        dut.io.id.valid.expect(false.B)
        dut.clock.step()
      }
    }
  }

  it should "handle ack being asserted while stalling" in {
    test(new Fetch).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      stallPreamble(dut)
      //Once ack is asserted, that instruction will be saved in sampledInstr
      //Will show up as output one CC later
      dut.io.mem.resp.ack.poke(true.B)
      dut.io.mem.resp.rdata.poke(10.U)
      dut.io.id.pc.expect(12.U)
      dut.io.id.valid.expect(false.B)
      dut.clock.step() //this sets hasSampledInstr high

      //Address should now be incremented because of ack since we have a sampled instruction
      dut.io.mem.resp.ack.poke(false.B)
      dut.io.mem.resp.rdata.poke(20.U)
      dut.io.mem.req.addr.expect(16.U)
      dut.io.id.valid.expect(false.B) //Because we're still stalled
      dut.io.id.instr.expect(10.U) //from sampledInstr
      dut.io.id.pc.expect(12.U)
      dut.clock.step()

      dut.io.mem.req.addr.expect(16.U) //Should still be true
      dut.io.id.valid.expect(false.B)
      dut.io.id.instr.expect(10.U)
      dut.io.id.pc.expect(12.U)
      dut.clock.step()

      //Next instruction is acknowledged. This should keep addr constant, as we already have a sampled instruction
      dut.io.mem.resp.ack.poke(true.B)
      dut.io.mem.resp.rdata.poke(30.U)
      dut.io.mem.req.addr.expect(16.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(10.U) //from sampledInstr
      dut.clock.step()

      //Once acknowledged, should still keep memory address constant and presented instruction constant
      dut.io.mem.req.addr.expect(16.U)
      dut.io.id.valid.expect(false.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(10.U)
      dut.clock.step()

      //Deasserting stall should make id.valid=1. Should now attempt to access next instruction
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.req.addr.expect(16.U)
      dut.io.id.valid.expect(true.B)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(10.U) //from sampledInstr
      dut.clock.step()

      //On next CC, address should increment to 20, and that PC value should also be sent to ID stage
      //Instruction rdata should equal 30, as that was most recently poked onto bus
      dut.io.mem.req.addr.expect(20.U)
      dut.io.id.valid.expect(true.B) //note: Indicates that current read (20, from I$) is valid
      dut.io.id.pc.expect(16.U)
      dut.io.id.instr.expect(30.U) //data read from bus
      dut.clock.step()

      //Should now be back to normal
      dut.io.mem.req.addr.expect(24.U)
      dut.io.mem.resp.rdata.poke(40.U)
      dut.io.id.valid.expect(true.B)
      dut.io.id.pc.expect(20.U)
      dut.io.id.instr.expect(40.U)
    }
  }

  it should "handle ack being asserted on same cc as stall is deasserted while no instruction has been sampled" in {
    test(new Fetch) { dut =>
      stallPreamble(dut)
      //Leaves the DUT in a state where ack=false, stall=true, in.rdata=9, out.addr=id.pc=12 and no sampled instr

      //Deassert stall and raise ack. Read data should be immediately present
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.resp.ack.poke(true.B)

      for(i <- 3 until 8) {
        dut.io.mem.resp.rdata.poke((10*i).U)
        dut.io.id.instr.expect((10*i).U)
        dut.io.id.pc.expect((4*i).U)
        dut.io.mem.req.addr.expect((4*(i+1)).U)
        dut.clock.step()
      }
    }
  }


  it should "handle ack being asserted on same cc as stall is deasserted while an instruction has been sampled" in {
    test(new Fetch) { dut =>
      stallPreamble(dut)
      //Leaves the DUT in a state where ack=false, stall=true, in.rdata=9, out.addr=id.pc=12 and no sampled instr

      //Ack to receive and sample first instruction (reading mem[12]==10)
      dut.io.mem.resp.ack.poke(true.B)
      dut.io.mem.resp.rdata.poke(10.U)
      dut.io.mem.req.addr.expect(16.U)
      dut.io.id.pc.expect(12.U)
      dut.io.id.instr.expect(10.U)
      dut.io.id.valid.expect(false.B) //because stalled
      dut.clock.step()

      //Lower stall and ack next instruction at the same time
      //Since we have a stored instruction, keep requesting same address as the pipeline
      //isn't yet ready
      dut.io.hzd.stall.poke(false.B)
      dut.io.mem.resp.ack.poke(true.B)
      dut.io.mem.resp.rdata.poke(20.U) //Reading mem[16]==20

      dut.io.id.instr.expect(10.U)
      dut.io.mem.req.addr.expect(16.U) //Still fetching from mem[16] due to sampled instruction
      dut.io.id.pc.expect(12.U)
      dut.io.id.valid.expect(true.B)
      dut.clock.step()

      //sampled instruction has been expedited, data from I$ is now visible
      //and next instruction is being sampled.
      //Because stall goes high, instruction from mem[16] gets saved
      dut.io.mem.req.addr.expect(20.U) //start fetching from addr 20
      dut.io.id.instr.expect(20.U) //Show instruction with value 20 fetched from mem[16]
      dut.io.id.pc.expect(16.U)
      dut.io.hzd.stall.poke(true.B)
      dut.io.id.valid.expect(false.B)
      dut.clock.step()

      //Due to being stalled, instruction from mem[16] is now saved in sampledInstr.
      //Instruction from mem[20] is ready on I$ output
      dut.io.mem.resp.rdata.poke(30.U)
      for(_ <- 0 until 4) {
        dut.io.mem.req.addr.expect(20.U)
        dut.io.id.instr.expect(20.U) //Still 20 from mem[16] in sampledInstr
        dut.io.id.pc.expect(16.U)
        dut.io.id.valid.expect(false.B)
        dut.clock.step()
      }
    }
  }
}
