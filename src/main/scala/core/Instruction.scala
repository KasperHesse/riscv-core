package core

import chisel3._
import chisel3.experimental.BundleLiterals._
import core.Opcode

trait Instruction {
  def toUInt: UInt
}

object Instruction {
  /**
   * Converts an integer to its Instruction-type representation of an integer.
   * If the integer does not match a valid instruction, throws an error
   * @param v
   * @return
   */
  def apply(v: Int): Instruction = {
    val (op, safe) = Opcode.safe((v & 0x7f).U)

    op match {
      case Opcode.OP => RtypeInstruction(v)//Parse as R-type
      case Opcode.OP_IMM | Opcode.LOAD | Opcode.JALR | Opcode.SYSTEM | Opcode.MISC_MEM => ItypeInstruction(v)
      case Opcode.STORE => StypeInstruction(v)
      case Opcode.AUIPC | Opcode.LUI => UtypeInstruction(v)
      case Opcode.JAL => JtypeInstruction(v)
      case Opcode.BRANCH => BtypeInstruction(v)
      case _ => throw new IllegalArgumentException(s"Opcode ${v.toBinaryString.prependedAll("0000000").takeRight(7)} was not recognized")
    }
  }
}

class RtypeInstruction extends Bundle with Instruction {
  val funct7 = UInt(7.W)
  val rs2 = UInt(5.W)
  val rs1 = UInt(5.W)
  val funct3 = UInt(3.W)
  val rd = UInt(5.W)
  val op = Opcode()

  override def toUInt: UInt = {
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
    require(Seq(Opcode.OP, Opcode.OP_IMM).contains(op), s"Opcode must be one of OP, OP_IMM, was $op")
    (new RtypeInstruction).Lit(_.funct7 -> funct7.U, _.rs2 -> rs2.U, _.rs1 -> rs1.U, _.funct3 -> funct3.U, _.rd -> rd.U, _.op -> op)
  }

  /**
   * Converts an integer to an R-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as an R-type instruction
   */
  def apply(v: Int): RtypeInstruction = {
    Opcode(0.U)
    apply(v >> 25, v >> 20 & 0x1f, v >> 15 & 0x1f, v >> 12 & 0x7, v >> 7 & 0x1f, Opcode.safe((v & 0x7f).U)._1)
  }
}

class ItypeInstruction extends Bundle with Instruction {
  val imm =    SInt(12.W) //31:20
  val rs1 =    UInt(5.W)  //19:15
  val funct3 = UInt(3.W)  //14:12
  val rd =     UInt(5.W)  //12:7
  val op =     Opcode()   // 6:0

  override def toUInt: UInt = {
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

    (new ItypeInstruction).Lit(_.rs1 -> rs1.U, _.rd -> rd.U, _.funct3 -> funct3.U, _.op -> op, _.imm -> imm.S)
  }

  /**
   * Converts an integer to an I-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as an I-type instruction
   */
  def apply(v: Int): ItypeInstruction = {
    apply(v >> 20, v >> 15 & 0x1f, v >> 7 & 0x1f, v >> 12 & 0x7, Opcode.safe((v & 0x7f).U)._1)
  }
}

class BtypeInstruction extends Bundle with Instruction {
  val imm12_10to5 = UInt(7.W)
  val rs2 = UInt(5.W)
  val rs1 = UInt(5.W)
  val funct3 = UInt(3.W)
  val imm4to1_11 = UInt(5.W)
  val op = Opcode()

  override def toUInt: UInt = {
    BtypeInstruction.apply(this)
  }
}

object BtypeInstruction {
  def apply(inst: BtypeInstruction): UInt = {
    inst.litValue.toLong.U(32.W)
  }

  /**
   * Creates a new B-type instruction
   * @param imm Signed immediate for the branch offset. Must be an even number within range [-(2^13^);2^13^-1]
   * @param rs2
   * @param rs1
   * @param funct3
   * @param op
   */
  def apply(imm: Int, rs2: Int, rs1: Int, funct3: Int, op: Opcode.Type): BtypeInstruction = {
    require(imm >= -math.pow(2,13) && imm < math.pow(2,13) && imm % 2 == 0, s"imm must be in the range -8192:8191 and even, was $imm")
    require(rs1 >= 0 && rs1 < 32, s"rs1 must be in the range 0:31, was $rs1")
    require(rs2 >= 0 && rs2 < 32, s"rs2 must be in the range 0:31, was $rs2")
    require(funct3 >= 0 && funct3 < 8, s"funct3 must be in the range 0:7, was $funct3")
    require(op == Opcode.BRANCH, s"Opcode must be BRANCH, was $op")

    val imm12_10to5 = (((imm >> 12) & 1) << 6) | ((imm >> 5) & 0x3f)
    val imm4to1_11 = imm & 0x1e | ((imm >> 11) & 1)

    (new BtypeInstruction).Lit(_.imm12_10to5 -> imm12_10to5.U, _.rs2 -> rs2.U, _.rs1 -> rs1.U, _.funct3 -> funct3.U, _.imm4to1_11 -> imm4to1_11.U, _.op -> op)
  }

  /**
   * Converts an integer to a B-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as a B-type instruction
   */
  def apply(v: Int): BtypeInstruction = {
    //Must first reconstruct the immediate value
    val imm = ((v >> 31) << 12) | (((v >> 25) & 0x3f) << 5) | (((v >> 8) & 0xf) << 1) | ((v >> 7) & 1) << 11

    apply(imm, v >> 20 & 0x1f, v >> 15 & 0x1f, v >> 12 & 0x7, Opcode.safe((v & 0x7f).U)._1)
  }
}

