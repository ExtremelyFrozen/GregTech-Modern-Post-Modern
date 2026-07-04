---
title: "注解"
---

# 注解
以下注解定义了 `ISyncManaged` 对象的同步和保存行为。

### `@SaveField`

`@SaveField` 注解定义了应保存到 server 的字段。`nbtKey` 是可选项，默认会使用字段名作为 key。
```java
@SaveField(nbtKey="nbtKeyToSaveTo")
public int mySaveInt = 10;
```

### `@SyncToClient`

`@SyncToClient` 注解定义了值应同步到 client 的字段。

!!! warning
    client 同步字段**不会**自动检测变更。修改 client 同步字段时，请调用 `ISyncManaged.getSyncDataHolder().markClientSyncFieldDirty(FIELD_NAME)`。
```java
@SaveField(nbtKey="nbtKeyToSaveTo")
@SyncToClient
public int mySaveAndSyncInt = 10;

@SyncToClient
@RerenderOnChanged
public long mySyncRerenderLong = 10000L;

public void serverTick() {
    int newIntValue = getNewIntValue();
    long newLongValue = getNewLongValue();
    if (mySaveAndSyncInt != newIntValue) {
        mySaveAndSyncInt = newIntValue;
        getSyncDataHolder().markClientSyncFieldDirty("mySaveAndSyncInt");
    }
    if (mySyncRerenderLong != newLongValue) {
        mySyncRerenderLong = newLongValue;
        getSyncDataHolder().markClientSyncFieldDirty("mySyncRerenderLong");
    }
}
```

### `@ClientFieldChangeListener` and `@RerenderOnChanged`

`@ClientFieldChangeListener` 注解定义了当 client 同步字段的值发生变化时，应在 client 调用的方法。

在 `@SyncToClient` 字段上添加 `@RerenderOnChanged` 后，当该字段变化时，client 会重新渲染该 block entity。

```java
@SyncToClient
@SaveField
@RerenderOnChanged
public boolean isWorkingEnabled = true;

@ClientFieldChangeListener(fieldName="isWorkingEnabled")
public void isWorkingChanged() {
    setRenderState(getRenderState().setValue(GTMachineModelProperties.IS_WORKING_ENABLED, isWorkingEnabled));
}
```
