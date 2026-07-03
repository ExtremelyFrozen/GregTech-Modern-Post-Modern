# 电能存储（Electric Energy Storage） { #electric-energy-storage }

[Generators](./Generators.md) 是昂贵的机器。虽然可以为每台机器都建造一台发电机，
但建造少量发电机并配合一种存储已生成 EU 的手段会便宜得多，从而让少量发电机驱动大量机器。
在 EV 及以后尤其如此，因为此时不再提供单方块发电机。

GregTech Modern 包含三种主要的能量存储形式：Batteries、Battery Buffers 和 Power Substation。

## 电池（Batteries） { #batteries }

Battery 从 LV 初期即可获得，并存在于所有能量 tier 中，是一种存储 EU 的物品。Batteries
通常在 Canner 中由 Battery Hull 和一定数量的可用 Dust（最初是 Lithium、Cadmium 或 Sodium）制成。
更高 tier 的电池（HV 及以后）还包括 Energy Crystal、Lapotron Crystal 以及各种 Lapotron Orb 衍生物。
这些 Crystal 电池使用 Autoclaves 和 Assemblers 制作，成本高于传统电池，但拥有高得多的储能容量。

Batteries 有四种用途：

* Batteries 可以放入单方块 [Electric Machines](./Machines.md#singleblock-machines)。所有单方块机器
  都有一个专用 Battery 槽位，以闪电图标标记。放入该槽位的 Battery 会：
    * 在机器能量缓冲超过 2/3 满时，从机器能量缓冲中充电
    * 在缓冲低于 1/3 满时，放电以补充机器缓冲
* 玩家物品栏中的 Batteries 会使用自身电量为玩家手持或穿戴的电动工具或 Armor 充电，速度为每 tick 1 Amp。
  该行为可以在手持 Battery 时通过 Shift + 右键启用或禁用。
* Batteries 可以放入 Turbochargers 中快速充电。Turbocharger 会按其中每个电动物品（Batteries、Tools、Armor）
  最多 4 Amps 的速率接受电力，并在其中的电池之间分配这些电力。
* Batteries 可以放入 Battery Buffers，下一节会讨论这一点。

## 电池缓冲器（Battery Buffers） { #battery-buffers }

Battery Buffer 是一种包含 1、4、8 或 16 个物品栏槽位的方块。每个槽位都可以容纳一块 Battery。
Battery Buffers 会按其中每个电动物品 2 Amps 的速率接受电力，并按每块 Battery **输出** 1 Amp 电力。
Battery Buffers 会均匀地为其中所有电池充放电，是从 LV 到 EV 中期进行大容量储能和供电稳定的主要手段。

在 EV 初期，Battery Buffers 还有一个重要用途。Battery Buffer 可以安装 Energy Detector 或
Advanced Energy Detector Cover，以读取其中电池持有的总能量。该读数会作为 Redstone Signal 输出，
并可传递给放置在 [Large Steam, Gas, or Plasma Turbine](./Generators.md#large-turbines) 上的
Machine Controller Cover。这样就可以在电池电量偏低时自动开启 Turbine，在电池充满时关闭 Turbine，
从而大幅节省燃料，并确保无论 Turbine 是否已经升速完成，都能随时获得完整供电。

## 电力变电站（Power Substation） { #power-substation }

Power Substation 是 GregTech Modern 对能量存储、集中化和分配需求的最强解决方案。PSS
是一个多方块结构，在 EV 中期可用，由 Palladium、Laminated Glass 和 Capacitor Blocks 建造。
Power Substation 的总能量存储量取决于用于建造它的 Capacitor Blocks 组合。

Power Substations 有几个重要特性：

* 极高的能量存储容量。初始 EV PSS 最多可存储 27 亿 EU，约相当于 18 Amp-Hours 的能量。
* 可在任意 Voltage 下进行高容量输入和输出。PSS 没有 Voltage Tier，可以放置多个任意 tier 的
  Energy Hatches 和 Dynamo Hatches。
    * 64 Amp Hatches。PSS 拥有一组独特的 Energy Hatch 和 Dynamo Hatch，可以从 EV 到 MAX Voltage
  接受或输出 64 Amps 电力。
* Laser Hatches。Power Substations 可以使用 Laser Source Hatches 和 Laser Target Hatches，
  在 Substations 或 Active Transformers 之间传输巨量 Amperage。
* 非常轻微的能量衰减。Power Substations 每 24 小时大约会损失 1% 的已存能量。以 PSS 所处的功率规模来看，
  这是一项相对不显著的消耗，但也意味着你不能完全忽视发电。

Power Substation 允许的极高 Amperage 与 [Multiblock Generators](./Generators.md#multiblock-generators)
的高输出和大型结构非常契合，并鼓励完全集中式的发电与配电。围绕这一点设计基地时，也会强烈鼓励使用
[Transformers](./Cables-and-Transformers.md#transformers)，用细 Cable 远距离传输极高电压能量，
再在机器生产线处降压使用。
