package bfcpu

import chisel3._
import chisel3.util._

class SDPRAMIO(word_width: Int, addr_bits: Int) extends Bundle {
  import Consts._
  val ren = Input(Bool())
  val raddr = Input(UInt(addr_bits.W))
  val rdata = Output(UInt(word_width.W))
  val wen = Input(Bool())
  val waddr = Input(UInt(addr_bits.W))
  val wdata = Input(UInt(word_width.W))
}

class SDPRAM(word_width: Int, addr_bits: Int) extends Module {
  import Consts._

  val io = IO(new SDPRAMIO(word_width, addr_bits))

  val mem = Mem(1 << addr_bits, UInt(word_width.W))

  io.rdata := MuxCase(
    mem(io.raddr),
    Seq((io.wen && io.waddr === io.raddr) -> io.wdata)
  )

  when(io.wen) {
    mem(io.waddr) := io.wdata
  }
}
