import chisel3._
import chiseltest._

import java.io.{BufferedWriter, FileInputStream, FileWriter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.sys.process._

package object core {

  /**
   * Assemble a program
   * @param s The program to assemble
   * @return An array of Int, each Int representing one instruction to execute
   */
  def assemble(s: String): Array[Int] = {
    val file = "asm"

    //Write contents to a file
    val bw = new BufferedWriter(new FileWriter(s"$file.s"))
    bw.write(s)
    bw.close()

    //Compile and extract .text-segment
    if(System.getProperty("os.name").contains("Windows")) {
      s"riscv64-unknown-elf-gcc.exe -march=rv32i -mabi=ilp32 -c $file.s -o $file.o".! //compile
      s"riscv64-unknown-elf-objcopy.exe -O binary $file.o $file.bin".! //extract
    } else { //Assuming Linux
      s"riscv64-linux-gnu-gcc -march=rv32i -mabi=ilp32 -c $file.s -o $file.o".!
      s"riscv64-linux-gnu-objcopy -O binary $file.o $file.bin".!
    }

    //Retrieve .text-segment, parse as int
    val fis = new FileInputStream(s"$file.bin")
    val bytes = fis.readAllBytes()
    fis.close()
    bytes.grouped(4)
      .map(x => (x(0) & 0xff) | ((x(1) & 0xff) << 8) | ((x(2) & 0xff) << 16) | ((x(3) & 0xff) << 24))
      .toArray
  }

  /**
   * Assemble a program, and then map each instruction consecutively to the address it should be placed at
   * @param s The program to assemble
   * @return An int-int map, mapping from addresses to instructions
   */
  def assembleMap(s: String): Map[Int, Int] = {
    assemble(s).zipWithIndex.map{case (instr, i) => (i*4, instr)}.toMap
  }

  /**
   * A SimulationDriver is a class representing a driver/monitor combo that
   * interfaces with a port on the DUT
   * @param port The port that this Driver drives and reads
   */
  abstract class SimulationDriver(port: Data)(implicit conf: Config) {
    /** Variable set high when [[stop]] is called. Represents end-of-simulation */
    var finish: Boolean = false
    /** The function implementing the drive/monitor loop of this Driver */
    def run(dut: Core): Unit
    /** Called by [[SimulationHarness]] when execution stops */
    def stop(): Unit = {
      this.finish = true
    }
  }

  /**
   * An driver for the instruction memory, where all memory requests may be served on the next clock cycle, or they may be served
   * after some delay. There is a uniform chance of each delay value in the range [1;maxDelay]
   * @param port The port that this Driver drives and reads
   * @param instrs The instructions to drive onto the port
   * @param maxDelay The maximum number of clock cycles to wait between seeing a request and acknowledging it
   * @param conf
   */
  class ImemDriverWithDelay(port: MemoryInterface, instrs: Map[Int, Int], maxDelay: Int)(implicit conf: Config) extends SimulationDriver(port) {
    require(maxDelay > 0, s"Imem driver cannot acknowledge on the same cycle as request, maxDelay must be >0, is $maxDelay")
    override def run(dut: Core): Unit = {
      val nop = ItypeInstruction(0, 0, 0, Funct3.ADDI, Opcode.OP_IMM).litValue.toInt
      port.in.rdata.poke(0.U)
      while(!this.finish) {
        while(!port.out.req.peekBoolean()) {
          port.in.ack.poke(false.B)
          dut.clock.step()
        }
        val addr = port.out.addr.peekInt()
        val d = scala.util.Random.nextInt(maxDelay)+1
        println(s"Delaying for $d cycles on access to $addr")
        for(_ <- Range.inclusive(1, d)) {
          dut.clock.step()
          port.in.ack.poke(false.B)
        }
        port.in.ack.poke(true.B)
        port.in.rdata.poke(instrs.getOrElse(addr.toInt, nop).toLong & 0xffff_ffffL)
      }
    }
  }

  /**
   * A [[SimulationDriver]] that drives instructions onto the imem-port of the Core.
   * If an address is accessed which doesn't map to an instruction, NOP is returned.
   * @param port The port that this Driver drives and reads
   * @param instrs The instructions to drive onto the port.
   *
   */
  class ImemDriver(port: MemoryInterface, instrs: Map[Int, Int])(implicit conf: Config) extends SimulationDriver(port) {
    override def run(dut: Core): Unit = {
      val nop = ItypeInstruction(0, 0, 0, Funct3.ADDI, Opcode.OP_IMM).litValue.toInt
      port.in.rdata.poke(1.U)
      while(!this.finish) {
        while(!port.out.req.peekBoolean()) {
          port.in.ack.poke(false.B)
          dut.clock.step()
        }
        val addr = port.out.addr.peekInt()
        //TODO: sample addr on this side of the clock cycle, poke ack and read data on the other side of the cycle
        dut.clock.step()
        port.in.ack.poke(true.B)
        port.in.rdata.poke(instrs.getOrElse(addr.toInt, nop).toLong & 0xffff_ffffL)
      }
    }
  }

  /**
   * A "serial port" that can be used for simulation purposes to simulate a memory-mapped IO (like a UART)
   * Values written to the address at which this port is registered are written to the console
   * @param port The port that this Driver drives and reads
   * @param addr The address at which this serial monitor should be registered
   * @param conf
   */
  class SoftwareSerialPort(port: MemoryInterface, addr: Int)(implicit conf: Config) extends SimulationDriver(port) {
    val buf = ListBuffer.empty[Char]
    /** The function implementing the drive/monitor loop of this Driver */
    override def run(dut: Core): Unit = {
      while(!this.finish) {
        while(!port.out.req.peekBoolean()) {
          dut.clock.step()
          //Should *not* poke ack/rdata, as it is connected to the same port as the dmemdriver
        }
        val addr = port.out.addr.peekInt().toInt
        val we = port.out.we.peekBoolean()
        val wdata = port.out.wdata.peekInt().toInt
        val wmask = port.out.wmask.peek().map(_.litToBoolean)

        dut.clock.step()
        if(addr == this.addr) {
          timescope {
            port.in.ack.poke(true.B)
          }
          val chars = Seq.tabulate(4)(i => ((wdata >> i*8) & 0xFF).toChar)
          if(we) {
            //Write the bytes that are high to serial-out
            wmask.zip(chars).foreach{case (b,c) => if (b) {print(c); buf += c}}
          }
        }
      }
    }

    def getBuffer: ListBuffer[Char] = this.buf
    def getBufString: String = this.buf.foldLeft(""){case (a,c) => s"$a$c"}
  }

  /**
   * A [[SimulationDriver]] that represents the attached memory. This driver responds immediately to all memory requests.
   * If an address is accessed which hasn't yet been written, returns 0
   * @param port The port that this Driver drives and reads
   * @param data An optional initial data mapping. If None is given, starts with an Empty memory
   * @param low  The lowest address (inclusive) that this dmem driver should respond to
   * @param high The highest address (inclusive) that this dmem driver should respond to
   */
  class DmemDriver(port: MemoryInterface, val data: Option[mutable.Map[Int, Byte]], var low: Int, var high: Int)(implicit conf: Config) extends SimulationDriver(port) {
    val d = data.getOrElse(mutable.Map.empty[Int,Byte])
    override def run(dut: Core): Unit = {
      while(!this.finish) {
        while(!port.out.req.peekBoolean()) {
          dut.clock.step()
          timescope {
            port.in.ack.poke(false.B)
            port.in.rdata.poke(0.U)
          }
        }
        val addr = port.out.addr.peekInt().toInt
        val we = port.out.we.peekBoolean()
        val wdata = port.out.wdata.peekInt().toInt
        val wmask = port.out.wmask.peek()
        dut.clock.step()
        if (addr >= this.low && addr <= this.high) {
          port.in.ack.poke(true.B)
          if (we) {
            for (i <- 0 until conf.WMASKLEN) {
              if (wmask(i).litToBoolean) {
                d(addr + i) = ((wdata >> i * 8) & 0xFF).toByte
              }
            }
          } else {
            var r = 0L
            for (i <- 0 until conf.WMASKLEN) {
              r |= (d.getOrElse(addr + i, 0.toByte) & 0xff) << 8 * i
            }
            port.in.rdata.poke(r & 0xffffffffL)
          }
        }
      }
    }

    def getData(addr: Int): Int = d(addr)
    def setLowBound(low: Int): Unit = this.low = low
    def setHighBound(high: Int): Unit = this.high = high
  }

  /**
   * The simulation harness wraps the DUT and a number of [[SimulationDriver]]s to provide the full
   * test I/O
   * @param dut The DUT to attach this harness to
   * @param drivers All drivers that should attach to the DUT
   * @param timeout The maximum number of clock cycles that the simulation should run for. Defaults to 50
   */
  class SimulationHarness(dut: Core, drivers: ListBuffer[SimulationDriver], var timeout: Int = 50)(implicit conf: Config) {
    def run(): Unit = {
      var clkCnt = 0
      drivers.foreach(d => fork{d.run(dut)})
      fork {
        do {
          dut.clock.step()
          clkCnt += 1
          //Cannot stop by peeking in.rdata, as that is being poked by another thread
          //Must peek something other than that (IRQ signal?)
        } while(clkCnt < timeout)
        drivers.foreach(_.stop())
        println(s"All drivers stopped after $clkCnt clock cycles")
      }.join()
    }

    /**
     * Sets the simulation timeout for this harness.
     * If the timeout is exceeded, the simulation is stopped (but does *not* fail)
     * @param timeout
     */
    def setTimeout(timeout: Int): Unit = {
      this.timeout = timeout
    }

    /**
     * Adds a new driver to the list of drivers controlled by this harness
     * @param driver The driver to add
     */
    def addDriver(driver: SimulationDriver): Unit = this.drivers += driver
  }


  object SimulationHarness {
    /**
     * Create a [[SimulationHarness]] instantiating a [[ImemDriver]] with the contained instructions
     * and a [[DmemDriver]] initiated with empty memory.
     * @param dut The DUT to simulate
     * @param instrs The instructions to supply on the instruction-memory
     */
    def apply(dut: Core, instrs: Map[Int, Int])(implicit conf: Config): SimulationHarness = {
      val imem = new ImemDriver(dut.io.imem, instrs)
      val dmem = new DmemDriver(dut.io.dmem, None, 0, Int.MaxValue)
      new SimulationHarness(dut, ListBuffer(imem, dmem))
    }
  }

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
    val nop = ItypeInstruction(0, 0, 0, Funct3.ADDI, Opcode.OP_IMM)
    while(!imem.out.req.peekBoolean()) {
      clock.step()
      imem.in.ack.poke(false.B)
    }
    val addr = imem.out.addr.peekInt()
    clock.step()
    imem.in.rdata.poke(instrs.getOrElse(addr.toInt, nop).toUInt)
    imem.in.ack.poke(true.B)
  }

  /**
   * Generates instructions to load random values into registers x1-x15
   * @param lb Buffer of instructions to be written
   */
  def loadFirst15(lb: ListBuffer[Instruction]): Unit = {
    val MAX_12BIT = math.pow(2,12).toInt
    def r = scala.util.Random.nextInt(MAX_12BIT) - MAX_12BIT/2
    for(i <- 1 until 16) {
      lb += ItypeInstruction(r, 0, i, Funct3.ADDI, Opcode.OP_IMM)
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

  def bufferToMap[T](buf: mutable.ListBuffer[T]): Map[Int, T] = {
    buf.zipWithIndex.map { case (instr,i) => (i*4, instr) }.toMap
  }

  def expectReg(dut: Core, i: Int, v: Int): Unit = {
    expectReg(dut, i, v.toLong & 0xffffffffL)
  }

  def expectReg(dut: Core, i: Int, v: Long): Unit = {
    dut.io.dbg.get.reg(i).expect(v.U)
  }
}
