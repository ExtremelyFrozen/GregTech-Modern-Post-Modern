---
title: "Annotations"
---

# Annotations
The following annotations define the sync/save behaviour for an `ISyncManaged` object.

### `@SaveField`

The `@SaveField` annotation defines a field that should be saved to the server. `nbtKey` is optional, the key will default to the field name.
```java
@SaveField(nbtKey="nbtKeyToSaveTo")
public int mySaveInt = 10;
```

### `@SyncToClient`

The `@SyncToClient` annotation defines a field with a value that should be synced to clients.

!!! warning 
    Client sync fields are scanned for value changes automatically. Prefer assigning the field directly and let the sync system collect changes during the managed tick.
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
    }
    if (mySyncRerenderLong != newLongValue) {
        mySyncRerenderLong = newLongValue;
    }
}
```

### `@ClientFieldChangeListener` and `@RerenderOnChanged`

The `@ClientFieldChangeListener` annotation defines a method to be called on the client when a client sync field has changed value;

Annotating a `@SyncToClient` field with `@RerenderOnChanged` will cause clients to rerender the block entity when this field changes.

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
