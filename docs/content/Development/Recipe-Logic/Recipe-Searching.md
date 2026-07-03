---
title: "配方搜索"
---

# 配方搜索
!!! Note
    本文不是对 Recipe Searching 的深入解析，而是总体概览。
    文中会引用、删减并简化大量代码。

recipe 搜索分为 3 个阶段。

1. 创建 recipe
2. Recipe 查找
3. Recipe 匹配

本文会逐步介绍这些阶段。

## Recipe 创建
要创建 recipe，必须先创建 `RecipeType`。`RecipeType` 包含 recipe 的 metadata；对这里来说，更重要的是 `GTRecipeType` 内部持有的 `RecipeDB`，也就是 recipe 的查找存储。

当前加载路径会先把 `GTRecipeDefinition` 放入 `StagingRecipeDB`。staging 完成后，每个 definition recipe 会通过 `GTRecipeDefinition.toRuntime()` 转成运行态 `GTRecipe`，再烘焙进 `RecipeDB` 的 Trie。这个 Trie 持有 `Branch`，而每个 branch 都有一个 `Map<AbstractMapIngredient, Either<GTRecipe, Branch>>`。
将 recipe 添加到 `RecipeDB` 时，会逐个添加 ingredient，直到抵达运行态 `GTRecipe`。

因此，一个简化的 assembler(8) trie 可能是：
```
   Map { "8 cobblestone" -> Left(FurnaceRecipe),
         "4 iron rods" -> Right(Map {
               "4 iron plates" -> Left(Iron Machine Hull)
               })
        }
```
## Recipe 查找
在 `RecipeLogic.serverTick` 方法中（参见 [Recipe Logic](./Recipe-Logic.md)），会调用一个名为 `findAndHandleRecipe` 的方法。
它会检查 `lastRecipe` 是否已设置，以及能否再次匹配并运行。
如果无法匹配，它会调用 `handleSearchingRecipes(searchRecipe())`。

`searchRecipe()` 是实际的 recipe 搜索逻辑。大体上，它会找出 machine 当前可用的 ingredient，对它们分组，然后遍历 `RecipeDB`，创建一个可用 recipe 的 iterator。
`handleSearchingRecipes()` 随后遍历这个 iterator；对每个 recipe，它都会运行 recipe modifier，检查该 machine 能否运行它、输入是否存在、输出空间是否足够等。

`searchRecipe` 的实际代码是：
```java
    return machine.getRecipeType().searchRecipe(machine, r -> matchRecipe(r).isSuccess())
```
`GTRecipeType.searchRecipe(...)` 实际上会调用内部 `RecipeDB.iterator(holder, canHandle)`，返回 `RecipeDB.RecipeIterator`。
这个 canHandle 函数会在 Recipe 匹配阶段进一步展开。

这个函数首先通过 `RecipeDB.fromHolder` 从 machine 生成 ingredient 列表。`fromHolder` 会读取 `holder.getCapabilitiesForIO(IO.IN)`，遍历每个 `RecipeHandlerList` 中可参与 recipe search 的 `RecipeCapability`，对 handler 调用 `.getContents`，再压缩并转换为 `AbstractMapIngredient`。
之后，它会将 ingredient 列表传给新的 `RecipeDB.RecipeIterator`。iterator 使用显式栈遍历 `RecipeDB` 的 `Branch`，找到 `GTRecipe` 后再用 canHandle predicate 过滤。

至此，我们到达了实际执行 recipe 搜索的流程。
在深入这个流程之前，需要先理解 Ingredient 的工作方式。

## 从 Machine 到 Ingredient
trait 是一种在 machine 创建时存储到 machine 上的对象，例如：
```java
    public MachineTrait(MetaMachine machine) {
        // ...
        machine.attachTraits(this);
    }
```
一台 machine 可以拥有许多不同 trait。其中之一是 `IRecipeHandler`。这个 trait 在为 `RecipeLogic` 收集 input/output 时使用。
`IRecipeHandler` 是 input bus 的 item slot、circuit slot，或 singleblock 的 energy buffer 等对象的抽象。

其中一个例子是 `new NotifiableItemStackHandler(machine, slots, IO)`。它创建时会将自身 attach 到 machine，因此不需要手动将它连接到 `RecipeLogic`。`WorkableMachine` 会负责处理这一点。

