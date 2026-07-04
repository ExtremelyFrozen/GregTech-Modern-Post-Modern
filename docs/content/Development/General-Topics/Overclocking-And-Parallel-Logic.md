---
title: 超频与并行逻辑
---


# 如何设置超频与并行逻辑

为了提高通用性，GregTech Post Modern 重写了 `RecipeLogic`，使其支持 EU、item 和 fluid 之外的输入与输出。

新的 `RecipeLogic` 不再直接处理超频和并行逻辑，而是通过 `IRecipeLogicMachine` 将这些逻辑委托给 machine：

```java
/**
 * Override it to modify recipe on the fly e.g. applying overclock,
 * change chance, etc
 *
 * @param recipe recipe from detected from GTRecipeType
 * @return modified recipe.
 *         null -- this recipe is unavailable
 */
@Nullable
GTRecipe modifyRecipe(GTRecipe recipe);
```


## 电力超频

通常，简单的电力超频可以按以下方式实现。细节请参阅 `OverclockingLogic` 和 `RecipeHelper`。

recipe tier 已从 EU 内容中独立出来，存储在 `recipe.tier`。EU 内容表示的是该 recipe 的 total EU/t，不再承担 recipe tier 的含义。进行机器电压判断时应使用 `ITieredMachine#getTierVoltage`；执行 overclock 计算时应使用 `IOverclockMachine#getOverclockVoltage`。

```java
public @Nullable GTRecipe modifyRecipe(GTRecipe recipe) {
    if (RecipeHelper.getRecipeEUtTier(recipe) > getTier()) {
        return null;
    }

    return RecipeHelper.applyOverclock(
        getDefinition().getOverclockingLogic(),
        recipe,
        getMaxVoltage()
    );
}
```


## 并行逻辑

并行逻辑实现起来也不复杂。这里以 `generator` 为例：

```java
public @Nullable GTRecipe modifyRecipe(GTRecipe recipe) {
    var EUt = RecipeHelper.getOutputEUt(recipe); // get the recipe's EU/t

    if (EUt > 0) {
        // calculate the max parallel limitation.
        var maxParallel = (int) (Math.min(
            energyContainer.getOutputVoltage(),
            GTValues.V[overclockTier]
        ) / EUt);

        while (maxParallel > 0) {
            // copy and apply parallel, it will affect all recipes' contents
            // and the recipe duration.
            var copied = recipe.copy(ContentModifier.multiplier(maxParallel));

            // If the machine has enough ingredients, return copied recipe.
            if (copied.matchRecipe(this)) {
                copied.duration = copied.duration / maxParallel;

                return copied;
            }

            // Trying to halve the number of parallelism
            maxParallel /= 2;
        }
    }
    return null;
}
```
