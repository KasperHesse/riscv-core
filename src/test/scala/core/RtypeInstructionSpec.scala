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


  it should "calculate ADD correctly" in {
    test(new Core()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val instrs = mutable.Map[Int, Instruction]()
      instrs.update(0, ItypeInstruction(4, 0, 1, Funct3.ADDI, Opcode.OP_IMM))
      instrs.update(4, ItypeInstruction(8, 0, 2, Funct3.ADDI, Opcode.OP_IMM))
      instrs.update(8, RtypeInstruction(Funct7.OTHERS, 1, 2, Funct3.ADD, 3, Opcode.OP))

      testFun(dut, 20, Map.from(instrs))

      dut.io.dbg.get.reg(1).expect(4)
      dut.io.dbg.get.reg(2).expect(8)
      dut.io.dbg.get.reg(3).expect(12)
    }
  }

  it should "calculate AND correctly" in {
    test(new Core()(defaultConf)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val instrs = mutable.Map[Int, Instruction]()
      instrs.update(0, ItypeInstruction(0x555, 0, 1, Funct3.ADDI, Opcode.OP_IMM))
      instrs.update(4, ItypeInstruction(0x707, 0, 2, Funct3.ADDI, Opcode.OP_IMM))
      instrs.update(8, RtypeInstruction(Funct7.OTHERS, 1, 2, Funct3.AND, 3, Opcode.OP))

      testFun(dut, 20, Map.from(instrs))

      dut.io.dbg.get.reg(1).expect(0x555)
      dut.io.dbg.get.reg(2).expect(0x707)
      dut.io.dbg.get.reg(3).expect(0x505)
    }
  }

  it should "calculate SUB correctly" in {
    val inst = ListBuffer.empty[Instruction]
    inst += ItypeInstruction(4, 0, 1, Funct3.ADDI, Opcode.OP_IMM)
    inst += ItypeInstruction(8, 0, 2, Funct3.ADDI, Opcode.OP_IMM)
    inst += RtypeInstruction(Funct7.SUB, 2, 1, Funct3.SUB, 3, Opcode.OP)

    test(new Core()(defaultConf)) {dut =>
      testFun(dut, 20, inst)
      expectReg(dut, 1, 4)
      expectReg(dut, 2, 8)
      expectReg(dut, 3, -4)
    }
  }
}
