package bfcpu

import chisel3._
import chisel3.util._

class Top(memory_init_file: Option[String]) extends Module {
  import Consts._

  val io = IO(new Bundle {
    val ctrl = new ControlIO()
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
    val imem_write = new WritePortIO(WORD_BITS, IMEM_ADDR_SIZE)
  })

  val imem = Module(
    new SDPRAM(WORD_BITS, IMEM_ADDR_SIZE, true, memory_init_file)
  )
  val dmem = Module(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None))
  val core = Module(new Core())
  val out_queue = Queue(core.io.out, 1024)
  val in_queue = Queue(io.in, 1024)
  val dcache = Module(new DCache(WORD_BITS, DMEM_ADDR_SIZE, 3))

  core.io.imem_read :<>= imem.io.read
  dcache.io.mem_read_port :<>= dmem.io.read
  dcache.io.mem_write_port :<>= dmem.io.write
  core.io.dcache_ctrl :<>= dcache.io.ctrl
  core.io.dcache_rbits := dcache.io.rbits

  io.imem_write :<>= imem.io.write
  io.ctrl :<>= core.io.ctrl
  core.io.in :<>= in_queue
  io.out :<>= out_queue
  io.status :<>= core.io.status
}

object Top {
  def main(args: Array[String]): Unit = {
    emitVerilog(new Top(None))
  }
}
