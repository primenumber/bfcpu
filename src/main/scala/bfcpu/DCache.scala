package bfcpu

import chisel3._
import chisel3.util._

class DCacheIO(word_width: Int, addr_bits: Int) extends Bundle {
  val reset = Input(Bool())
  val wenable = Input(Bool())
  val wbits = Input(UInt(word_width.W))
  val addr_inc = Input(Bool())
  val addr_dec = Input(Bool())
}

class DCacheStateIO(word_width: Int, addr_bits: Int) extends Bundle {
  val rbits_m1 = Output(UInt(word_width.W))
  val rbits_p1 = Output(UInt(word_width.W))
  val addr = Output(UInt(addr_bits.W))
  val addr_next = Output(UInt(addr_bits.W))
  val read_addr_delay1 = Output(UInt(addr_bits.W))
}

class DCache(word_width: Int, addr_bits: Int, reg_length: Int) extends Module {
  val io = IO(new Bundle {
    val ctrl = new DCacheIO(word_width, addr_bits)
    val rbits = Output(UInt(word_width.W))
    val mem_read_port = Flipped(new ReadPortIO(word_width, addr_bits))
    val mem_write_port = Flipped(new WritePortIO(word_width, addr_bits))
    val state = new DCacheStateIO(word_width, addr_bits)
  })

  val addr = RegInit(0.U(addr_bits.W))
  val regs = Seq.fill(reg_length)(RegInit(0.U(word_width.W)))
  val middle = reg_length / 2

  val next_addr = MuxCase(
    addr,
    Seq(
      io.ctrl.addr_inc -> (addr + 1.U),
      io.ctrl.addr_dec -> (addr - 1.U)
    )
  )

  val next_read_enable = io.ctrl.addr_inc || io.ctrl.addr_dec
  val read_enable_delay1 = RegNext(next_read_enable)

  val next_read_addr = MuxCase(
    addr,
    Seq(
      io.ctrl.addr_inc -> (addr + 2.U),
      io.ctrl.addr_dec -> (addr - 2.U)
    )
  )
  val read_addr_delay1 = RegNext(next_read_addr)

  val next_regs =
    for ((x, i) <- regs.zipWithIndex)
      yield {
        val inc_reg = if (i < reg_length - 1) {
          regs(i + 1)
        } else {
          DontCare
        }
        val dec_reg = if (i > 0) {
          regs(i - 1)
        } else {
          DontCare
        }
        MuxCase(
          x,
          Seq(
            ((i == middle).B && addr === next_addr && io.ctrl.wenable) -> io.ctrl.wbits,
            if (i > middle) {
              (read_addr_delay1 === next_addr + (i - middle).U && read_enable_delay1) -> io.mem_read_port.bits
            } else {
              (read_addr_delay1 + (middle - i).U === next_addr && read_enable_delay1) -> io.mem_read_port.bits
            },
            io.ctrl.addr_inc -> inc_reg,
            io.ctrl.addr_dec -> dec_reg
          )
        )
      }

  io.rbits := regs(middle)

  // states
  io.state.rbits_m1 := regs(middle - 1)
  io.state.rbits_p1 := regs(middle + 1)
  io.state.addr := addr
  io.state.addr_next := next_addr
  io.state.read_addr_delay1 := read_addr_delay1

  when(io.ctrl.reset) {
    addr := 0.U
    for (reg <- regs) {
      reg := 0.U
    }
    io.mem_read_port.enable := false.B
    io.mem_read_port.addr := 0.U
    io.mem_write_port.enable := false.B
    io.mem_write_port.addr := 0.U
    io.mem_write_port.bits := 0.U
  }.otherwise {
    io.mem_read_port.addr := next_read_addr
    io.mem_read_port.enable := next_read_enable
    io.mem_write_port.addr := addr
    io.mem_write_port.enable := true.B
    io.mem_write_port.bits := regs(middle)
    for ((reg, next) <- regs.zip(next_regs)) {
      reg := next
    }
    addr := next_addr
  }
}
