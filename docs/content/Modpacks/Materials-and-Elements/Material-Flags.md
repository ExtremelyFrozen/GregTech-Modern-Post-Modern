---
title: "Material Flags（材料 Flag）"
---


# Material Flags（材料 Flag）

使用 Material Flags 可以为每种 Material 指定若干属性，
这些属性会影响该 Material 的行为，以及会为它生成哪些物品。

=== "Javascript"
    ```js
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('my_material')
            // ...
            .flags(GTMaterialFlags.FLAMMABLE)
    })
    ```
=== "Java"
    ```java
    public static Material MY_MATERIAL;
    public static void register() {
       MY_MATERIAL = new Material.Builder(
            your_mod_id.id('my_material'))
            // ...
            .flags(GTMaterialFlags.FLAMMABLE)
            .buildAndRegister();
        }
    ```


# 通用 Flags

- `NO_UNIFICATION`
  - 说明：添加到 Material 后会完全禁用其自动配方生成。此 Flag 已弃用，请改用 `DISABLE_MATERIAL_RECIPES`。

- `DISABLE_MATERIAL_RECIPES`
  - 说明：添加到 Material 后会完全禁用其自动配方生成。它取代了 `NO_UNIFICATION`。

- `DECOMPOSITION_BY_ELECTROLYZING`
    - 说明：启用电解机分解配方生成。需要设置 `.components(...)`。

- `DECOMPOSITION_BY_CENTRIFUGING`
    - 说明：启用离心机分解配方生成。需要设置 `.components(...)`。

- `DISABLE_DECOMPOSITION`
    - 说明：禁用此 Material 的分解配方生成。

- `EXPLOSIVE`
    - 说明：带有此 Flag 的 Material 不会拥有内爆压缩配方，并且在电弧炉回收时会产出灰烬，而不是该 Material 本身。

- `FLAMMABLE`
    - 说明：添加此 Flag 表示该 Material 不能被熔炼，因此不会生成 EBF 配方/熔炉配方。同时也会像 `EXPLOSIVE` 一样禁用内爆压缩机配方。

- `STICKY`
    - 说明：如果 Material 是黏性的，请添加此 Flag。它会改变已放置流体的黏度。默认只有油类和木馏油拥有可放置状态。

- `PHOSPHORESCENT`
    - 说明：将此 Flag 添加到 Material 后，无论流体状态如何（液体、气体、等离子体），其流体亮度都会变为 15。否则，只有液态默认亮度为 10。

# Dust Flags（粉类 Flag） { #dust-flags }

