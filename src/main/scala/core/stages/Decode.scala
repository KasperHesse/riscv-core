package core.stages

import chisel3._
import chisel3.util._
import core._
import core.csr.{CSRDecode, CSRInputs}
import core.modules.{DecodeHazardIO, IDecode}

/**
 * The Decode stage of the Risc-V processor. Contains the register file and immediate generation logic
 * When flushed, converts the outgoing instruction to a NOP instead (rs1=0, rs2=0, op=addi, imm=0)
 *
 * @param conf
 */
class Decode(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    val fetch = Flipped(new FetchDecodeIO)
    val ex = new DecodeExecuteIO
    val csr = Output(new CSRInputs)
    val wb = Input(new ForwardingPort)
    val hzd = new DecodeHazardIO
    val dbg = if(!conf.debug) None else Some(new Bundle {
      val reg = Output(Vec(32, UInt(conf.XLEN.W)))
    })
  })

  /** Pipeline register */
  val fetch = RegEnable(io.fetch, 0.U(io.fetch.getWidth.W).asTypeOf(io.fetch), !io.hzd.stall)
  val instr = fetch.instr

  //INSTRUCTION DECODING
  val Idecode = Module(new IDecode)
  val CSRdecode = Module(new CSRDecode)
  Idecode.io.instr := instr
  CSRdecode.io.instr := instr

  val rs1 = WireDefault(Idecode.io.out.rs1)
  val rs2 = WireDefault(Idecode.io.out.rs2)
  val rd = WireDefault(Idecode.io.out.rd)
  //Override register-selects when CSRDecode is valid
  when(CSRdecode.io.out.valid) {
    rs1 := CSRdecode.io.out.rs1
    rd := CSRdecode.io.out.rd
  }

  //REGISTER FILE READ LOGIC
  /** Register file. Register 0 is redundant but makes things easier to implement */
  val reg = RegInit(VecInit(Seq.fill(32)(0.U(conf.XLEN.W))))

  //Register write logic
  //TODO: Look into the demuxing-implementation vs generating a reg-enable for each of the 31
  // registers, dependent on we=1 and rd being the correct value
  // Which uses the fewest resources?
  when(io.wb.we && io.wb.rd =/= 0.U) {
    reg(io.wb.rd) := io.wb.wdata
  }

  //Forwarding logic for regfile read
  val v1 = Mux(io.wb.we && io.wb.rd === rs1 && io.wb.rd =/= 0.U, io.wb.wdata, reg(rs1))
  val v2 = Mux(io.wb.we && io.wb.rd === rs2 && io.wb.rd =/= 0.U, io.wb.wdata, reg(rs2))

  //CONTROL SIGNALS AND HAZARD AVOIDANCE
  val valid = fetch.valid && !io.hzd.flush && !io.hzd.stall

  //Outputs
  io.ex.alu := Idecode.io.out
  io.ex.v1 := v1
  io.ex.v2 := v2
  io.ex.pc := fetch.pc

  io.csr := CSRdecode.io.out
  io.csr.rs1Val := v1

  //When invalidated, take all control signals low
  when(!valid) {
    //ALU control signals
    io.ex.alu.valid := false.B
    io.ex.alu.memWrite := false.B
    io.ex.alu.memRead := false.B
    io.ex.alu.branch := false.B
    io.ex.alu.jump := false.B
    io.ex.alu.we := false.B

    //CSR control signals
    io.csr.valid := false.B
  }

  io.hzd.rs1 := rs1
  io.hzd.rs2 := rs2

  //Debug ports
  if(conf.debug) {
    for (i <- 0 until 32) {
      io.dbg.get.reg(i) := reg(i)
    }
  }
  /*
  Failed test
  SimpleProgramsSpec should compute sum of 1..100
  R-type instruction should compute ADD/SRL instruction
  ImemSpec should function when ack is delayed
  DmemSpec should handle delayed memory responses
   */
