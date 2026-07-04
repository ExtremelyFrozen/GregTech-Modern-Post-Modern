---
title: "用法"
---

## 用法

### 将类注册到 sync system

系统的核心是 `ISyncManaged` 接口，它表示一个需要同步到 client 或需要保存的类。
所有需要同步或保存的 block entity 都必须继承抽象类 `ManagedSyncBlockEntity`。

!!! warning
  继承 `ManagedSyncBlockEntity` 的 block entity 必须在 ticker 中***每 tick*** 调用 `ManagedSyncBlockEntity::updateTick`，否则不会保存。

```java
class MySyncObject implements ISyncManaged {
    // Any class that directly implements ISyncManaged must have the following:
     @Getter
     protected final SyncDataHolder syncDataHolder = new SyncDataHolder(this);


    /**
     * Function called when the SyncDataHolder requests a rerender
     */
    void scheduleRenderUpdate();

    /**
     * Function called to notify the server that this object has been updated and must be synced to clients
     */
    void markAsChanged();
}
```

### 选择保存后端

普通 BlockEntity 的保存和 client 同步应使用 `ManagedSyncBlockEntity`。`ManagedSavedData` 只适合真正需要以 `SavedData` 作为后端的全局或跨区块持久数据，不应作为普通 BlockEntity 保存字段的替代方案。

默认 sync save key 是 `${GTCEu.MOD_ID}_sync_data`。当前 mod id 为 `gtpm`，所以默认写入 `CompoundTag` 的 key 是 `gtpm_sync_data`。如果一个 `SavedData` 子类需要独立后端，应由该子类设置专用 key。

### 注册由系统管理的字段
参阅 [Annotations](Annotations.md)。

### 类型兼容性
默认支持以下字段类型：

- 任何实现 `ISyncManaged` 的类
- 任何实现 `INBTSerializable<Tag>` 的类
- 所有 primitive type
- 如果 `T`、`K` 是受支持类型：
   - `T[]`
   - `Set<T>`
   - `List<T>`,
   - `Map<T, K>`
- `String`
- `ItemStack`
- `FluidStack`
- `UUID`
- `BlockPos`
- `CompoundTag`
- `GTRecipe`
- `GTRecipeType`
- `MachineRenderState`
- `Material`
- `Component`

### 为额外类型添加支持

`ValueTransformer<T>` 抽象类定义了 `T` 类型值应如何序列化。

要为额外类型添加支持，请调用 `ValueTransformers.registerTransformer(Class<T> cls, ValueTransformer<T> transformer)` 或 `ValueTransformers.registerTransformerSupplier(Class<T> cls, Supplier<ValueTransformer<T>> func)`。

此外，也可以显式指定某个字段使用特定的 value transformer：
```java
/**
 * Example from HullMachine.java. This example shows serialization of an AE2 class which may or may not be loaded at runtime.
 */

@SaveField(nbtKey = "grid_node")
private final Object gridNodeHost;

private static class GridNodeHostTransformer implements ValueTransformer<Object> {

  @Override
  public Tag serializeNBT(Object value, TransformerContext<Object> context) {
    if (GTCEu.Mods.isAE2Loaded() &&
            (context.currentValue()) instanceof IGridConnectedBlockEntity connectedBlockEntity) {
      var compound = new CompoundTag();
      connectedBlockEntity.getMainNode().saveToNBT(compound);
      return compound;
    }
    return new CompoundTag();
  }

  @Override
  public @Nullable Object deserializeNBT(Tag tag, TransformerContext<Object> context) {
    if (GTCEu.Mods.isAE2Loaded() &&
            context.currentValue() instanceof IGridConnectedBlockEntity connectedBlockEntity &&
            tag instanceof CompoundTag c) {
      connectedBlockEntity.getMainNode().loadFromNBT(c);
    }
    return null;
  }
}

static {
  ClassSyncData.getClassData(HullMachine.class).setCustomTransformerForField("gridNodeHost",
          new GridNodeHostTransformer());
}

```
