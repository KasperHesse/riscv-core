package core

import chisel3._

class ForwardingUnit(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    /** RS1-value from ID stage */
    val IDrs1 = Input(UInt(5.W))
    /** rs2-value from ID stage */
    val IDrs2 = Input(UInt(5.W))
    /** Value fetched from register rs1 */
    val IDv1 = Input(UInt(conf.XLEN.W))
    /** Value fetched from register rs2 */
    val IDv2 = Input(UInt(conf.XLEN.W))
    /** Inputs from MEM stage */
    val mem = Input(new ForwardingPort())
    /** Inputs from WB stage */
    val wb = Input(new ForwardingPort())
    /** rs1-value after forwarding */
    val v1 = Output(UInt(conf.XLEN.W))
    /** rs2-value after forwarding */
    val v2 = Output(UInt(conf.XLEN.W))
  })

  //Prioritize values from MEM over WB, if they are present
  //Don't forward values if x0 is used, as that breaks the constant-zero aspect

  //These constants aren't strictly necessary, but they make debugging with waveforms more convenient
  private val fwdMemRs1: Bool = io.IDrs1 === io.mem.rd && io.mem.we && io.IDrs1 =/= 0.U
  private val fwdWbRs1: Bool = io.IDrs1 === io.wb.rd && io.wb.we && io.IDrs1 =/= 0.U
  when(fwdMemRs1) {
    io.v1 := io.mem.wdata
  } .elsewhen(fwdWbRs1) {
    io.v1 := io.wb.wdata
  } .otherwise {
    io.v1 := io.IDv1
  }

  private val fwdMemRs2: Bool = io.IDrs2 === io.mem.rd && io.mem.we && io.IDrs2 =/= 0.U
  private val fwdWbRs2: Bool = io.IDrs2 === io.wb.rd && io.wb.we && io.IDrs2 =/= 0.U
  when(fwdMemRs2) {
    io.v2 := io.mem.wdata
  } .elsewhen(fwdWbRs2) {
    io.v2 := io.wb.wdata
  } .otherwise {
    io.v2 := io.IDv2
  }
}
