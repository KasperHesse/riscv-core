package core

import java.io.{BufferedWriter, FileWriter, FileInputStream, BufferedInputStream}
import os.proc
import scala.sys.process._
object UseTheAssembler extends App {
  //Try using the riscv64-unknown-elf toolchain to assembly stuff

  /**
   * Writes the string s to a file f. Overwrites existing contents if they exist
   * @param s
   * @param f
   */
  def toFile(s: String, f: String): Unit = {
    val bw = new BufferedWriter(new FileWriter(f))
    bw.write(s)
    bw.close()
  }

  /**
   * Parse a bin file containing instructions, returning the instruction words from that binary file
   * @param f Filename to parse
   * @return
   */
  def parseBin(f: String): Array[Int] = {
    val fis = new FileInputStream(f)
    val bytes = fis.readAllBytes()
    fis.close()
    bytes.grouped(4)
      .map(x => (x(0) & 0xff) | ((x(1) & 0xff) << 8) | ((x(2) & 0xff) << 16) | ((x(3) & 0xff) << 24))
      .toArray
  }

  parseBin("asm.bin")
/*
  val asm =
    """
      | addi x1, x0, 4
      | addi x2, x0, 8
      | add x3, x1, x2
      | """.stripMargin

  val file = "asm.s"
  toFile(asm, file)

  val compile = s"riscv64-unknown-elf-gcc.exe -march=rv32i -mabi=ilp32 -c asm.s -o asm.o".!
  val dump = s"riscv64-unknown-elf-objcopy.exe -O binary asm.o asm.bin".!

*/
//  os.proc(compile).call()
//  os.proc(dump).call()


}
