package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.utils.data.TagCompatibilityFixer;

import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings({ "rawtypes", "unchecked" })
public final class NBTSerializableCodec implements ContextualFieldCodec<INBTSerializable> {

    public static final Class<INBTSerializable> TYPE = INBTSerializable.class;
    public static final NBTSerializableCodec INSTANCE = new NBTSerializableCodec();

    private NBTSerializableCodec() {}

    @Override
    public Tag serializeNBT(INBTSerializable value, Context<INBTSerializable> context) {
        return value.serializeNBT(context.lookup());
    }

    @Override
    public @Nullable INBTSerializable deserializeNBT(Tag tag, Context<INBTSerializable> context) {
        INBTSerializable currentValue = context.currentValue();
        if (currentValue == null) {
            GTCEu.LOGGER.warn(
                    "Sync: Deserialization of INBTSerializable objects requires an existing object, they cannot be instantiated purely from saved data.");
            return null;
        }
        currentValue.deserializeNBT(context.lookup(), TagCompatibilityFixer.stripLDLibPayloadWrapper(tag));
        return currentValue;
    }
}
