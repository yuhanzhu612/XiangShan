package xiangshan

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{TLClientNode, TLMasterParameters, TLMasterPortParameters}
import xiangshan.frontend.icache.{HasICacheParameters, ICacheBundle, InsUncacheReq, InsUncacheResp, InstrUncacheImp}
import utils._

class MoniterEventCnt extends MoniterBundle {
  val counter = UInt(cntWidth.W)
}


class MoniterEventReg extends MoniterBundle{
  val couter = UInt(cntWidth.W)
}

class XSMoniter()(implicit p: Parameters) extends LazyModule
{

  val clientParameters = TLMasterPortParameters.v1(
    clients = Seq(TLMasterParameters.v1(
      "XSMoniter",
      sourceId = IdRange(0, 1)
    ))
  )
  val clientNode = TLClientNode(Seq(clientParameters))

  lazy val module = new XSMoniterImp(outer = this)

}

class XSMoniterImp(outer: XSMoniter) extends LazyModuleImp(outer)
  with MoniterConf
{
  val (bus, edge) = outer.clientNode.out.head

  val io = IO(new Bundle{
    val frontend_signals  = Input(new FrontendMoniter)
    val commit_signals    = Input(new BackendCommitMoniter)
    val wb_signals        = Input(new BackendWBMoniter)
    val excpt_signals     = Input(new BackendExcptMoniter)
  })

  val registers = VecInit(Seq.fill(moniterCntNum)(RegInit(0.U.asTypeOf(new MoniterEventReg))))

  // assign default values to output signals
  bus.b.ready := false.B
  bus.c.valid := false.B
  bus.c.bits  := DontCare
  bus.d.ready := true.B
  bus.e.valid := false.B
  bus.e.bits  := DontCare

  bus.a.valid := false.B
  bus.a.bits  := DontCare

  dontTouch(bus.a)
  dontTouch(bus.d)


//  def generateReq(in : BaseMoniterSignal) = {
//    val putAddr = (moniterDDRBase + in.offset * 4).U
//    val putData = in.data
//    val sendCnt = RegInit(in.transferNum.U)
//
//    val m_idle :: m_transfer :: m_finish :: Nil = Enum(3)
//    val state   = RegInit(m_idle)
//
//    when(in.valid && state === m_idle){
//      state := m_transfer
//    }
//
//    when(state === m_transfer){
//      bus.a.valid := true.B
//      bus.a.bits  := edge.Put(
//        fromSource = 0.U,
//        toAddress = putAddr + ((in.transferNum.U - sendCnt)<<2),
//        lgSize = 4.U,
//        data = putData(in.transferNum.U - sendCnt))._2
//
//      when(sendCnt === 0.U){
//        state := m_finish
//      }.elsewhen(bus.a.fire()){
//        sendCnt := sendCnt - 1.U
//      }
//    }
//
//    when(state === m_finish){
//      sendCnt := in.transferNum.U
//      state := m_idle
//    }
//  }

//  generateReq(io.excpt_signals)
//  generateReq(io.wb_signals)
//  generateReq(io.commit_signals)
//  generateReq(io.frontend_signals)
val putAddr = moniterDDRBase.U//(moniterDDRBase + in.offset * 4).U
val putData = RegNext(io.frontend_signals.data)
val sendCnt = RegInit(io.frontend_signals.transferNum.U)

val m_idle :: m_transfer :: m_finish :: Nil = Enum(3)
val state   = RegInit(m_idle)

when(io.frontend_signals.valid && state === m_idle){
  state := m_transfer
}

when(state === m_transfer){
  bus.a.valid := true.B
  bus.a.bits  := edge.Put(
    fromSource = 0.U,
    toAddress = putAddr + ((io.frontend_signals.transferNum.U - sendCnt)<<2),
    lgSize = 4.U,
    data = putData(io.frontend_signals.transferNum.U - sendCnt))._2

  when(sendCnt === 0.U){
    state := m_finish
  }.elsewhen(bus.a.fire()){
    sendCnt := sendCnt - 1.U
  }
}

when(state === m_finish){
  sendCnt := io.frontend_signals.transferNum.U
  state := m_idle
}

}

