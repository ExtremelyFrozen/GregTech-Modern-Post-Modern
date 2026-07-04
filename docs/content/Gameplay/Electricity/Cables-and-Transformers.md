# 线缆与变压器（Cables and Transformers） { #cables-and-transformers }

EU 需要从发电机传输到储能设备，再传输到机器。EU 通过 Wire 和 Cable 传输。

每种 Wire 或 Cable 都有若干属性：

* 可切换连接。Cable 在世界中作为方块放置，并且可以在六个方向上分别连接。Cable
  在允许能量流动时没有方向性。可以使用 Wire Cutters 添加或移除 Cable 连接，
  Wire Cutters 也是破坏并回收 Cable 方块所使用的工具。
* 最大电压。每种 Cable 都有它能承载的最高能量电压。向 Cable 中输入电压过高的 EU
  会导致 Cable 起火并被摧毁。
    * 如果 Cable 尝试承载一个电压过高的 Amp，它会在被摧毁前先将承载电压
  *降低到自身的安全上限*。这意味着，如果高压发电机被意外连接到低压 Cable 上，
  Cable 会像牺牲式保险丝一样被摧毁，但线路后方的低压机器会受到保护，不会爆炸。
  因此，通常来说，直接用承载电压 tier 高于机器的 Cable 给机器供电会不安全得多。