//
//  //OUTPUTS
//  //LUI, AUIPC and JAL don't ready any instructions. Avoid sending register-values for false forwarding
//  io.ex.rs1 := Mux(op === Opcode.LUI || op === Opcode.AUIPC || op === Opcode.JAL, 0.U, rs1)
//  io.ex.rs2 := Mux(op === Opcode.LUI || op === Opcode.AUIPC || op === Opcode.JAL, 0.U, rs2)
//  io.ex.imm := immGen.io.imm
//  //In LUI, we always forward the value 0 to compute. FOR AUIPC, we use the pc. Otherwise, reg-value
//  io.ex.v1 := Mux(op === Opcode.LUI, 0.U, Mux(op === Opcode.AUIPC, fetch.pc, v1))
//  io.ex.v2 := v2
//  io.ex.rd := rd
//  io.ex.pc := fetch.pc
//
//  //Should only set MSB for aluOp if operation is SUB, SRA or SRAI
//  //SUB,SRA: When opcode=OP, funct3=(ADD;SRA) && funct7[5]
//  val msb = ((op === Opcode.OP && (funct3 === Funct3.SRA.U || funct3 === Funct3.SUB.U)) |
//    (op === Opcode.OP_IMM && funct3 === Funct3.SRAI.U)) && funct7(5)
//  //When OP or OP_IMM, we use funct3 and one more bit to encode AluOp. Otherwise, default to adding operands
//  io.ex.aluOp := Mux(op === Opcode.OP || op === Opcode.OP_IMM || op === Opcode.BRANCH, AluOp(Cat(msb, funct3)), AluOp.ADD)
//  io.ex.pcNextSrc := op === Opcode.JALR
//
//  //CONTROL SIGNALS AND HAZARD AVOIDANCE
//  val valid = fetch.valid && !io.hzd.flush && !io.hzd.stall
//  //All control signals are AND with id.valid to ensure nothing bad happens (functionally a NOP)
//  //To execute stage
//  io.ex.valid := valid
//  io.ex.ctrl.op2src := op === Opcode.OP && valid //When OP, uses (rs1,rs2) otherwise uses (rs1,imm)
//  io.ex.ctrl.branch := op === Opcode.BRANCH && valid
//  io.ex.ctrl.jump := (op === Opcode.JAL || op === Opcode.JALR) && valid
//
//  //To memory stage
//  io.ex.ctrl.memWrite := op === Opcode.STORE && valid
//  io.ex.ctrl.memRead := op === Opcode.LOAD && valid
//  io.ex.ctrl.memOp := funct3 & Fill(funct3.getWidth, valid)
//
//  //To writeback stage
//  io.ex.ctrl.we := we && valid


}

/**
 * Immediate decoder for RV32I-instruction subset
 * @param conf
 */
class ImmediateDecoder(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val instr = Input(UInt(32.W))
    val imm = Output(UInt(conf.XLEN.W))
  })
  //If I-type or S-type, immediate is 12-bit encoded and requires 20-bit extension
  val sext = VecInit(Seq.fill(conf.XLEN-12)(io.instr(31))).asUInt //19 = 32-12-1
  val immI = Cat(sext(19,0), io.instr(31,20))
  val immS = Cat(sext(19,0), io.instr(31,25), io.instr(11,7))
  val immB = Cat(sext(18,0), io.instr(31), io.instr(7), io.instr(30,25), io.instr(11,8), 0.U(1.W))
  val immJ = Cat(sext(10,0), io.instr(31), io.instr(19,12), io.instr(20), io.instr(30,21), 0.U(1.W))
  val immU = Cat(io.instr(31,12), 0.U(12.W))

  val imm = WireDefault(immI)
  val (op, _) = Opcode.safe(io.instr(6,0))
  switch(op) {
    is(Opcode.STORE) {
      imm := immS
    }
    is(Opcode.BRANCH) {
      imm := immB
    }
    is(Opcode.LUI, Opcode.AUIPC) {
      imm := immU
    }
    is(Opcode.JAL) {
      imm := immJ
    }
    //Defaults to immI
  }

  //If using XLEN=64, we add another 32-bit sign-extension to the output
  if(conf.XLEN == 32) {
    io.imm := imm
  } else {
    io.imm := Fill(32, io.instr(31)) ## imm
  }
}