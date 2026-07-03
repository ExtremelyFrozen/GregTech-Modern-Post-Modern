# 发电机（Generators） { #generators }

Generators 是每 tick 消耗 Fuel 并产生 EU 的机器。发电机有多种不同类型，
每种都会消耗不同的燃料。

## 单方块发电机（Singleblock Generators） { #singleblock-generators }

从 LV 到 HV，EU 通过单方块发电机产生，例如 Basic Steam Turbine 或 Advanced Combustion
Generator。所有单方块发电机都具备以下特性：

* 消耗 Fuel。在基础 GregTech Modern 中，所有电力发电机都会消耗液体或气体作为燃料，但不会消耗物品。
  EMI 可以显示发电机可消耗的全部有效燃料列表。该燃料显示会列出：
    * 每个燃烧周期消耗的数量。对大多数燃料而言是 1mb，但对某些低效率燃料（例如 Steam）来说可能高得多。
    * 燃烧时间。
    * 发电速率。任何消耗该燃料的发电机，其功率 tier 必须等于或高于该速率。
    * 一个周期产生的总 EU。它等于燃烧时间 x 发电速率。
* 每 tick 产生 EU。所有单方块发电机都会产生并输出等于 1A @ 其 tier 电压的 EU。
* 每 tick 输出 EU。所有发电机都有一个输出面，以彩色圆点标记，会向 Cable 或相邻 Machine
  提供 1A @ tier 电压。
    * 发电机不会输出“部分” Amp 或部分电压。发电机始终会以精确的 1A @ tier 电压包输出电力。
