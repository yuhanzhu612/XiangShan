package xiangshan

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.util.DecoupledIO
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{TLClientNode, TLMasterParameters, TLMasterPortParameters}
import xiangshan.frontend.icache.{HasICacheParameters, ICacheBundle, InsUncacheReq, InsUncacheResp, InstrUncacheImp}
import utils._

trait MoniterConf {
  val cntWidth = 16
  val idWidth  = 5
}

abstract class MoniterBundle extends Bundle with MoniterConf

abstract class MoniterModule extends Module with MoniterConf


class MoniterEvent extends MoniterBundle {
  val counter = UInt(cntWidth.W)
}

trait MoniterInter {
  this : RawModule =>
  // name, id, value
  val moniterEvent: Seq[(String, Int, UInt)]

  lazy val io_moniter: Vec[MoniterEvent] = IO(Output(Vec(moniterEvent.length, new MoniterEvent)))
  def generateMoniterEvent()
}

class XSMoniter()(implicit p: Parameters) extends LazyModule
  with MoniterConf
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

class XSMoniterImp(outer: XSMoniter) extends LazyModuleImp(outer) {
  //WIP: Demo for magic number transformation
  val demoReg = RegInit(24.U(32.W))
  val (bus, edge) = outer.clientNode.out.head

  // assign default values to output signals
  bus.b.ready := false.B
  bus.c.valid := false.B
  bus.c.bits  := DontCare
  bus.d.ready := false.B
  bus.e.valid := false.B
  bus.e.bits  := DontCare

  bus.a.valid := false.B
  bus.a.bits  := DontCare

  dontTouch(bus.a)
  dontTouch(bus.d)

  val putMoniter = edge.Put(
    fromSource = 0.U,
    toAddress = 0x90000000L.U,
    lgSize = 2.U,
    data = demoReg)._2

  when(GTimer() === 1500.U){
    bus.a.valid := true.B
    bus.a.bits := putMoniter
  }

}

