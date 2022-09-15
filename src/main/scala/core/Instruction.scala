package core

import chisel3._
import chisel3.experimental.BundleLiterals._
import core.Opcode

trait Instruction {
  def toUInt: UInt
}

class RtypeInstruction extends Bundle with Instruction {
  val funct7 = UInt(7.W)
  val rs2 = UInt(5.W)
  val rs1 = UInt(5.W)
  val funct3 = UInt(3.W)
  val rd = UInt(5.W)
  val op = Opcode()

  def toUInt: UInt = {
    RtypeInstruction.apply(this)
  }
}

object RtypeInstruction {
  /**
   * Converts an Rtype instruction to its UInt representation
   * @param inst
   * @return
   */
  def apply(inst: RtypeInstruction): UInt = {
    var r: Long = 0
    r = inst.litValue.toLong
    r.U(32.W)
  }

  /**
   * Generates a new Rtype instruction with the given fields
   * @param funct7
   * @param rs2
   * @param rs1
   * @param funct3
   * @param op
   * @return
   */
  def apply(funct7: Int, rs2: Int, rs1: Int, funct3: Int, rd: Int, op: Opcode.Type): RtypeInstruction = {
    require(funct7 >= 0 && funct7 < 128, s"funct7 must be in the range 0:127, was $funct7")
    require(rs2 >= 0 && rs2 < 32, s"rs2 must be in the range 0:31, was $rs2")
    require(rs1 >= 0 && rs1 < 32, s"rs1 must be in the range 0:31, was $rs1")
    require(rd >= 0 && rd < 32, s"rd must be in the range 0:31, was $rd")
    require(funct3 >= 0 && funct3 < 8, s"funct3 must be in the range 0:7, was $funct3")
    require(Seq(Opcode.OP, Opcode.OP_IMM).contains(op), s"op must be one of OP, OP_IMM, was $op")
    (new RtypeInstruction).Lit(_.funct7 -> funct7.U, _.rs2 -> rs2.U, _.rs1 -> rs1.U, _.funct3 -> funct3.U, _.rd -> rd.U, _.op -> op)
  }
}

class ItypeInstruction extends Bundle with Instruction {
  val imm =    UInt(12.W) //31:20
  val rs1 =    UInt(5.W)  //19:15
  val funct3 = UInt(3.W)  //14:12
  val rd =     UInt(5.W)  //12:7
  val op =     Opcode()   // 6:0

  def toUInt: UInt = {
    ItypeInstruction.apply(this)
  }
}

object ItypeInstruction {
  /**
   * Converts an Itype instruction to its UInt representation
   * @param inst
   * @return
   */
  def apply(inst: ItypeInstruction): UInt = {
    inst.litValue.toLong.U(32.W)
  }

  /**
   * Generates a new I type instruction with the given fields
   * @param imm
   * @param rs1
   * @param funct3
   * @param rd
   * @param op
   * @return
   */
  def apply(imm: Int, rs1: Int, rd: Int, funct3: Int, op: Opcode.Type): ItypeInstruction = {
    require(imm >= -math.pow(2,12) && imm < math.pow(2,12), s"Immediate must be in the range -2048:2047, was $imm")
    require(rs1 >= 0 && rs1 < 32, s"rs1 must be in the range 0:31, was $rs1")
    require(rd >= 0 && rd < 32, s"rd must be in the range 0:31, was $rd")
    require(funct3 >= 0 && funct3 < 8, s"funct3 must be in the range 0:7, was $funct3")
    require(Seq(Opcode.OP_IMM, Opcode.JALR, Opcode.LOAD, Opcode.SYSTEM).contains(op), s"Opcode must be one of OP_IMM, JALR, LOAD, SYSTEM, was $op")

    (new ItypeInstruction).Lit(_.rs1 -> rs1.U, _.rd -> rd.U, _.funct3 -> funct3.U, _.op -> op, _.imm -> imm.U)
  }
}
