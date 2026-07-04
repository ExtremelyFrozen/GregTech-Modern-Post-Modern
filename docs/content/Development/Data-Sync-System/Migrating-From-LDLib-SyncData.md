---
title: "Migrating from LDLib SyncData"
---
# Migrating from LDLib SyncData

### Simple example

This simple example covers the majority of use cases when adding sync/save fields to a standard machine, machine trait or cover.

#### With LDLib:
```java
class CustomMachine extends SimpleTieredMachine {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(CustomMachine.class,
            SimpleTieredMachine.MANAGED_FIELD_HOLDER);

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Getter 
    @Persisted
    @DescSynced
    @RequireRerender
    protected int customIntValue;
    
    @Persisted(key = "customNBTKey")
    protected String customStringValue;

    public void setCustomIntValue(int newValue) {
        this.customIntValue = newValue;
    }
}
```

#### New System:
```java
class CustomMachine extends SimpleTieredMachine {
    @Getter 
    @SaveField
    @SyncToClient
    protected int customIntValue;
    
    @SaveField(nbtKey = "customNBTKey")
    protected String customStringValue;
    
    public void setCustomIntValue(int newValue) {
        this.customIntValue = newValue;
    }
}

```

### General migration guidelines

- Remove all `ManagedFieldHolder` fields.
- Replace `FieldManagedStorage` fields with `SyncDataHolder` fields.
- Replace `IEnhancedManaged` objects with `ISyncManaged`.
- Replace `IAsyncAutoSyncBlockEntity`, `IAutoPersistBlockEntity`, `IAutoSyncBlockEntity` and `IManagedBlockEntity` by extending `ManagedSyncBlockEntity`.

### LDLib2 UI migration rule

UI migration must not replace LDLib SyncData with LDLib2 syncdata or RPC. UI state changes must continue through GTM's sync system and its automatic synchronization path. New LDLib2 UI code must not introduce `@RPCMethod`, `RPCEmitter`, `setOnServerClick`, or manual dirty-marking calls as the business synchronization path.

### Annotations

!!! note
Client sync fields are scanned for value changes automatically. Use sync-managed child state or contextual codecs for mutable holders whose internal state changes without replacing the field value.

- `@DescSynced` -> `@SyncToClient`
- `@RequireRerender` -> `@RerenderOnChanged`
- `@Persisted` -> `@SaveField`
- `@UpdateListener` -> `@ClientFieldChangeListener` on listener method.
- `@DropSaved` - Removed, make machines implement `IDropSaveMachine` instead
- `@ReadOnlyManaged` and `@LazyManaged` See usage docs for instructions on complex sync objects 

### Other changes

 - `saveCustomPersistedData` & `loadCustomPersistedData` methods, and serialization of custom data types - See `ValueTransformer<T>` and `ValueTransformers` classes.
