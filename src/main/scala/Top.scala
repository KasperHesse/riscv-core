import chisel3._
import core._

object Top extends App {
  chisel3.emitVerilog(new Core()(Config()))
}
