package core.modules

import chisel3._
import chisel3.util._
import core.{AluOp, Config}

class ALU(implicit conf: Config) extends Module {
  class ALUIF extends Bundle {
    val v1 = Input(UInt(conf.XLEN.W))
    val v2 = Input(UInt(conf.XLEN.W))
    val op = Input(AluOp())
    val res = Output(UInt(conf.XLEN.W))
  }
  val io = IO(new ALUIF)

  //Arithmetic logic
  //Append the carry into the second operand to only instantiate one adder-symbol
  val carry = io.op === AluOp.SUB
  val arith = ((io.v1 ## 1.U(1.W)) + (Mux(carry, (~io.v2).asUInt, io.v2) ## carry))(conf.XLEN,1)

  //Shift logic
  val shifter = Module(new Shifter)
  shifter.io.in := io.v1
  shifter.io.shamt := io.v2(if(conf.XLEN == 32) 4 else 5, 0)
  shifter.io.mode := io.op
  val shift = shifter.io.out

  //SLT, SLTU
  //if sign(a)=1 and sign(b)=0, a<b when signed, a>b when unsigned
  //if sign(a)=0 and sign(b)=1, a>b when signed, a<b when unsigned
  val sgn1 = io.v1(conf.XLEN-1)
  val sgn2 = io.v2(conf.XLEN-1)
  val comp = io.v1(conf.XLEN-2,0) < io.v2(conf.XLEN-2,0) //unsigned comparison of remaining bits
  val slt = Mux(sgn1 === sgn2, comp,  //MSBs are the same, perform unsigned comparison on remaining bits
    Mux(io.op === AluOp.SLT, sgn1 & !sgn2, !sgn1 & sgn2)) //otherwise, use above logic

  //Bitwise logic expressions
  val logic = Mux(io.op === AluOp.AND, io.v1 & io.v2, Mux(io.op === AluOp.OR, io.v1 | io.v2, io.v1 ^ io.v2))

  io.res := arith //Default clause
  switch(io.op) {
    is(AluOp.SLL, AluOp.SRL, AluOp.SRA)  {io.res := shift}
    is(AluOp.SLT, AluOp.SLTU)            {io.res := slt}
    is(AluOp.AND, AluOp.OR, AluOp.XOR)   {io.res := logic}
  }
}
