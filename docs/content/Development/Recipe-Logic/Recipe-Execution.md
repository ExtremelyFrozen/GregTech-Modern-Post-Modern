---
title: "配方执行"
---

## 配方执行

!!! Note
    最近的迁移已经把 definition recipe 和 runtime recipe 拆开：展示、序列化和索引使用 `GTRecipeDefinition`，机器真正运行时使用 `GTRecipe`。`GTRecipeDefinition` 以 `ContentListMap` 保存 I/O 内容，进入执行阶段后会转换为 `GTRecipe`，再由 `RecipeLogic`、`RecipeHelper` 和 `RecipeRunner` 处理。配方 I/O 会通过 `RecipeHandlerGroup` / `RecipeHandlerList` 选择可用 handler，失败原因通过 `ActionResult` 返回给调用方。

找到配方后，会运行 `RecipeLogic.setupRecipe(recipe)`。
这个方法先调用 `machine.beforeWorking(recipe)`。如果失败，会重置状态并回到配方搜索。
如果没有失败，则继续通过 `handleRecipeIO(recipe, IO.IN)` 真正消耗配方内容。

- 这会调用 `RecipeHelper.handleRecipeIO(machine, recipe, io, this.chanceCaches)`；
- 接着调用 `handleRecipe(holder, recipe, io, io == IO.IN ? recipe.inputs : recipe.outputs, chanceCaches, false, false)`；
- 再创建 `RecipeRunner runner = new RecipeRunner(recipe, io, isTick, holder, chanceCaches, simulated); var result = runner.handle(contents)`。

实际的配方消耗发生在 `runner.handle(contents)` 这一步。
它会先调用 `runner.fillContentMatchList(contents)`，填充两个值：

- `this.searchRecipeContents`：包含全部配方内容，包括每个可能按概率消耗的 ingredient；
- `this.recipeContents`：包含已经完成概率判定后的配方内容。因此，如果某个概率 ingredient 没有通过概率判定，它不会出现在这里。

随后调用 `return this.handleContents()`，用这两个列表检查并/或消耗配方内容。


## 需要理解的概念

在深入流程前，需要先理解几个概念。
`RecipeHandler` 是 I/O 的最低层抽象。例如输入总线里的电路槽、能量仓里的能量缓存等都可以是 `RecipeHandler`。
`RecipeHandlerList` 是多个 `RecipeHandler` 的聚合抽象。例如一个 dual input bus 持有 3 个 `RecipeHandler`：

 - 一个用于电路槽；
 - 一个用于物品槽；
 - 一个用于流体槽。

这些 handler 会被打包进同一个 `RecipeHandlerList`，也就是 `handleContents` 方法实际交互的对象。调用 `RHL.handleRecipe` 时，它会对内部所有 `RecipeHandler` 调用 `.handleRecipe`。

`RecipeHandlerList` 还有两个和这里相关的属性：

 - `boolean isDistinct`：表示该总线是否启用 distinct；
 - `long color`：表示该总线在游戏内染成的颜色。

这会决定一个 `RecipeHandlerList` 被放入哪个 `RecipeHandlerGroup`：

 - 如果 `RecipeHandlerList` 中有某个 `RecipeHandler` 的 `RecipeCapability` 让 `.shouldBypassDistinct()` 返回 true，它会进入 `BYPASS_DISTINCT` 组。这类总线（例如 Energy Hatches）是全局的，不受 distinct、颜色等组合限制，应该参与每一种组合。
 - 如果 `RecipeHandlerList` 设置为 distinct，它会进入 `BUS_DISTINCT` 组。
 - 如果 `RecipeHandlerList` 未染色，它会进入 `UNDYED` 组。
 - 否则，`RecipeHandlerList` 会进入与自身颜色对应的组。

需要注意的是，分组时 `UNDYED` 组也会被加入到其他每个颜色组里。这是因为配方处理逻辑按下面的方式工作：

1. 如果是 `BYPASS_DISTINCT`，它应该参与每一次配方检查。
2. 如果是 `BUS_DISTINCT`，它应该只和自身（以及任意 `BYPASS_DISTINCT`）一起检查。
3. 如果是某个颜色组，它只会和同色总线、`UNDYED` 总线以及 `BYPASS_DISTINCT` 总线一起检查。
4. 如果是 `UNDYED`，它会和其他 `UNDYED` 总线一起检查。

因此，`UNDYED` 对其他染色组来说相当于通配组。