这个 NotifiableHandler 有几个重要方法：

 - `List<Ingredient> getContents()` 方法，用于 recipe search 时获取 ingredient 列表
 - `List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate)` 方法，用于执行 handler 的 I/O 逻辑
    - `IO io`：recipe 正尝试向世界 input 还是 output，例如 IO.IN 从 handler 中取出内容，IO.OUT 将内容放入 handler
    - `GTRecipe recipe`：正在运行的 recipe
    - `List<Ingredient> left`：仍需放入 handler 或从 handler 取出的剩余 item
    - `boolean simulate`：这是否是模拟执行，例如 recipe checking，还是实际修改 handler 内容
    - 返回 `List<Ingredient>`，表示剩余 ingredient，即无法插入或抽取的内容。请注意，如果没有剩余内容，应返回 `null`。

这个例子中它是 `NotifiableRecipeHandlerTrait<Ingredient>`（这里的 `Ingredient` 是对 `ItemStack` 的包装，用于处理范围 input/output），但 recipe handler 可以处理任意类型。

为了实际存储这个 `Ingredient`，需要一个能被 `RecipeDB` 正确处理的对象。为此，我们将 `Ingredient` 包装进另一个对象，也就是 `ItemStackMapIngredient`。
这个类继承 `AbstractMapIngredient`，最重要的是它拥有正确的 `.hash()` 和 `.equals()` 函数。
`RecipeDB` 会使用它们在 Trie 中找到正确的 ingredient。

!!! Note
    两个应该匹配的对象也应该在 `.equals` 检查中返回 true，并拥有相同的 hash code。但 `.equals` 不一定必须是严格正确的 `.equals` 实现，例如用于 partial NBT 匹配时：
    （以下代码是伪代码）
    ```
    PartialNBTItemStackMapIngredient(Iron, {foo: bar, bar: baz}).equals(PartialNBTItemStackMapIngredient(Iron, {foo: bar}) = True"
    ```

!!! Note
    需要注意，即使两个函数能通过 `.equals` 匹配，如果它们没有相同的 hash，也不会在 recipe search 中匹配。

## `RecipeDB` 查找流程

!!! warning
    当前实现已经从旧的 `GTRecipeLookup.recurseIngredientTreeFindRecipe` 迁移到 `RecipeDB.RecipeIterator`。下面的旧版递归摘录只用于说明 Trie 查询思想；实际代码使用 iterator 的显式栈遍历 `Branch`，并返回运行态 `GTRecipe`。

```java
  /**
     * Recursively finds a recipe
     *
     * @param ingredients the ingredients part
     * @param branchMap   the current branch of the tree
     * @param canHandle   predicate to test found recipe.
     * @param index       the index of the wrapper to get
     * @param count       how deep we are in recursion, < ingredients.length
     * @param skip        bitmap of ingredients to skip, i.e. which ingredients are already used in the recursion.
     * @return a recipe
     */
    @Nullable
    public GTRecipe recurseIngredientTreeFindRecipe(@NotNull List<List<AbstractMapIngredient>> ingredients,
                                                    @NotNull Branch branchMap, @NotNull Predicate<GTRecipe> canHandle,
                                                    int index, int count, BitSet skip) {
        // exhausted all the ingredients, and didn't find anything
        if (count == ingredients.size()) return null;

        // Iterate over current level of nodes.
        for (AbstractMapIngredient obj : ingredients.get(index)) {
            // determine the root nodes
            Map<AbstractMapIngredient, Either<GTRecipe, Branch>> targetMap = determineRootNodes(obj, branchMap);

            Either<GTRecipe, Branch> result = targetMap.get(obj);
            if (result != null) {
                // if there is a recipe (left mapping), return it immediately as found, if it can be handled
                // Otherwise, recurse and go to the next branch.
                GTRecipe r = result.map(potentialRecipe -> canHandle.test(potentialRecipe) ? potentialRecipe : null,
                        potentialBranch -> diveIngredientTreeFindRecipe(ingredients, potentialBranch, canHandle, index,
                                count, skip));
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
```

