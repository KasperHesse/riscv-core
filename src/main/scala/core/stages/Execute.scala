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

  /** Pipeline register */
  val id = RegEnable(io.id, 0.U(io.id.getWidth.W).asTypeOf(io.id), !io.hzd.stall)

  //MODULES
  val alu = Module(new ALU)
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
  alu.io.v1 := v1
  alu.io.v2 := Mux(id.ctrl.op2src, v2, id.imm)
  alu.io.op := id.aluOp
  val aluOut = alu.io.res

  //MEMORY MODULE CONNECTIONS
  val mask = Wire(UInt(conf.WMASKLEN.W))
  val wdata = Wire(UInt(conf.XLEN.W))
  when(id.ctrl.memOp === Funct3.SB.U) {
    mask := UIntToOH(aluOut(1,0))
    wdata := VecInit(Seq.fill(conf.WMASKLEN)(v2(7,0))).asUInt
  } .elsewhen(id.ctrl.memOp === Funct3.SH.U) {
    mask := Mux(aluOut(1), "b1100".U(4.W), "b0011".U(4.W))
    wdata := VecInit(Seq.fill(conf.WMASKLEN/2)(v2(15,0))).asUInt
  } .otherwise { //SW
    mask := "b1111".U(conf.WMASKLEN.W)
    wdata := v2
  }
  //Processing reads
  //Use an rmask? And then forward the rmask to the mem-stage for processing?
  //mask can be generated independently of

  //Memory request information
  val req = Wire(new MemoryRequest)
  req.addr := aluOut(conf.XLEN-1,2) ## 0.U(2.W) //Must zero out 2 LSB of memory access to use wmask correctly
  req.req := id.ctrl.memWrite | id.ctrl.memRead
  req.we := id.ctrl.memWrite
  req.wmask := mask & Fill(conf.WMASKLEN, id.ctrl.memWrite)
  req.wdata := wdata

  //Old memory request, to keep constant in case it is not acknowledged after 1 CC
  val oldReq = RegNext(io.mem)
  io.mem := Mux(io.hzd.stall, oldReq, req)


  //OUTPUTS
  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
  io.memstage.res := Mux(id.ctrl.jump, id.pc + 4.U(conf.XLEN.W), aluOut)
  io.memstage.rd := id.rd
  io.memstage.valid := id.valid && !io.hzd.stall

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