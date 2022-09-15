package core

import chisel3._
import chisel3.util._

import scala.None

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
    val wb = Flipped(new WritebackDecodeIO)
    val dbg = if(!conf.debug) None else Some(new Bundle {
      val reg = Output(Vec(32, UInt(conf.XLEN.W)))
    })
  })

  //REGISTERS
  /** Register file. Register 0 is redundant but makes things easier to implement */
  val reg = RegInit(VecInit(Seq.fill(32)(0.U(conf.XLEN.W))))

  //SIGNALS
  val (op,_) =     Opcode.safe(io.fetch.instr(6,0))
  val rs1 =    io.fetch.instr(19,15)
  val rs2 =    io.fetch.instr(24,20)
  val rd =     io.fetch.instr(11,7)
  val funct7 = io.fetch.instr(31,25)
  val funct3 = io.fetch.instr(14,12)

  //MODULES
  val immGen = Module(new ImmediateGenerator)
  immGen.io.instr := io.fetch.instr

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

  //OUTPUTS
  //AluOp: If opcode = OP, we can simply concat funct7
  //if opcode = OP_IMM, the uppermostbit is set if funct3 = 101
  io.ex.rs1 := rs1
  io.ex.rs2 := rs2
  io.ex.imm := immGen.io.imm
  io.ex.v1 := reg(rs1)
  io.ex.v2 := reg(rs2)
  io.ex.rd := rd
  io.ex.pc := io.fetch.pc
  io.ex.aluOp := AluOp(Cat(Mux(op === Opcode.OP, funct7(5), Mux(op === Opcode.OP_IMM, funct3 === Funct3.SRA.U, 0.U)), funct3))
  io.ex.ctrl.we := we
  io.ex.ctrl.op2src := op === Opcode.OP //When OP, uses (rs1,rs2) otherwise uses (rs1,imm)
  io.ex.ctrl.branch := op === Opcode.BRANCH

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
    io.imm := Cat(-1.S(32.W).asUInt, imm)
  }
}