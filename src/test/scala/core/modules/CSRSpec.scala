package core.modules

import chisel3._
import chiseltest._
import core.Config
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CSRSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "CSR Module"

  implicit val conf: Config = Config()

  it should "read and write the floating point CSRs" in {
    test(new CSR).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Setup
      dut.io.op.poke(CSROP.READSET)
      dut.io.imm.poke(0.U)
      dut.io.rs1.poke(1)
      dut.io.rd.poke(1)

      dut.clock.step()
      dut.io.csrRead.expect(0)

      //Write all 1's into fflags. fflags occupies the lower 5 bits of fcsr
      dut.io.csrReg.poke(CSRMap.fflags)
      dut.io.imm.poke(0xffffffffL)
      dut.clock.step()
      dut.io.csrRead.expect(0x1f)

      //Read value frm. Should not be modified
      dut.io.csrReg.poke(CSRMap.frm)
      dut.io.imm.poke(0x0)
      dut.clock.step()
      dut.io.csrRead.expect(0)

      //Read value in fcsr: Shoudl also be 0x1f
      dut.io.csrReg.poke(CSRMap.fcsr)
      dut.clock.step()
      dut.io.csrRead.expect(0x1f)

      //Write a value into fcsr by swapping. Should be returned in LSB when reading frm
      dut.io.op.poke(CSROP.SWAP)
      dut.io.imm.poke(0xab)
      dut.io.csrReg.poke(CSRMap.fcsr)
      dut.clock.step()
      dut.io.csrRead.expect(0xab) //TODO output registering of CSR outputs

      //Read value in frm. Should shadow the value written into fcsr
      dut.io.op.poke(CSROP.READSET)
      dut.io.imm.poke(0)
      dut.io.csrReg.poke(CSRMap.frm)
      dut.clock.step()
      dut.io.csrRead.expect(0xab >> 5)

      //Write new value into frm
      dut.io.imm.poke(0x2)
      dut.io.op.poke(CSROP.SWAP)
      dut.io.csrReg.poke(CSRMap.frm)
      dut.clock.step()
      dut.io.csrRead.expect(0x2)

      //Read entire fcsr again
      dut.io.op.poke(CSROP.READSET)
      dut.io.csrReg.poke(CSRMap.fcsr)
      dut.io.imm.poke(0.U)
      dut.clock.step()
      dut.io.csrRead.expect( (0x2 << 5) | (0xab & 0x1f))

      //Clear entire register
      dut.io.op.poke(CSROP.READCLEAR)
      dut.io.csrReg.poke(CSRMap.fcsr)
      dut.io.imm.poke(0xff)
      dut.clock.step()
      dut.io.csrRead.expect(0)

    }
  }
}
