import chisel3._
import chiseltest._
import scala.collection.mutable
package object core {

  val defaultConf = Config(debug=true)
  /**
   * Acts as a memory module connected to the instruction memory, serving instructions when requested.
   * Always returns a result on the same clock cycle as the request is raised.
   * If an address is requested which does not map into the instruction mapping given, a NOP(addi x0, x0, x0)
   * is returned
   * @param instrs A mapping from addresses [byte-aligned] to the instructions they represent
   * @param clock The clock of the DUT
   * @param imem The memory interface that this should drive instructions onto
   */
  def driveInstructionMemory(instrs: Map[Int, Instruction], clock: Clock, imem: MemoryInterface): Unit = {
    imem.ack.poke(false.B)
    while(!imem.req.peekBoolean()) {
      clock.step()
    }
    timescope {
      val addr = imem.addr.peekInt
      val nop = ItypeInstruction(0, 0, 0, Funct3.ADDI, Opcode.OP_IMM)
      imem.data.poke(instrs.getOrElse(addr.toInt, nop).toUInt)
      imem.ack.poke(true.B)
      clock.step()
    }
  }

  def testFun(dut: Core, iters: Int, instrs: Map[Int, Instruction]): Unit = {
    dut.clock.setTimeout(iters)
    fork {
      for(_ <- 0 until iters) {
        driveInstructionMemory(instrs, dut.clock, dut.io.imem)
      }
    } .fork {
      dut.clock.step() //Do nothing by default
    } .join()
  }

  def testFun(dut: Core, iters: Int, inst: mutable.ListBuffer[Instruction]): Unit = {
    val  k= inst.zipWithIndex.map(x => (x._2*4, x._1)).toMap
    testFun(dut, iters, k)
  }

  def expectReg(dut: Core, i: Int, v: Int): Unit = {
    val r = dut.io.dbg.get.reg(i)
    val x = if (v < 0) math.pow(2, r.getWidth).toLong+v else v
    r.expect(x)
  }
}
