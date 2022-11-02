package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.collection.mutable

class ControlTransferSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "control transfer instructions"

  implicit val conf: Config = defaultConf

  it should "perform a JAL" in {
    val instrs = mutable.Map.empty[Int, Instruction]
    instrs.addOne((0, ItypeInstruction(15, 0, 2, Funct3.ADDI, Opcode.OP_IMM)))
    instrs.addOne((4, JtypeInstruction(1000, 1, Opcode.JAL)))
    instrs += ((8, ItypeInstruction(30, 0, 4, Funct3.ADDI, Opcode.OP_IMM))) //Should not be executed
    instrs += ((1004, ItypeInstruction(40, 0, 3, Funct3.ADDI, Opcode.OP_IMM)))
    test(new Core) {dut =>
      testFun(dut, 30, instrs.toMap)
      expectReg(dut, 2, 15)
      expectReg(dut, 1, 8)
      expectReg(dut, 3, 40)
      expectReg(dut, 4, 0)
    }
  }

  it should "perform a JALR" in {
    val instrs = mutable.Map.empty[Int, Instruction]
    instrs += ((0, ItypeInstruction(15, 0, 2, Funct3.ADDI, Opcode.OP_IMM)))
    instrs += ((4, ItypeInstruction(102, 2, 1, Funct3.ZERO, Opcode.JALR)))
    instrs += ((8, ItypeInstruction(30, 0, 3, Funct3.ADDI, Opcode.OP_IMM)))
    instrs += ((116, ItypeInstruction(40, 0, 3, Funct3.ADDI, Opcode.OP_IMM)))
    test(new Core()(defaultConf)) {dut =>
      testFun(dut, 15, instrs.toMap)
      expectReg(dut, 1, 8)
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 40)
    }
  }

  it should "perform a BEQ" in {
    val instrs = mutable.ListBuffer.empty[Instruction]
    instrs += ItypeInstruction(15, 0, 2, Funct3.ADDI, Opcode.OP_IMM) //0
    instrs += BtypeInstruction(12, 2, 2, Funct3.BEQ, Opcode.BRANCH)  //4
    instrs += ItypeInstruction(1, 0, 4, Funct3.ADDI, Opcode.OP_IMM)  //8, should not be executed
    instrs += JtypeInstruction(8, 4, Opcode.JAL)                     //12, jump to 20
    instrs += ItypeInstruction(2, 0, 3, Funct3.ADDI, Opcode.OP_IMM)  //16
    test(new Core()(defaultConf)) {dut =>
      testFun(dut, 20, instrs)
      expectReg(dut, 2, 15)
      expectReg(dut, 3, 2)
      expectReg(dut, 4, 0)
    }
  }
  //TODO More randomized tests here
}
