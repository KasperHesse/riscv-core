package core

import chisel3._
import core.csr.CSRInputs

class FetchDecodeIO(implicit conf: Config) extends Bundle {
  /** Instruction fetched from memory in IF */
  val instr = Output(UInt(32.W))
  /** PC of the fetched instruction */
  val pc = Output(UInt(conf.XLEN.W))
  /** Flag indicating if the instruction is valid or not */
  val valid = Output(Bool())
}

class FetchControlIO(implicit conf: Config) extends Bundle {
  /** Asserted when PC should be updated to not be PC+4 */
  val loadPC = Input(Bool())
  /** New PC to go to when loadPC is asserted */
  val newPC = Input(UInt(conf.XLEN.W))
  //stalling: Don't update PC but keep outputting instructions
  //flushing: Zero out the instruction, update PC. Should be used when a branch is taken and the previously loaded instruction was wrong
  /** Flush the stage, changing the instruction to a NOP */
  val flush = Input(Bool())
  /** Stall the stage, preventing the PC value from being updated */
  val stall = Input(Bool())
}

/**
 * Outputs from the decoder used to decode RV32I-instructions
 * @param conf
 */
class IDecodeOutputs(implicit conf: Config) extends Bundle {
  // Outputs to decode stage //
  /** Source register 1 */
  val rs1 = UInt(5.W)
  /** Source register 2 */
  val rs2 = UInt(5.W)
  /** Destination register */
  val rd = UInt(5.W)
  /** Opcode */
  val aluOp = AluOp()
  /** Decoded immediate */
  val imm = UInt(conf.XLEN.W)
  /** Instruction valid flag */
  val valid = Bool()

  //Outputs to control signals along the pipe
  /** Branch flag */
  val branch = Bool()
  /** Branch operation to perform. From MSB to LSB: (bgeu, bltu, bge, blt, bne, beq) */
  val branchOp = UInt(6.W)
  /** JAL/JALR flag */
  val jump = Bool()
  /** Source of operand 1 to ALU. When CONST, set to 0 */
  val op1Src = OpSrc()
  /** Source of operand 2 to ALU. When CONST, set to 4 */
  val op2Src = OpSrc()
  /** Whether to add value in rs1 (1) or PC (0) to immediate when calculating new PC on jump/branch instructions */
  val newPCsrc = Bool()
  /** Memory read operation flag */
  val memRead = Bool()
  /** Memory write operation flag */
  val memWrite = Bool()
  /** Memory operation to perform. Direct copy of funct3-field from instruction */
  val memOp = UInt(3.W)
  /** Write-enable flag when instruction reaches WB stage */
  val we = Bool()
}

/** IO-ports between Decode and Execute stage.
 * Instantiate as-is in Decode stage, use Flipped() in Execute stage */
class DecodeExecuteIO(implicit conf: Config) extends Bundle {
  val v1 = Output(UInt(conf.XLEN.W))
  val v2 = Output(UInt(conf.XLEN.W))
  val pc = Output(UInt(conf.XLEN.W))
  val alu = Output(new IDecodeOutputs)
}

class ExecuteMemoryIO(implicit conf: Config) extends Bundle {
  /** Valid flag */
  val valid = Output(Bool())
  /** Result generated in execute stage */
  val res = Output(UInt(conf.XLEN.W))
  /** Register to write result into */
  val rd = Output(UInt(5.W))
  /** Control signals passed on from Execute to Memory stage */
  val ctrl = new Bundle {
    /** Write-enable to the WB stage */
    val we = Output(Bool())
    /** Flag indicating that a value should be fetched from memory and stored in rd */
    val memRead = Output(Bool())
    /** Flag indicating whether a memory write operation was performed */
    val memWrite = Output(Bool())
    /** Type of memory operation to perform. Is the funct3-field from the instruction */
    val memOp = Output(UInt(3.W))
  }
}

class WritebackInputs(implicit conf: Config) extends Bundle {
  /** Valid flag */
  val valid = Bool()
  /** Result generated in execute stage / value fetched from memory */
  val res = UInt(conf.XLEN.W)
  /** Register to write into */
  val rd = UInt(5.W)
  /** Write enable for register file */
  val we = Bool()
}

class MemoryRequest(implicit conf: Config) extends Bundle {
  /** Request initiating a new memory access */
  val req = Bool()
  /** Memory address to access */
  val addr = UInt(conf.XLEN.W)
  /** Data to be written into mem[addr] if `we` is high */
  val wdata = UInt(conf.XLEN.W)
  /** Write-enable flag */
  val we = Bool()
  /** Write mask for write-operations. Only bytes associated with 1's in the mask are written to memory */
  val wmask = UInt((conf.XLEN/8).W)
}

class MemoryResponse(implicit conf: Config) extends Bundle {
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
  val req = Output(new MemoryRequest)
  val resp = Input(new MemoryResponse)
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