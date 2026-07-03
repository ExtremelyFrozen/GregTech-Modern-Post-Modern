---
title: "配方逻辑"
---

# 配方逻辑

任何 `WorkableMachine` 都会将 `RecipeLogic` 作为 trait。这类 machine 拥有一个 `TickableSubscription`，用于调用 `recipeLogic.serverTick`。

## 定义态与运行态

recipe 系统现在明确区分定义态和运行态。`GTRecipeDefinition` 是定义态，用于表示数据加载、展示和查询时的稳定 recipe 定义；`GTRecipe` 是运行态，用于实际匹配、执行、消耗、输出和 modifier 处理。

`GTRecipeDefinition.toRuntime()` 必须对内容、chance logic、condition、DataComponents 等数据做深复制，避免运行态执行过程污染定义态数据。展示层应读取 `GTRecipeDefinition`，不要读取会被执行过程修改的 runtime `GTRecipe`。

`ContentListMap` 统一承载 item、fluid、EU、CWU 以及其他 `RecipeCapability` 内容。handler 分组由 `RecipeHandlerGroup` 和 `RecipeHandlerList` 承接，recipe I/O 不再只围绕 item、fluid 和 EU 的固定结构展开。

condition 检查分为启动路径和 per-tick 路径。`RecipeCondition.perTick` 为 true 的 condition 会在 `RecipeHelper.checkConditions(..., true)` 中参与每 tick 检查，用于运行中的动态条件。

recipe I/O、condition 和 modifier 失败都应通过 `ActionResult` 或 failure reason 向上返回具体原因。`RecipeLogic` 会收集这些原因，用于等待状态和失败提示；不要用静默失败掩盖可诊断的问题。

## `serverTick` 流程

下面是一个略微简化后的 `recipeLogic.serverTick`：
```java title="RecipeLogic.java"
public void serverTick() {
    if (!isSuspend()) {
        if (!isIdle() && lastRecipe != null) {
            if (progress < duration) {
                handleRecipeWorking();
            }
            if (progress >= duration) {
                onRecipeFinish();
            }
        } else {
            findAndHandleRecipe();
        } // Code for re-doing previous recipe
    }
    // Logic for unsubscribing if needed
}
```

我们会在 [Recipe Searching](./Recipe-Searching.md) 和 [Recipe Execution](./Recipe-Execution.md) 中拆解这个方法。
