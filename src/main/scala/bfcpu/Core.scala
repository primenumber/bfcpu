package bfcpu

import chisel3._
import chisel3.util._

object BFCPU {
  object State extends ChiselEnum {
    val sReset, sReady, sFetch, sExecuting, sStartFindBracket, sFindingBracket,
        sFinished =
      Value
  }
}

class ControlIO extends Bundle {
  val reset = Input(Bool())
  val ready = Output(Bool())
  val start = Input(Bool())
  val finished = Output(Bool())
}

class StateOneHot extends Bundle {
  import Consts._
  val reset = Output(Bool())
  val ready = Output(Bool())
  val fetch = Output(Bool())
  val executing = Output(Bool())
  val start_find_bracket = Output(Bool())
  val finding_bracket = Output(Bool())
  val finished = Output(Bool())
}

class StatusReg extends Bundle {
  import Consts._
  val imem_addr = Output(UInt(IMEM_ADDR_SIZE.W))
  val dmem_read_addr = Output(UInt(DMEM_ADDR_SIZE.W))
  val inst = Output(UInt(WORD_BITS.W))
  val data = Output(UInt(WORD_BITS.W))
  val dm1 = Output(UInt(WORD_BITS.W))
  val dp1 = Output(UInt(WORD_BITS.W))
  val bracket_count = Output(UInt(IMEM_ADDR_SIZE.W))
  val state_onehot = new StateOneHot()
}

class Core extends Module {
  import BFCPU.State
  import BFCPU.State._
  import Consts._

  val io = IO(new Bundle {
    val ctrl = new ControlIO()
    val imem_read = Flipped(new ReadPortIO(WORD_BITS, IMEM_ADDR_SIZE))
    val dmem_read = Flipped(new ReadPortIO(WORD_BITS, DMEM_ADDR_SIZE))
    val dmem_write = Flipped(new WritePortIO(WORD_BITS, DMEM_ADDR_SIZE))
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
  })

  // DCache

  val dcache = Module(new DCache(WORD_BITS, DMEM_ADDR_SIZE))
  dcache.io.mem_read_port <> io.dmem_read
  dcache.io.mem_write_port <> io.dmem_write

  // Registers

  val state = RegInit(sReset)

  val if_reg_imem_addr = RegInit(0.U(IMEM_ADDR_SIZE.W))

  val ex_reg_imem_addr = RegNext(if_reg_imem_addr)
  val reg_count_bracket = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val reg_finding_bracket = RegInit(0.U(WORD_BITS.W))
  val reg_finished = RegInit(false.B)

  val inst = io.imem_read.bits
  val data = dcache.io.rbits
  val in_ready = (state === sExecuting && inst === Insts.COMMA)
  val imem_addr_p1 = if_reg_imem_addr + 1.U
  val imem_addr_m1 = if_reg_imem_addr - 1.U
  val data_next_valid = (inst =/= Insts.COMMA || io.in.valid)
  val output_valid = (state === sExecuting && inst === Insts.PERIOD)
  val block_in = inst === Insts.COMMA && !io.in.valid
  val block_out = inst === Insts.PERIOD && !io.out.ready
  val block = block_in || block_out

  val anti_bracket = MuxCase(
    0.U,
    Seq(
      (reg_finding_bracket === Insts.OPEN) -> Insts.CLOSE,
      (reg_finding_bracket === Insts.CLOSE) -> Insts.OPEN
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

  val dmem_addr_inc = (state === sExecuting && inst === Insts.RIGHT)
  val dmem_addr_dec = (state === sExecuting && inst === Insts.LEFT)

  val count_bracket_next = MuxCase(
    reg_count_bracket,
    Seq(
      (inst === reg_finding_bracket) -> (reg_count_bracket - 1.U),
      (inst === anti_bracket) -> (reg_count_bracket + 1.U)
    )
  )

  val imem_addr_next = MuxCase(
    imem_addr_p1,
    Seq(
      (state === sReady && io.ctrl.start) -> 0.U,
      (state === sExecuting && inst === Insts.CLOSE && data =/= 0.U) -> (ex_reg_imem_addr - 1.U),
      (state === sExecuting && block) -> if_reg_imem_addr,
      (state === sFindingBracket && count_bracket_next === 0.U) -> (ex_reg_imem_addr + 1.U),
      (reg_finding_bracket === Insts.OPEN) -> imem_addr_m1
    )
  )

  val data_wen = (state === sExecuting && data_next_valid)

  io.ctrl.ready := (state === sReady)
  io.ctrl.finished := reg_finished
  io.imem_read.addr := imem_addr_next
  io.imem_read.enable := true.B
  dcache.io.ctrl.reset := io.ctrl.reset
  dcache.io.ctrl.addr_inc := dmem_addr_inc
  dcache.io.ctrl.addr_dec := dmem_addr_dec
  dcache.io.ctrl.wbits := data_next
  dcache.io.ctrl.wenable := (state === sExecuting && !block)
  io.in.ready := in_ready
  io.out.valid := output_valid
  io.out.bits := data

  io.status.imem_addr := ex_reg_imem_addr
  io.status.inst := inst
  io.status.dmem_read_addr := dcache.io.addr
  io.status.data := data
  io.status.dm1 := dcache.io.rbits_m1
  io.status.dp1 := dcache.io.rbits_p1
  io.status.bracket_count := reg_count_bracket
  io.status.state_onehot.reset := state === sReset
  io.status.state_onehot.ready := state === sReady
  io.status.state_onehot.fetch := state === sFetch
  io.status.state_onehot.executing := state === sExecuting
  io.status.state_onehot.start_find_bracket := state === sStartFindBracket
  io.status.state_onehot.finding_bracket := state === sFindingBracket
  io.status.state_onehot.finished := state === sFinished
  reg_finished := (state === sFinished)
  if_reg_imem_addr := imem_addr_next

  when(io.ctrl.reset) {
    state := sReset
  }.otherwise {
    switch(state) {
      is(sReset) {
        state := sReady
      }
      is(sReady) {
        when(io.ctrl.start) {
          state := sFetch
        }
      }
      is(sFetch) {
        state := sExecuting
      }
      is(sExecuting) {
        when(inst === 0.U) {
          state := sFinished
        }.elsewhen(inst === Insts.OPEN && data === 0.U) {
          state := sFindingBracket
          reg_count_bracket := 1.U
          reg_finding_bracket := Insts.CLOSE
        }.elsewhen(inst === Insts.CLOSE && data =/= 0.U) {
          state := sStartFindBracket
          reg_count_bracket := 1.U
          reg_finding_bracket := Insts.OPEN
        }
      }
      is(sStartFindBracket) {
        state := sFindingBracket
      }
      is(sFindingBracket) {
        when(count_bracket_next === 0.U) {
          state := sFetch
          reg_finding_bracket := 0.U
        }.otherwise {
          reg_count_bracket := count_bracket_next
        }
      }
    }
  }
}
