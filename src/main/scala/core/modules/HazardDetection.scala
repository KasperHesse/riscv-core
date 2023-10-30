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
  val memOp = Output(Bool())
  val valid = Output(Bool())
  val ack = Output(Bool())
  val stall = Input(Bool())
}

class CSRHazardIO extends Bundle {
  val stall = Input(Bool())
  val valid = Output(Bool())
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
    val CSR = Flipped(new CSRHazardIO)
  })
  //All outputs default to false
  io.IF.flush :=  false.B
  io.IF.stall :=  false.B
  io.ID.flush :=  false.B
  io.ID.stall :=  false.B
  io.EX.stall :=  false.B
  io.MEM.stall := false.B
  io.CSR.stall := false.B

  //Delayed memory response
  //If a memory operation is not ACK'd on cycle after issuing it, IF, ID, EX and MEM stages must be stalled.
  //Must be kept stalled until ACK arrives
  when (io.MEM.memOp && !io.MEM.ack) {
    io.IF.stall := true.B
    io.ID.stall := true.B
    io.EX.stall := true.B
    io.MEM.stall := true.B
  }

  //Load-use hazard
  //Since we don't forward result from memory stage,
  // we stall ID (to make it invalid) and stall IF (to keep mem-access constant)
  //Only when RD  != 0
  when(io.EX.memRead && io.EX.rd =/= 0.U && (io.ID.rs1 === io.EX.rd || io.ID.rs2 === io.EX.rd)) {
    io.ID.stall := true.B //Was io.ID.flush
    io.IF.stall := true.B
  }

  //Branch hazard: Flush IF/ID and ID/EX registers, load the new PC
  when(io.EX.loadPC) {
    io.ID.flush := true.B
    io.IF.flush := true.B
  }

  //CSR instruction: When CSR instruction, we stall IF and ID until MEM, WB are invalid.
  //No inputs from WB, so we use RegNext of mem.valid instead
  when(io.CSR.valid && (io.MEM.valid || RegNext(io.MEM.valid))) {
    io.IF.stall := true.B
    io.ID.stall := true.B
    io.CSR.stall := true.B
  }
  //No forwarding from CSR pipe, so we need to stall one additional CC after executing instruction,
  //such that potential forwarding from WB to EX can happen
  when(RegNext(io.CSR.valid)) {
    io.IF.stall := true.B
    io.ID.stall := true.B
  }
}
