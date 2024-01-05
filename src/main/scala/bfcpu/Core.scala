package bfcpu

import chisel3._
import chisel3.util._

object BFCPU {
  object State extends ChiselEnum {
    val sReset, sReady, sFetch, sExecuting, sFindingBracket, sFinished =
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
  val finding_bracket = Output(Bool())
  val finished = Output(Bool())
}

class StatusReg extends Bundle {
  import Consts._
  val imem_addr = Output(UInt(IMEM_ADDR_SIZE.W))
  val inst = Output(UInt(WORD_BITS.W))
  val data = Output(UInt(WORD_BITS.W))
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
    val dcache_ctrl = Flipped(new DCacheIO(WORD_BITS, IMEM_ADDR_SIZE))
    val dcache_rbits = Input(UInt(WORD_BITS.W))
    val in = Flipped(Decoupled(UInt(WORD_BITS.W)))
    val out = Decoupled(UInt(WORD_BITS.W))
    val status = new StatusReg()
    val btb_query = Flipped(new BTBQueryIO(WORD_BITS, IMEM_ADDR_SIZE))
    val btb_update = Flipped(new BTBUpdateIO(WORD_BITS, IMEM_ADDR_SIZE))
  })

  // Registers

  val state = RegInit(sReset)

  val if_reg_imem_addr = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val ex_reg_imem_addr = RegNext(if_reg_imem_addr)
  val reg_count_bracket = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val reg_finding_bracket = RegInit(0.U(WORD_BITS.W))
  val reg_finished = RegInit(false.B)
  val reg_inst_delay1 = RegInit(0.U(WORD_BITS.W))
  val reg_inst_delay2 = RegNext(reg_inst_delay1)
  val reg_forward_inst = RegInit(false.B)
  val reg_use_inst_from_btb = RegInit(false.B)
  val reg_inst_from_btb = RegInit(0.U(WORD_BITS.W))
  val reg_next_write_btb = RegInit(false.B)
  val reg_branch_addr = RegInit(0.U(IMEM_ADDR_SIZE.W))
  val reg_target_addr = RegInit(0.U(IMEM_ADDR_SIZE.W))

  val inst = MuxCase(
    io.imem_read.bits,
    Seq(
      reg_forward_inst -> reg_inst_delay2,
      reg_use_inst_from_btb -> reg_inst_from_btb
    )
  )
  val data = io.dcache_rbits
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
      (state === sExecuting && inst === Insts.CLOSE && data =/= 0.U && !io.btb_query.valid) -> (ex_reg_imem_addr - 2.U),
      (state === sExecuting && inst === Insts.CLOSE && data =/= 0.U && io.btb_query.valid) -> (io.btb_query.target_addr + 2.U),
      (state === sExecuting && inst === Insts.OPEN && data === 0.U && io.btb_query.valid) -> (io.btb_query.target_addr + 2.U),
      (state === sExecuting && block) -> if_reg_imem_addr,
      (state === sFindingBracket && reg_finding_bracket === Insts.OPEN && count_bracket_next =/= 0.U) -> imem_addr_m1,
      (state === sFindingBracket && reg_finding_bracket === Insts.OPEN && count_bracket_next === 0.U) -> (ex_reg_imem_addr + 2.U)
    )
  )

  val data_wen = (state === sExecuting && data_next_valid)

  io.ctrl.ready := (state === sReady)
  io.ctrl.finished := reg_finished
  io.imem_read.addr := imem_addr_next
  io.imem_read.enable := true.B
  io.dcache_ctrl.reset := io.ctrl.reset
  io.dcache_ctrl.addr_inc := dmem_addr_inc
  io.dcache_ctrl.addr_dec := dmem_addr_dec
  io.dcache_ctrl.wbits := data_next
  io.dcache_ctrl.wenable := (state === sExecuting && !block)
  io.in.ready := in_ready
  io.out.valid := output_valid
  io.out.bits := data

  io.status.imem_addr := ex_reg_imem_addr
  io.status.inst := inst
  io.status.data := data
  io.status.bracket_count := reg_count_bracket
  io.status.state_onehot.reset := state === sReset
  io.status.state_onehot.ready := state === sReady
  io.status.state_onehot.fetch := state === sFetch
  io.status.state_onehot.executing := state === sExecuting
  io.status.state_onehot.finding_bracket := state === sFindingBracket
  io.status.state_onehot.finished := state === sFinished
  reg_finished := (state === sFinished)
  if_reg_imem_addr := imem_addr_next
  reg_inst_delay1 := inst

  // state
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
        }.elsewhen(inst === Insts.OPEN && data === 0.U && !io.btb_query.valid) {
          state := sFindingBracket
        }.elsewhen(inst === Insts.CLOSE && data =/= 0.U && !io.btb_query.valid) {
          state := sFindingBracket
        }
      }
      is(sFindingBracket) {
        when(count_bracket_next === 0.U) {
          state := sExecuting
        }
      }
    }
  }

  // bracket finder
  switch(state) {
    is(sExecuting) {
      when(inst === 0.U) {
        reg_use_inst_from_btb := false.B
        reg_forward_inst := false.B
      }.elsewhen(inst === Insts.OPEN && data === 0.U) {
        when (io.btb_query.valid) {
          reg_use_inst_from_btb := true.B
        }.otherwise{
          reg_use_inst_from_btb := false.B
          reg_count_bracket := 1.U
          reg_finding_bracket := Insts.CLOSE
          reg_forward_inst := false.B
          reg_branch_addr := ex_reg_imem_addr
        }
      }.elsewhen(inst === Insts.CLOSE && data =/= 0.U) {
        when (io.btb_query.valid) {
          reg_use_inst_from_btb := true.B
        }.otherwise{
          reg_use_inst_from_btb := false.B
          reg_count_bracket := 1.U
          reg_finding_bracket := Insts.OPEN
          reg_forward_inst := true.B
          reg_branch_addr := ex_reg_imem_addr
        }
      }.otherwise {
        reg_use_inst_from_btb := false.B
        reg_forward_inst := false.B
      }
    }
    is(sFindingBracket) {
      when(count_bracket_next === 0.U) {
        reg_finding_bracket := 0.U
        reg_forward_inst := reg_finding_bracket === Insts.OPEN
        reg_target_addr := ex_reg_imem_addr
      }.otherwise {
        reg_count_bracket := count_bracket_next
        reg_forward_inst := false.B
      }
    }
  }

  when(io.ctrl.reset) {
    reg_next_write_btb := false.B
  }.otherwise {
    switch(state) {
      is(sExecuting) {
        reg_next_write_btb := false.B
      }
      is(sFindingBracket) {
        when(count_bracket_next === 0.U) {
          reg_next_write_btb := true.B
        }
      }
    }
  }

  reg_inst_from_btb := io.btb_query.next_inst
  io.btb_update.valid := reg_next_write_btb
  io.btb_update.addr := reg_branch_addr
  io.btb_update.target_addr := reg_target_addr
  io.btb_update.next_inst := inst

  io.btb_query.enable := true.B
  io.btb_query.addr := imem_addr_next
}
