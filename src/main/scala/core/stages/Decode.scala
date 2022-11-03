package core.stages

import chisel3._
import chisel3.util._
import core._
import core.modules.DecodeHazardIO

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
    val wb = Input(new ForwardingPort)
    val hzd = new DecodeHazardIO
    val dbg = if(!conf.debug) None else Some(new Bundle {
      val reg = Output(Vec(32, UInt(conf.XLEN.W)))
    })
  })

  /** Pipeline register. Defaults to always sampling */
  val fetch = RegEnable(io.fetch, 0.U(io.fetch.getWidth.W).asTypeOf(io.fetch), !io.hzd.stall)
  val instr = io.fetch.instr //Instruction is sampled in fetch stage and not by this register

  //REGISTERS
  /** Register file. Register 0 is redundant but makes things easier to implement */
  val reg = RegInit(VecInit(Seq.fill(32)(0.U(conf.XLEN.W))))

  //SIGNALS
  val (op,_) =     Opcode.safe(instr(6,0))
  val rs1 =    instr(19,15)
  val rs2 =    instr(24,20)
  val rd =     instr(11,7)
  val funct7 = instr(31,25)
  val funct3 = instr(14,12)

  //MODULES
  val immGen = Module(new ImmediateGenerator)
  immGen.io.instr := instr

  //LOGIC
  //Register write logic
  //TODO: Look into the demuxing-implementation vs generating a reg-enable for each of the 31
  // registers, dependent on we=1 and rd being the correct value
  // Which uses the fewest resources?
  when(io.wb.we && io.wb.rd =/= 0.U) {
    reg(io.wb.rd) := io.wb.wdata
  }

  //we for WB stage. Disabled for BRANCH, FENCE, ECALL, EBREAK, STORE
  val we = op =/= Opcode.BRANCH && op =/= Opcode.SYSTEM && op =/= Opcode.MISC_MEM && op =/= Opcode.STORE

  //Forwarding logic for regfile read
  val v1 = Mux(io.wb.we && io.wb.rd === rs1, io.wb.wdata, reg(rs1))
  val v2 = Mux(io.wb.we && io.wb.rd === rs2, io.wb.wdata, reg(rs2))
  //OUTPUTS

  //LUI, AUIPC and JAL don't ready any instructions. Avoid sending register-values for false forwarding
  io.ex.rs1 := Mux(op === Opcode.LUI || op === Opcode.AUIPC || op === Opcode.JAL, 0.U, rs1)
  io.ex.rs2 := Mux(op === Opcode.LUI || op === Opcode.AUIPC || op === Opcode.JAL, 0.U, rs2)
  io.ex.imm := immGen.io.imm
  //In LUI, we always forward the value 0 to compute. FOR AUIPC, we use the pc. Otherwise, reg-value
  io.ex.v1 := Mux(op === Opcode.LUI, 0.U, Mux(op === Opcode.AUIPC, fetch.pc, v1))
  io.ex.v2 := v2
  io.ex.rd := rd
  io.ex.pc := fetch.pc

  //Should only set MSB for aluOp if operation is SUB, SRA or SRAI
  //SUB,SRA: When opcode=OP, funct3=(ADD;SRA) && funct7[5]
  val msb = ((op === Opcode.OP && (funct3 === Funct3.SRA.U || funct3 === Funct3.SUB.U)) |
    (op === Opcode.OP_IMM && funct3 === Funct3.SRAI.U)) && funct7(5)
  //When OP or OP_IMM, we use funct3 and one more bit to encode AluOp. Otherwise, default to adding operands
  io.ex.aluOp := Mux(op === Opcode.OP || op === Opcode.OP_IMM || op === Opcode.BRANCH, AluOp(Cat(msb, funct3)), AluOp.ADD)
  io.ex.pcNextSrc := op === Opcode.JALR

  //CONTROL SIGNALS AND HAZARD AVOIDANCE
  //To execute stage
  io.ex.ctrl.op2src := op === Opcode.OP //When OP, uses (rs1,rs2) otherwise uses (rs1,imm)
  io.ex.ctrl.branch := op === Opcode.BRANCH
  io.ex.ctrl.jump := op === Opcode.JAL || op === Opcode.JALR

  //To memory stage
  io.ex.ctrl.memWrite := op === Opcode.STORE && !io.hzd.flush
  io.ex.ctrl.memRead := op === Opcode.LOAD && !io.hzd.flush
  io.ex.ctrl.memOp := funct3

  //To writeback stage
  io.ex.ctrl.we := we && !io.hzd.flush

  io.hzd.rs1 := rs1
  io.hzd.rs2 := rs2

  //Debug ports
  if(conf.debug) {
    for (i <- 0 until 32) {
      io.dbg.get.reg(i) := reg(i)
    }
  }
}



class ImmediateGenerator(implicit conf: Config) extends Module {
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
    io.imm := Cat(Fill(32, io.instr(31)), imm)
  }
}