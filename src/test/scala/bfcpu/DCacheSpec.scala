package bfcpu

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

/** This is a trivial example of how to run this Specification From within sbt
  * use:
  * {{{
  * testOnly bfcpu.TopSpec
  * }}}
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly bfcpu.TopSpec'
  * }}}
  * Testing from mill:
  * {{{
  * mill bfcpu.test.testOnly bfcpu.TopSpec
  * }}}
  */

class DCacheWithMem extends Module {
  import Consts._
  val io = IO(new Bundle {
    val ctrl = new DCacheIO(WORD_BITS, DMEM_ADDR_SIZE)
    val rbits = Output(UInt(WORD_BITS.W))
    val rbits_m1 = Output(UInt(WORD_BITS.W))
    val rbits_p1 = Output(UInt(WORD_BITS.W))
    val addr = Output(UInt(DMEM_ADDR_SIZE.W))
    val addr_next = Output(UInt(DMEM_ADDR_SIZE.W))
    val read_addr_delay1 = Output(UInt(DMEM_ADDR_SIZE.W))
    val read_addr = Output(UInt(DMEM_ADDR_SIZE.W))
    val read_enable = Output(Bool())
    val read_bits = Output(UInt(WORD_BITS.W))
    val write_addr = Output(UInt(DMEM_ADDR_SIZE.W))
    val write_enable = Output(Bool())
    val write_bits = Output(UInt(WORD_BITS.W))
  })
  val dcache = Module(new DCache(WORD_BITS, DMEM_ADDR_SIZE))
  val mem = Module(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None))
  dcache.io.mem_read_port <> mem.io.read
  dcache.io.mem_write_port <> mem.io.write
  io.ctrl <> dcache.io.ctrl
  io.rbits := dcache.io.rbits
  io.rbits_m1 := dcache.io.rbits_m1
  io.rbits_p1 := dcache.io.rbits_p1
  io.addr := dcache.io.addr
  io.addr_next := dcache.io.addr_next
  io.read_addr_delay1 := dcache.io.read_addr_delay1
  io.read_addr := dcache.io.mem_read_port.addr
  io.read_enable := dcache.io.mem_read_port.enable
  io.read_bits := dcache.io.mem_read_port.bits
  io.write_addr := dcache.io.mem_write_port.addr
  io.write_enable := dcache.io.mem_write_port.enable
  io.write_bits := dcache.io.mem_write_port.bits
}

class DCacheSpec extends AnyFreeSpec with ChiselScalatestTester {
  import Consts._

  "DCache" - {
    "should forward previous value" in {
      test(new DCache(WORD_BITS, DMEM_ADDR_SIZE)) { dcache =>
        dcache.io.ctrl.reset.poke(true.B)
        dcache.io.ctrl.wenable.poke(false.B)
        dcache.io.ctrl.wbits.poke(0.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.addr_dec.poke(false.B)
        dcache.io.mem_read_port.bits.poke(0.U)

        dcache.clock.step()
        dcache.io.ctrl.reset.poke(false.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.wbits.poke(0x42.U)
        dcache.io.ctrl.wenable.poke(true.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.addr_inc.poke(true.B)
        dcache.io.mem_read_port.enable.expect(true.B)
        dcache.io.mem_read_port.addr.expect(2.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0x42.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.wbits.poke(0x10.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x10.U)
        dcache.io.rbits_m1.expect(0x42.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.addr_dec.poke(true.B)
        dcache.io.mem_read_port.bits.poke(0.U)
        dcache.io.mem_read_port.enable.expect(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0x10.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.wenable.poke(false.B)
        dcache.io.ctrl.addr_dec.poke(false.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0x10.U)
        dcache.io.addr.expect(0.U)
        dcache.io.mem_read_port.bits.poke(0.U)
        dcache.io.mem_read_port.enable.expect(false.B)
      }
    }
    "should work with wenable" in {
      test(new DCache(WORD_BITS, DMEM_ADDR_SIZE)) { dcache =>
        dcache.io.ctrl.reset.poke(true.B)
        dcache.io.ctrl.wenable.poke(false.B)
        dcache.io.ctrl.wbits.poke(0.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.addr_dec.poke(false.B)
        dcache.io.mem_read_port.bits.poke(0.U)

        dcache.clock.step()
        dcache.io.ctrl.reset.poke(false.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.ctrl.wbits.poke(0x42.U)
        dcache.io.ctrl.wenable.poke(true.B)
        dcache.io.mem_read_port.enable.expect(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.ctrl.wbits.poke(0x10.U)
        dcache.io.ctrl.wenable.poke(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.ctrl.wbits.poke(0x20.U)
        dcache.io.ctrl.wenable.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x20.U)
      }
    }
    "should work with SDPRAM" in {
      test(new DCacheWithMem()) { dcache =>
        dcache.io.ctrl.reset.poke(true.B)
        dcache.io.ctrl.wenable.poke(false.B)
        dcache.io.ctrl.wbits.poke(0.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.addr_dec.poke(false.B)

        dcache.clock.step()
        dcache.io.ctrl.reset.poke(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.wbits.poke(0x42.U)
        dcache.io.ctrl.wenable.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.addr_inc.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.wbits.poke(0x10.U)
        dcache.io.ctrl.addr_inc.poke(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x10.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.addr_dec.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.addr_dec.poke(false.B)

        dcache.clock.step()
        dcache.io.addr.expect(0.U)
      }
    }
    "should restore value from SDPRAM" in {
      test(new DCacheWithMem()) { dcache =>
        dcache.io.ctrl.reset.poke(true.B)
        dcache.io.ctrl.wenable.poke(false.B)
        dcache.io.ctrl.wbits.poke(0.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.addr_dec.poke(false.B)

        dcache.clock.step()
        dcache.io.ctrl.reset.poke(false.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.wbits.poke(0x42.U)
        dcache.io.ctrl.wenable.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0x42.U)
        dcache.io.addr.expect(0.U)
        dcache.io.ctrl.addr_inc.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0x42.U)
        dcache.io.addr.expect(1.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.addr.expect(2.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.wbits.poke(0x08.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0x08.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(2.U)
        dcache.io.ctrl.wbits.poke(0x18.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0x18.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(2.U)
        dcache.io.ctrl.addr_inc.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0x18.U)
        dcache.io.addr.expect(3.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_m1.expect(0.U)
        dcache.io.addr.expect(4.U)
        dcache.io.ctrl.addr_inc.poke(false.B)
        dcache.io.ctrl.wbits.poke(0x10.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0x10.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(4.U)
        dcache.io.ctrl.addr_dec.poke(true.B)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_p1.expect(0x10.U)
        dcache.io.addr.expect(3.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0x18.U)
        dcache.io.rbits_p1.expect(0.U)
        dcache.io.addr.expect(2.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0.U)
        dcache.io.rbits_p1.expect(0x18.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.addr_dec.poke(false.B)
        dcache.io.ctrl.wbits.poke(0x20.U)

        dcache.clock.step()
        dcache.io.rbits.expect(0x20.U)
        dcache.io.rbits_p1.expect(0x18.U)
        dcache.io.rbits_m1.expect(0x42.U)
        dcache.io.addr.expect(1.U)
        dcache.io.ctrl.wbits.poke(0x30.U)

        dcache.clock.step()
        dcache.io.addr.expect(1.U)
        dcache.io.rbits.expect(0x30.U)
        dcache.io.rbits_p1.expect(0x18.U)
        dcache.io.rbits_m1.expect(0x42.U)
      }
    }
  }
}
