package xiangshan

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util.DecoupledIO
import utils._
import xiangshan.frontend.FtqPtr

trait MoniterConf {
  val cntWidth = 32
  val idWidth  = 5
  val inforWidth = 32
  val moniterCntNum = 8

  def moniterDDRBase = 0x90000000L
}

abstract class MoniterBundle extends Bundle with MoniterConf

abstract class MoniterModule extends Module with MoniterConf

class BaseMoniterSignal extends MoniterBundle {
  val valid = Bool()
  val data = Vec(transferNum,UInt(cntWidth.W))

  def offset = 0
  def transferNum = 0
}

class BackendWBMoniter extends BaseMoniterSignal
{
  override def transferNum = 0
  override def offset = 3
}

class BackendExcptMoniter extends BaseMoniterSignal
{
  override def transferNum = 1
  override def offset = 2

//  val isInterrupt = Bool()
//  val exceptionVec = ExceptionVec()
}

class BackendCommitMoniter extends BaseMoniterSignal {
  override def transferNum = 1
  override def offset = 1

//  val ftqIdx = UInt()
}

class FrontendMoniter extends BaseMoniterSignal {
  override def transferNum = 2
  override def offset = 0
//
//  val instr = UInt()
//  val ftqIdx = UInt()
//  val af = Bool()
//  val pf = Bool()
}
