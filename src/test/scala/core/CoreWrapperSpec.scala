package core

import chisel3._
import chiseltest._
import chiseltest.internal.CachingAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CoreWrapperSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Core wrapper"

  ignore should "load a program that toggles LEDs" in {
    val wrapConf = scala.collection.immutable.HashMap(
      "numKeys" -> 16,
      "numLeds" -> 16,
      "coreFreq" -> 2,
      "uartBaud" -> 1,
      "uartRxBufSize" -> 2,
      "uartTxBufSize" -> 2,
      "uartBaseAddr" -> 0x01,
      "ledsBaseAddr" -> 0x02,
      "keysBaseAddr" -> 0x03,
      "memSize" -> 128
    )
    val asm =
      """
        |li x1, 0xbeef
        |li x2, 0x02000000
        |sw x1, 0(x2)
        |LOOP: addi x0, x0, 0
        |j LOOP
        |""".stripMargin

    //Each int of the program should be mapped to a seq of 4 bytes
    val program = assemble(asm, this.getTestName).flatMap { x =>
      Seq(x & 0xFF, (x >> 8) & 0xFF, (x >> 16) & 0xFF, (x >> 24) & 0xFF)
    }

    test(new CoreWrapper(wrapConf)(Config())).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Write the instructions
      writeToUart(dut.io.uart.rx, dut.clock, 2, 1, program.toSeq)
      //Wait 50 cc, just because
      dut.clock.step(50)
      //Should see output on leds
      dut.io.leds.expect(0xbeef)
    }
  }

  ignore should "execute a blink program" in {
    val wrapConf = scala.collection.immutable.HashMap(
      "numKeys" -> 12,
      "numLeds" -> 12,
      "coreFreq" -> 10,
      "uartBaud" -> 3,
      "uartRxBufSize" -> 2,
      "uartTxBufSize" -> 2,
      "uartBaseAddr" -> 0x01,
      "ledsBaseAddr" -> 0x02,
      "keysBaseAddr" -> 0x03,
      "memSize" -> 128
    )
    val asm =
      """
        |li x1, 0          # Blink value
        |li x2, 0          # Counter value
        |li x3, 10         #Max count value
        |li x4, 0x02000000 #Address of LEDs
        |LOOP: addi x2, x2, 1
        |blt x2, x3, LOOP # count up
        |addi x1, x1, 1
        |sw x1, 0(x4)
        |li x2, 0         # Reset counter
        |j LOOP
        |""".stripMargin

    val program = assemble(asm, this.getTestName).flatMap { x =>
      Seq(x & 0xFF, (x >> 8) & 0xFF, (x >> 16) & 0xFF, (x >> 24) & 0xFF)
    }
    test(new CoreWrapper(wrapConf)(Config())).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      //Write the instructions
      writeToUart(dut.io.uart.rx, dut.clock, wrapConf("coreFreq"), wrapConf("uartBaud"), program.toSeq)
      //Wait 200 cc, just because
      dut.clock.step(200)
      //Should see output on leds
      assert(dut.io.leds.peekInt() != BigInt(0))
    }
  }

  ignore should "write hello world on the UART" in {
    val wrapConf = scala.collection.immutable.HashMap(
      "numKeys" -> 12,
      "numLeds" -> 12,
      "coreFreq" -> 2,
      "uartBaud" -> 1,
      "uartRxBufSize" -> 2,
      "uartTxBufSize" -> 2,
      "uartBaseAddr" -> 0x01,
      "ledsBaseAddr" -> 0x02,
      "keysBaseAddr" -> 0x03,
      "memSize" -> 512
    )
    val asm =
      """
        |.equ UART_BASE,    0x01000000
        |.equ UART_TX,      0x01000010
        |.equ UART_TX_FULL, 0x01000014
        |.globl _start
        |
        |.text
        |_start:
        |la x1, HELLO_WORLD
        |lb x2, 0(x1) #Initial fetch to simplify loop
        |lui x3, %hi(UART_TX)
        |addi x3, x3, %lo(UART_TX)
        |lui x4, %hi(UART_TX_FULL)
        |addi x4, x4, %lo(UART_TX_FULL)
        |ECHO:
        |beqz x2, PAUSE           #End of string
        |addi x1, x1, 1          #incr char index
        |CHECK_TX_FULL:
        |lw x5, (x4)     #check if buffer is full
        |bnez x5, CHECK_TX_FULL
        |sb x2, (x3)          #write to UART
        |j ECHO
        |PAUSE: #end of string. Count up before going back
        |li x1, 0
        |li x2, 4000000
        |PAUSE_LOOP: addi x1, x1, 1
        |blt x1, x2, PAUSE_LOOP #count up
        |j _start
        |
        |.data
        |HELLO_WORLD: .string "Hello World!"
        |.align(4) #Make sure a full byte is taken up
        |""".stripMargin

    val program = assemble(asm, this.getTestName).flatMap { x =>
      Seq(x & 0xFF, (x >> 8) & 0xFF, (x >> 16) & 0xFF, (x >> 24) & 0xFF)
    }.toSeq
    test(new CoreWrapper(wrapConf)(Config())).withAnnotations(Seq(WriteVcdAnnotation, CachingAnnotation)) { dut =>
      //Write the instructions
      writeToUart(dut.io.uart.rx, dut.clock, wrapConf("coreFreq"), wrapConf("uartBaud"), program)
      //Wait 200 cc, just because
      dut.clock.step(200)
    }
  }
}
