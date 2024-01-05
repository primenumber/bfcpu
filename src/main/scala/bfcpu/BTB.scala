package bfcpu

import chisel3._
import chisel3.util._

class BTBQueryIO(word_width: Int, addr_bits: Int) extends Bundle {
  val enable = Input(Bool())
  val addr = Input(UInt(addr_bits.W))
  val valid = Output(Bool())
  val target_addr = Output(UInt(addr_bits.W))
  val next_inst = Output(UInt(word_width.W))
}

class BTBUpdateIO(word_width: Int, addr_bits: Int) extends Bundle {
  val valid = Input(Bool())
  val addr = Input(UInt(addr_bits.W))
  val target_addr = Input(UInt(addr_bits.W))
  val next_inst = Input(UInt(word_width.W))
}

object BTB {
  object BTBState extends ChiselEnum {
    val sReset, sReady = Value
  }
}

// Branch Target Buffer
class BranchTargetBuffer(word_width: Int, addr_bits: Int, depth_bits: Int)
    extends Module {
  import BTB.BTBState._

  // Cat(valid, tag, target_addr, next_inst)
  val tag_bits = addr_bits - depth_bits
  val entry_width = 1 + tag_bits + addr_bits + word_width

  val io = IO(new Bundle {
    val reset = Input(Bool())
    val ready = Output(Bool())
    val query = new BTBQueryIO(word_width, addr_bits)
    val update = new BTBUpdateIO(word_width, addr_bits)
    val cmem_read = Flipped(new ReadPortIO(entry_width, depth_bits))
    val cmem_write = Flipped(new WritePortIO(entry_width, depth_bits))
  })

  val reset_addr = RegInit(0.U(addr_bits.W))
  val state = RegInit(sReady)

  // Write

  when(state === sReset) {
    when(reset_addr < ((1 << depth_bits) - 1).U) {
      reset_addr := reset_addr + 1.U
    }.otherwise {
      state := sReady
    }
    io.cmem_write.addr := reset_addr
    io.cmem_write.bits := 0.U
    io.cmem_write.enable := true.B
  }.elsewhen(io.reset) {
    state := sReset
    reset_addr := 0.U
    io.cmem_write.addr := DontCare
    io.cmem_write.bits := DontCare
    io.cmem_write.enable := false.B
  }.elsewhen(io.update.valid) {
    val key = io.update.addr(addr_bits - 1, tag_bits)
    val tag = io.update.addr(tag_bits - 1, 0)
    io.cmem_write.addr := key
    io.cmem_write.bits := Cat(
      true.B,
      tag,
      io.update.target_addr,
      io.update.next_inst
    )
    io.cmem_write.enable := true.B
  }.otherwise {
    io.cmem_write.addr := DontCare
    io.cmem_write.bits := DontCare
    io.cmem_write.enable := false.B
  }

  // Read

  val query_addr_delay = RegNext(RegNext(io.query.addr))
  io.cmem_read.addr := io.query.addr(addr_bits - 1, tag_bits)
  io.cmem_read.enable := io.query.enable
  val data = io.cmem_read.bits
  val valid = data(entry_width - 1)
  val tag = data(tag_bits + addr_bits + word_width - 1, addr_bits + word_width)
  val target_addr = data(addr_bits + word_width - 1, word_width)
  val next_inst = data(word_width - 1, 0)
  io.query.valid := valid && tag === query_addr_delay(tag_bits - 1, 0)
  io.query.target_addr := target_addr
  io.query.next_inst := next_inst

  // ready

  io.ready := state === sReady
}
