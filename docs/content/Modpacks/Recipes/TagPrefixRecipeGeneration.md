---
title: "Tag Prefix 配方生成"
---

# 基于 TagPrefixes 生成配方

大多数将某种材料形态转换为另一种形态的配方，例如 iron ingots 到 iron plates，或 tin bolts 到 tin screws，都是通过基于 tag prefix 的配方生成完成的。

Gregtech 会遍历所有 materials，以及该 material 可用的所有 tag prefixes 来生成配方。你也可以在自己的 addon 中参照以下方式执行同样逻辑：

```java title="TagPrefixRecipes.java"

public static void recipeAddition(Consumer<FinishedRecipe> consumer) {

    for (Material material : GTRegistries.MATERIALS) {
            if (material.hasFlag(MaterialFlags.NO_UNIFICATION)) {
                continue;
            }
        MaterialRecipeHandler.run(provider, material)
    }

}

```

```java title="MaterialRecipeHandler.java"

public static void run(@NotNull Consumer<FinishedRecipe> provider, @NotNull Material material) {
        processFrame(provider, material);
}

private static void processFrame(@NotNull Consumer<FinishedRecipe> provider, @NotNull Material material) {
        if (!material.shouldGenerateRecipesFor(frameGt) || !material.hasProperty(PropertyKey.DUST)) {
            return;
        } // (1)

        if (material.hasFlag(GENERATE_FRAME)) {
            boolean isWoodenFrame = material.hasProperty(PropertyKey.WOOD);
            VanillaRecipeHelper.addShapedRecipe(provider, String.format("frame_%s", material.getName()),
                    ChemicalHelper.get(frameGt, material, 2),
                    "SSS", isWoodenFrame ? "SsS" : "SwS", "SSS",
                    'S', new UnificationEntry(rod, material));

            ASSEMBLER_RECIPES.recipeBuilder("assemble_" + material.getName() + "_frame")
                    .inputItems(rod, material, 4)
                    .circuitMeta(4)
                    .outputItems(frameGt, material)
                    .EUt(VA[ULV]).duration(64)
                    .save(provider);
        }
    }

```
1. 检查该 material 是否拥有带特定 tag prefix 的有效物品，并且能生成对应配方。