class UtypeInstruction extends Bundle with Instruction {
  val imm31to12 = SInt(20.W) //31:12
  val rd = UInt(5.W)         //11:7
  val op = Opcode()          // 6:0

  override def toUInt: UInt = UtypeInstruction.apply(this)
}

object UtypeInstruction {
  def apply(inst: UtypeInstruction): UInt = {
    inst.litValue.toLong.U(32.W)
  }

  def apply(imm: Int, rd: Int, op: Opcode.Type): UtypeInstruction = {
    require((imm & 0xfff) == 0, s"12 LSB of immediate must be unset, was ${"000".concat((imm & 0xfff).toHexString).takeRight(3)}")
    require(rd >= 0 && rd < 31, s"rd must be in the range 0:31, was $rd")
    require(Seq(Opcode.AUIPC, Opcode.LUI).contains(op), s"Opcode must be one of AUIPC, LUI, was $op")

    (new UtypeInstruction).Lit(_.imm31to12 -> (imm >> 12).S, _.rd -> rd.U, _.op -> op)
  }

  /**
   * Converts an integer to a U-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as a U-type instruction
   */
  def apply(v: Int): UtypeInstruction = {
    apply(v & 0xfffff000, v >> 7 & 0x1f, Opcode.safe((v & 0x7f).U)._1)
  }
}

class StypeInstruction extends Bundle with Instruction {
  val imm11to5 = UInt(7.W) //31:25
  val rs2 = UInt(5.W)      //24:20
  val rs1 = UInt(5.W)      //19:15
  val funct3 = UInt(3.W)   //14:12
  val imm4to0 = UInt(5.W)  //11:7
  val op = Opcode()        // 6:0

  override def toUInt: UInt = StypeInstruction.apply(this)
}

object StypeInstruction {

  def apply(inst: StypeInstruction): UInt = inst.litValue.toLong.U(32.W)

  def apply(imm: Int, rs2: Int, rs1: Int, funct3: Int, op: Opcode.Type): StypeInstruction = {
    require(imm >= -math.pow(2,12) && imm < math.pow(2,12), s"Immediate must be in the range -2048:2047, was $imm")
    require(rs1 >= 0 && rs1 < 32, s"rs1 must be in the range 0:31, was $rs1")
    require(rs2 >= 0 && rs2 < 32, s"rs2 must be in the range 0:31, was $rs2")
    require(funct3 >= 0 && funct3 < 8, s"funct3 must be in the range 0:7, was $funct3")
    require(Opcode.STORE == op, s"Opcode must be STORE, was $op")

    (new StypeInstruction).Lit(_.imm11to5 -> ((imm >> 5) & 0x7f).U, _.rs2 -> rs2.U, _.rs1 -> rs1.U, _.funct3 -> funct3.U, _.imm4to0 -> (imm & 0x1f).U, _.op -> op)
  }

  /**
   * Converts an integer to an S-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as an S-type instruction
   */
  def apply(v: Int): StypeInstruction = {
    //Must extract immediate
    val imm = ((v >> 25) << 5) | ((v >> 7) & 0x1f)
    apply(imm, v >> 20 & 0x1f, v >> 15 & 0x1f, v >> 12 & 0x7, Opcode.safe((v & 0x7f).U)._1)
  }
}

class JtypeInstruction extends Bundle with Instruction {
  val imm20 = Bool()        //31
  val imm10to1 = UInt(10.W) //30:21
  val imm11 = Bool()        //20
  val imm19to12 = UInt(8.W) //19:12
  val rd = UInt(5.W)        //11:7
  val op = Opcode()         // 6:0

  override def toUInt: UInt = JtypeInstruction.apply(this)
}

object JtypeInstruction {

  def apply(inst: JtypeInstruction): UInt = inst.litValue.toLong.U(32.W)

  def apply(imm: Int, rd: Int, op: Opcode.Type): JtypeInstruction = {
    require(imm >= -math.pow(2,20) && imm < math.pow(2,20) && imm%2 == 0, s"imm must be in range [], was $imm ")
    require(rd >= 0 && rd < 31, s"rd must be in the range 0:31, was $rd")
    require(op == Opcode.JAL, s"Opcode must be one of AUIPC, LUI, was $op")

    val imm20 = (imm >> 20) & 1
    val imm10to1 = imm >> 1 & 0x3ff
    val imm11 = imm >> 11 & 1
    val imm19to12 = imm >> 12 & 0xff

    (new JtypeInstruction).Lit(_.imm20 -> imm20.B, _.imm10to1 -> imm10to1.U, _.imm11 -> imm11.B, _.imm19to12 -> imm19to12.U, _.rd -> rd.U, _.op -> op)
  }

  /**
   * Converts an integer to a J-type instruction. Throws an exception if the instruction cannot be parsed
   * @param v
   * @return
   * @throws IllegalArgumentException If the value cannot be parsed as a J-type instruction
   */
  def apply(v: Int): JtypeInstruction = {
    //Extract the immediate
    val imm20 = v >> 31
    val imm10to1 = v >> 21 & 0x3ff
    val imm11 = v >> 20 & 1
    val imm19to12 = v >> 12 & 0xff
    apply((imm20 << 20) | (imm19to12 << 12) | (imm11 << 11) | (imm10to1 << 1), v >> 7 & 0x1f, Opcode.safe((v & 0x7f).U)._1)
  }
}