- `GENERATE_PLATE`
     - 说明：为此 Material 生成板和双重板。
     - 必需 Flags：`GENERATE_PLATE`。
     - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_DENSE`
     - 说明：为此 Material 生成致密板。
     - 必需 Flags：`GENERATE_PLATE`。
     - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_ROD`
    - 说明：为此 Material 生成杆。
    - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_BOLT_SCREW`
    - 说明：为此 Material 生成螺栓和螺丝。
    - 必需 Flags：`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_FRAME`
    - 说明：为此 Material 生成框架。
    - 必需 Flags：`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_GEAR`
    - 说明：为此 Material 生成齿轮。
    - 必需 Flags：`GENERATE_PLATE`、`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.DUST`。

- `GENERATE_LONG_ROD`
    - 说明：为此 Material 生成长杆。
    - 必需 Flags：`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.DUST`。

- `FORCE_GENERATE_BLOCK`
    - 说明：添加到 Material 后会强制生成方块。
    - 必需 Properties：`PropertyKey.DUST`。

- `EXCLUDE_BLOCK_CRAFTING_RECIPES`
    - 说明：阻止 Material 创建粉到方块以及方块到粉的无序配方。同时也会阻止通过 `SHAPE_EXTRUDING`/`MOLD_BLOCK` 生成挤压和合金冶炼配方。
    - 必需 Properties：`PropertyKey.DUST`。

- `EXCLUDE_PLATE_COMPRESSOR_RECIPE`
    - 说明：如果想禁用锻造锤制板配方，请添加到 Material。
    - 必需 Flags：`GENERATE_PLATE`。
    - 必需 Properties：`PropertyKey.DUST`。

- `EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES`
    - 说明：阻止 Material 创建粉到方块以及方块到粉的无序配方。
    - 必需 Properties：`PropertyKey.DUST`。

- `MORTAR_GRINDABLE`
    - 说明：为此 Material 添加研钵研磨配方。
    - 必需 Properties：`PropertyKey.DUST`。

- `NO_WORKING`
    - 说明：如果该 Material 除砸碎或熔炼外不能通过其他方式加工，请添加此 Flag。这用于带涂层的 Material。
    - 必需 Properties：`PropertyKey.DUST`。

- `NO_SMASHING`
    - 说明：如果 Material 无法弯折，因此不能用于常规金属加工技术，请添加此 Flag。
    - 必需 Properties：`PropertyKey.DUST`。

- `NO_SMELTING`
    - 说明：如果无法熔炼此 Material，请添加此 Flag。
    - 必需 Properties：`PropertyKey.DUST`。

- `NO_ORE_SMELTING`
    - 说明：如果无法从矿石熔炼得到此 Material，请添加此 Flag。
    - 必需 Properties：`PropertyKey.DUST`。

- `NO_ORE_PROCESSING_TAB`
    - 说明：添加到 Material 后会禁用矿石处理标签页的创建。
    - 必需 Properties：`PropertyKey.ORE`。

- `BLAST_FURNACE_CALCITE_DOUBLE`
    - 说明：如果想让此 Material 的矿石在 Blast Furnace 中配合方解石加热并获得双倍产出，请添加此 Flag。已列出的 Material 包括：Iron、Pyrite、PigIron、WroughtIron。
    - 必需 Properties：`PropertyKey.DUST`。

- `BLAST_FURNACE_CALCITE_TRIPLE`
    - 说明：如果想让此 Material 的矿石在 Blast Furnace 中配合方解石加热并获得三倍产出，请添加此 Flag。
    - 必需 Properties：`PropertyKey.DUST`。

- `DISABLE_ALLOY_BLAST`
    - 说明：用于禁用合金高炉配方生成。
    - 必需 Properties：`PropertyKey.BLAST`、`PropertyKey.FLUID`。

- `DISABLE_ALLOY_PROPERTY`
    - 说明：用于禁用与合金高炉处理相关的一切内容。
    - 必需 Flags：`DISABLE_ALLOY_BLAST`。
    - 必需 Properties：`PropertyKey.BLAST`、`PropertyKey.FLUID`。

# Fluid Flags（流体 Flag） { #fluid-flags }

- `SOLDER_MATERIAL`
    - 说明：允许此 Material 代替焊料合金使用。
    - 必需 Properties：`PropertyKey.FLUID`。

- `SOLDER_MATERIAL_BAD`
    - 说明：尚未实现。预期用于将此 Material 设置为较差的焊料材料。
    - 必需 Properties：`PropertyKey.FLUID`。

- `SOLDER_MATERIAL_GOOD`
    - 说明：尚未实现。预期用于将此 Material 设置为优良的焊料材料。
    - 必需 Properties：`PropertyKey.FLUID`。

# Ingot Flags（锭 Flag） { #ingot-flags }

- `GENERATE_FOIL`
    - 说明：为此 Material 生成箔。
    - 必需 Flags：`GENERATE_PLATE`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_RING`
    - 说明：为此 Material 生成环。
    - 必需 Flags：`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_SPRING`
    - 说明：为此 Material 生成弹簧。
    - 必需 Flags：`GENERATE_LONG_ROD`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_SPRING_SMALL`
    - 说明：为此 Material 生成小弹簧。
    - 必需 Flags：`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_SMALL_GEAR`
    - 说明：为此 Material 生成小齿轮。
    - 必需 Flags：`GENERATE_PLATE`、`GENERATE_ROD`。
    - 必需 Properties：`PropertyKey.INGOT`。

-   `GENERATE_FINE_WIRE`
    - 说明：为此 Material 生成所有线材。
    - 必需 Flags：`GENERATE_FOIL`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_ROTOR`
    - 说明：为此 Material 生成转子。
    - 必需 Flags：`GENERATE_BOLT_SCREW`、`GENERATE_RING, GENERATE_PLATE`。
    - 必需 Properties：`PropertyKey.INGOT`。

- `GENERATE_ROUND`
    - 说明：为此 Material 生成圆片。
    - 必需 Properties：`PropertyKey.INGOT`。

- `IS_MAGNETIC`
    - 说明：如果此 Material 是另一种 Material 的磁化形态，请添加此 Flag。当 Material 拥有此 Flag 时，研磨机会将它研磨成对应的非磁性版本，后者会用于某些合成配方。
    - 必需 Properties：`PropertyKey.INGOT`。

# Gem Flags（宝石 Flag） { #gem-flags }

- `CRYSTALLIZABLE`
    - 说明：此 Material 是否可以结晶（通过高压釜重新转化为宝石）。
    - 必需 Properties：`PropertyKey.GEM`。

- `GENERATE_LENS`
    - 说明：为此 Material 生成透镜。
    - 必需 Flags：`GENERATE_PLATE`。
    - 必需 Properties：`PropertyKey.GEM`。

# Ore Flags（矿石 Flag） { #ore-flags }
- `HIGH_SIFTER_OUTPUT`
    - 说明：提高该 Material 的宝石矿石在筛选机中的产出。
    - 必需 Properties：`PropertyKey.GEM`、`PropertyKey.ORE`。
