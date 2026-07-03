# 电力机器（Electric Machines） { #electric-machines }

GregTech Modern 中绝大多数功能性机器都由 EU 驱动。和 Generators 一样，它们之间也有许多共同规则和模式。

## 单方块机器（Singleblock Machines） { #singleblock-machines }

单方块机器从 LV 到 UV 的所有电力 tier 都可用，会消耗 EU 来运行配方。所有单方块电力机器都具备以下特性：

* 执行配方，以生产或转换物品或流体。
* 每 tick 消耗 EU 以运行。
    * 如果机器在配方进行到一半时耗尽电力，就会发生 "Powerstall"：处理停止，配方进度重置为 0。
  随后机器会空闲数秒，尝试重新填充内部能量缓冲，然后再开始运行。
    * 发生 Powerstall 的机器不会删除输入物品；它只是无法在获得足够能量前完成工作。
    * 发生 Powerstall 的机器也可以通过用 Soft Mallet 右键点击来完全设置为待机。这会让机器在再次开启前
  停止尝试运行配方，但不会删除输入物品。
    * 在以前版本的 GregTech 中，机器可以在配方中途暂停和继续，发生 Powerstall 的配方进度会向后倒退，
  而不是完全重置。这些行为在 7.0.0 中被改变，因为它们允许在不提供足够电力的情况下运行机器的利用方式。
* 拥有 Voltage Tier。该电压 tier 决定：
    * 可以安全输入到机器的电压。机器如果接收到高于自身 tier 电压的 1 Amp 电力，会**爆炸**。
    * 机器可以运行的配方 tier。许多配方都有最低所需电压 tier。
    * 机器运行的 **Overclock** tier。高电压机器运行低电压配方时会对配方进行 Overclock。
  Overclock 后的配方会按高一 tier 消耗电力（4x EU/t），并在 1/2 时间内完成。
  这确实意味着 Overclock 后的机器能量效率更低（4x 电压、1/2 时间、2x 总能量消耗），
  但这就是技术、速度和工业化的代价。
* 接受由连接的 Generator 或 Cable 发出的 EU。Cable 和发电机可以连接到机器的任意一面。
    * 每台单方块机器都包含一个小型能量缓冲，容量等于（Voltage x 64）EU。
    * 每台单方块机器会接受自身电压 tier 下的 1 Amp 电力来填充缓冲。
    * 运行配方时，机器会接受与配方 Amperage 相等的 Amp。
    * 当能量缓冲低于 50% 且正在运行配方时，机器会额外接受 1 Amp。

最后一点意味着，通常情况下，单方块机器会从任何连接的发电机接受 1 或 2 Amps 电力。
虽然可以创建消耗多个 Amp 电力的配方，但 GregTech Modern 目前没有这样的配方。

