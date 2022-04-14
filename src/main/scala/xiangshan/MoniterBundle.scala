package xiangshan

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util.DecoupledIO
import utils._
import xiangshan.frontend.FtqPtr
import xiangshan.frontend.icache.HasICacheParameters

trait MoniterConf {
  val cntWidth = 32
  val idWidth  = 5
  val inforWidth = 32
  val moniterCntNum = 8


  def moniterDDRBase = 0x90000000L
  def UART_BASE      = 0x40600000L
  def UART_RX_FIFO   = 0x0
  def UART_TX_FIFO   = 0x4
  def UART_STAT_REG  = 0x8
  def UART_CTRL_REG  = 0xc

  def UART_RST_FIFO     = 0x03
  def UART_TX_FULL = 0x08

}

abstract class MoniterBundle extends Bundle with MoniterConf

abstract class MoniterModule extends Module with MoniterConf

trait MoniterHelper{
  this: Module =>

  val signals : Seq[(String,Bool)]

  lazy val io_moniter: Vec[Bool] = IO(Output(Vec(signals.length,Bool())))

  def genSignals :Unit = {
    io_moniter.zipWithIndex.map{case(out,index) => out := signals(index)._2}
  }

  def getMoniIO: Vec[Bool] = io_moniter
}


