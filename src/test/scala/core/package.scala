import chisel3._
import chiseltest._

import java.io.{BufferedWriter, FileInputStream, FileWriter}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.sys.process._
import scala.util.Random

package object core {

  val defaultConf = Config(debug=true)

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
    val gccOpts = s"-nostdlib -nostartfiles -march=rv32i -mabi=ilp32 -T programs/linker.ld -Wl,--no-relax $file.s -o $file.o"
    val objcopyOpts = s"-O binary $file.o $file.bin"
    val (gcc, objcopy) = if (System.getProperty("os.name").contains("Windows")) {
      ("riscv64-unknown-elf-gcc.exe", "riscv64-unknown-elf-objcopy.exe")
    } else {
      ("riscv64-linux-gnu-gcc", "riscv64-linux-gnu-objcopy")
    }
    s"$gcc $gccOpts".!
    s"$objcopy $objcopyOpts".!
//    if(System.getProperty("os.name").contains("Windows")) {
//      s"riscv64-unknown-elf-gcc.exe $gccOpts".! //compile
//      s"riscv64-unknown-elf-objcopy.exe $objcopyOpts".! //extract
//    } else { //Assuming Linux
//      s"riscv64-linux-gnu-gcc -march=rv32i -mabi=ilp32 -c $file.s -o $file.o".!
//      s"riscv64-linux-gnu-objcopy -O binary $file.o $file.bin".!
//    }

    //Retrieve .text and .data-segments, parse as int
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
   * A PortDriver is a class representing an module which is attached to a port on the DUT.
   * The PortDriver has an associated memory range [low,high] to which it can respond to port transactions.
   *
   * @param port The port on which the PortDriver acts
   * @param low Low memory address that is cached
   * @param high High memory address that is cached
   */
  abstract class PortDriver(port: Data, val low: Int, val high: Int)(implicit conf: Config) {
    def drive(): Unit
  }



    /**
     * The DcacheWithDelay is a PortDriver attached to the data memory bus of the DUT, acting as the data cache of the system
     * @param port The port on which the PortDriver acts
     * @param clock DUT clock
     * @param low Low memory address that is cached
     * @param high High memory address that is cached
     * @param minDelay minimum delay before responding to memory requests. Must be >=1
     * @param maxDelay Maximum delay before responding to memory requests. Must be >=minDelay
     * @param data Initial data mapping. Defaults to an empty memory
     */
  class DcacheWithDelay(port: MemoryInterface, clock: Clock, low: Int = 0, high: Int = 0xffff, minDelay: Int, maxDelay: Int)
                       (data: mutable.Map[Int, Byte] = mutable.Map[Int,Byte]())
                       (implicit conf: Config) extends PortDriver(port, low, high) {

    require(minDelay > 0, s"Dcache cannot acknowledge on same cycle as request. minDelay must be >0, is $minDelay")
    require(maxDelay >= minDelay, s"Dcache maxDelay must be >= minDelay ($minDelay), is $maxDelay")
    override def drive(): Unit = {
      val we = port.req.we.peekBoolean()
      val wmask = port.req.wmask.peek().asBools.map(_.litToBoolean)
      val addr = port.req.addr.peekInt().toInt
      val wdata = port.req.wdata.peekInt().toInt

      val d = Random.between(minDelay, maxDelay+1)
      for(_ <- 1 to d) {
        clock.step()
        port.resp.ack.poke(false.B)
      }
      port.resp.ack.poke(true.B)
      if (we) {
        for (i <- 0 until conf.WMASKLEN) {
          if (wmask(i)) {
            data(addr + i) = ((wdata >> i * 8) & 0xFF).toByte
          }
        }
      } else { //read
        var r = 0L
        for (i <- 0 until conf.WMASKLEN) {
          r |= (data.getOrElse(addr + i, 0.toByte) & 0xff) << 8 * i
        }
        port.resp.rdata.poke(r & 0xffffffffL)
      }
    }
  }

  /**
   * A variant of [[DcacheWithDelay]] that always responds to requests on the subsequent clock cycle
   * @param port The port on which the PortDriver acts
   * @param clock DUT clock
   * @param low Low memory address that is cached
   * @param high High memory address that is cached
   * @param data Initial data mapping. Defaults to an empty memory
   */
  class Dcache(port: MemoryInterface, clock: Clock, low: Int = 0, high: Int = 0xffff)
              (data: mutable.Map[Int, Byte] = mutable.Map[Int,Byte]())
              (implicit conf: Config) extends DcacheWithDelay(port, clock, low, high, 1, 1)(data)

  object Dcache {
    def apply(port: MemoryInterface, clock: Clock)(implicit conf: Config): Seq[PortDriver] = {
      Seq(new Dcache(port, clock)())
    }
  }

  /**
   * A "serial port" that can be used for simulation purposes to simulate a memory-mapped IO (like a UART)
   * Values written to the address of this driver are written to the console
   * @param port The port that this Driver drives and reads
   * @param clock DUT clock
   * @param low Low memory address that is cached
   * @param high High memory address that is cached
   */
  class SoftwareSerialPort(port: MemoryInterface, clock: Clock, low: Int, high: Int)
                           (implicit conf: Config) extends PortDriver(port, low, high) {
    override def drive(): Unit = {
      val we = port.req.we.peekBoolean()
      val wdata = port.req.wdata.peekInt().toInt
      val wmask = port.req.wmask.peek().asBools.map(_.litToBoolean)
      val chars = Seq.tabulate(4)(i => ((wdata >> i*8) & 0xFF).toChar)

      clock.step()
      port.resp.ack.poke(true.B)

      if(we) {
        //Write the bytes that are high to serial-out
        for (i <- chars.indices) {
          if (wmask(i)) {
            print(chars(i))/*; buf += chars(i)*/
          }
        }
      }
    }
  }

  /**
   * A [[SimulationAgent]] that drives instructions onto the imem-port of the Core.
   * If an address is accessed which doesn't map to an instruction, NOP is returned.
   *
   * @param port The port on which the PortDriver acts
   * @param clock DUT clock
   * @param low Low memory address that is cached
   * @param high High memory address that is cached
   * @param minDelay Minimum delay before ack is signalled
   * @param maxDelay Maximum delay before ack is signalled
   * @param instrs Instruction mapping
   * @param conf
   */
  class IcacheWithDelay(port: MemoryInterface, clock: Clock, low: Int, high: Int, minDelay: Int, maxDelay: Int)
                       (instrs: Map[Int, Int])
                       (implicit conf: Config) extends PortDriver(port, low, high) {
    require(minDelay > 0, s"ICache cannot acknowledge on same cycle as request. minDelay must be >0, is $minDelay")
    require(maxDelay >= minDelay, s"ICache maxDelay must be >= minDelay ($minDelay), is $maxDelay")

    val nop = ItypeInstruction(0, 0, 0, Funct3.ADDI, Opcode.OP_IMM).litValue.toInt
    override def drive(): Unit = {
      val addr = port.req.addr.peekInt().toInt
      val d = Random.between(minDelay, maxDelay+1)
//      println(s"Access to $addr, ack after $d")
      for (_ <- 1 to d) {
        clock.step()
        port.resp.ack.poke(false.B)
      }
      port.resp.ack.poke(true.B)
      port.resp.rdata.poke(instrs.getOrElse(addr, nop).toLong & 0xffff_ffffL)
    }

    def genRand(minl: Int, maxl: Int): Int = {
      (if (minl == maxl) {0} else {scala.util.Random.nextInt(maxl-minl+1)}) + minl
    }
  }

  /**
   * A variant of [[IcacheWithDelay]] which always responds to memory requests on the next clock cycle
 *
   * @param port The port on which the PortDriver acts
   * @param clock DUT clock
   * @param low Low memory address that is cached
   * @param high High memory address that is cached
   * @param instrs Instruction mapping
   * @param conf
   */
  class Icache(port: MemoryInterface, clock: Clock, low: Int, high: Int)
              (instrs: Map[Int, Int])
              (implicit conf: Config) extends IcacheWithDelay(port, clock, low, high, 1, 1)(instrs)

  object Icache {
    def apply(port: MemoryInterface, clock: Clock, instrs: Map[Int, Int])(implicit conf: Config): Seq[Icache] = {
      Seq(new Icache(port, clock, 0, 0xffff)(instrs))
    }

    def apply(port: MemoryInterface, clock: Clock, asm: String)(implicit conf: Config): Seq[Icache] = this(port, clock, assembleMap(asm))
  }

  /**
   * A SimulationAgent is a class representing a driver/monitor combo that
   * interfaces with a port on the DUT. The agent may have a number of [[PortDriver]] registered to it.
   * These drivers will respond to actions on the associated port
 *
   * @param port The port that this Driver drives and reads
   */
  abstract class SimulationAgent(port: Data)(implicit conf: Config) {
    val pds = ListBuffer.empty[PortDriver]
    /** Variable set high when [[stop]] is called. Represents end-of-simulation */
    var finish: Boolean = false
    /** The function implementing the drive/monitor loop of this Driver */
    def run(dut: Core): Unit
    /** Called by [[SimulationHarness]] when execution stops */
    def stop(): Unit = {
      this.finish = true
    }
    def register(pd: PortDriver): Unit = {
      this.pds.append(pd)
    }
    def register(pds: Seq[PortDriver]): Unit = {
      pds.foreach(this.pds.append)
    }
  }

  /**
   * A [[SimulationAgent]] that acts on the data or instruction memory bus.
 *
   * @param port The port that this Agent monitors and drives
   */
  class MemAgent(port: MemoryInterface)(implicit conf: Config) extends SimulationAgent(port) {
    override def run(dut: Core): Unit = {
      port.resp.ack.poke(false.B)
      port.resp.rdata.poke(0.U)
      while(!this.finish) {
        if (!port.req.req.peekBoolean()) {
          dut.clock.step()
          port.resp.ack.poke(false.B)
          port.resp.rdata.poke(0.U)
        } else {
          val addr = port.req.addr.peekInt().toInt
          for (pd <- pds) {
            if (pd.low <= addr && addr <= pd.high) {
              pd.drive()
            }
          }
        }
      }
    }
  }

  object MemAgent {
    def apply(port: MemoryInterface, pds: Seq[PortDriver])(implicit conf: Config): MemAgent = {
      val ma = new MemAgent(port)
      ma.register(pds)
      ma
    }
  }

  /**
   * Write a single value onto a UART receiver
   * @param rx The UART's rx pin
   * @param clock The clock of the system
   * @param freq The clock frequency of the system
   * @param baud The baudrate of the UART receiver
   * @param value THe values to write out. All values should be 0<=v<=255
   */
  def writeToUart(rx: Bool, clock: Clock, freq: Int, baud: Int, value: Int): Unit = {
    val cyclesPerBaud = freq/baud
    val txData = 0x600 | ((value & 0xFF) << 1)
    for(i <- 0 until 11) {
      val tx = (txData >> i) & 1
      rx.poke(tx.B)
      clock.step(cyclesPerBaud)
    }
    rx.poke(true.B)
  }
  /**
   * Write a stream of values onto a UART receiver
   * @param rx The UART's rx pin
   * @param clock The clock of the system
   * @param freq The clock frequency of the system
   * @param baud The baudrate of the UART receiver
   * @param values THe values to write out. All values should be 0<=v<=255
   */
  def writeToUart(rx: Bool, clock: Clock, freq: Int, baud: Int, values: Seq[Int]): Unit = {
    for(group <- values.grouped(255)) {
      writeToUart(rx, clock, freq, baud, group.length)
      group.foreach(v => writeToUart(rx, clock, freq, baud, v))
    }
    //If exactly a multiple of 255, should send 0 afterwards to move into GO state
    if (values.length % 255 == 0) {
      writeToUart(rx, clock, freq, baud, 0)
    }
  }


  /**
   * The simulation harness wraps the DUT and a number of [[SimulationAgent]]s to provide the full
   * test I/O
 *
   * @param dut The DUT to attach this harness to
   * @param agents All agents that should attach to the DUT
   * @param timeout The maximum number of clock cycles that the simulation should run for. Defaults to 50
   */
  class SimulationHarness(dut: Core, agents: ListBuffer[SimulationAgent], var timeout: Int = 50)(implicit conf: Config) {
    def run(): Unit = {
      var clkCnt = 0
      agents.foreach(d => fork{d.run(dut)})
      fork {
        do {
          dut.clock.step()
          clkCnt += 1
          //Cannot stop by peeking in.rdata, as that is being poked by another thread
          //Must peek something other than that (IRQ signal?)
        } while(clkCnt < timeout)
        agents.foreach(_.stop())
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
     * Adds a new agent to the list of agents controlled by this harness
     * @param agent The agent to add
     */
    def addAgent(agent: SimulationAgent): Unit = this.agents += agent
  }


  object SimulationHarness {
    /**
     * Create a [[SimulationHarness]] instantiating an [[Icache]] with the contained instructions
     * and a [[Dcache]] initiated with empty memory.
 *
     * @param dut The DUT to simulate
     * @param instrs The instructions to supply on the instruction-memory
     */
    def apply(dut: Core, instrs: Map[Int, Int])(implicit conf: Config): SimulationHarness = {
      val imem = new MemAgent(dut.io.imem)
      val dmem = new MemAgent(dut.io.dmem)

      imem.register(new Icache(dut.io.imem, dut.clock, 0, 0x7fff_ffff)(instrs))
      dmem.register(new Dcache(dut.io.dmem, dut.clock, 0, 0xffff)())

      new SimulationHarness(dut, ListBuffer(imem, dmem))
    }
  }


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
    while(!imem.req.req.peekBoolean()) {
      clock.step()
      imem.resp.ack.poke(false.B)
    }
    val addr = imem.req.addr.peekInt()
    clock.step()
    imem.resp.rdata.poke(instrs.getOrElse(addr.toInt, nop).toUInt)
    imem.resp.ack.poke(true.B)
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
