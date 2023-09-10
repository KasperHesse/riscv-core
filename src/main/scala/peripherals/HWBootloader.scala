package peripherals

import chisel3._
import chisel3.util._
import core._
import peripherals.HWBootloaderState._

class HWBootloader(val frequency: Int,
                   val baudRate: Int,
                   rxBufSize: Int,
                   txBufSize: Int)(implicit conf: Config) extends Module {
  val io = IO(new Bundle {
    val memIn = Input(new MemoryResponse)
    val memOut = Output(new MemoryRequest)
    /** UART rx data */
    val rxd = Input(Bool())
    /** GO-signal to core to start execution */
    val go = Output(Bool())
  })
  //Modules
  val uart = Module(new Uart(frequency, baudRate, rxBufSize, txBufSize))

  //Registers
  val go = RegInit(false.B)
  val state = RegInit(IDLE)
  val cnt = RegInit(0.U(8.W)) //num bytes received this loop
  val numBytesInTx = RegInit(0.U(8.W)) //total number of bytes in this loop
  val wmask = RegInit(1.U(4.W))
  val addr = RegInit(0.U(14.W))
  val byte = RegInit(0.U(8.W))

  //Signals, default assignments
  val isLastPacket = !numBytesInTx.andR //If numBytesInTx is 255, not the last packet. If not and andR is 0, is last packet
  uart.io.rdData.ready := false.B
  uart.io.rxd := io.rxd

  //State handling
  switch(state) {
    is(IDLE) {
      when(uart.io.rxAvail) {
        state := LOOP1
        uart.io.rdData.ready := true.B
        numBytesInTx := uart.io.rdData.bits
        cnt := 0.U
      }
    }
    is(LOOP1) { //Wait for new byte, or exit loop if finished
      when(cnt === numBytesInTx && isLastPacket) {
        state := GO
      } .elsewhen(cnt === numBytesInTx && !isLastPacket) {
        state := IDLE
      } .elsewhen(uart.io.rxAvail) {
        state := LOOP2
        byte := uart.io.rdData.bits
        uart.io.rdData.ready := true.B
      }
    }
    is(LOOP2) { //Store byte in memory
      //req high -> set below
      when(io.memIn.ack) {
        state := LOOP3
      }
    }
    is(LOOP3) {
      state := LOOP1
      addr := Mux(wmask(3), addr + 1.U, addr) //Full instruction has been written, go to next instruction
      wmask := wmask(2,0) ## wmask(3)
      cnt := cnt + 1.U
    }
  }

  io.memOut.wmask := wmask
  io.memOut.addr := 0.U(16.W) ## addr ## 0.U(2.W)
  io.memOut.wdata := Fill(4, byte)
  io.memOut.we := true.B
  io.memOut.req := state === LOOP2 && !io.memIn.ack
  io.go := state === GO


  uart.io.txData.bits := DontCare
  uart.io.txData.valid := false.B

}