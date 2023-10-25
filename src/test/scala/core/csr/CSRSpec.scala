package core.csr

import chisel3._
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CSRSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
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
      dut.io.in.mask.poke(0)
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
      dut.io.in.mask.poke(value)
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
      dut.io.in.mask.poke(mask)
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
      dut.io.out.csrRead.expect(0x1f)
      dut.io.out.valid.expect(true)

      //Read value in frm. Should be all 0's
      readCSR(dut, CSRMap.frm)
      dut.io.out.csrRead.expect(0)
      dut.io.out.valid.expect(true)

      //Read value in fcsr: should also be 0x1f
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.csrRead.expect(0x1f)
      dut.io.out.valid.expect(true)

      //Write a value into fcsr by swapping. Should be returned in LSB when reading frm
      writeCSR(dut, CSRMap.fcsr, 0xab)

      //Read value in frm. Should shadow the value written into fcsr
      readCSR(dut, CSRMap.frm)
      dut.io.out.csrRead.expect(0xab >> 5)
      dut.io.out.valid.expect(true)

      //Write new value into frm
      writeCSR(dut, CSRMap.frm, 0x2)

      //Read entire fcsr again
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.csrRead.expect( (0x2 << 5) | (0xab & 0x1f))

      //Clear entire register
      clearCSR(dut, CSRMap.fcsr, 0xff)
      readCSR(dut, CSRMap.fcsr)
      dut.io.out.csrRead.expect(0)
    }
  }

  it should "not read registers when CSRRW/I and rd==x0" in {
    test(new CSR()) {dut =>
      dut.io.in.rd.poke(0.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.valid.poke(true.B)
      dut.io.in.mask.poke(0xabcd)

      dut.clock.step()
      dut.io.out.we.expect(false.B)
      dut.io.out.valid.expect(true.B)
    }
    //TODO implement. Check value of we-output
  }

  it should "not write registers when valid is not asserted" in {
    test(new CSR) {dut =>
      //Write the value 0x1f into fcsr
      dut.io.in.rd.poke(1.U)
      dut.io.in.rs1.poke(1.U)
      dut.io.in.csrReg.poke(CSRMap.fcsr)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.mask.poke(0x1f)
      dut.io.in.valid.poke(true.B)
      dut.clock.step()
      dut.io.out.valid.expect(true.B)
      dut.io.out.we.expect(true.B)
      dut.io.out.csrRead.expect(0.U)
      dut.io.out.rd.expect(1.U)

      //Disable valid and change mask. This should disable the valid output and the we output
      dut.io.in.rd.poke(2.U)
      dut.io.in.rs1.poke(2.U)
      dut.io.in.mask.poke(0x3)
      dut.io.in.valid.poke(false.B)
      dut.clock.step()
      dut.io.out.valid.expect(false.B)
      dut.io.out.we.expect(false.B)
      dut.io.out.csrRead.expect(0x1f)
      dut.io.out.rd.expect(2.U)


      //Re-enable valid.
      dut.io.in.valid.poke(true.B)
      dut.clock.step()

      //Value has been updated. One more cc to read it
      dut.clock.step()
      dut.io.out.csrRead.expect(0x3)
    }
  }

  it should "not write registers when CSRRS or CSRRC and rs1==0" in {
    test(new CSR){dut =>
      //Write 0x1f into instret
      dut.io.in.rd.poke(1.U)
      dut.io.in.rs1.poke(1.U)
      dut.io.in.csrReg.poke(CSRMap.instret)
      dut.io.in.op.poke(CSROP.SWAP)
      dut.io.in.mask.poke(0x1f)
      dut.io.in.valid.poke(true.B)
      dut.clock.step()

      //Set mode to CSRRS and keep set rs1=mask=0. Write should not affect anything
      dut.io.in.rs1.poke(0.U)
      dut.io.in.mask.poke(0.U)
      dut.io.in.op.poke(CSROP.READSET)
      dut.clock.step()

      //Should not have modified value
      dut.io.out.csrRead.expect(0x1f)
      //Setting mode to CSRRC should not do anything either
      dut.io.in.op.poke(CSROP.READCLEAR)
      dut.clock.step()

      dut.io.out.csrRead.expect(0x1f)
    }
  }

  it should "increment instret and instreth" in {
    test(new CSR).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Load instret with a value close to max
      val init = (1L << 32) - 5
      val intMax = (1L << 32) - 1
      writeCSR(dut, CSRMap.instret, init)

      //Read value written
      readCSR(dut, CSRMap.instret)
      dut.io.out.csrRead.expect(init)

      //Take instret trigger high. Causes value increment
      dut.io.triggers.instret.poke(true)
      dut.clock.step() //one CC: Value in register increments
      for(i <- 1L until 5L) {
        readCSR(dut, CSRMap.instret)
        dut.io.out.csrRead.expect(init + i)
      }
      //Should be at max value now
      dut.io.out.csrRead.expect(intMax)
      //On next clock cycle, should reset back down to zero
      readCSR(dut, CSRMap.instret)
      dut.io.out.csrRead.expect(0)

      //Change to reading instreth. Should be 1
      readCSR(dut, CSRMap.instreth)
      dut.io.out.csrRead.expect(1)

      //Reset instret back to max value
      writeCSR(dut, CSRMap.instret, intMax)

      //Write a value to instret, forcing instreth to not update even though instret trigger is high
      clearCSR(dut, CSRMap.instret, 0xdeadbeefL)

      //Read out updated value
      readCSR(dut, CSRMap.instret)
      dut.io.out.csrRead.expect(intMax & ~0xdeadbeefL)

      //Read from instreth. Using SWAP, rd==x0 to disable writing
      readCSR(dut, CSRMap.instreth)
      dut.io.out.csrRead.expect(1)
    }
  }
}
