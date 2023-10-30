package core.csr

import chisel3._
import chiseltest._
import core.{Config, MyTestFixture}

class CSRSpec extends MyTestFixture {
  behavior of "CSR Module"

  implicit val conf: Config = Config()

  /**
   * Performs a read-access from a CSR
   * @param csrReg
   */
  def readCSR(dut: CSR, csrReg: Int, rd: Int = 0): Unit = {
    timescope {
      dut.io.in.valid.poke(true)
      dut.io.in.csrReg.poke(csrReg)
      dut.io.in.op.poke(CSROP.READSET)
      dut.io.in.rs1.poke(0)
      dut.io.in.imm.poke(0)
      dut.io.in.rs1Val.poke(0)
      dut.io.in.rd.poke(rd)
      dut.clock.step()
    }
  }

  /**
   * Writes a CSR with a given value
   * @param csrReg
   * @param value
   */
  def writeCSR(dut: CSR, csrReg: Int, value: Long): Unit = {
    timescope {
      dut.io.in.valid.poke(true)
      dut.io.in.csrReg.poke(csrReg)
      dut.io.in.imm.poke(value)
      dut.io.in.rs1Val.poke(value)
      dut.io.in.useImm.poke(false)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.rd.poke(1)
      dut.io.in.rs1.poke(1)
      dut.clock.step()
    }
  }

  /**
   * Clear bits in a CSR with a given mask
   * @param dut
   * @param csrReg
   * @param mask
   */
  def clearCSR(dut: CSR, csrReg: Int, mask: Long): Unit = {
    timescope {
      dut.io.in.valid.poke(true)
      dut.io.in.csrReg.poke(csrReg)
      dut.io.in.imm.poke(mask)
      dut.io.in.rs1Val.poke(mask)
      dut.io.in.useImm.poke(false)
      dut.io.in.op.poke(CSROP.READCLEAR)
      dut.io.in.rd.poke(1)
      dut.io.in.rs1.poke(1)
      dut.clock.step()
    }
  }

  it should "read and write the floating point CSRs" in {
    test(new CSR).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Setup
      writeCSR(dut, CSRMap.fflags, 0xffffffffL)

      //Read out the value
      readCSR(dut, CSRMap.fflags)
      dut.io.out.res.expect(0x1f)
      dut.io.out.valid.expect(true)

      //Read value in frm. Should be all 0's
      readCSR(dut, CSRMap.frm)
      dut.io.out.res.expect(0)
      dut.io.out.valid.expect(true)

      //Read value in fcsr: should also be 0x1f
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.res.expect(0x1f)
      dut.io.out.valid.expect(true)

      //Write a value into fcsr by swapping. Should be returned in LSB when reading frm
      writeCSR(dut, CSRMap.fcsr, 0xab)

      //Read value in frm. Should shadow the value written into fcsr
      readCSR(dut, CSRMap.frm)
      dut.io.out.res.expect(0xab >> 5)
      dut.io.out.valid.expect(true)

      //Write new value into frm
      writeCSR(dut, CSRMap.frm, 0x2)

      //Read entire fcsr again
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.res.expect( (0x2 << 5) | (0xab & 0x1f))

      //Clear entire register
      clearCSR(dut, CSRMap.fcsr, 0xff)
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.res.expect(0)
    }
  }

  it should "not read registers when CSRRW and rd==x0" in {
    test(new CSR()) {dut =>
      //Test CSRRW
      dut.io.in.rd.poke(0.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.useImm.poke(false)
      dut.io.in.rs1Val.poke(0xabcd)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.valid.poke(true.B)

      dut.clock.step()
      dut.io.out.we.expect(false.B)
      dut.io.out.valid.expect(true.B)
    }
    //TODO implement. Check value of we-output
  }

  it should "not read registers when CRRRWI and rd==x0" in {
    test(new CSR()) {dut =>
      //Test CSRRW
      dut.io.in.rd.poke(0.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.useImm.poke(true)
      dut.io.in.imm.poke(0xabcd)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.valid.poke(true.B)

      dut.clock.step()
      dut.io.out.we.expect(false.B)
      dut.io.out.valid.expect(true.B)
    }
  }

  it should "not write registers when valid is not asserted" in {
    test(new CSR) {dut =>
      //Write the value 0x1f into fcsr
      dut.io.in.rd.poke(1.U)
      dut.io.in.rs1.poke(1.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.rs1Val.poke(0x1f)
      dut.io.in.valid.poke(true.B)
      dut.clock.step()
      dut.io.out.valid.expect(true.B)
      dut.io.out.we.expect(true.B)
      dut.io.out.res.expect(0.U)
      dut.io.out.rd.expect(1.U)

      //Disable valid and change mask. This should disable the valid output and the we output
      dut.io.in.rd.poke(2.U)
      dut.io.in.rs1.poke(2.U)
      dut.io.in.rs1Val.poke(0x3)
      dut.io.in.valid.poke(false.B)
      dut.clock.step()
      dut.io.out.valid.expect(false.B)
      dut.io.out.we.expect(false.B)
      dut.io.out.res.expect(0x1f)
      dut.io.out.rd.expect(2.U)


      //Re-enable valid.
      dut.io.in.valid.poke(true.B)
      dut.clock.step()

      //Value has been updated. One more cc to read it
      dut.clock.step()
      dut.io.out.res.expect(0x3)
    }
  }

  it should "not write registers when CSRRS or CSRRC and rs1==0" in {
    test(new CSR){dut =>
      //Write 0x1f into fcsr
      dut.io.in.rd.poke(1.U)
      dut.io.in.rs1.poke(1.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.rs1Val.poke(0x1f)
      dut.io.in.valid.poke(true.B)
      dut.clock.step()

      //Set mode to CSRRS and keep set rs1=mask=0. Write should not affect anything
      dut.io.in.rs1.poke(0.U)
      dut.io.in.rs1Val.poke(0.U)
      dut.io.in.op.poke(CSROP.READSET)
      dut.clock.step()

      //Should not have modified value
      dut.io.out.res.expect(0x1f)
      //Setting mode to CSRRC should not do anything either
      dut.io.in.op.poke(CSROP.READCLEAR)
      dut.clock.step()

      dut.io.out.res.expect(0x1f)
    }
  }
}
