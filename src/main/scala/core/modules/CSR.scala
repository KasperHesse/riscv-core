package core.modules

import chisel3._
import chisel3.util._
import core._

class CSR(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    /** Signal high when an instruction is retired */
    val instret = Input(Bool())
    /** GPR register read from */
    val rs1 = Input(UInt(5.W))
    /** CS-register to read (write) from (to) */
    val csrReg = Input(UInt(12.W))
    /** Destination register in GPR to write into */
    val rd = Input(UInt(5.W))
    /** CSR Operation to perform */
    val op = Input(CSROP())
    /** Value read from integer register or 0-extended immediate value */
    val imm = Input(UInt(conf.XLEN.W))
    /** Value read from CS-register */
    val csrRead = Output(UInt(conf.XLEN.W))

    val rdOut = Output(UInt(5.W))
  })

  /*
  SWAP: Write value from imm into CSR. Read CSR and output
  READSET: Value in 'imm' is used to set bits of register high
  READCLEAR: Value in 'imm' is used to set bits of register low

  Example reg: `fcsr` actually consists of multiple register
  - fflags: lower 5 bits
  - frm: Rounding mode, 3 bits
  - fcsr: Floating point control and status (frm + fflags)

  Keep one underlying register for fcsr. fflags and frm shadow values stored in that register
   */

  /*===================================
  * GENERAL DEFINITIONS
  *=================================*/
  val csrRdata = Wire(UInt(conf.XLEN.W))
  val csrWdata = Wire(UInt(conf.XLEN.W))

  val doCsrRead = !(io.op === CSROP.SWAP && io.rd === 0.U)
  val doCsrWrite = io.op === CSROP.SWAP || (io.rs1 =/= 0.U) //!((io.op === CSROP.READSET || io.op === CSROP.READCLEAR) && io.rs1 === 0.U)
  val csrEn = Seq(
    CSRMap.fcsr,
    CSRMap.frm,
    CSRMap.fflags,
    CSRMap.time,
    CSRMap.instret,
    CSRMap.cycle,
    CSRMap.timeh,
    CSRMap.instreth,
    CSRMap.cycleh
  ).map(
    x => (x, x.U(12.W) === io.csrReg && doCsrRead)
  ).toMap
  val csrWe = csrEn.map{case (csr, en) => (csr, en && doCsrWrite)}

  /*===================================
 * COUNTER AND TIMER CSR'S
 *=================================*/
  /* Instructions retired, INSTRET */
  val instret = RegInit(0.U(32.W))
  instret := MuxCase(instret, Seq(
    (csrWe(CSRMap.instret), csrWdata),
    (io.instret, instret + 1.U)
  ))

  val cycle = RegInit(0.U(32.W))
  cycle := Mux(csrWe(CSRMap.cycle), csrWdata, cycle + 1.U)

  /*===================================
   * FLOATING POINT CSR'S
   *=================================*/
  val fcsrReg = RegInit(0.U(8.W))
  val fcsr = 0.U((conf.XLEN-8).W) ## fcsrReg
  val fflags = 0.U((conf.XLEN - 5).W) ## fcsrReg(4,0)
  val frm = 0.U((conf.XLEN - 3).W) ## fcsrReg(7,5)

  fcsrReg := MuxCase(fcsrReg, Seq(
    (csrWe(CSRMap.fcsr), csrWdata(7,0)),
    (csrWe(CSRMap.fflags), fcsrReg(7,5) ## csrWdata(4,0)),
    (csrWe(CSRMap.frm), csrWdata(2,0) ## fcsrReg(4,0))
  ))

  /*===================================
 * General stuff
 *=================================*/

  csrRdata := MuxLookup(io.csrReg, fflags, Seq(
    (CSRMap.fflags.U, fflags),
    (CSRMap.frm.U, frm),
    (CSRMap.fcsr.U, fcsr),
    (CSRMap.instret.U, instret)
  ))

  //TODO Check if this has better semantics on newer version of Chisel. Doesn't work with Enum right now
  csrWdata := MuxLookup(io.op.asUInt, csrRdata, Seq(
    (CSROP.SWAP.asUInt, io.imm),
    (CSROP.READSET.asUInt, csrRdata | io.imm),
    (CSROP.READCLEAR.asUInt, csrRdata & (~io.imm).asUInt)
  ))

  io.csrRead := csrRdata
  io.rdOut := io.rd
}

object CSR extends App {
  chisel3.emitVerilog(new CSR()(Config()))
}

object CSRMap {
  val fflags = 0x001
  val frm = 0x002
  val fcsr = 0x003

  val cycle = 0xc00
  val time = 0xc01
  val instret = 0xc02
  val cycleh = 0xc80
  val timeh = 0xc81
  val instreth = 0xc82
}