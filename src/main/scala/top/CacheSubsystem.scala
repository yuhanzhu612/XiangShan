package top

import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import device.AXI4RAMWrapper
import freechips.rocketchip.amba.axi4.{AXI4Deinterleaver, AXI4IdIndexer, AXI4SlaveNode, AXI4SlaveParameters, AXI4SlavePortParameters, AXI4UserYanker}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.jtag.JTAGIO
import freechips.rocketchip.tilelink._
import huancun.debug.TLLogger
import huancun.{HCCacheParamsKey, HuanCun, PrefetchField, PreferCacheField, DirtyField}
import system.SoCParamsKey
import utils.{BinaryArbiter, TLClientsMerger, TLEdgeBuffer}
import xiangshan.XSTileKey

class CacheSubsystem()(implicit p: Parameters) extends LazyModule() {
  val L3BlockSize = p(SoCParamsKey).L3BlockSize
  val L3NBanks = p(SoCParamsKey).L3NBanks
  val L3OuterBusWidth = p(SoCParamsKey).L3OuterBusWidth
  val cacheParams = p(HCCacheParamsKey)

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

  bankedNode :*=
    // TLLogger("MEM_L3") :*=
    l3Out

  val memoryPort = InModuleBody {
    memAXI4SlaveNode.makeIOs()
  }


  val l3cache = p(SoCParamsKey).L3CacheParamsOpt.map(l3param =>
    LazyModule(new HuanCun()(new Config((_, _, _) => {
      case HCCacheParamsKey => l3param.copy(
          ctrl = None
      )
    })))
  ).get

  val l2Tol3 = TLIdentityNode()
  val l3BankedXBar = TLXbar()

  l3Out :*= l3cache.node :*= TLBuffer() :*= l3BankedXBar

  l3BankedXBar :=*
    // TLLogger(s"L3_L2") :=*
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

  def createClientNode(name: String, sources: Int) = {
      val masterNode = TLClientNode(Seq(
        TLMasterPortParameters.v2(
          masters = Seq(
            TLMasterParameters.v1(
              name = name,
              sourceId = IdRange(0, sources),
              supportsProbe = TransferSizes(cacheParams.blockBytes)
            )
          ),
          channelBytes = TLChannelBeatBytes(cacheParams.blockBytes),
          minLatency = 1,
          echoFields = cacheParams.echoField,
          requestFields = Seq(PrefetchField(), PreferCacheField(), DirtyField()),
          responseKeys = cacheParams.respKey
        )
      ))
      masterNode
    }
  val master_nodes = (0 until 1) map( i => createClientNode(s"master_node$i", 32))

  l2Binder :*= l2cache.node :*= TLXbar() := master_nodes.head

  // lazy val module = new LazyRawModuleImp(this) {
  //   val io = IO(new Bundle {
  //     val clock = Input(Bool())
  //     val reset = Input(Bool())
  //   })
  //   // override LazyRawModuleImp's clock and reset
  //   childClock := io.clock.asClock
  //   childReset := io.reset
  // }
  val above_l2_ios = InModuleBody{ master_nodes.map(_.makeIOs()) }
  lazy val module = new LazyModuleImp(this){
  }
}

