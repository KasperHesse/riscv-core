package peripherals

import chisel3._
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class MemBlockSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory block"

  it should "support full word writes and reads" in {
    test(new MemBlock(8)(Config())).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val req = dut.io.req
      val resp = dut.io.resp

      req.req.poke(false.B)
      dut.clock.step(3)

      req.req.poke(true.B)
      req.addr.poke(0x0)
      req.wdata.poke(0x12345678)
      req.wmask.poke(0xf)
      req.we.poke(true.B)
      dut.clock.step()

      resp.ack.expect(true.B)
      req.we.poke(false.B)
      dut.clock.step()

      resp.ack.expect(true.B)
      resp.rdata.expect(0x12345678)
    }
  }

  it should "support half word writes" in {
    test(new MemBlock(8)(Config())) {dut =>
      //Write some initial data
      val req = dut.io.req
      val resp = dut.io.resp

      req.req.poke(false.B)
      dut.clock.step(3)

      req.req.poke(true.B)
      req.addr.poke(0x0)
      req.wdata.poke(0x12345678L)
      req.wmask.poke(0xf)
      req.we.poke(true.B)
      dut.clock.step()

      resp.ack.expect(true.B)
      req.addr.poke(0x4)
      req.wdata.poke(0x9abcdef0L)
      dut.clock.step()

      //Perform a half-word write to the LSB of address 0
      resp.ack.expect(true.B)
      req.addr.poke(0)
      req.wdata.poke(0xabcdabcdL)
      req.wmask.poke(0x3)
      dut.clock.step()

      resp.ack.expect(true.B)
      req.wdata.poke(0xfecdfecdL)
      req.wmask.poke(0xc) //1100
      dut.clock.step()

      //Read back the data
      req.we.poke(false.B)
      resp.ack.expect(true.B)
      dut.clock.step()

      resp.ack.expect(true.B)
      resp.rdata.expect(0xfecdabcdL)
    }
  }

  it should "support byte writes" in {
    test(new MemBlock(8)(Config())) { dut =>
      //Write some initial data
      val req = dut.io.req
      val resp = dut.io.resp

      req.req.poke(false.B)
      dut.clock.step(3)

      req.req.poke(true.B)
      req.addr.poke(0x0)
      req.wdata.poke(0x12345678L)
      req.wmask.poke(0xf)
      req.we.poke(true.B)
      dut.clock.step()

      req.wdata.poke(0xababababL)
      req.wmask.poke(0x1)
      dut.clock.step()

      req.wdata.poke(0x0e0e0e0eL)
      req.wmask.poke(0x1 << 1)
      dut.clock.step()

      req.wdata.poke(0x86868686L)
      req.wmask.poke(0x1 << 2)
      dut.clock.step()

      req.wdata.poke(0xc3c3c3c3L)
      req.wmask.poke(0x1 << 3)
      dut.clock.step()

      //Read back our data
      req.we.poke(false.B)
      dut.clock.step()

      resp.ack.expect(true)
      resp.rdata.expect(0xc3860eabL)
    }
  }
}
