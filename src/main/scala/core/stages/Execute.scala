package core.stages

import chisel3._
import chisel3.util._
import core._
import core.csr.CSR
import core.modules.{ALU, ExecuteControllerIO, ForwardingUnit}

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
    val hzd = new ExecuteControllerIO
  })

  //MODULES
  val fwd = Module(new ForwardingUnit)
  val alu = Module(new ALU)

  //PIPELINE REGISTER
  val id = RegInit(0.U(io.id.getWidth.W).asTypeOf(io.id))
  when (!io.hzd.stall) {
    id := io.id
  } .otherwise {
    //When stalled, update values in pipeline register if they pass by on forwarding paths
    id.v1 := fwd.io.v1
    id.v2 := fwd.io.v2
  }

  //FORWARDING CONTROLS
  fwd.io.IDrs1 := id.alu.rs1
  fwd.io.IDrs2 := id.alu.rs2
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

  val beq  = eq   && id.alu.branchOp(0)
  val bne  = !eq  && id.alu.branchOp(1)
  val blt  = lt   && id.alu.branchOp(2)
  val bge  = !lt  && id.alu.branchOp(3)
  val bltu = ltu  && id.alu.branchOp(4)
  val bgeu = !ltu && id.alu.branchOp(5)

  val loadPC = (id.alu.branch && (beq | bne | blt | bge | bltu | bgeu)) | id.alu.jump
  //We always set the LSB to 0 since JAL, branches already have 0's in the LSB and JALR requires a 0
  val newPC = (Mux(id.alu.newPCsrc, v1, id.pc) + id.alu.imm)(conf.XLEN-1,1) ## 0.U(1.W)


  //ALU FOR CALCULATING REGISTER RESULTS
  alu.io.v1 := MuxLookup(id.alu.op1Src, v1)(Seq(
    (OpSrc.REG, v1),
    (OpSrc.PC, id.pc),
    (OpSrc.IMM, id.alu.imm),
    (OpSrc.CONST, 0.U(conf.XLEN.W))
  ))
  alu.io.v2 := MuxLookup(id.alu.op2Src, v2)(Seq(
    (OpSrc.REG, v2),
    (OpSrc.PC, id.pc),
    (OpSrc.IMM, id.alu.imm),
    (OpSrc.CONST, 4.U(conf.XLEN.W))
  ))
  alu.io.op := id.alu.aluOp
  val aluOut = alu.io.res

  //MEMORY MODULE CONNECTIONS
  val mask = Wire(UInt(conf.WMASKLEN.W))
  val wdata = Wire(UInt(conf.XLEN.W))
  when(id.alu.memOp === Funct3.SB.U) {
    mask := UIntToOH(aluOut(1,0))
    wdata := VecInit(Seq.fill(conf.WMASKLEN)(v2(7,0))).asUInt
  } .elsewhen(id.alu.memOp === Funct3.SH.U) {
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
  req.req := (id.alu.memWrite | id.alu.memRead) & id.alu.valid
  req.we := id.alu.memWrite
  req.wmask := mask & Fill(conf.WMASKLEN, id.alu.memWrite)
  req.wdata := wdata

  //Old memory request, to keep constant in case it is not acknowledged after 1 CC
  val oldReq = RegNext(io.mem)
  io.mem := Mux(io.hzd.stall, oldReq, req)

  //OUTPUTS
  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
//  io.memstage.res := Mux(id.alu.jump, id.pc + 4.U(conf.XLEN.W), aluOut)
  io.memstage.res := aluOut
  io.memstage.rd := id.alu.rd
  io.memstage.valid := id.alu.valid && !io.hzd.stall

  //Forward control signals to MEM stage
  io.memstage.ctrl.we := id.alu.we
  io.memstage.ctrl.memOp := id.alu.memOp
  io.memstage.ctrl.memWrite := id.alu.memWrite
  io.memstage.ctrl.memRead := id.alu.memRead

  //Control signals and hazard detection
  io.fetch.loadPC := loadPC
  io.fetch.newPC := newPC

  io.hzd.rd := id.alu.rd
  io.hzd.memRead := id.alu.memRead
  io.hzd.loadPC := loadPC
}