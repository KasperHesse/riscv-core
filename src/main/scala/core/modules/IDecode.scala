package core.modules

import chisel3._
import chisel3.util._
import core.stages.ImmediateDecoder
import core.{AluOp, Config, Funct3, IDecodeOutputs, OpSrc, Opcode}

class IDecode(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    /** Instruction to be decoded */
    val instr = Input(UInt(32.W))
    /** Decoded output signals */
    val out = Output(new IDecodeOutputs())
  })
  val immGen = Module(new ImmediateDecoder)
  immGen.io.instr := io.instr

  val funct7 = io.instr(31, 25)
  val funct3 = io.instr(14, 12)
  val (op,_) = Opcode.safe(io.instr(6, 0))

  //OP1SRC, OP2SRC AND NEWPCSRC SELECTION
  val op1src = MuxCase(OpSrc.REG, Seq( //Default to using register value
    (op === Opcode.LUI, OpSrc.CONST),  //When LUI, use constant (0)
    (op === Opcode.AUIPC || op === Opcode.JAL || op === Opcode.JALR, OpSrc.PC) //On AUIPC, JAL or JALR, use PC value
  ))
  val op2src = MuxCase(OpSrc.IMM, Seq( //Default to using immediate value
    (op === Opcode.OP, OpSrc.REG),     //When reg-reg instruction, take register value
    (op === Opcode.JAL || op === Opcode.JALR, OpSrc.CONST) //When JAL or JALR, use constant (4)
  ))
  val newPCsrc = op === Opcode.JALR //When JALR, use value in rs1. When JAL and BRANCH, use value of PC

  //ALU OPCODE DECODING
  //Should only set MSB of aluOp if operation is SUB, SRA or SRAI
  //SUB,SRA: When opcode=OP, funct3=(ADD;SRA) && funct7[5]
  val msb = ((op === Opcode.OP && (funct3 === Funct3.SRA.U || funct3 === Funct3.SUB.U)) |
    (op === Opcode.OP_IMM && funct3 === Funct3.SRAI.U)) && funct7(5)
  //When OP or OP_IMM, we use funct3 and one more bit to encode AluOp. Otherwise, default to adding operands
  val aluOp = Mux(op === Opcode.OP || op === Opcode.OP_IMM,
                    AluOp.safe(msb ## funct3)._1,
                    AluOp.ADD)

  //FLAGS DECODING
  val branch = op === Opcode.BRANCH
  val jump = op === Opcode.JAL || op === Opcode.JALR
  val memRead = op === Opcode.LOAD
  val memWrite = op === Opcode.STORE

  //BRANCH OP DECODING
  val beq  = funct3 === Funct3.BEQ.U(3.W)
  val bne  = funct3 === Funct3.BNE.U(3.W)
  val blt  = funct3 === Funct3.BLT.U(3.W)
  val bge  = funct3 === Funct3.BGE.U(3.W)
  val bltu = funct3 === Funct3.BLTU.U(3.W)
  val bgeu = funct3 === Funct3.BGEU.U(3.W)
  val branchOp = Cat(bgeu, bltu, bge, blt, bne, beq)

  //VALID INSTRUCTION CHECK
  //Valid R-type instruction
  //If funct3 == (SUB, SRA) implies funct7 can be 0x00 or 0x20
  //Otherwise, funct7 == 0x00
  val validR = op === Opcode.OP && (
    ((funct3 === Funct3.SUB.U || funct3 === Funct3.SRA.U) && (funct7 === 0x20.U(7.W) || funct7 === 0x00.U(7.W))) ||
      (funct7 === 0.U))

  //Valid I-type instruction
  //if OP_IMM, Funct3=SRAI implies Funct7==0x20. Funct3==SLLI, SRLI implies Funct7==0x00
  //If LOAD, funct3 must be one of (LB, LH, LW, LBU, LHU)
  //If JALR, funct3 must be 0
  val validSRAI = funct3 =/= Funct3.SRAI.U || funct7 === 0x20.U(7.W)
  val validSLLI_SRLI = (funct3 =/= Funct3.SRLI.U && funct3 =/= Funct3.SLLI.U) || funct7 === 0.U
  val validI = (op === Opcode.OP_IMM && (validSRAI || validSLLI_SRLI)) ||
    (op === Opcode.LOAD && VecInit(Seq(Funct3.LB, Funct3.LH, Funct3.LW, Funct3.LBU, Funct3.LHU).map(_.U(3.W))).contains(funct3)) ||
    (op === Opcode.JALR && funct3 === 0.U)

  //Valid S-type instruction
  val validS = op === Opcode.STORE && VecInit(Seq(Funct3.SB.U(3.W), Funct3.SH.U(3.W), Funct3.SW.U(3.W))).contains(funct3)

  //Valid U-type instruction
  val validU = op === Opcode.LUI || op === Opcode.AUIPC

  //Valid J-type instruction
  val validJ = op === Opcode.JAL

  //Valid B-type instruction
  //Can use decoded branchOp to check if one of the correct funct3-values was present
  val validB = op === Opcode.BRANCH && branchOp.orR


  //OUTPUTS
  io.out.rs1 := io.instr(19, 15)
  io.out.rs2 := io.instr(24, 20)
  io.out.rd  := io.instr(11,  7)
  io.out.aluOp := aluOp
  io.out.imm := immGen.io.imm
  io.out.op1Src := op1src
  io.out.op2Src := op2src
  io.out.newPCsrc := newPCsrc
  io.out.valid := validR | validI | validS | validU | validJ | validB
  io.out.branch := branch
  io.out.branchOp := branchOp
  io.out.jump := jump
  io.out.memRead := memRead
  io.out.memWrite := memWrite
  io.out.memOp := funct3
  io.out.we := op =/= Opcode.BRANCH && op =/= Opcode.SYSTEM && op =/= Opcode.MISC_MEM && op =/= Opcode.STORE
}
