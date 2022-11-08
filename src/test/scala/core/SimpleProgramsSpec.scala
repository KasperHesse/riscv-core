package core

import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class SimpleProgramsSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Simple programs"

  implicit val conf = defaultConf

    it should "compute the sum of 1..100" in {
      val asm =
        """
          |addi x1, x0, 1
          |add x2, x0, x0
          |addi x3, x0, 101
          |L1: add x2, x1, x2
          |addi x1, x1, 1
          |blt x1, x3, L1
          |""".stripMargin

      test(new Core) {dut =>
        val sh = SimulationHarness(dut, assembleMap(asm))
        sh.setTimeout(500)
        sh.run()

        expectReg(dut, 1, 101)
        expectReg(dut, 2, 5050)
        expectReg(dut, 3, 101)
      }
    }
}
