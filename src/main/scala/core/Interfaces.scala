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
    /** Whether the second operand to ALU comes from register file (1) or immediate (0) */
    val op2src = Output(Bool())
    /** Flag indicating if this is a branch instruction which should be evaluated */
    val branch = Output(Bool())
    /** Flag indicating if this is an unconditional jump instruction that should be taken */
    val jump = Output(Bool())
    /** Flag indicating that a value should be fetched from memory and stored in rd */
    val memRead = Output(Bool())
    /** Flag indicating that the value in rs2 should be written to memory */
    val memWrite = Output(Bool())
    /** Type of memory operation to perform. Is the funct3-field from the instruction */
    val memOp = Output(UInt(3.W))
    /** Write-enable in the WB stage */
    val we = Output(Bool())
  }
}

class ExecuteMemoryIO(implicit conf: Config) extends Bundle {
  /** Result generated in execute stage / address to access */
  val res = Output(UInt(conf.XLEN.W))
  /** Register to write result into */
  val rd = Output(UInt(5.W))

  val wdata = Output(UInt(conf.XLEN.W))

  /** Control signals passed on from Execute to Memory stage */
  val ctrl = new Bundle {
    /** Write-enable in the WB stage */
    val we = Output(Bool())
    /** Flag indicating that a value should be fetched from memory and stored in rd */
    val memRead = Output(Bool())
    /** Flag indicating that the value in rs2 should be written to memory */
    val memWrite = Output(Bool())
    /** Type of memory operation to perform. Is the funct3-field from the instruction */
    val memOp = Output(UInt(3.W))
  }
}

class MemoryWritebackIO(implicit conf: Config) extends Bundle {
  /** Result generated in execute stage / value fetched from memory */
  val res = Output(UInt(conf.XLEN.W))
  /** Flag indicating if a value was read from memory. If true, output is from rdata instead of res-port */
  val memRead = Output(Bool())
  /** Value fetched from memory. Should NOT be registered */
  val rdata = Output(UInt(conf.XLEN.W))
  /** Register to write into */
  val rd = Output(UInt(5.W))
  /** Write enable for register file */
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

/**
 * Interface between the core and a memory device. Used for both the [[Fetch]] and [[Memory]] stages.
 * The interface is a simple request-acknowledge interface. When `req` is asserted, the address must be valid.
 *
 * If a read is performed, `we` is deasserted and `wdata` is undefined.
 * When `ack` is made valid, the read data arrives *on the next clock cycle*. `ack` may be asserted during the same
 * clock cycle as `req` is made valid, to indicate that read data is valid on the subsequent clock cycle.
 *
 * If a write is performed, `we` is asserted and `wdata` holds the write data. When `ack` is asserted, the write
 * data has been sampled, and may be changed on the next clock cycle.
 *
 * For both read- and write-operations, when `ack` is asserted, a new request may be initiated on the next clock cycle.
 * If no new request is to be made, `req` must be deasserted. If `ack` is not asserted on the same clock cycle as
 * `req` is asserted, `req` must be kept high until `ack` is valid.

 * @param conf
 */
class MemoryInterface(implicit conf: Config) extends Bundle {
  /** Memory address to access */
  val addr = Output(UInt(conf.MLEN.W))
  /** Request initiating a new memory access */
  val req = Output(Bool())
  /** Acknowledge that previously requested data has arrived. May go high in same cycle as req */
  val ack = Input(Bool())
  /** Write-enable to the memory device */
  val we = Output(Bool())
  /** Data retrieved from mem[addr] */
  val rdata = Input(UInt(conf.XLEN.W))
  /** Data to write into mem[addr] */
  val wdata = Output(UInt(conf.XLEN.W))
}