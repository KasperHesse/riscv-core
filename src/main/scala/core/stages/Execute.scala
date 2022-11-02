package core.stages

import chisel3._
import chisel3.util._
import core._
import core.modules.{ALU, ExecuteHazardIO, ForwardingUnit}

class Execute(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    /** Inputs from ID stage */
    val id = Flipped(new DecodeExecuteIO)
    /** Outputs to MEM stage */
    val memstage = new ExecuteMemoryIO
    /** Output to memory module, initiating a memory action if required */
    val mem = Output(new MemoryRequest)
    /** Values forwarded from MEM stage */
    val memFwd = Input(new ForwardingPort)
    /** Values forwarded from WB stage */
    val wbFwd = Input(new ForwardingPort)
    /** Control signals to fetch stage */
    val fetch = new Bundle {
      val loadPC = Output(Bool())
      val newPC = Output(UInt(conf.XLEN.W))
    }
    /** Connections to hazard detection module */
    val hzd = new ExecuteHazardIO
  })

  val id = RegEnable(io.id, 0.U(io.id.getWidth.W).asTypeOf(io.id), !io.hzd.stall)
  val fwd = Module(new ForwardingUnit)

  fwd.io.IDrs1 := id.rs1
  fwd.io.IDrs2 := id.rs2
  fwd.io.IDv1 := id.v1
  fwd.io.IDv2 := id.v2
  fwd.io.mem := io.memFwd
  fwd.io.wb := io.wbFwd

  val v1 = fwd.io.v1
  val v2 = fwd.io.v2

  //BRANCH EVALUATION LOGIC
  val eq = v1 === v2
  val lt = v1.asSInt < v2.asSInt
  val ltu = v1 < v2

  val beq = eq && (id.aluOp.asUInt(2,0) === 0.U(3.W))
  val bne = !eq && (id.aluOp.asUInt(2,0) === 1.U(3.W))
  val blt = lt && (id.aluOp.asUInt(2,0) === 4.U(3.W))
  val bge = !lt && (id.aluOp.asUInt(2,0) === 5.U(3.W))
  val bltu =ltu && (id.aluOp.asUInt(2,0) === 6.U(3.W))
  val bgeu = !ltu && (id.aluOp.asUInt(2,0) === 7.U(3.W))

  val loadPC = (id.ctrl.branch && (beq | bne | blt | bge | bltu | bgeu)) | id.ctrl.jump
  //We always set the LSB to 0 since JAL, branches already have 0's in the LSB and JALR requires a 0
  val newPC = Cat((Mux(id.pcNextSrc, v1, id.pc) + id.imm)(conf.XLEN-1,1), 0.U(1.W))


  //ALU FOR CALCULATING REGISTER RESULTS
  //TODO increase bitwidth of op2src to 2, make op1src a signal as well
  // Will allow us to easier implement JAL and JALR instructions
  val alu = Module(new ALU)
  alu.io.v1 := v1
  alu.io.v2 := Mux(id.ctrl.op2src, v2, id.imm)
  alu.io.op := id.aluOp
  val aluOut = alu.io.res

  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
  io.memstage.res := Mux(id.ctrl.jump, id.pc + 4.U(conf.XLEN.W), aluOut)
  io.memstage.rd := id.rd

  //MEMORY MODULE CONNECTIONS
  //TODO: Need a way of keeping req high in this stage, if ack isn't signaled in MEM stage
  //Perhaps a register storing old values, using that as output in case a control signal is asserted?

  val mask = Wire(Vec(conf.XLEN/8, Bool()))
  when(id.ctrl.memOp === Funct3.SB.U) {
    mask := VecInit(UIntToOH(aluOut(1,0)).asBools)
    io.mem.wdata := VecInit(Seq.fill(conf.WMASKLEN)(v2(7,0))).asUInt
  } .elsewhen(id.ctrl.memOp === Funct3.SH.U) {
    mask := VecInit(Mux(aluOut(1), "b1100".U, "b0011".U).asBools)
    io.mem.wdata := VecInit(Seq.fill(conf.WMASKLEN/2)(v2(15,0))).asUInt
  } .otherwise { //SW
    mask := VecInit("b1111".U.asBools)
    io.mem.wdata := v2
  }
  //Processing reads
  //Use an rmask? And then forward the rmask to the mem-stage for processing?
  //mask can be generated independently of
  io.mem.addr := aluOut(conf.XLEN-1,2) ## 0.U(2.W) //Must zero out 2 LSB of memory access to use wmask correctly
  io.mem.req := id.ctrl.memWrite | id.ctrl.memRead
  io.mem.we := id.ctrl.memWrite
  io.mem.wmask := mask.map(_ & id.ctrl.memWrite)

  //Forward control signals to MEM stage
  io.memstage.ctrl.we := id.ctrl.we
  io.memstage.ctrl.memOp := id.ctrl.memOp
  io.memstage.ctrl.memWrite := id.ctrl.memWrite
  io.memstage.ctrl.memRead := id.ctrl.memRead

  //Control signals and hazard detection
  io.fetch.loadPC := loadPC
  io.fetch.newPC := newPC

  io.hzd.rd := id.rd
  io.hzd.memRead := id.ctrl.memRead
  io.hzd.loadPC := loadPC
}