* 最大安培数。与电压类似，每种 Cable 都有它可以安全承载的最大 Amperage。但是，向 Cable 中输入过多
  Amp 不会导致立即故障。相反，过载的 Cable 会短暂升温。如果 Cable 升温过高，
  它的绝缘层会烧掉；如果继续升温，它最终会起火并被摧毁。
    * Wire 可以组合成 2x、4x、8x 或 16x Wire。组合后的 Wire 会合并最大 Amperage，
  允许在单个方块空间内承载更多 Amp。
    * 为确保 Amperage 安全，通常建议不要使用可承载 Amp 数少于已连接
  [Generators](./Generators.md) 或 [Battery Buffers](./Energy-Storage.md#battery-buffers) 可供给 Amp 数的 Cable。
  （每台单方块发电机 1A，多方块发电机按 [Dynamo Hatch] Amp 数，Battery Buffer 按 [Battery slots] Amp 数。）
  这一点很重要，因为机器通常会*接受*超过 1 Amp 的电力，所以控制供能端通常比控制耗能端更简单。
* 每方块电压损耗。电力传输并非无成本。每当 1 Amp 电流通过一个 Cable 方块，就会损失一个或多个 Volt。
  在低电压、使用较差的 Cable 材料，尤其是使用未绝缘 Wire 时，这种影响会更加明显。
  示例见[下文](./Cables-and-Transformers.md#an-example-of-voltage-loss-and-transformer-usage)。
    * 可以使用 [Battery Buffers](./Energy-Storage.md)、Diodes 或 Transformers 来补偿电压损耗。
  虽然它们不能消除电压损耗，但可以减轻其对机器的影响。
* 绝缘。Wire 在覆盖绝缘层并转换成 Cable 之前不应直接使用。Cable 的 Voltage Drop
  明显低于未绝缘 Wire。此外，如果在上一个 tick 中传输过能量，触碰未绝缘 Wire 会造成大量伤害，
  在高电压下很容易致命。
    * 更粗的 Cable 通过给更粗的 Wire 添加绝缘层制成。
    * Cable 绝缘材料由 Rubber 制成。LV Cable 可以通过与 Rubber Sheets 合成来绝缘，
  但更高 tier 的 Cable 需要 Assembler 和 Liquid Rubber。
      * Liquid Rubber 最终可以替换为 Silicone Rubber 或 Styrene-Butadiene Rubber（高 tier
    组件也会要求这些材料）。
      * EV 及以上 Cable 还需要 Thin Polyvinyl Chloride Sheets，更高 tier 的 Cable 还需要更多薄片。
    * 某些 Wire 标记为 Superconductors。这些 Wire 不需要绝缘，触碰安全，并且每方块电压损耗为 0。
  不过它们都由比通常用于输电的简单 Wire 和 Cable 更复杂的合金制成。
    * 可以使用 Packer 移除 Wire 的绝缘层。这可用于淘汰旧 Cable，并回收其中使用的 Wire。

## 二极管（Diodes） { #diodes }

Diodes 是用于帮助管理电力传输的方块。

* 能量可以从五个面中的任意一面进入 Diode，但只能从 Diode 的输出面离开。
* Diodes 会限制流经自身的能量。默认情况下，Diode 会输出其电压下的 1 Amp；用 Soft Mallet 右键点击
  会在 2、4、8 和 16 Amps 输出之间循环。Diodes 会接受与其输出相等的 Amp 数。
* Diodes 不会存储大量电力。它们与同 tier 的其他机器一样，只有 64 tick 的缓冲。
* Diodes 可以放置在 **Cleanrooms** 的墙体中。这是将 EU 送入 Cleanroom、为内部机器供电的方式。

Diodes 比 Battery Buffers 便宜得多。虽然它们不能作为大容量储能，但可以用于将几条小 Cable
合并为一条大 Cable，或者从一条大 Cable 上分出小 Cable，并确保输出到这些 Cable 的 Amp 数不会超过其承载能力。
另外，由于 Diodes 是吸收并发出电力的方块，而不是简单传输电力，每个由 Diode 发出的 Amp
都会处于标准电压，因此 Diodes 可以通过（在需要时）将多个降压后的 Amp 合并为少量满电压 Amp 来补偿电压损耗。

## 变压器（Transformers） { #transformers }

Transformers 可用于升降电压。一个 Transformer 可以将高于自身一个 tier 的电压下的 1 Amp
转换为自身电压 tier 的 4 Amps，反向也可以。（例如，LV Transformer 会接受 1A MV 并输出 4A LV，
或者接受 4A LV 并输出 1A MV。）Soft Mallet 会将 Transformer 的模式从 Down 切换为 Up。

Transformers 还有三种进一步的变体：2x High-Amp（将 2A 转换为 8A）、4x High-Amp（将 4A 转换为 16A），
以及 Power Transformer（将 16A 转换为 64A）。

Transformers 适合用少量高压发电机为大量低压机器阵列供电。由于发电机构建成本很高，
只用少量发电机驱动大量并行机器可以显著节省资源和空间。

Transformers 也有助于缓解 Cable 的 Voltage Loss。高电压 Cable 并不总是拥有更低的每方块电压损耗，
但每 Amp 的每方块电压损耗是*减法*，不是*乘法*。因此，远距离传输少量高电压 Amp
造成的能量损失，会远低于在相同距离上传输大量低电压 Amp。（而且，承载更多 Amp 需要更粗、更昂贵的 Cable。）

### 电压损耗与 Transformer 用法示例 { #an-example-of-voltage-loss-and-transformer-usage }

以下列简单情况说明上一点：16A LV 通过一条 16x Tin Cable 传输 20 个方块。
16x Tin Cable 可在 LV 下承载 16 Amps，并且每方块每 Amp 损失 1V。经过 20 个方块后，
进入 Cable 的 32V x 16A 已经降低为 12V x 16A，损失了 68.75% 的功率，并大幅降低了其可靠驱动机器的能力
（此时任何运行需求超过 12EU/t 的机器都*需要*消耗完整的第二个 Amp），可能会让这条 Cable 最多只能驱动 8 台机器。

作为对比，如果先将这 16A LV 升压到 MV，再通过同样 20 个方块长度的 4x Annealed Copper Cable 传输。
4x Annealed Copper Cable 可在 MV 下承载 4A，并且每方块每 Amp 损失 1V。经过 20 个方块后，
进入 Cable 的 128V x 4A 只降低到 108V x 4A，仅损失 15.6% 的功率；目的端的
4x Hi-Amp LV Transformer 能够将其输出为 13.5A LV（Amp 必须是整数，因此会在 13A 和 14A 之间交替），
从而轻松驱动至少 13 台满速运行的机器。

## 主动变压器（Active Transformers） { #active-transformers }

Active Transformer 是 LuV-tier 的多方块机器。Active Transformer 最多可以接受 **13** 个任意电压 tier 的
Energy Hatch、Dynamo Hatch 或 Laser Hatch，并会将所有输入供能转换后提供给所有输出端。

Active Transformers 通常与 [Power Substations](./Energy-Storage.md#power-substation) 配合使用，
用于接收 Substation 极高的输出 Amperage，并将其合适地分配给机器阵列。

### Laser Hatch 与 Laser Pipe { #laser-hatches-and-laser-pipes }

Power Substations 和 Active Transformers 可以使用 Laser Source Hatches 与 Laser Target Hatches
作为传统供电方式的极高 Amp 替代方案。Laser Hatches 从 IV 开始可用，可以发出或接收 256A 到 4096A 的电力。

Laser Pipes 将能量从 Source Hatch 传输到 Target Hatch。它们相对于传输能力而言很便宜，但有几个重要特性：

* Laser Pipes 没有 Max Voltage、Max Amperage、Voltage Loss 或 Insulation。它们会以 Source Hatch
  输入的任意 Voltage 和 Amperage 传输能量。
* Laser Pipes 必须连接成**直线**。Laser Pipes 不允许出现任何弯折，否则不会传输电力。
    * 因此，Laser Pipes 只能作为单个 Source Hatch 与单个 Target Hatch 之间的一对一连接。
