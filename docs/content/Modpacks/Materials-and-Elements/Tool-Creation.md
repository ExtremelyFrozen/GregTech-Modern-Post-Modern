---
title: "工具创建"
---

你可以在 Material 的代码中调用 toolStats，用自己创建的 Materials 制作工具。

toolStats 接受以下参数：

`.toolStats(float harvestSpeed, float attackDamage, int durability, int harvestLevel, GTToolType[] types)`

- `harvestSpeed: float` 是工具在世界中实际破坏方块的速度。
- `attackDamage: float` 是每次命中对生物/玩家造成的伤害量。
- `durability: int` 是工具在损坏前可以使用的次数。
    - 这同时适用于合成使用和世界中使用。
      合成通常每次使用消耗 2 点耐久。
- `harvestLevel: int` 是它能破坏的方块等级。
    - 可以取 0-6 之间的整数，其中 0 表示木，6 表示 neutronium。
- `types: GTToolType[]` 是对象中的工具数组。
    - 必须使用 [] 记法将它们作为数组传入。
      如果希望你的 Material 应用于所有工具类型，可以省略此参数。

下面包含一个使用示例。
=== "JavaScript"
    ```js title="example_tool_material.js"
    // When working with tools in kubejs you will need to load these classes at the top of your file.
    Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey');
    Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty');
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('aluminfrost')
            .ingot()
            .color(0xadd8e6).secondaryColor(0xc0c0c0).iconSet(GTMaterialIconSet.DULL)
            .toolStats(new ToolProperty(12.0, 7.0, 3072, 6,
                [
                    GTToolType.DRILL_LV,
                    GTToolType.MINING_HAMMER
                ]
            ))
    });
    ```
=== "Java"
    ```java title="ExampleToolMaterial.java"
            public static Material ALUMINFROST;
            ALUMINFROST = new Material.Builder(
                your_mod_id.id("aluminfrost"))
                .color(0xadd8e6).secondaryColor(0xc0c0c0).iconSet(MaterialIconSet.DULL)
                .toolStats(new ToolProperty(12.0F, 7.0F, 3072, 6,
                        new GTToolType[] { GTToolType.DRILL_LV, GTToolType.MINING_HAMMER }))
                .buildAndRegister();
    ```
使用 ToolProperties.Builder 时，也可以为工具添加更多参数。
builder 拥有与构造器相同的参数，并且可以链式调用以下方法：

- `.unbreakable()`
    - 使电动工具实际上绕过耐久，从而永不损坏。
- `.magnetic()`
    - 使挖掘出的方块和生物掉落物传送到玩家物品栏。
- `attackSpeed(float attackSpeed)`
    - 设置由此 Material 制成工具的攻击速度（动画时间）。
- `ignoreCraftingTools()`
    - 禁用由此 Material 制作合成工具。

- `.enchantment(Enchantment enchantment, int level)`
    - Enchantment 是工具创建时应用的默认附魔。
      Level 是该附魔的等级。
- `enchantability(int enchantability)`
    - 设置由此 Material 制成工具的基础附魔能力。
      Iron 为 14，Diamond 为 10，Stone 为 5。

下面是在 Material 中使用 builder 的示例：
=== "JavaScript"
    ```js title="example_tool_material.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('aluminfrost')
            .ingot()
            .color(0xadd8e6).secondaryColor(0xc0c0c0).iconSet(GTMaterialIconSet.DULL)
            .toolStats(
                ToolProperty.Builder.of(1.8, 1.7, 700, 3,
                    [
                        GTToolType.SWORD,
                        GTToolType.PICKAXE,
                        GTToolType.SHOVEL,
                    ]
                )
                .unbreakable()
                .enchantment(SILK_TOUCH, 1)
                .build()
            )
    });
    ```
=== "Java"
    ```java title="ExampleToolMaterial.java"
        public static Material ALUMINFROST;
        ALUMINFROST = new Material.Builder(
                your_mod_id.id("aluminfrost"))
                .ingot()
                .color(0xadd8e6).secondaryColor(0xc0c0c0).iconSet(MaterialIconSet.DULL)
                .toolStats(ToolProperty.Builder.of(1.8F, 1.7F, 700, 3)
                        .types(
                                GTToolType.SWORD,
                                GTToolType.PICKAXE,
                                GTToolType.SHOVEL)
                        .unbreakable()
                        .enchantment(SILK_TOUCH, 1)
                        .build())
                .buildAndRegister();
    ```

你也可以修改已经拥有 tool property 的 GT Material 的工具属性。不过，由于当前 tool property 是不可变的，你必须先移除它。
=== "JavaScript"
    ```js title="tool_replacement.js"
    GTCEuStartupEvents.materialModification(event => {
        if (GTMaterials.TungstenCarbide.hasProperty(PropertyKey.TOOL)) {
            GTMaterials.TungstenCarbide.removeProperty(PropertyKey.TOOL);
        }
        GTMaterials.TungstenCarbide.setProperty(PropertyKey.TOOL,
            ToolProperty.Builder.of(180, 5.9, 2147483647, 6,
            [
                GTToolType.SOFT_MALLET,
                GTToolType.DRILL_LV
            ]
        ).build());
    });
    ```
=== "Java"
    ```java title="ToolReplacement.java"
    public static void modifyMaterials() {
        if (GTMaterials.TungstenCarbide.hasProperty(PropertyKey.TOOL)) {
            GTMaterials.TungstenCarbide.removeProperty(PropertyKey.TOOL);
        }
        TungstenCarbide.setProperty(PropertyKey.TOOL,
                (ToolProperty.Builder.of(180, 5.9, 2147483647, 6, GTToolType.SOFT_MALLET, GTToolType.DRILL_LV)
                        .build()));
    }

    ```

以下是所有 GtToolTypes 的列表。

- SWORD
- PICKAXE
- SHOVEL
- AXE
- HOE
- MINING_HAMMER
- SPADE
- SAW
- HARD_HAMMER
- SOFT_MALLET
- WRENCH
- FILE
- CROWBAR
- SCREWDRIVER
- MORTAR
- WIRE_CUTTER
- SCYTHE
- KNIFE
- BUTCHERY_KNIFE
- PLUNGER
- DRILL_LV
- DRILL_MV
- DRILL_HV
- DRILL_EV
- DRILL_IV
- CHAINSAW_LV
- WRENCH_LV
- WRENCH_HV
- WRENCH_IV
- BUZZSAW
- SCREWDRIVER_LV
- WIRE_CUTTER_LV
- WIRE_CUTTER_HV
- WIRE_CUTTER_IV
