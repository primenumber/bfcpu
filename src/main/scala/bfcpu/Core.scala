package bfcpu

import chisel3._
import chisel3.util._

object BFCPU {
  object State extends ChiselEnum {
    val sReset, sReady, sExecuting, sFindingOpen, sFindingClose, sFinished =
      Value
  }
}

class ControlIO extends Bundle {
  val reset = Input(Bool())
  val ready = Output(Bool())
  val start = Input(Bool())
  val finished = Output(Bool())
}

class StatusReg extends Bundle {
  import Consts._
  val inst_addr = Output(UInt(IMEM_ADDR_SIZE.W))
  val data_addr = Output(UInt(DMEM_ADDR_SIZE.W))
  val inst = Output(UInt(WORD_BITS.W))
  val data = Output(UInt(WORD_BITS.W))
  val bracket_count = Output(UInt(IMEM_ADDR_SIZE.W))
}

class Core extends Module {
  import BFCPU.State
  import BFCPU.State._
  import Consts._

  val io = IO(new Bundle {
    val ctrl = new ControlIO()
    val imem = Flipped(new SPRAMIO())
    val dmem = Flipped(new SDPRAMIO(WORD_BITS, DMEM_ADDR_SIZE))
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
  })

  val state = RegInit(sReset)
  val inst_addr_reg = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val data_addr_reg = RegInit(0.U(DMEM_ADDR_SIZE.W))
  val data_waddr_reg = RegInit(0.U(DMEM_ADDR_SIZE.W))
  val count_bracket_reg = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val data_wen_reg = RegInit(false.B)
  val wdata_reg = RegInit(0.U(WORD_BITS.W))
  val finished_reg = RegInit(false.B)

  val data = io.dmem.rdata
  val ready = state === sReady
  val inst = io.imem.dataOut
  val inst_addr_p1 = inst_addr_reg + 1.U
  val inst_addr_m1 = inst_addr_reg - 1.U
  val in_ready = (state === sExecuting && inst === Insts.COMMA)
  val data_next_valid = (inst =/= Insts.COMMA || io.in.valid)
  val output_valid = (state === sExecuting && inst === Insts.PERIOD)
  val block = inst === Insts.COMMA && !io.in.valid

  val finding_bracket = MuxCase(
    0.U,
    Seq(
      (state === sFindingOpen) -> Insts.OPEN,
      (state === sFindingClose) -> Insts.CLOSE
    )
  )
  val anti_bracket = MuxCase(
    0.U,
    Seq(
      (state === sFindingOpen) -> Insts.CLOSE,
      (state === sFindingClose) -> Insts.OPEN
    )
  )

  val data_next = MuxCase(
    data,
    Seq(
      (inst === Insts.PLUS) -> (data + 1.U),
      (inst === Insts.MINUS) -> (data - 1.U),
      (inst === Insts.COMMA) -> io.in.bits
    )
  )

  val data_addr_next = MuxCase(
    data_addr_reg,
    Seq(
      (inst === Insts.RIGHT) -> (data_addr_reg + 1.U),
      (inst === Insts.LEFT) -> (data_addr_reg - 1.U)
    )
  )

  val inst_addr_next = MuxCase(
    inst_addr_p1,
    Seq(
      (state === sFindingOpen) -> inst_addr_m1
    )
  )

  val count_bracket_next = MuxCase(
    count_bracket_reg,
    Seq(
      (inst === finding_bracket) -> (count_bracket_reg - 1.U),
      (inst === anti_bracket) -> (count_bracket_reg + 1.U)
    )
  )

  io.ctrl.ready := ready
  io.ctrl.finished := finished_reg
  io.imem.addr := inst_addr_reg
  io.imem.enable := true.B
  io.imem.write := false.B
  io.imem.dataIn := 0.U
  io.dmem.ren := true.B
  io.dmem.raddr := data_addr_reg
  io.dmem.waddr := data_waddr_reg
  io.dmem.wdata := wdata_reg
  io.dmem.wen := data_wen_reg
  io.in.ready := in_ready
  io.out.valid := output_valid
  io.out.bits := data

  io.status.inst_addr := inst_addr_reg
  io.status.inst := inst
  io.status.data_addr := data_addr_reg
  io.status.data := data
  io.status.bracket_count := count_bracket_reg
  // io.status.state := state

  data_wen_reg := (state === sExecuting && data_next_valid)
  finished_reg := (state === sFinished)
  data_waddr_reg := data_addr_reg

  when(io.ctrl.reset) {
    state := sReset
  }.otherwise {
    switch(state) {
      is(sReset) {
        state := sReady
      }
      is(sReady) {
        when(io.ctrl.start) {
          inst_addr_reg := 0.U
          data_addr_reg := 0.U
          state := sExecuting
        }
      }
      is(sExecuting) {
        when(inst === 0.U) {
          state := sFinished
        }.elsewhen(inst === Insts.OPEN && data === 0.U) {
          state := sFindingClose
          count_bracket_reg := 1.U
          inst_addr_reg := inst_addr_p1
          wdata_reg := data_next
        }.elsewhen(inst === Insts.CLOSE && data =/= 0.U) {
          state := sFindingOpen
          count_bracket_reg := 1.U
          inst_addr_reg := inst_addr_m1
          wdata_reg := data_next
        }.elsewhen(data_next_valid) {
          inst_addr_reg := inst_addr_next
          data_addr_reg := data_addr_next
          wdata_reg := data_next
        }.otherwise {
          // nothing to do
        }
      }
      is(sFindingOpen) {
        when(count_bracket_next === 0.U) {
          state := sExecuting
          inst_addr_reg := inst_addr_p1
        }.otherwise {
          inst_addr_reg := inst_addr_next
          count_bracket_reg := count_bracket_next
        }
      }
      is(sFindingClose) {
        when(count_bracket_next === 0.U) {
          state := sExecuting
          inst_addr_reg := inst_addr_p1
        }.otherwise {
          inst_addr_reg := inst_addr_next
          count_bracket_reg := count_bracket_next
        }
      }
    }
  }
}
