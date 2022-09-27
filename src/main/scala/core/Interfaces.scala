package core

import chisel3._

class FetchDecodeIO(implicit conf: Config) extends Bundle {
  /** Instruction fetched from memory in IF */
  val instr = Output(UInt(32.W))
  /** PC of the fetched instruction */
  val pc = Output(UInt(conf.XLEN.W))
}

class FetchControlIO(implicit conf: Config) extends Bundle {
  /** Asserted when PC should be updated to not be PC+4 */
  val loadPC = Input(Bool())
  /** New PC to go to when loadPC is asserted */
  val newPC = Input(UInt(conf.MLEN.W))
  //stalling: Don't update PC but keep outputting instructions
  //flushing: Zero out the instruction, update PC. Should be used when a branch is taken and the previously loaded instruction was wrong
  /** Flush the stage, changing the instruction to a NOP */
  val flush = Input(Bool())
  /** Stall the stage, preventing the PC value from being updated */
  val stall = Input(Bool())
}

/** IO-ports between Decode and Execute stage.
 * Instantiate as-is in Decode stage, use Flipped() in Execute stage */
class DecodeExecuteIO(implicit conf: Config) extends Bundle {
  /** Immediate value encoded in the instruction if an IMM is used */
  val imm = Output(UInt(conf.XLEN.W))
  /** Value stored in register rs1 */
  val v1 = Output(UInt(conf.XLEN.W))
  /** Value stored in register rs2 */
  val v2 = Output(UInt(conf.XLEN.W))
  /** rs1-register, used for proper forwarding */
  val rs1 = Output(UInt(5.W))
  /** rs2-register, used for proper forwarding */
  val rs2 = Output(UInt(5.W))
  /** PC value of the current instruction, to be used when calculating branches */
  val pc = Output(UInt(conf.XLEN.W))
  /** Value to write result into if instruction requires this */
  val rd = Output(UInt(5.W))
  /** Operation for the ALU to perform. In practice, is a concatenation of funct7[5] and funct3.
   * The 3 LSB (funct3) also encode the branch comparison to be performed */
  val aluOp = Output(AluOp())
  /** Whether the value to add to the immediate when calculating branch/jump targets is the PC (0, JAL and branches),
   * or value in rs1 (1, JALR) */
  val pcNextSrc = Output(Bool())
  /** Control values passed on to the Execute stage */
  val ctrl = new Bundle {
    /** Write-enable in the WB stage */
    val we = Output(Bool())
    /** Whether the second operand to ALU comes from register file (1) or immediate (0) */
    val op2src = Output(Bool())
    /** Flag indicating if this is a branch instruction which should be evaluated */
    val branch = Output(Bool())
    /** Flag indicating if this is an unconditional jump instruction that should be taken */
    val jump = Output(Bool())
  }
}

class ExecuteMemoryIO(implicit conf: Config) extends Bundle {
  /** Result generated in execute stage / address to access */
  val res = Output(UInt(conf.XLEN.W))
  val rd = Output(UInt(5.W))
  val we = Output(Bool())
}

class MemoryWritebackIO(implicit conf: Config) extends Bundle {
  /** Result generated in execute stage / value fetched from memory */
  val res = Output(UInt(conf.XLEN.W))
  val rd = Output(UInt(5.W))
  val we = Output(Bool())
}

/**
 * IO ports between Writeback and Decode stage.
 * Instantiate as-is in the Writeback stage, use Flipped() in the Decode stage
 */
class WritebackDecodeIO(implicit conf: Config) extends Bundle {
  /** Write-enable for register file */
  val we = Output(Bool())
  /** Register to write into */
  val rd = Output(UInt(5.W))
  /** Data to write into rd-register */
  val wdata = Output(UInt(conf.XLEN.W))
}

class MemoryInterface(implicit conf: Config) extends Bundle {
  /** Memory address to access */
  val addr = Output(UInt(conf.MLEN.W))
  /** Request initiating a new memory access */
  val req = Output(Bool())
  /** Acknowledge that previously requested data has arrived. May go high in same cycle as req */
  val ack = Input(Bool())
  /** Data retrieved from mem[addr] */
  val data = Input(UInt(conf.XLEN.W))
}