配方处理期间，`RecipeHandlerList` 会在 `RecipeHelper.addToRecipeHandlerMap` 中拆分成不同组。`UNDYED` 通配逻辑也在这里处理，因为它会被加入到其他颜色组中。

## RecipeRunner.handleContents

这是一个很大的函数，下面按步骤拆开说明：

```java title="RecipeRunner.java"
private ActionResult handleContents() {
    if (recipeContents.isEmpty()) return ActionResult.SUCCESS;
    if (!capabilityProxies.containsKey(io)) {
        return ActionResult.FAIL_NO_CAPABILITIES;
    }

    List<RecipeHandlerList> handlers = capabilityProxies.getOrDefault(io, Collections.emptyList());
    // Only sort for non-tick outputs
    if (!isTick && io.support(IO.OUT)) {
        handlers.sort(RecipeHandlerList.COMPARATOR.reversed());
    }

    Map<RecipeHandlerGroup, List<RecipeHandlerList>> handlerGroups = new HashMap<>();
    for (var handler : handlers) {
        addToRecipeHandlerMap(handler.getGroup(), handler, handlerGroups);
    }
```

这段逻辑负责取得机器的 `RecipeHandlerList`，并把它们划分到各自的组里。
也就是说，这一步会按每个 `RecipeHandlerList` 对应的 group 做归类。

```java title="RecipeRunner.java"
    // Specifically check distinct handlers first
    for (RecipeHandlerList handler : handlerGroups.getOrDefault(BUS_DISTINCT, Collections.emptyList())) {
        // Handle the contents of this handler and also all the bypassed handlers
        var res = handler.handleRecipe(io, recipe, searchRecipeContents, true);
        if (!res.isEmpty()) {
            for (RecipeHandlerList bypassHandler : handlerGroups.getOrDefault(BYPASS_DISTINCT,
                    Collections.emptyList())) {
                res = bypassHandler.handleRecipe(io, recipe, res, true);
                if (res.isEmpty()) break;
            }
        }
        if (res.isEmpty()) {
            if (!simulated) {
                // Actually consume the contents of this handler and also all the bypassed handlers
                recipeContents = handler.handleRecipe(io, recipe, recipeContents, false);
                if (!recipeContents.isEmpty()) {
                    for (RecipeHandlerList bypassHandler : handlerGroups.getOrDefault(BYPASS_DISTINCT,
                            Collections.emptyList())) {
                        recipeContents = bypassHandler.handleRecipe(io, recipe, recipeContents, false);
                        if (recipeContents.isEmpty()) break;
                    }
                }
            }
            recipeContents.clear();
            return ActionResult.SUCCESS;
        }
    }
```

这是第一段真正进行检查和消耗的逻辑。
注意 `handleRecipe` 的最后一个参数是 `simulate`。传入 `true` 时不会真正消耗物品，传入 `false` 时才会实际消耗。
流程会先遍历每个 `BUS_DISTINCT` `RecipeHandlerList`，让它检查配方。
如果剩余内容列表还不为空，就继续检查 `BYPASS_DISTINCT` 组中的 `RecipeHandlerList`。
如果剩余内容为空，说明模拟成功。如果 `simulated` 为 false，就再次执行这段代码，这次真正消耗配方内容。
如果剩余内容不为空，就继续尝试下一个 distinct bus。

