package peripherals

import chisel3._
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class MemMuxSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory mux"

  it should "forward DMEM req and block IMEM req while waiting" in {
    test(new MemMux()(Config())) {dut =>
      dut.io.dmem.req.req.poke(true)

      dut.io.dmem.req.addr.poke(30)
      dut.io.dmem.req.wdata.poke(40)
      dut.io.imem.req.addr.poke(130)
      dut.io.imem.req.wdata.poke(140)

      dut.io.mem.req.req.expect(true)
      dut.io.mem.req.addr.expect(30)
      dut.io.mem.req.wdata.expect(40)

      dut.clock.step()
      //Taking IMEM req high now should not let it propagate
      dut.io.imem.req.req.poke(true)

      for(_ <- 0 until 5) {
        dut.io.mem.req.req.expect(true)
        dut.io.mem.req.addr.expect(30)
        dut.io.mem.req.wdata.expect(40)
        dut.clock.step()
      }

      //Taking ack high should only forward it to dmem
      dut.io.mem.resp.ack.poke(true)
      dut.io.mem.resp.rdata.poke(300)

      dut.io.dmem.resp.ack.expect(true)
      dut.io.dmem.resp.rdata.expect(300)
      dut.io.imem.resp.ack.expect(false)
      dut.io.imem.resp.rdata.expect(300)
    }
  }

  it should "forward IMEM req and block DMEM req while waiting" in {
    test(new MemMux()(Config())) {dut =>
      dut.io.imem.req.req.poke(true)
      dut.io.dmem.req.req.poke(false)

      dut.io.imem.req.addr.poke(30)
      dut.io.imem.req.wdata.poke(40)
      dut.io.dmem.req.addr.poke(130)
      dut.io.dmem.req.wdata.poke(140)

      dut.io.mem.req.req.expect(true)
      dut.io.mem.req.addr.expect(30)
      dut.io.mem.req.wdata.expect(40)

      dut.clock.step()
      //Taking IMEM req high now should not let it propagate
      dut.io.dmem.req.req.poke(true)

      for(_ <- 0 until 5) {
        dut.io.mem.req.req.expect(true)
        dut.io.mem.req.addr.expect(30)
        dut.io.mem.req.wdata.expect(40)
        dut.clock.step()
      }

      //Taking ack high should only forward it to dmem
      dut.io.mem.resp.ack.poke(true)
      dut.io.mem.resp.rdata.poke(300)

      dut.io.dmem.resp.ack.expect(false)
      dut.io.dmem.resp.rdata.expect(300)
      dut.io.imem.resp.ack.expect(true)
      dut.io.imem.resp.rdata.expect(300)
    }
  }

  it should "prioritize DMEM over IMEM when both req at the same time" in {
    test(new MemMux()(Config())) {dut =>
      dut.io.imem.req.req.poke(true)
      dut.io.dmem.req.req.poke(true)

      dut.io.imem.req.addr.poke(30)
      dut.io.imem.req.wdata.poke(40)
      dut.io.dmem.req.addr.poke(130)
      dut.io.dmem.req.wdata.poke(140)

      dut.io.mem.req.req.expect(true)
      dut.io.mem.req.addr.expect(130)
      dut.io.mem.req.wdata.expect(140)

      dut.clock.step()
      dut.io.mem.req.req.expect(true)
      dut.io.mem.req.addr.expect(130)
      dut.io.mem.req.wdata.expect(140)

      dut.io.mem.resp.ack.poke(true)
      dut.io.dmem.resp.ack.expect(true)
      dut.io.imem.resp.ack.expect(false)
    }
  }

  it should "prioritize DMEM over IMEM when IMEM is acknowledging" in {
    test(new MemMux()(Config())) {dut =>
      //Set up connection from IMEM->MEM
      dut.io.imem.req.req.poke(true.B)
      dut.io.imem.req.addr.poke(100)
      dut.io.mem.req.addr.expect(100)

      //IMEM request not yet acknowledged, DMEM request activated
      dut.clock.step()
      dut.io.dmem.req.req.poke(true)
      dut.io.dmem.req.addr.poke(42)
      dut.io.dmem.req.wdata.poke(84)

      dut.io.mem.req.addr.expect(100)

      //Acknowledge IMEM request, should now forward DMEM request
      dut.clock.step()
      dut.io.mem.resp.ack.poke(true)
      dut.io.mem.resp.rdata.poke(100)

      dut.io.imem.resp.ack.expect(true)
      dut.io.dmem.resp.ack.expect(false)
      dut.io.imem.resp.rdata.expect(100)
      dut.io.mem.req.addr.expect(42)
      dut.io.mem.req.wdata.expect(84)

      dut.clock.step()
      dut.io.dmem.resp.ack.expect(true)
      dut.io.imem.resp.ack.expect(false)
      dut.io.dmem.resp.rdata.expect(100)
    }
  }
}
