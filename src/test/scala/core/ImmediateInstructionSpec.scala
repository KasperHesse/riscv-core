package core

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable.ListBuffer

class ImmediateInstructionSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "I-type instruction"

  implicit val conf: Config = defaultConf

  /**
   * Generates random instruction that use an immediate and the value in reg (i-16) to generate a result
   * @param lb The current buffer of instruction
   * @param funct3 The funct3-field of the OP_IMM instruction used to operate on the values in the buffer
   * @return
   */
  def operateLast15(lb: ListBuffer[Instruction], funct3: Int): Unit = {
    val MAX_12BIT = math.pow(2,12).toInt
    def r = scala.util.Random.nextInt(MAX_12BIT) - MAX_12BIT/2
    for(i <- 17 until 32) {
      lb += ItypeInstruction(r, i-16, i, funct3, Opcode.OP_IMM)
    }
  }


  def computeResults(lb: ListBuffer[Instruction], op: (Int, Int) => Int): Array[Int] = {
    val r = Array.ofDim[Int](32)
    for(i <- 1 until 16) {
      r(i) = lb(i-1).asInstanceOf[ItypeInstruction].imm.litValue.toInt
    }
    for(i <- 17 until 32) {
      r(i) = op(r(i-16), lb(i-2).asInstanceOf[ItypeInstruction].imm.litValue.toInt)
    }
    r
  }

  def immTestFun(funct3: Int, op: (Int, Int) => Int): Unit = {
    val inst = ListBuffer.empty[Instruction]
    loadFirst15(inst)
    operateLast15(inst, funct3)
    val r = computeResults(inst, op)
    test(new Core()).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 32) {
        expectReg(dut, i, r(i))
      }
    }
  }

  it should "compute ADDI instructions" in {
    immTestFun(Funct3.ADDI, _+_)
  }

  it should "compute SLTI instructions" in {
    def slti(x: Int, y: Int): Int = {
      if (x < y) 1 else 0
    }
    immTestFun(Funct3.SLTI, slti)
  }

  it should "compute SLTIU instructions" in {
    def sltiu(x: Int, y: Int): Int = {
      if((x < y) ^ (x < 0) ^ (y < 0)) 1 else 0
    }
    immTestFun(Funct3.SLTIU, sltiu)
  }

  it should "compute XORI instructions" in {
    immTestFun(Funct3.XORI, _^_)
  }

  it should "compute ORI instructions" in {
    immTestFun(Funct3.ORI, _|_)
  }

  it should "compute ANDI instructions" in {
    immTestFun(Funct3.ANDI, _&_)
  }

  it should "compute SLLI instructions" in {
    val inst = ListBuffer.empty[Instruction]
    loadFirst15(inst)
    for(i <- 17 until 32) { //Must manually do this, as all immediates must be in range [0:31]
      inst += ItypeInstruction(scala.util.Random.nextInt(32), i-16, i, Funct3.SLLI, Opcode.OP_IMM)
    }
    val r = computeResults(inst, _<<_)
    test(new Core()) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 32) {
        expectReg(dut, i, r(i))
      }
    }
  }

  it should "compute SRLI instructions" in {
    val inst = ListBuffer.empty[Instruction]
    loadFirst15(inst)
    for(i <- 17 until 32) { //Must manually do this, as all immediates must be in range [0:31]
      inst += ItypeInstruction(scala.util.Random.nextInt(32), i-16, i, Funct3.SRLI, Opcode.OP_IMM)
    }
    val r = computeResults(inst, _>>>_)
    test(new Core()) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 32) {
        expectReg(dut, i, r(i))
      }
    }
  }

  it should "compute SRAI instructions" in {
    val inst = ListBuffer.empty[Instruction]
    loadFirst15(inst)
    for(i <- 17 until 32) { //Must manually do this, as all immediates must be in range [0:31]
      //Note that we're OR'ing with 0x400 to set the "Funct7"-field correctly
      inst += ItypeInstruction(scala.util.Random.nextInt(32) | 0x400, i-16, i, Funct3.SRAI, Opcode.OP_IMM)
    }
    val r = computeResults(inst, _>>_)
    test(new Core()) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 32) {
        expectReg(dut, i, r(i))
      }
    }
  }

  it should "perform forwarding from MEM to EX" in {
    val instrs = ListBuffer.empty[Instruction]
    instrs += ItypeInstruction(5, 0, 1, Funct3.ADDI, Opcode.OP_IMM)
    instrs += ItypeInstruction(5, 1, 2, Funct3.ADDI, Opcode.OP_IMM)
    test(new Core()) { dut =>
      testFun(dut, 50, instrs)
      expectReg(dut, 1, 5)
      expectReg(dut, 2, 10)
    }
  }

  it should "perform forwarding from WB to EX" in {
    val instrs = ListBuffer.empty[Instruction]
    instrs += ItypeInstruction(5, 0, 1, Funct3.ADDI, Opcode.OP_IMM) //addi x1, x0, 5
    instrs += ItypeInstruction(5, 0, 2, Funct3.ADDI, Opcode.OP_IMM) //addi x2, x0, 5
    instrs += ItypeInstruction(5, 1, 3, Funct3.ADDI, Opcode.OP_IMM) //addi x3, x1, 5
    test(new Core()) { dut =>
      testFun(dut, 50, instrs)
      expectReg(dut, 1, 5)
      expectReg(dut, 2, 5)
      expectReg(dut, 3, 10)
    }
  }

  it should "forward multiple values in a row" in {
    val instrs = ListBuffer.empty[Instruction]
    instrs += ItypeInstruction(5, 0, 1, Funct3.ADDI, Opcode.OP_IMM) //addi x1, x0, 5
    instrs += ItypeInstruction(5, 1, 2, Funct3.ADDI, Opcode.OP_IMM) //addi x2, x1, 5
    instrs += ItypeInstruction(5, 2, 3, Funct3.ADDI, Opcode.OP_IMM) //addi x3, x2, 5
    instrs += ItypeInstruction(20, 3, 4, Funct3.ADDI, Opcode.OP_IMM) //addi x4, x3, 20
    test(new Core()) { dut =>
      testFun(dut, 50, instrs)
      expectReg(dut, 1, 5)
      expectReg(dut, 2, 10)
      expectReg(dut, 3, 15)
      expectReg(dut, 4, 35)
    }
  }
  behavior of "U-type instruction"

  it should "compute AUIPC instruction" in {
    val inst = ListBuffer.empty[Instruction]
    val ui = Seq.fill(30)(scala.util.Random.nextInt() << 12)
    for(i <- 0 until 30) {
      inst += UtypeInstruction(ui(i), i+1, Opcode.AUIPC)
    }
    test(new Core()) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 30) {
        expectReg(dut, i+1, ui(i) + 4*i)
      }
    }
  }

  it should "compute LUI instructions" in {
    val inst = ListBuffer.empty[Instruction]
    val ui = Seq.fill(30)(scala.util.Random.nextInt() << 12)
    for(i <- 0 until 30) {
      inst += UtypeInstruction(ui(i), i+1, Opcode.LUI)
    }
    test(new Core()) {dut =>
      testFun(dut, 50, inst)
      for(i <- 0 until 30) {
        expectReg(dut, i+1, ui(i))
      }
    }
  }
}
