package cachewu

import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3.Module
import chisel3.stage.ChiselGeneratorAnnotation
import device.AXI4RAMWrapper
import freechips.rocketchip.amba.axi4.{AXI4Deinterleaver, AXI4IdIndexer, AXI4SlaveNode, AXI4SlaveParameters, AXI4SlavePortParameters, AXI4UserYanker}
import freechips.rocketchip.diplomacy.{AddressSet, DisableMonitors, InModuleBody, LazyModule, LazyRawModuleImp, RegionType, TransferSizes}
import freechips.rocketchip.tilelink.{BankBinder, TLBuffer, TLCacheCork, TLIdentityNode, TLTempNode, TLToAXI4, TLWidthWidget, TLXbar}
import huancun.debug.TLLogger
import huancun.{HCCacheParamsKey, HuanCun}
import system.SoCParamsKey
import top.{DefaultConfig, XiangShanStage}
import utils.{BinaryArbiter, TLClientsMerger, TLEdgeBuffer}
import xiangshan.XSTileKey

class CacheSubsystem()(implicit p: Parameters) extends LazyModule() {
  val L3BlockSize = p(SoCParamsKey).L3BlockSize
  val L3NBanks = p(SoCParamsKey).L3NBanks
  val L3OuterBusWidth = p(SoCParamsKey).L3OuterBusWidth

  val memRange = AddressSet(0x00000000L, 0xfffffffffL).subtract(AddressSet(0x0L, 0x7fffffffL))
  val memAXI4SlaveNode = AXI4SlaveNode(Seq(
    AXI4SlavePortParameters(
      slaves = Seq(
        AXI4SlaveParameters(
          address = memRange,
          regionType = RegionType.UNCACHED,
          executable = true,
          supportsRead = TransferSizes(1, L3BlockSize),
          supportsWrite = TransferSizes(1, L3BlockSize),
          interleavedId = Some(0)
        )
      ),
      beatBytes = L3OuterBusWidth / 8
    )
  ))

  val mem_xbar = TLXbar()

  memAXI4SlaveNode :=
    AXI4IdIndexer(idBits = 14) :=
    AXI4UserYanker() :=
    AXI4Deinterleaver(L3BlockSize) :=
    TLToAXI4() :=
    TLWidthWidget(L3OuterBusWidth / 8) :=
    TLEdgeBuffer(_ => true, Some("MemXbar_to_DDR_buffer")) :=
    mem_xbar

  val bankedNode = BankBinder(L3NBanks, L3BlockSize)

  mem_xbar :=*
    TLXbar() :=*
    TLEdgeBuffer(i => i == 0, Some("L3EdgeBuffer_1")) :=*
    BinaryArbiter() :=*
    TLEdgeBuffer(i => i == 0, Some("L3EdgeBuffer_0")) :=*
    TLCacheCork() :=*
    bankedNode

  val l3Out = TLTempNode()

  bankedNode :*= TLLogger("MEM_L3") :*= l3Out

  val memoryPort = InModuleBody {
    memAXI4SlaveNode.makeIOs()
  }

  val simAXIMem = LazyModule(new AXI4RAMWrapper(
    memAXI4SlaveNode, 8L * 1024 * 1024 * 1024, useBlackBox = true
  ))
  val simAXIMemModule = Module(simAXIMem.module)
  simAXIMem.io_axi4 <> memoryPort


  val l3cache = p(SoCParamsKey).L3CacheParamsOpt.map(l3param =>
    LazyModule(new HuanCun()(new Config((_, _, _) => {
      case HCCacheParamsKey => l3param
    })))
  ).get

  val l2Tol3 = TLIdentityNode()
  val l3BankedXBar = TLXbar()

  l3Out :*= l3cache.node :*= TLBuffer() :*= l3BankedXBar

  l3BankedXBar :=*
    TLLogger(s"L3_L2") :=*
    TLBuffer() :=
    l2Tol3

  val coreParams = p(XSTileKey).head
  val l2cache = coreParams.L2CacheParamsOpt.map(l2param =>
    LazyModule(new HuanCun()(new Config((_, _, _) => {
      case HCCacheParamsKey => l2param
    })))
  ).get

  val l2Binder = coreParams.L2CacheParamsOpt.map(_ => BankBinder(coreParams.L2NBanks, 64)).get

  l2Tol3 := TLBuffer() := TLClientsMerger() := TLXbar() :=* l2Binder

  l2Binder :*= l2cache.node :*= TLXbar() := TLTempNode() // add here

  val ctrl = TLTempNode() // FIXME
  l3cache.ctlnode.get := ctrl

  lazy val module = new LazyRawModuleImp(this) {

  }
}

object CacheWarmup extends App {
  implicit val config : Parameters = new DefaultConfig()
  val cacheSubsystem = DisableMonitors(p => LazyModule(new CacheSubsystem()(p)))
  XiangShanStage.execute(Array[String](), Seq(
    ChiselGeneratorAnnotation(() => {
      cacheSubsystem.module
    })
  ))
}
