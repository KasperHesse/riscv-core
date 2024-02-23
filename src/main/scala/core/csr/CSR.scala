package core.csr

import chisel3._
import chisel3.util._
import core._
import core.modules.CSRControllerIO

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
    /** Inputs and outputs to/from hazard detection module */
    val hzd = new CSRControllerIO
    /** Output from pipeline for reading/writing CSRs */
    val out = Output(new WritebackInputs)
    /** Input from WB forwarding port */
    val fwd = Input(new ForwardingPort)
  })

  /*===================================
   * INPUT PIPELINE REGISTER
   *=================================*/
  val in = RegInit(0.U(io.in.getWidth.W).asTypeOf(io.in))
  when(!io.hzd.stall) {
    in := io.in
  }
  //Handle forwarding while stalled waiting for pipe to empty
  when(io.fwd.we && io.fwd.rd === in.rs1) {
    in.rs1Val := io.fwd.wdata
  }
  //TODO: Handle forwarding?

  /*===================================
  * GENERAL DEFINITIONS
  *=================================*/
  val mask = Mux(in.useImm, in.imm, in.rs1Val)
  val csrRdata = Wire(UInt(conf.XLEN.W))
  val csrWdata = Wire(UInt(conf.XLEN.W))

  val doCsrRead = !(in.op === CSROP.SWAP && in.rd === 0.U)
  val doCsrWrite = (in.op === CSROP.SWAP || (in.rs1 =/= 0.U))  //This indirectly also checks the value of uimm
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
    x => (x, x.U(12.W) === in.csrReg && in.valid)
  ).toMap
  val csrRe = csrEn.map{case (csr, en) => (csr, en && doCsrRead)}
  val csrWe = csrEn.map{case (csr, en) => (csr, en && doCsrWrite)}

  /*===================================
   * COUNTER AND TIMER CSR'S
   *=================================*/

  /* Instructions retired, INSTRET and INSTRETH */
  val instret = RegInit(0.U(32.W))
  instret := Mux(io.triggers.instret, instret + 1.U, instret)

  val instreth = RegInit(0.U(32.W))
  instreth := Mux(instret.andR, instreth + 1.U, instret)

  /* Clock cycles elapsed, CYCLE and CYCLEH */
  val cycle = RegInit(0.U(32.W))
  cycle := cycle + 1.U

  val cycleh = RegInit(0.U(32.W))
  cycleh := Mux(cycle.andR, cycleh + 1.U, cycleh)

  /*===================================
   * FLOATING POINT CSR'S
   *=================================*/
  val fcsrReg = RegInit(0.U(8.W))
  val fcsr = 0.U((conf.XLEN-8).W) ## fcsrReg
  val fflags = 0.U((conf.XLEN - 5).W) ## fcsrReg(4,0) //fflags is fcsr(4,0)
  val frm = 0.U((conf.XLEN - 3).W) ## fcsrReg(7,5)    //frm is fcsr(7,5)

  //Updated value of fflags with accrued exceptions
  val fflagsNew = (fflags(4) | io.triggers.NV) ##
    (fflags(3) | io.triggers.DVZ) ##
    (fflags(2) | io.triggers.OF) ##
    (fflags(1) | io.triggers.UF) ##
    (fflags(0) | io.triggers.NX)

  fcsrReg := MuxCase(fcsrReg(7,5) ## fflagsNew, Seq(
    (csrWe(CSRMap.fcsr), csrWdata(7,0)),
    (csrWe(CSRMap.fflags), fcsrReg(7,5) ## csrWdata(4,0)),
    (csrWe(CSRMap.frm), csrWdata(2,0) ## fflagsNew)
  ))

  /*===================================
   * General stuff
   *=================================*/

  //CSR read data
  csrRdata := MuxLookup(in.csrReg, fflags)(Seq(
    (CSRMap.fflags.U, fflags),
    (CSRMap.frm.U, frm),
    (CSRMap.fcsr.U, fcsr),
    (CSRMap.instret.U, instret),
    (CSRMap.instreth.U, instreth),
    (CSRMap.cycle.U, cycle),
    (CSRMap.cycleh.U, cycleh)
  ))

  csrWdata := MuxLookup(in.op, csrRdata)(Seq(
    (CSROP.SWAP, mask),
    (CSROP.READSET, csrRdata | mask),
    (CSROP.READCLEAR, csrRdata & (~mask).asUInt)
  ))

  io.out.res := csrRdata
  io.out.rd := in.rd
  io.out.valid := in.valid && !io.hzd.stall
  io.out.we := doCsrRead && in.valid && !io.hzd.stall //Only write a value into GPR if a read was performed
  io.hzd.valid := in.valid
}

object CSR extends App {
  chisel3.emitVerilog(new CSR()(Config()))
}

object CSRMap {
  val fflags = 0x001
  val frm = 0x002
  val fcsr = 0x003

  //TODO: These csrs should not be writeable, only readable
  val cycle = 0xc00
  val time = 0xc01
  val instret = 0xc02
  val cycleh = 0xc80
  val timeh = 0xc81
  val instreth = 0xc82
}