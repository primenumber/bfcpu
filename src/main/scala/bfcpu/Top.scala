package bfcpu

import chisel3._
import chisel3.util._

class BTBStatus(word_width: Int, addr_bits: Int) extends Bundle {
  val valid = Output(Bool())
  val target_addr = Output(UInt(addr_bits.W))
  val next_inst = Output(UInt(word_width.W))
}

class Top(memory_init_file: Option[String]) extends Module {
  import Consts._

  val io = IO(new Bundle {
    val ctrl = new ControlIO()
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
    val imem_write = new WritePortIO(WORD_BITS, IMEM_ADDR_SIZE)
    val btb_status = new BTBStatus(WORD_BITS, IMEM_ADDR_SIZE)
  })

  val imem = Module(
    new SDPRAM(WORD_BITS, IMEM_ADDR_SIZE, true, memory_init_file)
  )
  val btb_mem = Module(new SDPRAM(BTB_WORD_SIZE, BTB_ADDR_SIZE, true, None))
  val dmem = Module(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None))
  val core = Module(new Core())
  val out_queue = Queue(core.io.out, 1024)
  val in_queue = Queue(io.in, 1024)
  val dcache = Module(new DCache(WORD_BITS, DMEM_ADDR_SIZE, 3))
  val btb = Module(new BranchTargetBuffer(WORD_BITS, IMEM_ADDR_SIZE, BTB_ADDR_SIZE))

  core.io.imem_read :<>= imem.io.read
  dcache.io.mem_read_port :<>= dmem.io.read
  dcache.io.mem_write_port :<>= dmem.io.write
  core.io.dcache_ctrl :<>= dcache.io.ctrl
  core.io.dcache_rbits := dcache.io.rbits
  btb.io.cmem_read :<>= btb_mem.io.read
  btb.io.cmem_write :<>= btb_mem.io.write
  core.io.btb_query :<>= btb.io.query
  core.io.btb_update :<>= btb.io.update

  io.imem_write :<>= imem.io.write
  io.ctrl :<>= core.io.ctrl
  core.io.in :<>= in_queue
  io.out :<>= out_queue
  io.status :<>= core.io.status
  io.btb_status.valid := btb.io.query.valid
  io.btb_status.target_addr := btb.io.query.target_addr
  io.btb_status.next_inst := btb.io.query.next_inst
  btb.io.reset := io.ctrl.reset
}

object Top {
  def main(args: Array[String]): Unit = {
    emitVerilog(new Top(None))
  }
}
