---
title: "添加与移除配方"
---


# 添加与移除配方

## 移除配方

用 KubeJS 移除 GTCEu Modern 配方的方式与其他配方相同，可以按：
ID、Mod、Input、Output、Type 或它们的组合来移除。

```js title="gtceu_removal.js"
ServerEvents.recipes(event => {
    event.remove({ id: 'gtceu:smelting/sticky_resin_from_slime' }) // (1)
    event.remove({ mod: 'gtceu' }) // (2)
    event.remove({ type: 'gtceu:arc_furnace' }) // (3)
    event.remove({ input: '#forge:ingots/iron' }) // (4)
    event.remove({ output: 'minecraft:cobblestone' }) // (5)
    event.remove({ type: 'gtceu:assembler', input: '#forge:plates/steel' }) // (6)
})
```

1. 只移除 slime 到 sticky resin 的熔炉配方。
2. 移除 gtceu mod id 下的所有配方。
3. 移除 arc furnace 中的所有配方。
4. 移除输入为 `#forge:ingots/iron` 的所有配方。
5. 移除输出为 `minecraft:cobblestone` 的所有配方。
6. 移除 gtceu assembler 中输入为 `#forge:plates/steel` 的所有配方。


## 修改配方

使用 KubeJS 可以修改现有 GTCEu Modern 配方的 Inputs 或 Outputs，定位配方时使用同一套方式。

```js title="gtceu_modify.js"
ServerEvents.recipes(event => {
    event.replaceInput({ mod: 'gtceu' }, 'minecraft:sand', '#forge:sand') // (1)
    event.replaceOutput({ type: 'gtceu:arc_furnace' }, 'gtceu:wrought_iron_ingot', 'minecraft:dirt') // (2)
})
```

1. 定位所有输入为 `minecraft:sand` 的 gtceu 配方，并将其替换为 `#forge:sand`。
2. 定位所有输出为 `gtceu:wrought_iron_ingot` 的 gtceu arc furnace 配方，并将其替换为
   `minecraft:dirt`。


## 添加配方

语法：`event.recipes.gtceu.RECIPE_TYPE(string: recipe id)`

```js title="gtceu_add.js"
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('test')
        .itemInputs(
            '64x minecraft:dirt',
            '32x minecraft:diamond'
        )
        .inputFluids(
            Fluid.of('minecraft:lava', 1500)
        )
        .itemOutputs(
            'minecraft:stick'
        )
        .duration(100)
        .EUt(30)
})
```

### 添加输入与输出的事件调用

- 基础调用：
    - `.input()`:
      最基础的输入定义。接收两个参数：一个定义输入类型的 RecipeCapability，以及一个定义输入内容的 Object。
      可用的 RecipeCapability 可以在 GTCEu Modern 的 GitHub 或 mod 的 .JAR 文件中找到，但包含
      GTCEu Modern 原生 RecipeCapability 的类 `GTRecipeCapabilites` 需要在脚本中手动加载。
      这个方法在 Javascript 中不太方便使用；通常更建议使用下方这些明确表达输入类型的方法。
    - `.output()`:
      与上方类似，但定义的是输出。接收完全相同的参数。这个方法同样不太方便使用；
      通常更建议使用下方这些明确表达输出类型的方法。
- Inputs:
    - Items:
        - `.itemInput()`
        - `.itemInputs()`
        - `.chancedInput()`
        - `.itemInputsRanged()`
        - `.notConsumable()`
    - Fluids:
        - `.inputFluids()`
        - `.chancedFluidInput()`
        - `.inputFluidsRanged()`
        - `.notConsumableFluid()`
    - Misc:
        - `.circuit()`
- Outputs:
    - Items:
        - `.itemOutput()`
        - `.itemOutputs()`
        - `.chancedOutput()`
        - `.itemOutputsRanged()`
    - Fluids:
        - `.outputFluids()`
        - `.chancedFluidOutput()`
        - `.outputFluidsRanged()`
- Energy:
    - `.inputEU(long)`:
      让配方在启动时消耗一次性 EU。最常见于 fusion reactor 配方。传入值会保存为 total EU 的 `Long`，recipe tier 会由该 EU 值推导。
    - `.inputEU(voltage, amperage)`:
      让配方在启动时消耗一次性 EU。保存值是 `voltage * amperage` 的 total EU，recipe tier 由 voltage 推导。
    - `.outputEU(long)`:
      让配方在完成时产出一次性 EU。传入值会保存为 total EU 的 `Long`，recipe tier 会由该 EU 值推导。
    - `.outputEU(voltage, amperage)`:
      让配方在完成时产出一次性 EU。保存值是 `voltage * amperage` 的 total EU，recipe tier 由 voltage 推导。
    - `.EUt(long)`:
      接收表示 EU/t 的数值。正数表示每 tick 输入 EU/t，负数表示每 tick 输出 EU/t。recipe builder 会临时切到 per-tick 语义后委托 input/output EU 写入；recipe content 会保存为 total EU/t 的 `Long` 值，tier 推导沿用被委托的 input/output EU 规则。
    - `.EUt(voltage, amperage)`:
      将 content 保存为 `voltage * amperage` 的 total EU/t，而不是分别保存电压和安培数；recipe tier 由 `Math.abs(voltage)` 推导。
    - `.tier(int)`:
      recipe tier 现在独立存储在 `GTRecipeDefinition` / `GTRecipe` 上；当不能或不想依赖 `.EUt(voltage, amperage)` 推导 tier 时，请手动指定 `.tier(int)`。
