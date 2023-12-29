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
      top.clock.step(10)
      top.io.ctrl.ready.expect(true.B)
      top.io.ctrl.start.poke(true.B)
      top.clock.setTimeout(0)

      val status = top.io.status
      // top.clock.step(1)
      // for (i <- 1 to 1001) {
      //  val pc = status.imem_addr.peek().litValue
      //  val inst = status.inst.peek().litValue.toChar
      //  val dp = status.dmem_read_addr.peek().litValue
      //  val data = status.data.peek().litValue
      //  val dm1 = status.dm1.peek().litValue
      //  val dp1 = status.dp1.peek().litValue
      //  val cb = status.bracket_count.peek().litValue
      //  val issfb = status.state_onehot.start_find_bracket.peek().litValue
      //  val isfb = status.state_onehot.finding_bracket.peek().litValue
      //  val isfetch = status.state_onehot.fetch.peek().litValue
      //  val isexec = status.state_onehot.executing.peek().litValue
      //  val isfin = status.state_onehot.finished.peek().litValue
      //  if (isfin == 0) {
      //    println(
      //      s"[cycle=${i}]pc: ${pc}, inst: ${inst}, dp: ${dp}, data: ${dm1}-${data}-${dp1}, state: f${isfetch}e${isexec}o${issfb}c${isfb}, bc: ${cb}"
      //    )
      //  }
      //  top.clock.step(1)
      // }

      top.clock.step(45000)

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
