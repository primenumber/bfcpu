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
class TopSpec extends AnyFreeSpec with ChiselScalatestTester {
  import Consts._

  "brainf*ck cpu" in {
    test(new Top("Hanoi4.bf.hex")) { top =>
      top.io.in.initSource()
      top.io.in.setSourceClock(top.clock)
      top.io.out.initSink()
      top.io.out.setSinkClock(top.clock)

      top.io.ctrl.reset.poke(true.B)
      top.clock.step(10)
      top.io.ctrl.reset.poke(false.B)
      top.clock.step(10)
      top.io.ctrl.ready.expect(true.B)
      top.io.ctrl.start.poke(true.B)
      top.clock.setTimeout(0)

      val status = top.io.status
      top.clock.step(1)
      for (i <- 1 to 1001) {
        val pc = status.imem_addr.peek().litValue
        val inst = status.inst.peek().litValue.toChar
        val dp = status.dmem_read_addr.peek().litValue
        val data = status.data.peek().litValue
        val cb = status.bracket_count.peek().litValue
        val isfo = status.state_onehot.finding_open.peek().litValue
        val isfc = status.state_onehot.finding_close.peek().litValue
        val isfetch = status.state_onehot.fetch.peek().litValue
        val isexec = status.state_onehot.executing.peek().litValue
        val iswb = status.state_onehot.writeback.peek().litValue
        if (isexec == 1 || isfc == 1 || isfo == 1) {
          println(
            s"[cycle=${i}]pc: ${pc}, inst: ${inst}, dp: ${dp}, data: ${data}, state: f${isfetch}e${isexec}w${iswb}o${isfo}c${isfc}, bc: ${cb}"
          );
        }
        top.clock.step(1)
      }

      top.clock.step(110000)
      top.io.ctrl.finished.expect(true.B)
      println("execution finished")
      top.io.out.ready.poke(true.B)
      val out_seq = Seq(
        0x41.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x32.U,
        0x0a.U,
        0x42.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x32.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x43.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x32.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x33.U,
        0x20.U,
        0x31.U,
        0x0a.U,
        0x42.U,
        0x20.U,
        0x33.U,
        0x20.U,
        0x32.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x32.U,
        0x0a.U,
        0x44.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x32.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x42.U,
        0x20.U,
        0x32.U,
        0x20.U,
        0x31.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x33.U,
        0x20.U,
        0x31.U,
        0x0a.U,
        0x43.U,
        0x20.U,
        0x32.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x32.U,
        0x0a.U,
        0x42.U,
        0x20.U,
        0x31.U,
        0x20.U,
        0x33.U,
        0x0a.U,
        0x41.U,
        0x20.U,
        0x32.U,
        0x20.U,
        0x33.U,
        0x0a.U
      )
      for (ex <- out_seq) {
        top.io.out.valid.expect(true.B)
        top.io.out.bits.expect(ex)
        top.clock.step()
      }
      top.io.out.valid.expect(false.B)
    }
  }
}
