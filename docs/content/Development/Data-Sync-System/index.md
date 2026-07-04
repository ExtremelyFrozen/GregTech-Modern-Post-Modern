---
title: "Data Sync/Save 系统"
---

# Data Sync/Save 系统

用于保存序列化数据和同步到 client 的逻辑，使用的是一套基于 Java 注解的自定义数据同步系统。

- 有关系统用法，请参阅 [Usage](Usage.md)
- 有关系统提供的注解列表，请参阅 [Annotations](Annotations.md)
- 有关如何从 7.x 及更早版本使用的 LDLib SyncData 系统迁移，请参阅 [Migrating From LDLib SyncData](Migrating-From-LDLib-SyncData.md)

## 持久化后端选择

普通 BlockEntity 应使用 `ManagedSyncBlockEntity`。它负责通过 sync system 管理 BlockEntity 自身的保存和 client 同步数据。

`ManagedSavedData` 只用于真正需要以 `SavedData` 作为后端的全局或跨区块持久数据。不要为了普通 BlockEntity 的保存需求引入 `ManagedSavedData`。

默认 sync save key 为 `${GTCEu.MOD_ID}_sync_data`。当前 mod id 为 `gtpm`，因此默认保存到 `CompoundTag` 中的 key 是 `gtpm_sync_data`。如果需要区分多个 `SavedData` 后端，应由对应的 `SavedData` 子类设置自己的 key。
