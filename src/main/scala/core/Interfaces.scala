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
  /** Result generated in execute stage */
  val res = Output(UInt(conf.XLEN.W))
  /** Register to write result into */
  val rd = Output(UInt(5.W))
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
  /** Register to write into */
  val rd = Output(UInt(5.W))
  /** Write enable for register file */
  val we = Output(Bool())
}


class MemoryDriverInterface(implicit conf: Config) extends Bundle {
  /** Request initiating a new memory access */
  val req = Bool()
  /** Memory address to acess */
  val addr = UInt(conf.XLEN.W)
  /** Data to be written into mem[addr] if `we` is high */
  val wdata = UInt(conf.XLEN.W)
  /** Write-enable flag */
  val we = Bool()
}

class MemoryResponseInterface(implicit conf: Config) extends Bundle {
  /** Acknowledge flag, either that valid read data can be sampled or that a write has been performed */
  val ack = Bool()
  /** Read data from mem[addr] if a read was performed */
  val rdata = UInt(conf.XLEN.W)
}
/**
 * Interface between the core and a memory device.
 * The interface is a simple request-acknowledge interface. When `req` is asserted, the address must be valid.
 *
 * When `ack` is asserted, the operation has been performed. It may be asserted at the earliest on the cycle following
 * `req` going high.
 *
 * If a read is performed, `we` is deasserted and `wdata` is undefined.
 * When `ack` is made valid, the read data must be valid.
 *
 * If a write is performed, `we` is asserted and `wdata` holds the write data. When `ack` is asserted, the write
 * data has been sampled, and may be changed on the same clock cycle.
 *
 * For both read- and write-operations, when `ack` is asserted, a new request may be initiated on the same clock cycle
 * by keeping `req` high.
 * If no new request is to be made, `req` must be deasserted.
 * Until `ack` has been asserted, `req` must be kept high.

 * @param conf
 */
class MemoryInterface(implicit conf: Config) extends Bundle {
  val out = Output(new MemoryDriverInterface)
  val in = Input(new MemoryResponseInterface)
}

/**
 * Port used for forwarding to EX from MEM and WB stages.
 * Also serves as the interface between WB and ID stages
 * @param conf
 */
class ForwardingPort(implicit conf: Config) extends Bundle {
  /** Register to be written into */
  val rd = UInt(5.W)
  /** Value to be written into that register */
  val wdata = UInt(conf.XLEN.W)
  /** Write-enable flag signifying is forwarding should actually take place */
  val we = Bool()
}