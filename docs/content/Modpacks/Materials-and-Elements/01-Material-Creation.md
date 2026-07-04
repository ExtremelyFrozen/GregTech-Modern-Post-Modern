---
title: Material 创建
---


Materials 是游戏内物品或流体。它们可以是 dusts、ingots、gems、fluids 及其所有派生形态。
!!! note
    如果要添加一种存在于元素周期表中、但没有任何游戏内物品/流体的 material，请查看 [material 修改页面](./Modifying-Existing-Materials.md)。

可以通过添加以下调用的任意组合来修改 material 的属性：

- `.ingot()` 会让 material 同时拥有 ingot 和 dust 形态。
- `.dust()` 会让 material 拥有 dust 形态。不要与 `.ingot()` 一起使用。
- `.gem()` 会让 material 同时拥有 gem 形态和 dust 形态。不要与 `.dust()` 或 `.ingot()` 一起使用。
- `.liquid()` 会让 material 拥有具备液体属性的 liquid（fluid）形态。
- `.block()` 会让 material 拥有可放置的（block）fluid 形态。需要 `.liquid()`。
- `.gas()` 会让 material 拥有具备气体属性的 gas（fluid）形态。
- `.plasma()` 会让 material 拥有具备 plasma 属性的 plasma（fluid）形态。
- `.polymer()` 会让 material 拥有具备 polymer 属性的 dust 形态。
- `.ore()` 会从该 material 创建 ore。
    - 可选地，你可以添加以下任意参数组：
        1. `boolean isEmissive` -> `true` 表示使用 emissive textures
        2. `int oreMultiplier, int byproductMultiplier` -> 一个 raw ore 会给出多少 crushed ores，以及整个矿石处理过程中会给出多少 byproducts dusts
        3. `int oreMultiplier, int byproductMultiplier, boolean isEmissive` -> 见前两点
- `.burnTime(int burnTime)` 会让 material 成为熔炉燃料。
- `.fluidBurnTime(int burnTime)` 定义该 material 的 fluid 会燃烧多久。
- `.components(component1, component2, ...)` 描述组成。components 是元素列表，形式为：`'Kx material_name'`，其中 `K` 是正整数。
- `.element(element)` 类似于 `.components()`，但用于 material 表示元素本身的情况。
- `.iconSet(set)` 为 material 指定 icon set。
- `.color(int colorCode)` 为 material 指定颜色。颜色必须以十六进制值提供，形式为：`0xRRGGBB`。
- `.secondaryColor(int colorCode)` 为 material 指定 secondary color。如果不调用它，secondary 值默认是白色（0xffffff）。
    - secondary color 是覆盖在 material 主颜色上的叠加色。可以在 material 的 dust 上看到，因为 secondary color 的轮廓可见。Rotors 也是另一个明显例子。
- `.addDefaultEnchant(string EnchantName, int level)` 为 material 添加默认 enchant。

!!! tip "Harvest Level & Burn Time"
    对于 `.ingot()`、`.dust()` 和 `.gem()`，可以选择在括号内放入以下任意参数组：

    1. harvest level（例如 `.ingot(2)` 会让 material 拥有 iron tools 的 harvest level）
    2. harvest level, burn time（例如 `ingot(2, 2000)` 会让 material 拥有 iron tools 的 harvest level，并可在熔炉中作为燃料燃烧 2000 ticks 或 100 秒）。

!!! tip "选择 EU/t"
    GT 有一些内置常量，可以方便选择所需的 EU/t：

    - `GTValues.V` 表示所选 tier 的一整安培电力

    - `GTValues.VA` 表示一整安培，并按 cable loss 调整

    - `GTValues.VH` 表示半安培

    - `GTValues.VHA` 表示半安培，并按 cable loss 调整

    这些值是数组，包含各 tier 对应的 EU/t 值。
    例如，可以这样获取按 cable loss 调整后的一整安培 EV 电力：

    ```js
    GTValues.VA[GTValues.EV]
    ```

