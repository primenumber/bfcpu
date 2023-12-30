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

class SDPRAM(
    word_width: Int,
    addr_bits: Int,
    reg: Boolean,
    memory_init_file: Option[String]
) extends Module {
  import Consts._

  val io = IO(new Bundle {
    val read = new ReadPortIO(word_width, addr_bits)
    val write = new WritePortIO(word_width, addr_bits)
  })

  val read_addr_delay1 = RegNext(io.read.addr)

  val mem = SyncReadMem(1 << addr_bits, UInt(word_width.W))

  memory_init_file.map(loadMemoryFromFileInline(mem, _))

  if (reg) {
    io.read.bits := RegNext(mem.read(io.read.addr, io.read.enable))
  } else {
    io.read.bits := mem.read(io.read.addr, io.read.enable)
  }

  when(io.write.enable) {
    mem.write(io.write.addr, io.write.bits)
  }
}
