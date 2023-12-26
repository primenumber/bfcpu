package bfcpu

import chisel3._
import chisel3.util._

class DCacheIO(word_width: Int, addr_bits: Int) extends Bundle {
  val reset = Input(Bool())
  val rbits = Output(UInt(word_width.W))
  val wenable = Input(Bool())
  val wbits = Input(UInt(word_width.W))
  val addr_inc = Input(Bool())
  val addr_dec = Input(Bool())
}

class DCache(word_width: Int, addr_bits: Int) extends Module {
  val io = IO(new Bundle {
    val ctrl = new DCacheIO(word_width, addr_bits)
    val mem_read_port = Flipped(new ReadPortIO(word_width, addr_bits))
    val mem_write_port = Flipped(new WritePortIO(word_width, addr_bits))
  })

  val addr = RegInit(0.U(addr_bits.W))
  val regs = Seq.fill(3)(RegInit(0.U(word_width.W)))

  val next_addr = MuxCase(
    addr,
    Seq(
      io.ctrl.addr_inc -> (addr + 1.U),
      io.ctrl.addr_dec -> (addr - 1.U)
    )
  )

  val next_read_enable = MuxCase(
    false.B,
    Seq(
      io.ctrl.addr_inc -> true.B,
      io.ctrl.addr_dec -> true.B
    )
  )
  val read_enable_delay1 = RegNext(next_read_enable)
  val read_enable_delay2 = RegNext(read_enable_delay1)

  val next_read_addr = MuxCase(
    addr,
    Seq(
      io.ctrl.addr_inc -> (addr + 2.U),
      io.ctrl.addr_dec -> (addr - 2.U)
    )
  )
  val read_addr_delay1 = RegNext(next_read_addr)
  val read_addr_delay2 = RegNext(read_addr_delay1)

  val next_reg0 = MuxCase(
    regs(0),
    Seq(io.ctrl.addr_inc -> regs(1), io.ctrl.addr_dec -> DontCare)
  )

  val next_reg1 = MuxCase(
    regs(1),
    Seq(io.ctrl.addr_inc -> regs(2), io.ctrl.addr_dec -> regs(0))
  )

  val next_reg2 = MuxCase(
    regs(2),
    Seq(io.ctrl.addr_inc -> DontCare, io.ctrl.addr_dec -> regs(1))
  )

  io.mem_write_port.bits := io.ctrl.wbits
  io.ctrl.rbits := regs(1)
  when(io.ctrl.reset) {
    addr := 0.U
    regs(0) := 0.U
    regs(1) := 0.U
    regs(2) := 0.U
    io.mem_read_port.enable := false.B
    io.mem_read_port.addr := 0.U
    io.mem_write_port.enable := false.B
    io.mem_write_port.addr := 0.U
  }.otherwise {
    io.mem_read_port.addr := next_read_addr
    io.mem_read_port.enable := next_read_enable
    io.mem_write_port.addr := addr
    io.mem_write_port.enable := io.ctrl.wenable
    regs(0) := Mux(
      read_addr_delay1 === next_addr - 1.U && read_enable_delay1,
      io.mem_read_port.bits,
      next_reg0
    )
    regs(1) := MuxCase(
      next_reg1, Seq(
        (addr === next_addr && io.ctrl.wenable) -> io.ctrl.wbits,
        (read_addr_delay1 === next_addr && read_enable_delay1) -> io.mem_read_port.bits,
      )
    )
    regs(2) := Mux(
      read_addr_delay1 === next_addr + 1.U && read_enable_delay1,
      io.mem_read_port.bits,
      next_reg2
    )
    addr := next_addr
  }
}
