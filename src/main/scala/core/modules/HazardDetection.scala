package core.modules

import chisel3._

class FetchHazardIO extends Bundle {
  val flush = Input(Bool())
  val stall = Input(Bool())
}

class DecodeHazardIO extends Bundle {
  val rs1 = Output(UInt(5.W))
  val rs2 = Output(UInt(5.W))
  val flush = Input(Bool())
  val stall = Input(Bool())
}

class ExecuteHazardIO extends Bundle {
  val memRead = Output(Bool())
  val rd = Output(UInt(5.W))
  val loadPC = Output(Bool())
  val stall = Input(Bool())
}

class MemoryHazardIO extends Bundle {
  val memRead = Output(Bool())
  val memWrite = Output(Bool())
  val ack = Output(Bool())
  val stall = Input(Bool())
}
/**
 * The [[HazardDetection]] unit is used to avoid data hazards when executing programs
 */
class HazardDetection extends Module {
  val io = IO(new Bundle {
    val IF = Flipped(new FetchHazardIO)
    val ID = Flipped(new DecodeHazardIO)
    val EX = Flipped(new ExecuteHazardIO)
    val MEM = Flipped(new MemoryHazardIO)
  })
  //All outputs default to false

  io.IF.flush :=  false.B
  io.IF.stall :=  false.B
  io.ID.flush :=  false.B
  io.ID.stall :=  false.B
  io.EX.stall :=  false.B
  io.MEM.stall := false.B

  //Delayed memory response
  //If a memory operation is not ACK'd on cycle after issuing it, IF, ID and EX stages must be stalled.
  //Must be kept stalled until ACK arrives
  when (io.MEM.memRead && !io.MEM.ack) {
    io.IF.stall := true.B
    io.ID.stall := true.B
    io.EX.stall := true.B
    io.MEM.stall := true.B
  }


  //Load-use hazard
  //Since we don't forward result from memory stage,
  //we flush ID (to make it invalid) and stall IF (to keep mem-access constant)
  //Only when RD  != 0
  when(io.EX.memRead && io.EX.rd =/= 0.U && (io.ID.rs1 === io.EX.rd || io.ID.rs2 === io.EX.rd)) {
    io.ID.flush := true.B
    io.IF.stall := true.B
  }

  //Branch hazard: Flush IF/ID and ID/EX registers, load the new PC
  when(io.EX.loadPC) {
    io.ID.flush := true.B
    io.IF.flush := true.B
  }
  /*
  HAZARD AVOIDANCE
  Load-use: When EX.memRead && EX.rd == (ID.rs1 || ID.rs2) && EX.rd=!=0, stall IF and ID
    ID (flush): Change all control signals to 0's (memread, memwrite etc). No need to overwrite remaining values
    IF (stall): Don't update PC being accessed

  Jump/branch: If a jump or branch is taken, flush IF/ID and ID/EX registers
    ID/EX: Clear control signals
    IF/ID: Clear output, fetch new PC

  Delayed memory load/store: When LOAD or STORE operation is performed and not ACK'd on next CC
    MEM: Stall
    EX: Stall, keep outputs constant
    ID: Stall
    IF: Stall

   Required inputs from environment:
   Load-use:
    - memRead
    - EX.rd
    - ID.rs1
    - ID.rs2

  Jump/branch:
    - Jump signal

  Delayed memory:
    - mem.ack
    - MEM.memRead, MEM.memWrite

   */
}
