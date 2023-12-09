package bfcpu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

class ReadPortIO(word_width: Int, addr_bits: Int) extends Bundle {
  val enable = Input(Bool())
  val addr = Input(UInt(addr_bits.W))
  val bits = Output(UInt(word_width.W))
}

class WritePortIO(word_width: Int, addr_bits: Int) extends Bundle {
  val enable = Input(Bool())
  val addr = Input(UInt(addr_bits.W))
  val bits = Input(UInt(word_width.W))
}

class SDPRAM(word_width: Int, addr_bits: Int, memory_init_file: Option[String])
    extends Module {
  import Consts._

  val io = IO(new Bundle {
    val read = new ReadPortIO(word_width, addr_bits);
    val write = new WritePortIO(word_width, addr_bits);
  })

  val mem = Mem(1 << addr_bits, UInt(word_width.W))

  memory_init_file match {
    case Some(path) =>
      loadMemoryFromFileInline(mem, path)
    case None =>
    // nothing to do
  }

  io.read.bits := RegNext(MuxCase(
    mem.read(io.read.addr),
    Seq((io.write.enable && io.write.addr === io.read.addr) -> io.write.bits)
  ))

  when(io.write.enable) {
    mem.write(io.write.addr, io.write.bits)
  }
}
