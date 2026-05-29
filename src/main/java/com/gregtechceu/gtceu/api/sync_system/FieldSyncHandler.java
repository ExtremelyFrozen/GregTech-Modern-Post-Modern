package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformers;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import com.mojang.serialization.Codec;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FieldSyncHandler {

    @SuppressWarnings("unchecked")
    public static Tag serializeField(HolderLookup.Provider registries, Object holder, FieldSyncData field,
                                     boolean writeClientFields, boolean fullSync) {
        Object currentValue = field.handle.get(holder);

        if (currentValue == null) {
            var nullCompound = new CompoundTag();
            nullCompound.putBoolean("null", true);
            return nullCompound;
        }

        if (field.contextualCodec == null) {
            field.setContextualCodec(FieldCodecs.getContextual(field.type.getRawType()));
        }
        if (field.contextualCodec != null) {
            try {
                return ((ContextualFieldCodec<Object>) field.contextualCodec).serializeNBT(currentValue,
                        new ContextualFieldCodec.Context<>(holder, field.type, currentValue, field.fieldName,
                                writeClientFields, fullSync, registries));
            } catch (Exception e) {
                GTCEu.LOGGER.error("Sync: Failed to contextual-codec serialize field {}", field.fieldName, e);
                return new CompoundTag();
            }
        }

        if (field.codec == null) {
            field.setCodec(FieldCodecs.get(field.type.getRawType()));
        }
        if (field.codec != null) {
            try {
                return ((Codec<Object>) field.codec)
                        .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), currentValue)
                        .getOrThrow();
            } catch (Exception e) {
                GTCEu.LOGGER.error("Sync: Failed to codec-serialize field {}", field.fieldName, e);
                return new CompoundTag();
            }
        }

        if (field.transformer == null) {
            field.setTransformer(ValueTransformers.get(field.type.getRawType()));
            if (field.transformer == null) {
                GTCEu.LOGGER.error("Sync: Failed to serialize field {} in class {}: Missing value transformer for {}",
                        field.fieldName, holder.getClass().getName(), field.type);
                return new CompoundTag();
            }
        }

        try {
            return ((ValueTransformer<Object>) field.transformer).serializeNBT(currentValue,
                    new ValueTransformer.TransformerContext<>(holder, field.type, currentValue, field.fieldName,
                            writeClientFields, fullSync, registries));

        } catch (Exception e) {
            GTCEu.LOGGER.error("Sync: Failed to serialize field {}", field.fieldName, e);
        }

        return new CompoundTag();
    }

    @SuppressWarnings("unchecked")
    public static void deserializeField(HolderLookup.Provider registries, Object holder, FieldSyncData field,
                                        @Nullable Tag savedValue,
                                        boolean readingClientFields) {
        if (savedValue == null || savedValue instanceof CompoundTag compound && compound.isEmpty()) return;

        if (savedValue instanceof CompoundTag compound && compound.getBoolean("null")) {
            field.handle.set(holder, null);
            return;
        }

        if (field.contextualCodec == null) {
            field.setContextualCodec(FieldCodecs.getContextual(field.type.getRawType()));
        }
        if (field.contextualCodec != null) {
            try {
                Object current = field.handle.get(holder);
                Object result = ((ContextualFieldCodec<Object>) field.contextualCodec).deserializeNBT(savedValue,
                        new ContextualFieldCodec.Context<>(holder, field.type, current, field.fieldName,
                                readingClientFields, false, registries));
                if (copyIntoMutableCurrent(current, result)) {
                    return;
                }
                if (result != current) {
                    field.handle.set(holder, result);
                }
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    GTCEu.LOGGER.error(
                            "Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
                            field.fieldName);
                    return;
                }
                GTCEu.LOGGER.error("Sync: Failed to contextual-codec deserialize field {}", field.fieldName, e);
            }
            return;
        }

        if (field.codec == null) {
            field.setCodec(FieldCodecs.get(field.type.getRawType()));
        }
        if (field.codec != null) {
            try {
                Object result = ((Codec<Object>) field.codec)
                        .parse(registries.createSerializationContext(NbtOps.INSTANCE), savedValue)
                        .getOrThrow();
                Object current = field.handle.get(holder);
                if (copyIntoMutableCurrent(current, result)) {
                    return;
                }
                if (result != current) {
                    field.handle.set(holder, result);
                }
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    GTCEu.LOGGER.error(
                            "Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
                            field.fieldName);
                    return;
                }
                GTCEu.LOGGER.error("Sync: Failed to codec-deserialize field {}", field.fieldName, e);
            }
            return;
        }

        if (field.transformer == null) {
            field.setTransformer(ValueTransformers.get(field.type.getRawType()));
            if (field.transformer == null) {
                GTCEu.LOGGER.error("Sync: Failed to deserialize field {} in class {}: Missing value transformer for {}",
                        field.fieldName, holder.getClass().getName(), field.type);
                return;
            }
        }

        try {
            ValueTransformer<Object> transformer = (ValueTransformer<Object>) field.transformer;
            var current = field.handle.get(holder);

            Object result = transformer.deserializeNBT(savedValue, new ValueTransformer.TransformerContext<>(
                    holder, field.type, current, field.fieldName, readingClientFields, false, registries));

            if (result != current) {
                field.handle.set(holder, result);
            }

        } catch (Exception e) {
            if (e instanceof UnsupportedOperationException) {
                GTCEu.LOGGER.error(
                        "Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
                        field.fieldName);
                return;
            }
            GTCEu.LOGGER.error("Sync: Failed to deserialize field {}", field.fieldName, e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static boolean copyIntoMutableCurrent(@Nullable Object current, @Nullable Object result) {
        if (current instanceof Collection currentCollection && result instanceof Collection resultCollection) {
            currentCollection.clear();
            currentCollection.addAll(resultCollection);
            return true;
        }
        if (current instanceof Map currentMap && result instanceof Map resultMap) {
            currentMap.clear();
            currentMap.putAll(resultMap);
            return true;
        }
        return false;
    }
}
