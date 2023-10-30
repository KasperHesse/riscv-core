package core

import chiseltest.simulator.WriteVcdAnnotation

class CSRInstructionSpec extends MyTestFixture {
  behavior of "CSR Instructions"

  implicit val conf: Config = Config(debug=true)
  it should "read and write the fflags register" in {
    /*
    Do some computations
    Write into a CSR register
    Do some more computations
    Read a CSR register
    Use that value immediately to add into another register
     */
    val asm =
      """
        |li x1, 5
        |li x2, 0xab
        |csrw fcsr, x2
        |li x3, 0x02
        |li x4, 0x5
        |sll x5, x3, x4
        |or x6, x5, x4
        |csrr x7, fflags
        |slli x8, x7, 1
        |""".stripMargin

    test(new Core).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val sh = SimulationHarness(dut, assembleMap(asm, this.getTestName))
      sh.run()
      expectReg(dut, 1, 5)
      expectReg(dut, 2, 0xab)
      expectReg(dut, 3, 0x02)
      expectReg(dut, 4, 0x5)
      expectReg(dut, 5, 2 << 5)
      expectReg(dut, 6, 64 | 5)
      expectReg(dut, 7, 0xab & 0x1f)
      expectReg(dut, 8, (0xab & 0x1f) << 1)
    }
  }

  it should "perform CSR acceses and memory accesses interleaved" in {
    /*
    Do some computations, and try all combinations of memory access / csr access interleaving

    memory write
    CSR write
    memory load

    memory load
    CSR read
    memory load

    memory write
    CSR read
    memory write

    memory load
    CSR write
    memory write


     */
    val asm =
      """
        |li x1, 0x20
        |li x2, 0xdeadbeef
        |li x3, 5
        |sw x2, 0(x1)
        |csrw fflags, x3
        |lw x10, 0(x1)
        |lhu x11, 0(x1)
        |csrr x12, fflags
        |sh x1, 4(x1)
        |sh x1, 6(x1)
        |csrr x13, instret
        |sw x1, 8(x1)
        |""".stripMargin
    test(new Core) {dut =>
      val sh = SimulationHarness(dut, assembleMap(asm, this.getTestName))
      sh.run()

      expectReg(dut, 1, 0x20)
      expectReg(dut, 2, 0xdeadbeefL)
      expectReg(dut, 3, 5)
      expectReg(dut, 10, 0xdeadbeefL)
      expectReg(dut, 11, 0xbeef)
      expectReg(dut, 12, 5)
      expectReg(dut, 13, 11)
    }
  }
}
