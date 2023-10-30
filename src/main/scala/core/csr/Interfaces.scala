package core.csr

import chisel3._
import core.Config

class Interfaces {

}

/**
 * Inputs to CSR that indicate external events that can update CSR's.
 *
 * For example, instruction retired
 */
class CSRTriggers extends Bundle {
  /** Instruction retired */
  val instret = Input(Bool())
  /** Floating point result inexact */
  val NX = Input(Bool())
  /** Floating point result underflow */
  val UF = Input(Bool())
  /** Floating point result overflow */
  val OF = Input(Bool())
  /** Floating point divide-by-zero */
  val DVZ = Input(Bool())
  /** Floating point invalid operation */
  val NV = Input(Bool())
  
}

/**
 * Inputs to the CSR module
 * @param conf
 */
class CSRInputs(implicit conf: Config) extends Bundle {
  /** The CSR to read or write */
  val csrReg = UInt(12.W)
  /** The GPR register to read from, if necessary */
  val rs1 = UInt(5.W)
  /** Value read from register rs1 */
  val rs1Val = UInt(conf.XLEN.W)
  /** The encoded CSR immdiate */
  val imm = UInt(conf.XLEN.W)
  /** Flag whether to use rs1Val (0) or imm (1) as mask for CSR operations */
  val useImm = Bool()
  /** The CSR operation to perform */
  val op = CSROP()
  /** The GPR register to write the result into */
  val rd = UInt(5.W)
  /** Flag indicating if instruction was a valid CSR instruction */
  val valid = Bool()
}