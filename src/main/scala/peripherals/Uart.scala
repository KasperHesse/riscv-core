/*
 * Copyright: 2014-2018, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 *
 * A UART is a serial port, also called an RS232 interface.
 * Adapted from Martin Schoeberls original implementation
 *
 */

package peripherals

import chisel3._
import chisel3.util._
import core.{Config, MemoryRequest, MemoryResponse}

/**
 * This is a minimal data channel with a ready/valid handshake.
 */
//- start uart_channel
//class Channel extends Bundle {
//  val data = Input(Bits(8.W))
//  val ready = Output(Bool())
//  val valid = Input(Bool())
//}
//- end

/**
 * Transmit part of the UART.
 * A minimal version without any additional buffering.
 * Use an AXI like valid/ready handshake.
 */
//- start uart_tx
class Tx(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd = Output(Bits(1.W))
    val channel = Flipped(Decoupled(UInt(8.W)))
  })

  val BIT_CNT = ((frequency + baudRate / 2) / baudRate - 1).asUInt

  val shiftReg = RegInit(0x7ff.U) //Shift register containing data to transmit
  val cntReg = RegInit(0.U(20.W)) //down-counter, when 0 transmits 1 bit
  val bitsReg = RegInit(0.U(4.W)) //number of bits remaining to transmit in this packet

  io.channel.ready := (cntReg === 0.U) && (bitsReg === 0.U)
  io.txd := shiftReg(0)

  when(cntReg === 0.U) {

    cntReg := BIT_CNT
    when(bitsReg =/= 0.U) {
      val shift = shiftReg >> 1
      shiftReg := Cat(1.U, shift(9, 0))
      bitsReg := bitsReg - 1.U
    }.otherwise {
      when(io.channel.valid) {
        // two stop bits, data, one start bit
        shiftReg := Cat(Cat(3.U, io.channel.bits), 0.U)
        bitsReg := 11.U
      }.otherwise {
        shiftReg := 0x7ff.U
      }
    }

  }.otherwise {
    cntReg := cntReg - 1.U
  }
}
//- end

/**
 * Receive part of the UART.
 * A minimal version without any additional buffering.
 * Use an AXI like valid/ready handshake.
 *
 * The following code is inspired by Tommy's receive code at:
 * https://github.com/tommythorn/yarvi
 */
//- start uart_rx
class Rx(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(Bits(1.W))
    val channel = Decoupled(UInt(8.W))
  })

  val BIT_CNT = ((frequency + baudRate / 2) / baudRate - 1).U
  val START_CNT = ((3 * frequency / 2 + baudRate / 2) / baudRate - 1).U

  // Sync in the asynchronous RX data
  // Reset to 1 to not start reading after a reset
  val rxReg = RegNext(RegNext(io.rxd, 1.U), 1.U)

  val shiftReg = RegInit('A'.U(8.W))
  val cntReg = RegInit(0.U(20.W))
  val bitsReg = RegInit(0.U(4.W))
  val valReg = RegInit(false.B)

  when(cntReg =/= 0.U) {
    cntReg := cntReg - 1.U
  }.elsewhen(bitsReg =/= 0.U) {
    cntReg := BIT_CNT
    shiftReg := Cat(rxReg, shiftReg >> 1)
    bitsReg := bitsReg - 1.U
    // the last shifted in
    when(bitsReg === 1.U) {
      valReg := true.B
    }
    // wait 1.5 bits after falling edge of start
  }.elsewhen(rxReg === 0.U) {
    cntReg := START_CNT
    bitsReg := 8.U
  }

  when(valReg && io.channel.ready) {
    valReg := false.B
  }

  io.channel.bits := shiftReg
  io.channel.valid := valReg
}
//- end

class Uart(frequency: Int,
           baudRate: Int,
           rxBufSize: Int,
           txBufSize: Int) extends Module {
  val io = IO(new Bundle {
    /** UART rx data */
    val rxd = Input(Bool())
    /** UART tx data */
    val txd = Output(Bool())

    /** First byte from read buffer */
    val rdData = Decoupled(UInt(8.W))
    /** Number of items in read buffer */
    val rxCnt = Output(UInt(log2Ceil(rxBufSize+1).W))
    /** Read buffer full flag */
    val rxFull = Output(Bool())
    /** Data available in RX buffer */
    val rxAvail = Output(Bool())
    /** Write to transmit buffer */
    val txData = Flipped(Decoupled(UInt(8.W)))
    /** Number of items in transmit buffer */
    val txCnt = Output(UInt(log2Ceil(txBufSize+1).W))
    /** Transmit buffer full flag */
    val txFull = Output(Bool())
  })
  require(isPow2(rxBufSize), s"rxBufSize must be a power of two, was $rxBufSize")
  require(isPow2(txBufSize), s"txBufSize must be a power of two, was $txBufSize")

  val rx = Module(new Rx(frequency, baudRate))
  val tx = Module(new Tx(frequency, baudRate))
  val rxBuf = Module(new Queue(UInt(8.W), rxBufSize))
  val txBuf = Module(new Queue(UInt(8.W), txBufSize))

  io.txData <> txBuf.io.enq
  txBuf.io.deq <> tx.io.channel

  rx.io.channel <> rxBuf.io.enq
  rxBuf.io.deq <> io.rdData

  io.rxCnt := rxBuf.io.count
  io.txCnt := txBuf.io.count
  io.rxFull := rxBuf.io.count === rxBufSize.U
  io.rxAvail := rxBuf.io.deq.valid
  io.txFull := txBuf.io.count === txBufSize.U
  io.txd := tx.io.txd
  rx.io.rxd := io.rxd
}

class UartWrapper(frequency: Int,
                  baudRate: Int,
                  rxBufSize: Int,
                  txBufSize: Int)(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val memIn = Input(new MemoryRequest)
    val memOut = Output(new MemoryResponse)
    val txd = Output(Bool())
    val rxd = Input(Bool())
  })

  //UART connections
  val uart = Module(new Uart(frequency, baudRate, rxBufSize, txBufSize))
  io.txd := uart.io.txd
  uart.io.rxd := io.rxd

  //Register read logic
  val rdData = RegInit(0.U(8.W))
  val ack = RegNext(io.memIn.req)

  //Default assignments
  uart.io.rdData.ready := false.B
  uart.io.txData.valid := false.B
  uart.io.txData.bits := io.memIn.wdata(7,0)

  //Handle reads
  when(io.memIn.req && !io.memIn.we) { //By definition, 2LSB will be 0
    //Multiplex read register
    switch(io.memIn.addr(4,2)) {
      is(0.U(3.W)) { //rdData
        uart.io.rdData.ready := true.B
        rdData := 0.U((conf.XLEN-8).W) ## uart.io.rdData.bits
      }
      is(1.U(3.W)) { //rdFlag
        rdData := Cat(0.U(6.W), uart.io.rxFull)
      }
      is(2.U(3.W)) { //rdCnt
        rdData := uart.io.rxCnt
      }
      is(4.U(3.W)) { //wrData
        rdData := uart.io.txData.bits
      }
      is(5.U(3.W)) {
        rdData := uart.io.txFull
      }
      is(6.U(3.W)) {
        rdData := uart.io.txCnt
      }
    }
  }

  //Handle writes
  uart.io.txData.bits := io.memIn.wdata(7,0)
  uart.io.txData.valid := io.memIn.req && io.memIn.we && io.memIn.wmask(0)

  //Handle I/O
  io.memOut.ack := ack
  io.memOut.rdata := rdData
}

object UartMain extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Uart(100_000_000, 115200, 2, 2), Array("--target-dir", "generated"))
}