* 不能放置在 **Cleanrooms** 内。将 Generator 放入 Cleanroom 会阻止 Cleanroom Controller 激活。
    * 要将电力传入 Cleanroom，请使用 [Diode](./Cables-and-Transformers.md#diodes)。

单方块发电机有多个变体，每种接受不同燃料。

* Steam Turbines 通过大量 Steam 产生 EU。Steam Turbines 的 EMI 显示中也会显示输出 Distilled Water；
  这是多方块 Large Steam Turbine（下文讨论）的特性，不是基础单方块涡轮的特性。
* Gas Turbines 通过 Methane、Benzene 等可燃气体产生 EU。
* Combustion Engines 通过液态 Oils、Diesels、Gasolines 和 Biofuels 产生 EU。
  Combustion Engines 会极大受益于燃料在燃烧前的精炼处理。

单方块发电机操作简单，但成本相对较高，通常比它们供电的机器还要昂贵。虽然可以用发电机直接给机器供电，
但通常更适合建造少量发电机，并配合某种 [Energy Storage](./Energy-Storage.md) 来驱动大量机器，
因为大多数机器不会消耗完整的 1 Amp 电力，或者不会同时运行。

MV 和 HV 发电机会消耗额外燃料，以在更高电压下产生电力。它们不是通过改变燃料燃烧时间来做到这一点；
而是同时消耗多个燃料周期（有时会显示为“并行运行 [X] 个配方”），从而同时提高 EU/t 和燃料消耗速率。

## 多方块发电机（Multiblock Generators） { #multiblock-generators }

从 EV 及以后，发电会转向多方块机器。这些机器的体积强烈鼓励集中化发电、储能与配电；
不过，由于这些发电机很大一部分由 Casings 构成，它们比为了达到同等输出而需要建造的大量单方块发电机便宜得多。

所有 Multiblock Generators 都具备以下特性：

* 消耗燃料。很多燃料。与其他多方块机器一样，燃料通过 Input Hatches 提供。
* 生成并输出 EU。不同于单方块发电机，这些 EU 会从放置在发电机某一侧的 **Dynamo Hatch** 输出。
  所使用的 Dynamo Hatch 决定发电机的最大输出。Dynamo Hatches 有多个变体，具有不同的 Voltage
  *和 Amperage* 输出值，使单个多方块发电机可以在其电压下产生多个 Amp 的电力。这有助于弥补它们的巨大体积。
    * Dynamo Hatch 决定发电机输出的 Voltage 和 Maximum Amperage。这与发电机每 tick 产生的 EU 数量**并不相同**。
  使用尺寸不足的 Dynamo Hatch 会限制发电机最大输出；而使用尺寸过大的 Dynamo Hatch 会导致发电机从闲置状态启动后，
  在几秒内发出远高于纸面持续发电能力的 Amperage，直到 Dynamo 内部能量缓冲耗尽。
* 拥有效率提升。根据配置不同，多方块发电机每 mB 燃料可产生的 EU 会显著高于单方块发电机。
  这进一步弥补了它们的体积，使更大、更强力的机器可以由较小的燃料来源驱动。
* 前方需要开放空气（位于 Air Intakes 或 Rotor Holder 前方）。
* 允许 Wallsharing，以在多个涡轮之间节省 casing。

Multiblock Generators 主要有两类：Large Combustion Engine 和 Large Turbines。

### 大型燃烧引擎（Large Combustion Engines） { #large-combustion-engines }

Large Combustion Engine（EV）和 Extreme Combustion Engine（IV）是大型发电机类型中较简单的种类。
它们消耗 Combustion Engine 燃料来产生 EU。它们运行时还会被动消耗 Lubricant。

通过向发电机供应 Oxygen（LCE）或 Liquid Oxygen（ECE），可以显著提高 LCE/ECE 的输出和能量效率。
该提升会使发电机燃料消耗翻倍，但会使能量产出变为三倍（LCE）或四倍（ECE）。

如果 LCE/ECE 的 Dynamo Hatch 被 EU 填满，引擎会暂停并停止消耗燃料。

Large Combustion Engines 建造成本高，但投入运行后维护量较低；Gasoline 等更高 tier 的燃烧燃料能量密度也相当高。

### 大型涡轮（Large Turbines） { #large-turbines }

Large Steam、Gas 和 Plasma Turbine 是更复杂的发电机，可以接受更广泛的燃料。

* Large Steam Turbines 消耗极大量 Steam 来产生 EU。LST 还会输出 Distilled Water，
  这允许搭建回收自身给水的装置，或提供可在其他地方使用的免费蒸馏水。
* Large Gas Turbines 消耗大量 Gas Turbine 燃料（例如 Benzene）来产生 EU。
* Large Plasma Turbines 消耗少量由 Fusion Reactors 产生的 Plasma，以产生大量 EU。
  LPT 还会输出所消耗 Plasma 燃料对应的液态或气态形式。

Large Turbines 的建造成本显著低于 Large Combustion Engines。不过，它们需要额外组件：**Rotor**。
Rotor 是一种昂贵且具有有限耐久（以秒计）的物品，它决定 Turbine 的燃料消耗、能量产出和效率。
Rotor 放置在 Rotor Holder 中，且 Rotor Holder 在 Turbine 激活时无法打开。（尝试打开会受伤。）
更高 tier 的 Rotor Holder 会提高 Turbine 的功率输出（每提升一个 Rotor Holder tier，功率输出和燃料消耗翻倍），
但也会提高涡轮燃料效率（每高于 Turbine 最低需求一个 tier，燃料消耗降低 10%）。与小型发电机不同，
这种燃料消耗降低会表现为*燃料燃烧持续时间增加*。

此外，Large Turbines 需要数分钟才能升速到完整输出，并会在非活动时降速。能量产出会随 Turbine RPM
按指数缩放，这意味着在大部分升速时间内，Turbine 的输出都会相当低。处于活动状态时，RPM 每 tick 增加 1；
处于非活动状态时，RPM 每 tick 减少 3。要管理这种行为，Large Turbines 最适合连续运行，
或以有限爆发方式为储能设备充电：在储能偏低时通过 Machine Controller Cover 激活，在储能接近充满时停用。

Large Turbine 行为在 7.3.0 中略有改变。在此版本之前，如果 Large Turbine 的 Dynamo Hatch 被 EU 填满，
Turbine 会停止消耗燃料并降速。从 7.3.0 起，Large Turbines 会忽略其 Dynamo 和 Output Hatches 中的内容，
即使产生的 EU 或 Fluid 没有去处，也会继续满速运行。虽然这会导致多余输出被 void，但也意味着 Turbines
会保持完整速度和输出。

Large Turbine 的总 EU/t 输出和燃料消耗由以下内容决定：

* EU/t Output = [Turbine base EU/t] x [2 ^ Rotor Holder Tier - minimum tier] x [Turbine Power Mutiplier] x [Current RPM / Max RPM]^2
* Fuel Consumption = [EU/t output] / [Fuel base generation rate]（向上取整）
* Fuel Duration = [1 + 0.1 x [Rotor Holder Tier - minimum tier]] x [Rotor Efficiency Multiplier]
