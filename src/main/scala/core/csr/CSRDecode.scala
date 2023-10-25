package core.csr

import chisel3._
import core.{Config, Funct3, Opcode}

class CSRDecode(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    /** The instruction to decode */
    val instr = Input(UInt(32.W))
    /** Outputs, forwarded to CSR Module */
    val out = Output(new CSRInputs)
    /** Flag indicating whether to use immediate (1) or value read from rs1 (0) */
    val useImm = Output(Bool())
  })
  val validFunct3 = Seq(Funct3.CSRRW, Funct3.CSRRC, Funct3.CSRRS, Funct3.CSRRWI, Funct3.CSRRSI, Funct3.CSRRCI).map(_.U(3.W))
  val funct3 = io.instr(14, 12)
  val (op, _) = Opcode.safe(io.instr(6,0))

  io.out.csrReg := io.instr(31,20)
  io.out.rs1 := io.instr(19, 15)
  io.out.rd :=  io.instr(11, 7)
  io.out.mask := 0.U((conf.XLEN - 5).W) ## io.instr(19, 15)
  io.out.valid := op === Opcode.SYSTEM && VecInit(validFunct3).contains(funct3)
  io.useImm := VecInit(Seq(Funct3.CSRRSI, Funct3.CSRRWI, Funct3.CSRRCI).map(_.U(3.W))).contains(funct3)
  when(funct3 === Funct3.CSRRW.U(3.W) || funct3 === Funct3.CSRRWI.U(3.W)) {
    io.out.op := CSROP.SWAP
  } .elsewhen(funct3 === Funct3.CSRRS.U(3.W) || funct3 === Funct3.CSRRSI.U(3.W)) {
    io.out.op := CSROP.READSET
  } .otherwise {
    io.out.op := CSROP.READCLEAR
  }
}