当 `RecipeDB` 扫描 machine input 时（通过 handler 的 `.getContents()` 方法），它会将这些内容转换为 MapIngredient。
这里的输入是 `List<List<AbstractMapIngredient>>`。之所以是双层 list，是因为一个 `Content` 可以转换为多个 `Ingredient`。例如一本 written book 可以变成 `ItemStackMapIngredient`、`ItemTagMapIngredient`、`PartialNBTItemStackMapIngredient`、`StrictNBTItemStackMapIngredient`，还可能根据 addon 或注册内容变成其他类型。
因此，`.getContents()` 中的每个 content 都会生成一个 `List<AbstractMapIngredient>`。参见 `.fromHolder` 方法的这段摘录：
```java
var compressed = cap.compressIngredients(handler.getContents());
for (var ingredient : compressed) {
    list.add(MapIngredientTypeManager.getFrom(ingredient, cap));
}
```

随后，这个 list 会在上方方法的 for 循环中迭代。对每个 `AbstractMapIngredient`，会在 `determineRootNodes` 中调用 `branchMap.getNodes()`。

然后，我们会在这个 map 上调用 `map.get(obj)`。由于它实现为 `HashMap`，这里就会用到 `AbstractMapIngredient` 的方法。根据 `.hash` 和 `.equals` 方法，它会匹配 Trie 当前层的 ingredient。
调用 get 时，可能返回 `null`（该 ingredient 不在 map 中），也可能返回 `Either<GTRecipe, Branch>`。
如果返回的是 recipe，我们会检查它是否适用于当前 machine（后面会介绍 canHandle），如果适用就返回这个 recipe。
如果返回的是 branch，则递归进入 recipe search 的下一层。

当前 `RecipeDB.RecipeIterator` 的分支含义相同：`Either.left` 是已经烘焙出的运行态 `GTRecipe`，`Either.right` 是下一层 `Branch`。区别在于它不再递归调用旧方法，而是在 `getNext()` 中维护搜索栈；命中 recipe 后先执行 predicate，只有 predicate 通过才返回给 `RecipeLogic`。

## Recipe 匹配
在整个调用栈中，会向下传递一个 canHandle predicate。你可能还记得，它是 `r -> matchRecipe(r).isSuccess()`，用于检查 machine 是否能处理当前 recipe。
经过几层间接调用后，我们会到达 `RecipeHelper.matchRecipe`。
```java
    private static ActionResult matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, boolean tick) {
        if (!holder.hasCapabilityProxies()) return ActionResult.FAIL_NO_CAPABILITIES;

        var result = handleRecipe(holder, recipe, IO.IN, tick ? recipe.tickInputs : recipe.inputs,
                Collections.emptyMap(), tick, true);
        if (!result.isSuccess()) return result;

        result = handleRecipe(holder, recipe, IO.OUT, tick ? recipe.tickOutputs : recipe.outputs,
                Collections.emptyMap(), tick, true);
        return result;
    }
```
可以看到，它会以 `simulate = true` 调用 handleRecipe，检查 normal 和 tick input/output。请注意，在这个阶段 RecipeModifiers 尚未应用。

理解上面 `searchRecipes()` 如何创建 `Iterator<GTRecipe>` 后，我们可以继续看 `handleSearchingRecipes(Iterator<GTRecipe>)`。
这个方法会遍历 iterator，并对每个 recipe 调用 `checkMatchedRecipeAvailable`。

```java
    public boolean checkMatchedRecipeAvailable(GTRecipe match) {
    var modified = machine.fullModifyRecipe(match);
    if (modified != null) {
        var recipeMatch = checkRecipe(modified);
        if (recipeMatch.isSuccess()) {
            setupRecipe(modified);
        }
        if (lastRecipe != null && getStatus() == Status.WORKING) {
            lastOriginRecipe = match;
            lastFailedMatches = null;
            return true;
        }
    }
    return false;
}
```

这里会应用 Recipe Modifiers。如果任何 RecipeModifier 返回 null，这个 recipe 会被忽略，并继续检查 iterator 中的下一个 recipe。
如果不是 null，则会再次验证输入是否可用（通过前面看到的 `RecipeHelper.matchRecipe` 间接完成）。
如果可用，就会调用 `.setupRecipe(...)`。这个 setupRecipe 调用会调用 `machine.beforeWorking()`，并尝试消耗 input item。
如果在这之后 recipe 正在运行，就返回 true，表示已经找到 recipe，recipe search 结束。

I/O 匹配失败、condition 失败和 modifier 拒绝 recipe 时，都应向 `RecipeLogic` 提供可记录的失败原因。当前链路通过 `ActionResult` 传递 I/O 与 condition 的失败原因，并将 modifier 拒绝转换为 failure reason，供机器状态和界面提示使用。