```java title="RecipeRunner.java"
    // Check the other groups.
    for (Map.Entry<RecipeHandlerGroup, List<RecipeHandlerList>> handlerListEntry : handlerGroups.entrySet()) {
        if (handlerListEntry.getKey().equals(BUS_DISTINCT)) continue;

        // List to keep track of the remaining items for this RecipeHandlerGroup
        Map<RecipeCapability<?>, List<Object>> copiedRecipeContents = searchRecipeContent;
        boolean found = false;

        for (RecipeHandlerList handler : handlerListEntry.getValue()) {
            copiedRecipeContents = handler.handleRecipe(io, recipe, copiedRecipeContents, true);
            if (copiedRecipeContents.isEmpty()) {
                found = true;
                break;
            }
        }
        // If we're already in the bypass_distinct group, don't check it twice.
        if (!handlerListEntry.getKey().equals(BYPASS_DISTINCT)) {
            for (RecipeHandlerList bypassHandler : handlerGroups.getOrDefault(BYPASS_DISTINCT,
                    Collections.emptyList())) {
                copiedRecipeContents = bypassHandler.handleRecipe(io, recipe, copiedRecipeContents, true);
                if (copiedRecipeContents.isEmpty()) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) continue;
        if (simulated) return ActionResult.SUCCESS;
        // Start actually removing items.
        // Keep track of the remaining items for this RecipeHandlerGroup
        // First go through the handlers of the group
        for (RecipeHandlerList handler : handlerListEntry.getValue()) {
            recipeContents = handler.handleRecipe(io, recipe, recipeContents, false);
            if (recipeContents.isEmpty()) {
                return ActionResult.SUCCESS;
            }
        }
        // Then go through the handlers that bypass the distinctness system and empty those
        // If we're already in the bypass_distinct group, don't check it twice.
        if (!handlerListEntry.getKey().equals(BYPASS_DISTINCT)) {
            for (RecipeHandlerList bypassHandler : handlerGroups.getOrDefault(BYPASS_DISTINCT,
                    Collections.emptyList())) {
                recipeContents = bypassHandler.handleRecipe(io, recipe, recipeContents, false);
                if (recipeContents.isEmpty()) {
                    return ActionResult.SUCCESS;
                }
            }
        }
    }

    for (var entry : recipeContents.entrySet()) {
        if (entry.getValue() != null && !entry.getValue().isEmpty()) {
            return ActionResult.fail(null, entry.getKey(), io);
        }
    }

    return ActionResult.FAIL_NO_REASON;
}
```

这和前面的循环逻辑类似，但稍微更复杂一些。
和 `BUS_DISTINCT` 中逐个检查 `RecipeHandlerList` 不同，这里会一次检查每个 group 中的所有总线。
同时还有一个限制：`BYPASS_DISTINCT` 必须和每个 group 一起检查，但不能和自身重复检查，因此这里有额外逻辑处理这一点。
剩余流程基本相同。

这就是 `RecipeHelper.handleRecipeIO` 的处理逻辑。tick ingredient 的逻辑也类似，只是传入的是 tick inputs，并且开头的排序步骤也会参与；其他部分保持一致。

## RecipeLogic 的其余流程

回顾一下，找到配方后，`RecipeLogic.setupRecipe(recipe)` 会执行上面这些逻辑。
如果返回成功，就会设置一组和 `RecipeLogic` 相关的变量，例如：

```java title="RecipeLogic.java"
    var handledIO = handleRecipeIO(recipe, IO.IN);
    if (handledIO.isSuccess()) {
        recipeDirty = false;
        lastRecipe = recipe;
        setStatus(Status.WORKING);
        progress = 0;
        duration = recipe.duration;
        isActive = true;
    }
```

这个 tick 中只会发生这些事情（见 [RecipeLogic.serverTick](./Recipe-Logic.md)）。
下一个 tick 会调用 `recipeLogic.handleRecipeWorking()`。它会先执行 `RecipeHelper.checkConditions(lastRecipe, this, true)`；只有 `RecipeCondition.perTick()` 返回 true 的 condition 会在这里参与检查。per-tick condition 通过后，才会继续调用下面的方法：

```java title="RecipeLogic.java"
public ActionResult handleTickRecipe(GTRecipe recipe) {
    if (!recipe.hasTick()) return ActionResult.SUCCESS;

    var result = RecipeHelper.matchTickRecipe(machine, recipe);
    if (!result.isSuccess()) return result;

    result = handleTickRecipeIO(recipe, IO.IN);
    if (!result.isSuccess()) return result;

    result = handleTickRecipeIO(recipe, IO.OUT);
    return result;
}
```

其中 `handleTickRecipeIO` 会使用 `recipe.tickInputs` / `recipe.tickOutputs` 并以 `tick=true` 调用 `handleRecipe`。

随后，如果 `recipeLogic.progress >= recipeLogic.duration`，会调用 `onRecipeFinish()`。
这个方法会调用 `machine.afterWorking()` 和 `handleRecipeIO(lastRecipe, IO.OUT)`，把配方输出放入输出总线。

如果 I/O 匹配或执行失败，`RecipeRunner` 会保留剩余内容对应的 `RecipeCapability` 和 IO 方向，并通过 `ActionResult` 返回。`RecipeHelper` 会把这些信息转换为具体的失败原因，例如输入不足、输出空间不足、缺少 capability 或 condition 不满足，供 `RecipeLogic` 进入等待状态并展示原因。
