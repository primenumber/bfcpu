package bfcpu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

class SPRAMIO extends Bundle {
  import Consts._
  val enable = Input(Bool())
  val write = Input(Bool())
  val addr = Input(UInt(IMEM_ADDR_SIZE.W))
  val dataIn = Input(UInt(WORD_BITS.W))
  val dataOut = Output(UInt(WORD_BITS.W))
}

class SPRAM(memory_init_file: String) extends Module {
  import Consts._

  val io = IO(new SPRAMIO())

  val mem = Mem(1 << IMEM_ADDR_SIZE, UInt(WORD_BITS.W))

  loadMemoryFromFileInline(mem, memory_init_file)

  io.dataOut := DontCare
  when(io.enable) {
    val rdwrPort = mem(io.addr)
    when(io.write) { rdwrPort := io.dataIn }
      .otherwise { io.dataOut := rdwrPort }
  }
}
