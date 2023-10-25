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
  val instret = Input(Bool())
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
  /** The encoded CSR immediate, if present, or the value read from rs1 register */
  val mask = UInt(conf.XLEN.W)
  /** Flag indiating whether the value read from rs1 or the encoded immediate should be used for operations */
  val op = CSROP()
  /** The GPR register to write the result into */
  val rd = UInt(5.W)
  /** Flag indicating if instruction was a valid CSR instruction */
  val valid = Bool()
}

/**
 * Outputs from the CSR Module
 * @param conf
 */
class CSROutputs(implicit conf: Config) extends Bundle {
  /** The GPR to write into */
  val rd = UInt(5.W)
  /** Value read from CSR */
  val csrRead = UInt(conf.XLEN.W)
  /** Valid flag */
  val valid = Bool()
  /** Write enable flag */
  val we = Bool()
}