- Chanced Ingredients:
    - 不是每次配方运行都会消耗或产出的 ingredients。可以表示为分数，也可以表示为 10,000 分制的整数概率。
    - 将 Input ingredient 的 Chance 设为 `0` 会使该 ingredient 在 EMI 中标记为 `Non-Consumed`。
      也可以更方便地使用 `.notConsumable()` 实现。
    - 带 chanced ingredients 的配方还可以为每组输入/输出指定 Chance Logic，使用一个或多个函数：
      `.chancedItemInputLogic()`、`.chancedFluidInputLogic()`、`.chancedTickInputLogic()`、
      `.chancedItemOutputLogic()`、`.chancedFluidOutputLogic()`、`.chancedTickOutputLogic()`
    - chanced logic 的有效选项：
        - `or` - （默认）任意通过 chance roll 的 item/fluid 都会被产出或消耗。
        - `and` - 如果_所有_ item/fluid 都通过 chance roll，则全部一起产出或消耗；否则全部不产出或消耗。
        - `xor` - 归一化 ingredient 概率，并保证每次运行正好有一个 chanced item/fluid 被
        产出或消耗。XOR 的行为在 7.0.0 中发生了变化。
        - `first` - 按注册顺序为每个 item/fluid 进行 chance roll。只返回第一个成功的 item。
        在 7.0.0 之前，这是 `xor` logic 的行为。
            - 由于行为不可预测，FIRST 自 7.3.0 起已弃用，并计划在 8.0.0 中移除。
- Ranged Ingredients:
    - Item 或 Fluid ingredients 会在 `min, max` 范围内随机消耗或产出数量（闭区间）。
- Circuits
    - 许多 GT 配方使用 Configuration 值为 `1-32` 的 `Programmed Circuit` 物品作为 `Non-Consumed` 输入，
用来与同一机器中使用相似 ingredients 的其他配方区分开。`.circuit()` 会向配方添加一个 circuit。
- 更细粒度的功能：
    - `.perTick()`:
      使用它可以控制配方输入/输出是在配方运行的每 tick 消耗/产出，还是在配方开始/结束时一次性消耗/产出。
      使用 `.perTick(true)` 设为 true 后，recipe builder 会将之后的 input/output 调用视为 per-tick。
      记得在你希望 per-tick 的调用结束后用 `.perTick(false)` 将其设回 false，避免产生不想要的行为。


### 研究系统

GTCEu 拥有 Research System，可以为配方添加额外要求，例如：
Scanner Research、Station Research 和 Computation。

```js title="scanner_research.js"
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembly_line('scanner_test')
        .itemInputs('64x minecraft:coal')
        .itemOutputs('minecraft:diamond')
        .duration(10000)
        .EUt(GTValues.VA[GTValues.IV])
        ["scannerResearch(java.util.function.UnaryOperator)"](b => b.researchStack(Item.of('minecraft:coal_block')).EUt(GTValues.VA[GTValues.IV]).duration(420)) // (1)
})
```

1. 注意，由于 JS 集成的工作方式，你必须强制 `scannerResearch` 以特定方式解释：
   Scanner Research 在 `.researchStack()` 对象中接收 `ItemStack` 输入，也可以在 `.researchStack()` 对象外定义 `EUt` 和
   `Duration`。

```js title="station_research"
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembly_line('station_test')
        .itemInputs('64x minecraft:coal')
        .itemOutputs('minecraft:diamond')
        .duration(10000)
        .EUt(GTValues.VA[GTValues.IV])
        .stationResearch(b => b.researchStack(Item.of('minecraft:coal_block')).EUt(GTValues.VA[GTValues.IV]).CWUt(10)) // (1)
})
```

1. 与 `Scanner Research` 一样，`Station Research` 在 `.researchStack()` 对象中接收 `ItemStack` 输入，
   但你只能在 `.researchStack()` 对象外定义 `EUt` 和 `CWUt`。`CWUt` 用于定义
   `Station Research` 配方的持续时间。

### 碎石机流体

碎石机配方使用 AdjacentFluidConditions。

要添加条件，可以使用 `adjacentFluids(Fluid...)` 方法，参见[其他 condition builder 方法](https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/src/main/java/com/gregtechceu/gtceu/integration/kjs/recipe/GTRecipeSchema.java#L894)。

```js title="rock_breaker.js"
ServerEvents.recipes(event => {
    event.recipes.gtceu.rock_breaker('rhino_jank')
        .notConsumable('minecraft:dirt')
        .itemOutputs('minecraft:dirt')
        .adjacentFluids('minecraft:water')
        .adjacentFluids('minecraft:lava')
        .duration(16)
        .EUt(30)
})
```

### 更多自定义 ingredients
更多自定义 ingredients 请参见[侧边栏中的 Ingredients 列表](Ingredients/index.md)。
