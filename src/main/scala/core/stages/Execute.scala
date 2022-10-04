package core.stages

import chisel3._
import chisel3.util._
import core._
import core.modules.{ALU, ForwardingUnit}

class Execute(implicit conf: Config) extends PipelineStage {
  val io = IO(new Bundle {
    /** Inputs from ID stage */
    val id = Flipped(new DecodeExecuteIO)
    /** Outputs to MEM stage */
    val memstage = new ExecuteMemoryIO
    /** Output to memory module, initiating a memory action if required */
    val mem = Output(new MemoryDriverInterface)
    /** Values forwarded from MEM stage */
    val memFwd = Input(new ForwardingPort)
    /** Values forwarded from WB stage */
    val wbFwd = Input(new ForwardingPort)
    /** Control signal master bundle */
    val ctrl = new Bundle {
      val fetch = new Bundle {
        val loadPC = Output(Bool())
        val newPC = Output(UInt(conf.XLEN.W))
      }
    }
  })

  val id = RegEnable(io.id, true.B)
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
  val alu = Module(new ALU)
  //TODO increase bitwidth of op2src to 2, make op1src a signal as well
  val op1 = v1
  val op2 = Mux(id.ctrl.op2src, v2, id.imm)
  alu.io.v1 := op1
  alu.io.v2 := op2
  alu.io.op := id.aluOp
  val aluOut = alu.io.res

  //JAL and JALR require that PC+4 is written to regfile.
  //AUIPC requires that we add imm to PC
  //LUI requires that we add imm to 0
  io.memstage.res := Mux(id.ctrl.jump, id.pc + 4.U(conf.XLEN.W), aluOut)
  io.memstage.rd := id.rd

  //Serve requests to memory module
  //TODO: Need a way of keeping req high in this stage, if ack isn't signaled in MEM stage
  //Perhaps a register storing old values, using that as output in case a control signal is asserted?
  val wdata = Wire(UInt(conf.XLEN.W))
  //TODO use a byte-enable / strobe signal instead (wishbone??)
  when(id.ctrl.memOp === Funct3.SB.U) {
    wdata := 0.U((conf.XLEN-8).W) ## v2(7,0)
  } .elsewhen(id.ctrl.memOp === Funct3.SH.U) {
    wdata := 0.U((conf.XLEN-16).W) ## v2(15,0)
  } .otherwise {
    wdata := v2
  }
  io.mem.wdata := wdata
  io.mem.addr := aluOut
  io.mem.req := id.ctrl.memWrite | id.ctrl.memRead
  io.mem.we := id.ctrl.memWrite

  //Forward control signals to MEM stage
  io.memstage.ctrl.we := id.ctrl.we
  io.memstage.ctrl.memOp := id.ctrl.memOp
  io.memstage.ctrl.memWrite := id.ctrl.memWrite
  io.memstage.ctrl.memRead := id.ctrl.memRead

  io.ctrl.fetch.loadPC := loadPC
  io.ctrl.fetch.newPC := newPC
}