---
title: 优化
---


# 优化技巧

GregTech Post Modern 的优化目标是让昂贵逻辑只在真正需要时运行，并让数据变更能精确通知相关系统。开发新 machine、trait 或 capability 时，应优先检查是否可以复用现有的订阅、缓存和 notifiable 容器，而不是每 tick 轮询全部状态。

## Tick 订阅

server side 逻辑应尽量通过 `ITickSubscription` 或 `ConditionalSubscriptionHandler` 管理。只有在机器存在待处理工作时才订阅 tick；当输入、输出空间、相邻方块或启用状态不再满足条件时，应及时取消订阅。

需要周期性执行但不要求每 tick 执行的逻辑，应在订阅回调内部使用机器的 offset timer 降低频率。不要为了等待外部状态变化而保持永久订阅，优先在相关状态变更监听器、邻居更新或容器变更回调中重新评估订阅条件。

## 数据同步和保存

client 同步字段不会自动检测变更。修改 `@SyncToClient` 字段后，应调用 `markClientSyncFieldDirty`，只同步实际发生变化的字段。需要重新渲染时再使用 `@RerenderOnChanged`，避免把渲染刷新作为普通数据同步的默认行为。

普通 BlockEntity 使用 `ManagedSyncBlockEntity` 管理保存和同步。只有真正需要 `SavedData` 作为后端的全局或跨区块持久数据才使用 `ManagedSavedData`。

## Recipe 处理

用于 recipe I/O 的 item、fluid、energy 容器优先使用 notifiable 实现，例如 `NotifiableItemStackHandler`、`NotifiableFluidTank` 和 `NotifiableEnergyContainer`。这些容器会在内部内容变化时通知监听器，避免 recipe 逻辑依赖持续轮询。

recipe 搜索、匹配和执行阶段应通过 `ActionResult` 返回失败原因，让 `RecipeLogic` 能记录并展示具体问题。不要用静默失败替代可诊断的失败结果。

## 全局缓存

全局 mutable 缓存必须区分 remote side 和 server side。若缓存同时可能被两侧访问，应使用 `SideLocal<T>` 或等价的 side 隔离结构，避免 client 数据和 server 数据互相污染。
