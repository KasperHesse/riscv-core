import chisel3.{Bool, RegNext}
import core.Config

package object utils {


  /** Makes a possibly negative value positive by converting it to a long and forcing the 32 MSB to zero */
  def mkPos(v: Int): Long = {
    v.toLong & 0xffff_ffffL
  }

//  def mkPos(v: Long): Long = {
//    v & 0xffff_ffffL
//  }

  /** Makes a possibly negative value positive by converting it to a long and forcing the MSB to zero */
//  def mkPos(v: Long)(implicit conf: Config): Long = {
//    v & ((1L << conf.XLEN) -1)
//  }

  def mkPos(v: Long)(implicit conf: Config): BigInt = {
    BigInt(v) & ((BigInt(1) << conf.XLEN)-1)
  }

  def risingEdge(x: Bool): Bool = x && !RegNext(x)
  def fallingEdge(x: Bool): Bool = !x && RegNext(x)
}
