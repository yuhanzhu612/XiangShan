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

  val l_simAXIMem = LazyModule(new AXI4RAMWrapper(
    l_soc.memAXI4SlaveNode, 8L * 1024 * 1024 * 1024, useBlackBox = true
  ))
  val simAXIMem = Module(l_simAXIMem.module)
  l_simAXIMem.io_axi4 <> l_soc.memoryPort

  soc.io.clock := clock.asBool
  soc.io.reset := reset.asBool

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
