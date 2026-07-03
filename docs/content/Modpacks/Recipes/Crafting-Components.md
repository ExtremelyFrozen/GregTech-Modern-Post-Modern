---
title: "合成组件"
---

# 合成组件

Crafting Components 是一种组织和简化 GregTech 生成的各类相似配方的方式。例如：所有 tier 的合金炉配方可以迭代写出，而不必逐个手写。

Crafting Components 是一个 map，将 Voltage tier（tier number）映射到一个值。该值可以是 `MaterialEntry`、`ItemStack` 或 `TagPrefix<Item>`。

## 修改单个条目

使用 KubeJS 可以修改现有 GTCEu Modern 机器合成配方中预定义的 components。
你可以替换单个条目，也可以批量修改 components。
如果没有其他条目存在，-1 会作为 fallback 值。

```js title="startup/modification.js"
const Map = Java.loadClass("java.util.Map")

GTCEuStartupEvents.craftingComponents(event => {
    event.setItem(GTCraftingComponents.CIRCUIT, GTValues.MV, Item.of('minecraft:dirt')) // (1)
    event.setItems(GTCraftingComponents.PUMP, Map.of(
        GTValues.LV, Item.of('gtceu:lv_robot_arm'),
        GTValues.MV, Item.of('gtceu:mv_robot_arm'),
        GTValues.HV, Item.of('gtceu:hv_robot_arm'),
        GTValues.EV, Item.of('gtceu:ev_robot_arm'),
        GTValues.IV, Item.of('gtceu:iv_robot_arm'),
        GTValues.LuV, Item.of('gtceu:luv_robot_arm'),
        GTValues.ZPM, Item.of('gtceu:zpm_robot_arm'),
        GTValues.UV, Item.of('gtceu:uv_robot_arm'),
    )) // (2)
    event.setTag(GTCraftingComponents.CASING, GTValues.EV, 'minecraft:logs') // (3)
    event.setMaterialEntry(GTCraftingComponents.PLATE, GTValues.UEV, new MaterialEntry('plate', 'gtceu:infinity')) // (4)
    event.removeTier("sensor", 3) // (5)
})
```
1. 将所有 GT 机器合成配方中的 MV circuit tag 替换为单个 `minecraft:dirt` 方块。
2. 将 GT 机器合成配方中的所有 pumps 替换为 robot arm。
3. 将 EV casing 替换为 `#minecraft:logs` 标签。注意标签开头没有 `#`！
4. 为 plate component 的 UEV 添加一个新条目，prefix 为 `plate`，material 为 `gtceu:infinity`。
5. 移除 sensor crafting component 的第 3 个偏移条目 `(HV Tier)`，会回退到 fallback `(LV Sensor)`。


## 创建新 components

也可以使用 KubeJS 创建新的 crafting components。
crafting component 通过一个 id 和一个 fallback 值构造。构造后可以链式调用 `.add(tier, value)` 方法添加条目。

```js title="creation.js"
const Map = Java.loadClass("java.util.Map")

let ITEM_CRAFTING_COMPONENT = null
let TAG_CRAFTING_COMPONENT = null
let UNIFICATION_CRAFTING_COMPONENT = null

GTCEuServerEvents.craftingComponents(event => {
    ITEM_CRAFTING_COMPONENT = event.createItem("item_component", 'minecraft:cyan_stained_glass')
        .addItem(GTValues.LV, Item.of('minecraft:cyan_stained_glass'))
        .addItem(GTValues.MV, Item.of('minecraft:cyan_stained_glass'))
        .addItem(GTValues.HV, Item.of('minecraft:cyan_stained_glass'))
        .addItem(GTValues.EV, Item.of('minecraft:lime_stained_glass'))
        .addItem(GTValues.IV, Item.of('minecraft:lime_stained_glass'))
        .addItem(GTValues.LuV, Item.of('minecraft:lime_stained_glass'))
        .addItem(GTValues.ZPM, Item.of('minecraft:magenta_stained_glass'))
        .addItem(GTValues.UV, Item.of('minecraft:magenta_stained_glass'))
    // (1)
    TAG_CRAFTING_COMPONENT = event.createTag("tag_component", 'forge:barrels/wooden')
        .addTag(GTValues.LV, 'forge:chests/wooden')
        .addTag(GTValues.MV, 'forge:chests/trapped')
        .addTag(GTValues.HV, 'forge:chests/ender')
        .addTag(GTValues.EV, 'forge:cobblestone')
        .addTag(GTValues.IV, 'forge:cobblestone/normal')
        .addTag(GTValues.LuV, 'forge:cobblestone/infested')
        .addTag(GTValues.ZPM, 'forge:cobblestone/mossy')
        .addTag(GTValues.UV, 'forge:cobblestone/deepslate')
    // (2)
    UNIFICATION_CRAFTING_COMPONENT = event.createMaterialEntry("material_entry_component", new MaterialEntry('plate', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.LV, new MaterialEntry('block', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.MV, 'ingot', 'gtceu:infinity')
        .addMaterialEntry(GTValues.HV, new MaterialEntry('dust', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.EV, new MaterialEntry('round', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.IV, new MaterialEntry('foil', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.LuV, 'longRod', 'gtceu:infinity')
        .addMaterialEntry(GTValues.ZPM, new MaterialEntry('rod', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.UV, new MaterialEntry('bolt', 'gtceu:infinity'))
    // (3)
})
```

