package xiangshan

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{TLClientNode, TLMasterParameters, TLMasterPortParameters}
import xiangshan.frontend.icache.{HasICacheParameters, ICacheBundle, InsUncacheReq, InsUncacheResp, InstrUncacheImp}
import utils._
import xiangshan.frontend.FtqPtr

class XSMoniter()(implicit p: Parameters) extends LazyModule with HasXSParameter
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

class XSMoniterImp(outer: XSMoniter)(implicit p: Parameters) extends LazyModuleImp(outer)
  with HasXSParameter
  with MoniterConf
{
  val (bus, edge) = outer.clientNode.out.head

  val io = IO(new Bundle{
    val frontend_signals  = Input(Bool())
  })

  // assign default values to output signals
  bus.b.ready := false.B
  bus.c.valid := false.B
  bus.c.bits  := DontCare
  bus.e.valid := false.B
  bus.e.bits  := DontCare

  bus.a.valid := false.B
  bus.a.bits  := DontCare

  dontTouch(bus.a)
  dontTouch(bus.d)

  dontTouch(io.frontend_signals)


  val putAddr = moniterDDRBase.U

  val m_idle :: m_send_check :: m_wait_check ::  m_send_req :: m_wait_resp :: Nil = Enum(5)
  val state   = RegInit(m_idle)

  bus.d.ready := true.B

  val frontend_valid = io.frontend_signals
  val sendChar = putChar('F')
  val sendCnt  = RegInit(8.U(16.W))

  def putChar(char: Char): UInt = {
    val sendData = char.toInt.U
    sendData
  }

  when(frontend_valid && state === m_idle){
    state := m_send_check
    sendCnt := 8.U
  }

  when(state === m_send_check){
    bus.a.valid := true.B
    bus.a.bits  := edge.Get(
      fromSource = 0.U,
      toAddress = (UART_BASE + UART_STAT_REG).U,
      lgSize = 0.U )._2
    when(bus.a.fire()) {
      state := m_wait_check
    }
  }

  when(state === m_wait_check){
    when(bus.d.fire() && (bus.d.bits.data(7,0) & UART_TX_FULL.U).orR()){
      state := m_send_check
    }.elsewhen(bus.d.fire() && !(bus.d.bits.data(7,0) & UART_TX_FULL.U).orR()){
      state := m_send_req
    }
  }

  when(state === m_send_req){
    bus.a.valid := true.B
    bus.a.bits  := edge.Put(
      fromSource = 0.U,
      toAddress = (UART_BASE + UART_TX_FIFO).U,
      lgSize = 0.U, //32 bit transform
      data = sendChar)._2
   when(bus.a.fire()) {
     state := m_wait_resp
   }
  }

  when(state === m_wait_resp){
    when(bus.d.fire() && sendCnt =/= 0.U){
      sendCnt := sendCnt - 1.U
      state := m_send_check
    }.elsewhen(bus.d.fire() && sendCnt === 0.U){
      state := m_idle
    }
  }

}

