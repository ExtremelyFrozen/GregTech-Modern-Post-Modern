---
title: 为 Cover 和 Machine 添加 Copy & Paste 支持
---

Machine Memory Card 物品允许将 Machine 设置和 Cover 复制到其他 Machine。

要添加需要额外复制的字段，请在 Machine 或 Cover 上重写以下方法：
```java
/// Copies the current machine/cover config to a CompoundTag.
public CompoundTag copyConfig(CompoundTag tag);
/// Loads a machine/cover config from a CompoundTag.
public void pasteConfig(ServerPlayer player, CompoundTag tag);
/// Returns a list of items (covers, filters, etc) which are needed to copy and paste this machine.
public List<ItemStack> getItemsRequiredToPaste();
```