1. 创建带 item stack 条目的新 crafting component。
2. 创建带 item tag 条目的新 crafting component。注意标签开头没有 `#`！
3. 创建带 UnificationEntry 条目的新 crafting component。

## 获取现有 crafting components

所有 `remove`、`modify*` 和 `setFallback*` 方法都会将 Crafting Component 作为第一个参数；你可以只提供匹配该 crafting component id 的字符串作为参数。

```js title="modify.js"

GTCEuServerEvents.craftingComponents(event => {
    event.removeTier('robot_arm', GTValues.EV) // (1)

    event.removeTiers('pump', GTValues.EV, GTValues.IV, GTValues.LuV) // (2)
})
```

1. 找到 id 为 `robot_arm` 的 crafting component，并移除 `EV` tier 的条目。
2. 找到 id 为 `pump` 的 crafting component，并移除 `EV, IV & LuV` tiers 的条目。

### 内置 Crafting Components

- `CIRCUIT 'circuit'`
- `BETTER_CIRCUIT 'better_circuit'`
- `WIRE_ELECTRIC 'wire_single'`
- `WIRE_QUAD 'wire_quad'`
- `WIRE_OCT 'wire_oct'`
- `WIRE_HEX 'wire_hex'`
- `CABLE 'cable_single'`
- `CABLE_DOUBLE 'cable_double'`
- `CABLE_QUAD 'cable_quad'`
- `CABLE_OCT 'cable_oct'`
- `CABLE_HEX 'cable_hex'`
- `CABLE_TIER_UP 'cable_tier_up_single'`
- `CABLE_TIER_UP_DOUBLE 'cable_tier_up_double'`
- `CABLE_TIER_UP_QUAD 'cable_tier_up_quad'`
- `CABLE_TIER_UP_OCT 'cable_tier_up_oct'`
- `CABLE_TIER_UP_HEX 'cable_tier_up_hex'`
- `CASING 'casing'`
- `HULL 'hull'`
- `PIPE_NORMAL 'normal_pipe'`
- `PIPE_LARGE 'large_pipe'`
- `PIPE_NONUPLE 'nonuple_pipe'`
- `GLASS 'glass'`
- `PLATE 'plate'`
- `HULL_PLATE 'hull_plate'`
- `ROTOR 'rotor'`
- `GRINDER 'grinder'`
- `SAWBLADE 'sawblade'`
- `DIAMOND 'diamond'`
- `MOTOR 'motor'`
- `PUMP 'pump'`
- `PISTON 'piston'`
- `EMITTER 'emitter'`
- `SENSOR 'sensor'`
- `CONVEYOR 'conveyor'`
- `ROBOT_ARM 'robot_arm'`
- `FIELD_GENERATOR 'field_generator'`
- `COIL_HEATING 'coil_heating'`
- `COIL_HEATING_DOUBLE 'coil_heating_double'`
- `COIL_ELECTRIC 'coil_electric'`
- `STICK_MAGNETIC 'rod_magnetic'`
- `STICK_DISTILLATION 'rod_distillation'`
- `STICK_ELECTROMAGNETIC 'rod_electromagnetic'`
- `STICK_RADIOACTIVE 'rod_radioactive'`
- `PIPE_REACTOR 'pipe_reactor'`
- `POWER_COMPONENT 'power_component'`
- `VOLTAGE_COIL 'voltage_coil'`
- `SPRING 'spring'`
- `CRATE 'crate'`
- `DRUM 'drum'`
- `FRAME 'frame'`
- `SMALL_SPRING_TRANSFORMER 'small_spring_transformer'`
- `SPRING_TRANSFORMER 'spring_transformer'`
