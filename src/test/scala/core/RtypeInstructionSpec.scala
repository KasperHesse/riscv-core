package core

import chisel3._
import chiseltest._

import scala.collection.mutable
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable.ListBuffer

/**
 * Specification for all R-type instructions
 */
class RtypeInstructionSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  behavior of "R-type instruction"

  /**
   * Applies the given operator to the values in registers x(i-16) and x(i-15), placing the value
   * into x(i).
   * @param op
   * @param inst
   */
  def operateLast15(funct7: Int, funct3: Int, inst: ListBuffer[Instruction]): Unit = {
    for(i <- 17 until 32) {
      inst += RtypeInstruction(funct7, i-15, i-16, funct3, i, Opcode.OP)
    }
  }

  def computeResults(lb: ListBuffer[Instruction], op: (Int, Int) => Int): Array[Int] = {
    val r = Array.ofDim[Int](32)
    for(i <- 1 until 16) {
      r(i) = lb(i-1).asInstanceOf[ItypeInstruction].imm.litValue.toInt
    }
    for(i <- 17 until 32) {
      r(i) = op(r(i-16), r(i-15))
    }
    r
  }

  def regTestFun(funct7: Int, funct3: Int, op: (Int, Int) => Int) = {
    val inst = mutable.ListBuffer.empty[Instruction]
    loadFirst15(inst)
    operateLast15(funct7, funct3, inst)
    val r = computeResults(inst, op)
    test(new Core()(defaultConf)) {dut =>
      testFun(dut, iters=50, inst)
      for(i <- 0 until 32) {
        expectReg(dut, i, r(i))
      }
    }
  }


  it should "compute ADD instructions" in {
    regTestFun(Funct7.OTHERS, Funct3.ADD, _+_)
  }

  it should "compute SUB instructions" in {
    regTestFun(Funct7.SUB, Funct3.SUB, _-_)
  }

  it should "compute SLT instructions" in {
    def slt(x: Int, y: Int): Int = {
      if (x < y) 1 else 0
    }
    regTestFun(Funct7.OTHERS, Funct3.SLT, slt)
  }

  it should "compute SLTU instructions" in {
    def sltu(x: Int, y: Int): Int = {
      if((x < y) ^ (x < 0) ^ (y < 0)) 1 else 0
    }
    regTestFun(Funct7.OTHERS, Funct3.SLTU, sltu)
  }

  it should "compute XOR instructions" in {
    regTestFun(Funct7.OTHERS, Funct3.XOR, _^_)
  }

  it should "compute OR instructions" in {
    regTestFun(Funct7.OTHERS, Funct3.OR, _|_)
  }

  it should "compute AND instructions" in {
    regTestFun(Funct7.OTHERS, Funct3.AND, _&_)
  }

  it should "compute SLL instructions" in {
    regTestFun(Funct7.OTHERS, Funct3.SLL, _<<_)
  }

  it should "compute SRL instructions" in {
    def srl(v1: Int, v2: Int): Int = {
      v1 >>> (v2 & 0x1f)
    }
    regTestFun(Funct7.OTHERS, Funct3.SRL, srl)
  }

  it should "compute SRA instructions" in {
    def sra(v1: Int, v2: Int): Int = {
      v1 >> (v2 & 0x1f): Int
    }
    regTestFun(Funct7.SRA, Funct3.SRA, sra)
  }
}
