package bfcpu

import chisel3._
import chisel3.util._

class Top(memory_init_file: String) extends Module {
  import Consts._

  val io = IO(new Bundle {
    val ctrl = new ControlIO()
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
  })

  val imem = Module(
    new SDPRAM(WORD_BITS, IMEM_ADDR_SIZE, Some(memory_init_file))
  )
  val dmem = Module(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, None))
  val core = Module(new Core())
  val queue = Queue(core.io.out, 1024)

  core.io.imem_read <> imem.io.read
  core.io.dmem_read <> dmem.io.read
  core.io.dmem_write <> dmem.io.write
  io.ctrl <> core.io.ctrl
  io.in <> core.io.in
  io.out <> queue
  io.status <> core.io.status

  // imem write is not used yet
  imem.io.write.addr := 0.U
  imem.io.write.bits := 0.U
  imem.io.write.enable := false.B
}

object Top {
  def main(args: Array[String]): Unit = {
    emitVerilog(new Top("Hanoi4.bf.hex"))
  }
}
