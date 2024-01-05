package bfcpu;

import chisel3._

object Consts {
  val WORD_BITS = 8;
  val IMEM_ADDR_SIZE = 14;
  val DMEM_ADDR_SIZE = 14;
  val BTB_ADDR_SIZE = 8;
  val BTB_WORD_SIZE = WORD_BITS + 2 * IMEM_ADDR_SIZE - BTB_ADDR_SIZE + 1
}

object Insts {
  val PLUS = "b0010_1011".U;
  val COMMA = "b0010_1100".U;
  val MINUS = "b0010_1101".U;
  val PERIOD = "b0010_1110".U;
  val LEFT = "b0011_1100".U;
  val RIGHT = "b0011_1110".U;
  val OPEN = "b0101_1011".U;
  val CLOSE = "b0101_1101".U;
}
