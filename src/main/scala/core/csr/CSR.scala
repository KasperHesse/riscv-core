package core.csr

import chisel3._
import chisel3.util._
import core._

/**
 * Control and status-register module. Contains all CSR's and handles CSR read/write operations,
 * as well as external triggers (e.g. instret, exceptions).
 * Has a pipeline register on the output
 * @param conf
 */
class CSR(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    /** External triggers, e.g. instruction retired */
    val triggers = Input(new CSRTriggers())
    /** Inputs from pipeline for reading/writing CSRs */
    val in = Input(new CSRInputs)
    /** Output from pipeline for reading/writing CSRs */
    val out = Output(new CSROutputs)
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

  val doCsrRead = !(io.in.op === CSROP.SWAP && io.in.rd === 0.U)
  val doCsrWrite = (io.in.op === CSROP.SWAP || (io.in.rs1 =/= 0.U))  //This indirectly also checks the value of uimm
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
    x => (x, x.U(12.W) === io.in.csrReg && io.in.valid)
  ).toMap
  val csrRe = csrEn.map{case (csr, en) => (csr, en && doCsrRead)}
  val csrWe = csrEn.map{case (csr, en) => (csr, en && doCsrWrite)}

  /*===================================
   * COUNTER AND TIMER CSR'S
   *=================================*/

  /* Instructions retired, INSTRET and INSTRETH */
  val instret = RegInit(0.U(32.W))
  instret := MuxCase(instret, Seq(
    (csrWe(CSRMap.instret), csrWdata),
    (io.triggers.instret, instret + 1.U)
  ))

  val instreth = RegInit(0.U(32.W))
  instreth := MuxCase(instreth, Seq(
    (csrWe(CSRMap.instreth), csrWdata),
    //only update instreth when not explicitly writing instret, instret is being updated and it is maxed out
    (!csrWe(CSRMap.instret) && io.triggers.instret && instret.andR, instreth + 1.U)
  ))

  /* Clock cycles elapsed, CYCLE */
  val cycle = RegInit(0.U(32.W))
  cycle := Mux(csrWe(CSRMap.cycle), csrWdata, cycle + 1.U)

  /*===================================
   * FLOATING POINT CSR'S
   *=================================*/
  val fcsrReg = RegInit(0.U(8.W))
  val fcsr = 0.U((conf.XLEN-8).W) ## fcsrReg
  val fflags = 0.U((conf.XLEN - 5).W) ## fcsrReg(4,0) //fflags is fcsr(4,0)
  val frm = 0.U((conf.XLEN - 3).W) ## fcsrReg(7,5)    //frm is fcsr(7,5)

  fcsrReg := MuxCase(fcsrReg, Seq(
    (csrWe(CSRMap.fcsr), csrWdata(7,0)),
    (csrWe(CSRMap.fflags), fcsrReg(7,5) ## csrWdata(4,0)),
    (csrWe(CSRMap.frm), csrWdata(2,0) ## fcsrReg(4,0))
  ))

  /*===================================
   * General stuff
   *=================================*/

  //CSR read data
  csrRdata := MuxLookup(io.in.csrReg, fflags)(Seq(
    (CSRMap.fflags.U, fflags),
    (CSRMap.frm.U, frm),
    (CSRMap.fcsr.U, fcsr),
    (CSRMap.instret.U, instret),
    (CSRMap.instreth.U, instreth)
  ))

  csrWdata := MuxLookup(io.in.op, csrRdata)(Seq(
    (CSROP.SWAP, io.in.mask),
    (CSROP.READSET, csrRdata | io.in.mask),
    (CSROP.READCLEAR, csrRdata & (~io.in.mask).asUInt)
  ))

  io.out.csrRead := RegNext(csrRdata)
  io.out.rd := RegNext(io.in.rd)
  io.out.valid := RegNext(io.in.valid)
  io.out.we := RegNext(doCsrRead && io.in.valid) //Only write a value into GPR if a read was performed
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