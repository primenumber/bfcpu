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
    test(new Top(Some("Hanoi4.bf.hex"))) { top =>
      top.io.in.initSource()
      top.io.in.setSourceClock(top.clock)
      top.io.out.initSink()
      top.io.out.setSinkClock(top.clock)

      top.io.ctrl.reset.poke(true.B)
      top.clock.step(10)
      top.io.ctrl.reset.poke(false.B)
      top.clock.step(1 << BTB_ADDR_SIZE)
      top.io.ctrl.ready.expect(true.B)
      top.io.ctrl.start.poke(true.B)
      top.clock.setTimeout(0)

      val status = top.io.status
      val btb_s = top.io.btb_status
      top.clock.step(1)
      var cycles_exec = 0
      var cycles_fb = 0
      //var printed = 0
      for (i <- 1 to 21000) {
        val imem_addr = status.imem_addr.peek().litValue
        val isfb = status.state_onehot.finding_bracket.peek().litValue
        val isfetch = status.state_onehot.fetch.peek().litValue
        val isexec = status.state_onehot.executing.peek().litValue
        val isfin = status.state_onehot.finished.peek().litValue
        val btb_valid = btb_s.valid.peek().litValue
        val btb_target_addr = btb_s.target_addr.peek().litValue
        val btb_next_inst = btb_s.next_inst.peek().litValue
        //if (printed < 1000) {
        //  println(s"${isexec} ${imem_addr} ${btb_valid} ${btb_target_addr} ${btb_next_inst}")
        //  printed += 1
        //}
        if (isfetch == 1 || isexec == 1) {
          cycles_exec += 1
        }
        if (isfb == 1) {
          cycles_fb += 1
        }
        top.clock.step(1)
      }
      println(
        s"execute: ${cycles_exec} cycles, finding bracket: ${cycles_fb} cycles"
      )

      top.io.ctrl.finished.expect(true.B)
      println("execution finished")

      val ex_path = os.pwd / "Hanoi4.bf.out.hex"
      val lines = os.read.lines(ex_path)
      top.io.out.ready.poke(true.B)
      for (ex_str <- lines) {
        if (ex_str != "") {
          val ex = Integer.parseInt(ex_str, 16)
          top.io.out.valid.expect(true.B)
          top.io.out.bits.expect(ex.U)
          top.clock.step()
        }
      }
      top.io.out.valid.expect(false.B)
    }
  }
}
