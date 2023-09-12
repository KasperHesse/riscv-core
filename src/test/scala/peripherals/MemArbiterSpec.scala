package peripherals

import chisel3._
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class MemArbiterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Memory arbiter"

  it should "always forward the correct memory port" in {
    test(new MemArbiter(3, Seq(0x01, 0x02, 0x03))(Config())).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      val p = dut.io.periph
      val m = dut.io.master
      val N = 3
      for(i <- 0 until N) {
        p(i).resp.rdata.poke((i+1) * 100)
      }
      //Test connection to no peripheral
      //None should have req or data forwarded
      m.req.req.poke(true)
      m.req.addr.poke(0)
      m.req.wdata.poke(50)
      for(_ <- 0 until 3) {
        p.foreach{p =>
          p.req.req.expect(false)
          p.req.wdata.expect(0)
        }
        dut.clock.step()
      }
      m.req.req.poke(false.B)
      dut.clock.step(2)
      m.req.req.poke(true.B)

      //Test connection to each peripheral in turn
      for(i <- 0 until N) {
        m.req.addr.poke((i+1) << 24)
        for(j <- 0 until N) {
          p(j).req.req.expect(i == j, f"i=$i, j=$j")
          p(j).req.wdata.expect(if (i == j) 50 else 0, s"i=$i, j=$j")
        }
        dut.clock.step()
        //Now, rdata should be correctly forwarded from the attached peripheral the master
        for (_ <- 0 until 5) { //Num clock cycles, just to test over multiple CC
          for(j <- 0 until N) {
            p(j).req.req.expect(i == j, f"i=$i, j=$j")
            p(j).req.wdata.expect(if (i == j) 50 else 0, s"i=$i, j=$j")
            m.resp.rdata.expect((i+1)*100, s"i=$i, j=$j")
          }
          dut.clock.step()
        }
      }
    }
  }
}