* 包含一个以闪电图标标记的 Battery Slot。Batteries 会在 [Energy Storage](./Energy-Storage.md#batteries)
  中进一步讨论，不过放入 Machines 内部的 Batteries 会：
    * 当机器缓冲超过 2/3 满时给自身充电。
    * 当机器缓冲低于 1/3 满时放电以驱动机器。

这些行为意味着，Battery 可以用于稳定机器供电；否则该机器可能没有足够电力连续运行。
Battery 也能抵消 Powerstalling，让机器缓冲足够电力以短时间爆发运行并完成重要配方。

在 IndustrialCraft 2 的古早时代，Redstone Dust 可以放入机器的 Battery Slot，为机器提供 1000 EU。
这*不是* GregTech Modern 中的功能。

## 多方块机器（Multiblock Machines） { #multiblock-machines }

从作为 MV 门槛的 Electric Blast Furnace 开始，并在 HV 和 IV 阶段大幅扩展，Multiblock Machines
用于解决需要更高处理速度和吞吐量，或需要执行过大、过耗电而无法在单方块机器中运行的工艺。所有多方块电力机器都具备以下特性：

* 执行配方，以生产或转换物品或流体。
    * 大多数 Multiblock machines 拥有一个额外运行模式，称为 Batch Mode。Batch Mode 通过多方块
  Controller 中的切换按钮启用。Batch Mode 对耗时超过 2.5 秒（在 Overclock 后）的配方没有影响。
  但对于任何短于 2.5 秒的配方，机器会尝试将多次配方运行合并为一个大批次，在 5 秒周期内尽可能完成更多配方，
  并合并它们的输入和持续时间。这减少了机器搜索新配方的频率，并提升具有极快机器的大型后期基地的服务器性能。
* 是围绕 Controller 建造的 Structure。
    * Controller 定义多方块结构，用于查看机器当前活动和状态，也用于开启或关闭机器的配方处理。
  但是，Controller **不**处理任何物品、流体或能量输入输出。
    * 多方块结构的大部分会由某种 Casings 组成。Casings 是完全惰性的方块，构成多方块结构成本的大部分，
  但与功能性机器方块相比非常简单且便宜。
    * 某些多方块机器，尤其是 Electric Blast Furnace 和 Alloy Blast Smelter，还包含 Heating Coil 方块。
  这些 Heating Coils 有多个 tier，会决定机器的部分配方能力和参数，可能解锁新配方或提高现有配方效率。
  对于包含 Heating Coils 的机器，结构中的所有 Coils 必须匹配。
* 包含 Hatches 和 Busses。Hatches 和 Busses 是在结构中替代 Casings 的方块，也是多方块结构与
  Items、Fluids、Energy 以及其他交互发生的位置。
    * Input hatches 和 busses 会通过其输入面自动拉入物品或流体；Output hatches 和 busses
  会通过其输出面自动将物品或流体推入相连的物品栏或储罐。可以通过电源按钮或 Soft Mallet
  将 Hatch 关闭，从而禁用这种自动化行为。
    * 所有多方块结构都有一个 "Minimum Required Casings"，它作为可被 busses 和 hatches 替换的 Casings 数量上限。
    * 附属 mod 也可以定义其他类型的 Hatches，用于输入或输出其他特殊类型的配方材料。
* 从 Energy Hatches 消耗 EU。与单方块机器不同，多方块机器没有内置电压 tier。相反，多方块机器会以所有
  Energy Hatches 组合输入所对应的电压 tier 运行。
    * 标准 Energy Hatches 接受同 tier 的 2 Amps 电力，并将这部分电力提供给关联的 Controller。
  接收到高于自身 tier 电压的 1 Amp 电力的 Energy Hatch 会**爆炸**。
    * 大多数多方块机器可以接受多个 Energy Hatches，使其能够用较低 tier 组件运行更高 tier 配方。
  Electric Blast Furnace 会立即用到这一点：它需要 MV 电力运行大多数配方，但最初只能使用 LV Energy Hatches 建造。
  因此，玩家的第一台 EBF 必须使用两个 LV Energy Hatches 建造，并由四台 LV Generators（或一个
  [4x Battery Buffer](./Energy-Storage.md#battery-buffers)）供电。
    * 多方块机器的功率 tier 也用于决定 Overclocks。几乎所有多方块机器都使用与普通机器相同的
  Overclocking 规则（4x EU/t、1/2 配方时间），但有三个显著例外：
        * **Large Chemical Reactor** 使用 "Perfect Overclocks"：配方以 4x EU/t 运行，但配方时间变为 **1/4**，
      这意味着 LCR 在 Overclock 时不会损失能量效率。这也让 LCR 完成配方的速度极快。
        * **Fusion Reactor** 使用 "Perfect Half Overclocks"：配方以 2x EU/t 和 1/2 配方时间运行。
        * 如果多方块机器被 Overclock 到其配方耗时低于 1 tick，它会开始执行 Subtick Overclocks。
      Subtick Overclocks 的工作方式类似 Batch Mode：机器会尝试执行多个配方副本
      （数量为它在 1 tick 内本来能完成的实例数），以跟上机器新获得的极高速度。
* 大多数（但不是全部）需要 Maintenance。Maintenance Issues 会在多方块机器运行数小时后出现，
  必须在 Maintenance Hatch 上使用 Tool 进行修理。出现的维护问题决定了需要使用哪种工具。
  大多数机器需要 Maintenance Hatch；Maintenance Hatch 第一次放置时会显示所有维护问题，因此机器上线前需要先维护。
    * 存在维护问题的机器会在完成当前配方后安全关闭；维护问题解决后会重新开启。
* 可以 **Wallshare** 组件。多方块机器结构中的惰性 casings、frames、coils 以及*大多数 Hatches 和 Busses*
  可以在多个 Controller 之间共享。与大多数包含多方块机器的其他 mod 不同，GregTech Multiblock Machines
  不会由完整机器形成一个单一连接实体；唯一可交互的 Block Entities 是 Controller 和 Hatches。
  结果是，同类型第一台多方块机器必须按完整成本建造，但之后可以与第一台共享墙体来建造同类型额外机器，
  从而显著降低完整建造成本。
    * 大多数 Hatches 和 Busses 也可以在机器之间共享，让共享墙体的机器从同一份输入材料中取料，
  并排运行同一配方，或运行共享某种通用材料的不同配方。
    * Energy Hatches 可以在机器之间共享，但通常不建议这样做，因为这可能导致机器耗尽电力。

### GCYM 多方块机器

建造 Alloy Blast Smelter 会解锁多种复杂金属合金的生产。这些合金用于建造一系列 IV-tier 机器，
有时称为 Gregicality Multiblocks。GCYM 曾是一个独立的 GregTech 附属 mod，现已完全集成进
GregTech Modern，并加入了一组用于替代单方块机器的多方块机器。
GCYM Multiblocks 具备上述普通 Multiblock machines 的所有属性，并额外拥有一些特性：

* 由 Alloy Blast Smelter 制造的合金建造。
* 执行原本可在单方块机器中执行的配方。
    * 某些 GCYM Multis 还可以执行多个不同配方组。例如，Large Centrifuge 可以执行
  Centrifuge 或 Thermal Centrifuge 配方。机器运行模式可以在其 Controller 中设置。
* 接受 **Parallel Hatch**。Parallel Hatches 会使机器尝试同时运行多个配方副本，
  合并它们的输入、输出和 EU/t 成本。如果机器没有足够能量输入来以完整 Overclock 速度运行 Hatch
  允许的并行数，它会降低 Overclock tier 以补偿，从而提高能量效率，同时（由于并行运行）不牺牲配方吞吐量。
* 可以使用 High-Amp Energy Hatches。
    * 普通 Energy Hatches 接受 2 Amps 电力。GCYM machines 可以接受 4A 或 16A Energy Hatches，
  从而在更高 Overclock tier 下运行更高并行数。
