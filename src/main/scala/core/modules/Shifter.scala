package core.modules

import chisel3._
import chisel3.util._
import core.{AluOp, Config}

/**
 * Shifting module that performs logic right-shift, arithmetic right-shift and logic left-shift
 *
 * @param conf
 */
class Shifter(implicit conf: Config) extends Module {
  val sh = if(conf.XLEN == 32) 5 else 6
  val io = IO(new Bundle {
    /** Input signal to be shifted */
    val in = Input(UInt(conf.XLEN.W))
    /** Which kind of shift to perform. Additional encodings are ignored */
    val mode = Input(AluOp())
    /** Amount to shift by */
    val shamt = Input(UInt(sh.W))
    /** Result of the shift operation */
    val out = Output(UInt(conf.XLEN.W))
  })

  val signed = io.mode === AluOp.SRA
  val rev = io.mode === AluOp.SLL

  val input = Mux(rev, Reverse(io.in), io.in)

  //multi-step shifting logic
  //First steps perform 1,2,4,8 and 16-step shifts
  //A shift-stage shifts the MSB in, either shifting or not
  def shiftStage(amt: Int, sig: UInt, sel: Bool): UInt = {
    //MSB values to be shifted in. When signed shift, depends on MSB of input. When unsigned, always zeros
    val msb = Mux(signed, Fill(amt, sig(conf.XLEN-1)), 0.U(amt.W))
    //Shifted signal
    val shift = Cat(msb, sig(conf.XLEN-1, amt))
    //Select either the shifted or non-shifted versions
    Mux(sel, shift, sig)
  }

  val shift = Wire(Vec(sh, UInt(conf.XLEN.W)))
  shift(0) := shiftStage(1, input, io.shamt(0))
  for(i <- 1 until sh) {
    shift(i) := shiftStage(math.pow(2,i).toInt, shift(i-1), io.shamt(i))
  }
  io.out := Mux(rev, Reverse(shift(sh-1)), shift(sh-1))
}
