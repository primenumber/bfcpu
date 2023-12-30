package bfcpu

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

/** This is a trivial example of how to run this Specification From within sbt
  * use:
  * {{{
  * testOnly bfcpu.SDPRAMSpec
  * }}}
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly bfcpu.SDPRAMSpec'
  * }}}
  * Testing from mill:
  * {{{
  * mill bfcpu.test.testOnly bfcpu.SDPRAMSpec
  * }}}
  */
class SDPRAMSpec extends AnyFreeSpec with ChiselScalatestTester {
  import Consts._

  "SDPRAM" - {
    "should work with addresses" in {
      test(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None)) { mem =>
        mem.io.read.enable.poke(false.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.enable.poke(true.B)
        mem.io.write.addr.poke(0.U)
        mem.io.write.bits.poke(0x10.U)

        mem.clock.step()
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.addr.poke(1.U)
        mem.io.write.bits.poke(0x20.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x10.U)
        mem.io.read.enable.poke(false.B)
        mem.io.write.addr.poke(2.U)
        mem.io.write.bits.poke(0x30.U)

        mem.clock.step()
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(2.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x30.U)
        mem.io.read.addr.poke(1.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x20.U)
      }
    }
    "should work with forwarding" in {
      test(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None)) { mem =>
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.enable.poke(true.B)
        mem.io.write.addr.poke(0.U)
        mem.io.write.bits.poke(0x10.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x10.U)
        mem.io.read.addr.poke(1.U)
        mem.io.write.addr.poke(1.U)
        mem.io.write.bits.poke(0x20.U)

        mem.clock.step()
        mem.io.read.addr.poke(2.U)
        mem.io.write.addr.poke(3.U)
        mem.io.read.bits.expect(0x20.U)
      }
    }
    "should work read after write" in {
      test(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, false, None)) { mem =>
        mem.io.read.enable.poke(false.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.enable.poke(true.B)
        mem.io.write.addr.poke(0.U)
        mem.io.write.bits.poke(0x10.U)

        mem.clock.step()
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.addr.poke(1.U)
        mem.io.write.bits.poke(0x20.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x10.U)
        mem.io.read.addr.poke(1.U)
        mem.io.write.addr.poke(3.U)
        mem.io.write.bits.poke(0x30.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x20.U)
      }
    }
    "should work with reg mode" in {
      test(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, true, None)) { mem =>
        mem.io.read.enable.poke(false.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.enable.poke(true.B)
        mem.io.write.addr.poke(0.U)
        mem.io.write.bits.poke(0x10.U)

        mem.clock.step()
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.addr.poke(1.U)
        mem.io.write.bits.poke(0x20.U)

        mem.clock.step()
        mem.io.read.enable.poke(false.B)
        mem.io.write.addr.poke(2.U)
        mem.io.write.bits.poke(0x30.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x10.U)
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(2.U)

        mem.clock.step()
        mem.io.read.addr.poke(1.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x30.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x20.U)
      }
    }
    "should work with forwarding and reg mode" in {
      test(new SDPRAM(WORD_BITS, DMEM_ADDR_SIZE, true, None)) { mem =>
        mem.io.read.enable.poke(true.B)
        mem.io.read.addr.poke(0.U)
        mem.io.write.enable.poke(true.B)
        mem.io.write.addr.poke(0.U)
        mem.io.write.bits.poke(0x10.U)

        mem.clock.step()
        mem.io.read.addr.poke(1.U)
        mem.io.write.addr.poke(1.U)
        mem.io.write.bits.poke(0x20.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x10.U)
        mem.io.read.addr.poke(2.U)
        mem.io.write.addr.poke(3.U)

        mem.clock.step()
        mem.io.read.bits.expect(0x20.U)
      }
    }
  }
}