??? tip "颜色选择器"
    要为 material 选择颜色，可以查看 [color picker](https://www.w3schools.com/colors/colors_picker.asp)。
    使用上述工具选择颜色后，复制颜色预览下 # 后面的 6 位数字。

## 创建 Ingot

=== "JavaScript"
    ```js title="ingot.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('andesite_alloy')
            .ingot()
            .components('1x andesite', '1x iron')
            .color(0x839689).iconSet(GTMaterialIconSet.DULL)
            .flags(GTMaterialFlags.GENERATE_PLATE, GTMaterialFlags.GENERATE_GEAR, GTMaterialFlags.GENERATE_SMALL_GEAR)
    })
    ```
=== "Java"
    ```java title="Ingot.java"
    public static Material ANDESITE_ALLOY;
    public static void register() {
        ANDESITE_ALLOY = new Material.Builder(
                your_mod_id.id("andesite_alloy"))
                .ingot()
                .components("1x andesite", "1x iron")
                .color(0xFF0000).secondaryColor(0x840707).iconSet(GTMaterialIconSet.DULL)
                .flags(MaterialFlags.GENERATE_PLATE, MaterialFlags.GENERATE_GEAR, MaterialFlags.GENERATE_SMALL_GEAR)
                .buildAndRegister();
        }
    ```

## 创建 Dust

=== "JavaScript"
    ```js title="dust.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('mysterious_dust')
            .dust() // The harvest level and burn time can be specified in the brackets. Example: `.dust(2, 4000)`
            .color(0x7D2DDB)
    })
    ```

=== "Java"
    ```java title="Dust.java"
    public static Material MYSTERIOUS_DUST;
    public static void register() {
        MYSTERIOUS_DUST = new Material.Builder(
            your_mod_id.id("mysterious_dust"))
            .dust() // The harvest level and burn time can be specified in the brackets. Example: `.dust(2, 4000)`
            .color(0x7D2DDB)
            .buildAndRegister();
    }
    ```

## 创建 Gem

=== "JavaScript"
    ```js title="gem.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('purple_coal')
            .gem(2, 4000)
            .element(GTElements.C)
            .ore(2, 3)
            .color(0x7D2DDB).iconSet(GTMaterialIconSet.LIGNITE)

    })
    ```

=== "Java"
    ```java title="Gem.java"
    public static Material PURPLE_COAL;
    public static void register() {
        PURPLE_COAL = new Material.Builder(
            your_mod_id.id("purple_coal"))
            .gem(2, 4000)
            .element(GTElements.C)
            .ore(2, 3)
            .color(0x7D2DDB).iconSet(GTMaterialIconSet.LIGNITE)
            .buildAndRegister();
        }
    ```

## 创建 Fluid

=== "JavaScript"
    ```js title="fluid.js"
    // const $FluidBuilder = Java.loadClass('com.gregtechceu.gtceu.api.fluids.FluidBuilder'); Uncomment if you want to use the Fluid Builder.
    GTCEuStartupEvents.registry('gtceu:material', event => {
        event.create('mysterious_ooze')
          .fluid() // Or .liquid(Int Temperature)
          .color(0x500bbf)
    })
    ```

=== "Java"
    ```java title="Fluid.java"
    public static Material MYSTERIOUS_OOZE;
    public static void register() {
        MYSTERIOUS_OOZE = new Material.Builder(
            your_mod_id.id("mysterious_ooze"))
            .fluid() // Or .liquid(Int Temperature)
            .color(0x500bbf)
            .buildAndRegister();
        }
    ```

!!! note
    - 要创建可放置的 fluid，需要调用 FluidBuilder class 的新实例，并在其中调用 .block()。Java 和 kubejs 中的语法相同，但 kubejs 需要先加载 FluidBuilder class。
        - 例如：`.liquid(new $FluidBuilder().block().temperature(3100))`。


!!! tip "更多 Material 信息"
    如需更细粒度地控制 material，请查看下方页面。

完整 flags 列表请查看 [Material Flags 页面](./Material-Flags.md)。

完整 material properties 列表请查看 [Material Properties 页面](./Material-Properties.md)。

关于 tools 的说明请查看 [Tool Creation 页面](./Tool-Creation.md)。

关于自定义 icon sets 的说明和现有列表，请查看 [Icon Set 页面](./Material-Icon-Sets.md)。
