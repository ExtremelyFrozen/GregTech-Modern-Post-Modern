---
title: "Material Properties（材料属性）"
---


# Material Properties（材料属性）

Properties 可以应用到 Material 上，用于决定它们的行为。下面是一个示例：

=== "Javascript"
    ```js
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('my_material')
            // ...
            .blastTemp(3700, "mid", GTValues.VA[GTValues.EV], 1600)
    })
    ```
=== "Java"
    ```java
    public static Material MY_MATERIAL;
    public static void register() {
       MY_MATERIAL = new Material.Builder(
            your_mod_id.id('my_material'))
            // ...
            .blastTemp(3700, "mid", GTValues.VA[GTValues.EV], 1600)
            .buildAndRegister();
        }
    ```

## `Blast Furnace Properties`（Blast Furnace 属性）
- `.blastTemp()` 需要与 `.ingot()` 搭配使用。它会根据你提供的参数生成 EBF 配方（以及 ABS 配方）：
    1. `int temperature` -> 决定需要哪一级线圈（查看线圈 tooltip 中的最高温度，或访问 [标准 Coil](../Other-Topics/Custom-Coils.md#standard-coils)）。
        如果温度低于 1000，还会生成 PBF 配方。
        如果温度高于 1750，会生成热锭，因此需要 Vacuum Freezer。
    2. （可选）`string gasTier` -> 无气体可设为 `null`，`'low'` 表示氮气，`'mid'` 表示氦气，`'high'` 表示氩气，`'higher'` 表示氖气，`'highest'` 表示氪气。
    3. （可选）`long EUPerTick` -> 配方电压
    4. （可选）`int durationInTicks` -> 配方耗时
!!! tip "ABS Recipe 生成"
    如果要让你的 Material 实际生成 ABS 配方，必须设置 `.components()`。如果在设置了 `.components` 和 `.blastTemp` 的同时想禁用 alloy blast smelter 配方生成，请参阅 [DISABLE_ALLOY_BLAST](./Material-Flags.md#dust-flags)

- `.durationOverride(int duration)`
    - `int duration` -> 覆盖 EBF 对配方耗时的默认行为。

- `.eutOverride(int EU/t)`
    - `int EU/t` -> 覆盖 EBF 对 EU/t 的默认行为。

## `Fluid Pipe Property`（流体管道属性）
- `.fluidPipeProperties(int maxTemp, int throughput, boolean gasProof, boolean acidProof, boolean cryoProof, boolean plasmaProof)` -> 这会从此 Material 创建流体管道
      1. `int maxtemp` -> 此管道在破裂并清空流体前能承受的最高流体温度。
      2. `int throughput` -> 流体通过此管道的流速。
      3. `boolean gasProof` -> 此管道是否能容纳气体。如果不能，气体在管道中传输时会损失一部分。
      4. `boolean acidProof` -> 此管道是否能容纳酸。如果不能，管道会破裂并清空所有流体。
      5. `boolean cryoProof` -> 此管道是否能容纳低温流体（低于 120K）。如果不能，管道会破裂并清空所有流体。
      6. `boolean plasmaProof` -> 此管道是否能容纳等离子体。如果不能，管道会破裂并清空所有流体。可承载等离子体的管道不关心温度。

## `Item Pipe Property`（物品管道属性）
- `.itemPipeProperties(int priority, int stacksPerSecond)` -> 这会从此 Material 创建物品管道
      1. `int priority` -> 此 Item Pipe 的优先级，用于标准路由模式。
      2. `int stacksPerSecond` -> 每秒（20 ticks）可移动多少组物品。

## `Rotor Property`（转子属性）
- `.rotorStats(int power, int efficiency, float damage, int durability)` -> 这会从此 Material 创建涡轮转子
    1. `int power` -> Power 是装配此转子时涡轮获得的 EU/t 和燃料消耗倍率。
     该输出会随涡轮速度和转子支架变化。
    2. `int efficiency` -> Efficiency 表示它处理燃料的效率。
     数值越小会消耗更多燃料，数值越大则消耗更少燃料。
     实际效率：rotorEfficiency * holder Efficiency / 100
    3. `float damage` -> Damage 是打开正在运行的涡轮转子支架 UI 时对玩家造成的伤害量。
    4. `int durability` -> Durability 是它拥有的基础耐久。
- 以下是一些基础 GT 转子的示例：
    1. Titanium Rotor: .rotorStats(130, 115, 3.0, 1600)
    2. HSS-S Rotor .rotorStats(250, 180, 7.0, 3000)

## `Cable Property`（线缆属性）
- .cableProperties(long voltage, int amperage, int lossPerBlock, boolean isSuperconductor)
    1. `long voltage` -> 此 Cable 的电压等级。应符合标准 GregTech 电压等级。
    2. `int amperage` -> 此 Cable 的安培数。应大于零。
    3. `int lossPerBlock` -> 此 Cable 每方块损耗。这里为零时，线材仍然会有损耗。
    4. `boolean isSuperconductor` -> 此 Material 是否是 Superconductor。如果是，则不会生成 Cables，Wires 的线缆损耗为零，并忽略损耗参数。

## Fluid Properties（流体属性）

### `Fluid Block Property`（流体方块属性）
- 在液态 Material 的 builder 中添加 `.block()`，即可允许此 Material 被放置。

## `Ingot Property`（锭属性）
- `.polarizesInto(string newMaterial)`
    - `string newMaterial` -> 对该 Material 进行极化后获得的内容。
- `.arcSmeltInto(string newMaterial)`
    - `string newMaterial` -> 对该 Material 进行电弧熔炼后获得的内容。
- `.macerateInto(string newMaterial)`
    - `string newMaterial` -> 对该 Material 进行研磨后获得的内容。
- `.ingotSmeltInto(string newMaterial)`
    - `string newMaterial` -> 熔炼该 Material 的锭时获得的内容。

## `Ore Property`（矿石属性）
- `.addOreByproducts()` 是额外副产物 Material 的“开放”列表。
      1. 在研磨机中从 crushed 到 impure，或在离心机中从 impure dust 到 dust 时使用的 Material
      2. 在热力离心机中从 crushed 到 refined，或在研磨机中从 crushed 到 dust，或在离心机中从 pure dust 到 dust 时使用的 Material
      3. 在研磨机中从 refined 到 dust 时使用的 Material
      4. 从 crushed ore 到 purified，或在 chemical bath 中处理时使用的 Material（仅当你有 getWashedIn Material 时生效）
- `.washedIn(string fluid)`
    - `string fluid` 是当它拥有 ore prop 并生成 crushed->refined 处理时使用的流体。例如过硫酸钠和汞的洗矿配方。
- `.separatedInto(list material)`
    - `list material` 是在电磁选矿机中处理 purified dusts 时获得的 Material 列表。最多两个 Material。
- `.oreSmeltInto(string material)`
    - `string material` 是直接熔炼矿石后获得的内容。

