package bfcpu

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

/** This is a trivial example of how to run this Specification From within sbt
  * use:
  * {{{
  * testOnly bfcpu.BTBSpec
  * }}}
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly bfcpu.BTBSpec'
  * }}}
  * Testing from mill:
  * {{{
  * mill bfcpu.test.testOnly bfcpu.BTBSpec
  * }}}
  */

class BTBWithMem(word_width: Int, addr_bits: Int, depth_bits: Int) extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val ready = Output(Bool())
    val query = new BTBQueryIO(word_width, addr_bits)
    val update = new BTBUpdateIO(word_width, addr_bits)
  })
  val btb_word_size = 1 + 2 * addr_bits - depth_bits + word_width
  val btb = Module(new BranchTargetBuffer(word_width, addr_bits, depth_bits))
  val mem = Module(new SDPRAM(btb_word_size, depth_bits, false, None))
  btb.io.cmem_read :<>= mem.io.read
  btb.io.cmem_write :<>= mem.io.write
  btb.io.reset := io.reset
  io.ready := btb.io.ready
  io.query :<>= btb.io.query
  io.update :<>= btb.io.update
}

class BTBSpec extends AnyFreeSpec with ChiselScalatestTester {
  import Consts._
  import Insts._

  "BTB" - {
    "should cache target address and next instruction" in {
      test(new BTBWithMem(WORD_BITS, DMEM_ADDR_SIZE, 2)) { btb =>
        btb.io.reset.poke(true.B)

        btb.clock.step(1)
        btb.io.reset.poke(false.B)

        btb.clock.step(1)
        btb.io.ready.expect(false.B)

        btb.clock.step(2)
        btb.io.ready.expect(false.B)

        btb.clock.step(1)
        btb.io.ready.expect(true.B)
        btb.io.update.valid.poke(true.B)
        btb.io.update.addr.poke(0x2FED.U)
        btb.io.update.target_addr.poke(0x3184.U)
        btb.io.update.next_inst.poke(Insts.PLUS)

        btb.clock.step(1)
        btb.io.update.valid.poke(false.B)
        btb.io.update.addr.poke(64.U)
        btb.io.update.target_addr.poke(42.U)
        btb.io.update.next_inst.poke(Insts.OPEN)
        btb.io.query.enable.poke(true.B)
        btb.io.query.addr.poke(0x2FED.U)

        btb.clock.step(1)
        btb.io.query.enable.poke(false.B)

        btb.clock.step(1)
        btb.io.query.enable.poke(false.B)
        
        btb.clock.step(1)
        btb.io.query.valid.expect(true.B)
        btb.io.query.target_addr.expect(0x3184.U)
        btb.io.query.next_inst.expect(Insts.PLUS)
      }
    }
    "should not return irrelevant result" in {
      test(new BTBWithMem(WORD_BITS, DMEM_ADDR_SIZE, 2)) { btb =>
        btb.io.reset.poke(true.B)

        btb.clock.step(1)
        btb.io.reset.poke(false.B)

        btb.clock.step(1)
        btb.io.ready.expect(false.B)

        btb.clock.step(2)
        btb.io.ready.expect(false.B)

        btb.clock.step(1)
        btb.io.ready.expect(true.B)
        btb.io.update.valid.poke(true.B)
        btb.io.update.addr.poke(0x2FED.U)
        btb.io.update.target_addr.poke(0x3184.U)
        btb.io.update.next_inst.poke(Insts.PLUS)

        btb.clock.step(1)
        btb.io.update.valid.poke(false.B)
        btb.io.update.addr.poke(64.U)
        btb.io.update.target_addr.poke(42.U)
        btb.io.update.next_inst.poke(Insts.OPEN)
        btb.io.query.enable.poke(true.B)
        btb.io.query.addr.poke(0x2F6D.U)

        btb.clock.step(1)
        btb.io.query.enable.poke(false.B)

        btb.clock.step(1)
        btb.io.query.enable.poke(false.B)
        
        btb.clock.step(1)
        btb.io.query.valid.expect(false.B)
      }
    }
  }
}

