/***************************************************************************************
* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
* Copyright (c) 2020-2021 Peng Cheng Laboratory
*
* XiangShan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

package top

import chipsalliance.rocketchip.config.Parameters
import chisel3._
import chisel3.stage.ChiselGeneratorAnnotation
import device.{AXI4RAMWrapper, SimJTAG}
import freechips.rocketchip.diplomacy.{DisableMonitors, LazyModule}
import utils.GTimer
import xiangshan.DebugOptionsKey

class CacheWarmup(implicit p: Parameters) extends Module {
  val l_soc = LazyModule(new CacheSubsystem())
  val soc = Module(l_soc.module)

  val axi4MemPort = IO(l_soc.memoryPort.cloneType)
  axi4MemPort <> l_soc.memoryPort

  l_soc.above_l2_ios.zipWithIndex.foreach {
    case (above_l2_io, i) => {
      val outer_port = IO(Flipped(above_l2_io.cloneType)).suggestName(s"master_port_$i")
      outer_port <> above_l2_io
    }
  }

  // soc.io.clock := clock.asBool
  // soc.io.reset := reset.asBool

  val io = IO(new Bundle(){
  })
}

object CacheWarmup extends App {
  implicit val config : Parameters = new DefaultConfig()
  XiangShanStage.execute(Array[String](), Seq(
    ChiselGeneratorAnnotation(() => {
      DisableMonitors(p => new CacheWarmup()(p))(config)
    })
  ))
}
