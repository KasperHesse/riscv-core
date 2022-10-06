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
    val bw = new BufferedWriter(new FileWriter(s"$f.s"))
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

//  print(parseBin("asm.bin").mkString("Array(", ", ", ")"))
  val asm =
    """
      | addi x0, x0, 0
      | addi x1, x0, 4
      | addi x2, x0, 8
      | add x3, x1, x2
      | addi x2, x0, -1
      | """.stripMargin

  val file = "asm"
  toFile(asm, file)

  val compile = s"riscv64-unknown-elf-gcc.exe -march=rv32i -mabi=ilp32 -c $file.s -o $file.o".!
  val dump = s"riscv64-unknown-elf-objcopy.exe -O binary $file.o $file.bin".!
  //Given parseBin, we can then map each value to a UInt
  print(parseBin(s"$file.bin").map(x => ("00000000" + x.toHexString).takeRight(8)).mkString("Array(",",",")"))

//  os.proc(compile).call()
//  os.proc(dump).call()


}
