package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.sync_system.FieldCodecs;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import org.jetbrains.annotations.Nullable;

public final class AE2SyncCodecs {

    private AE2SyncCodecs() {}

    public static void register() {
        FieldCodecs.registerContextual(KeyStorage.class, KeyStorageCodec.INSTANCE);
        FieldCodecs.registerContextual(GridNodeHolder.class, GridNodeHolderCodec.INSTANCE);
        FieldCodecs.registerContextual(ExportOnlyAEItemSlot.class, ExportOnlyAESlotCodec.ITEM);
        FieldCodecs.registerContextual(ExportOnlyAEFluidSlot.class, ExportOnlyAESlotCodec.FLUID);
        FieldCodecs.registerContextual(MEPatternBufferPartMachine.InternalSlot.class, InternalSlotCodec.INSTANCE);
    }

    private static final class KeyStorageCodec implements ContextualFieldCodec<KeyStorage> {

        private static final KeyStorageCodec INSTANCE = new KeyStorageCodec();
        private static final String KEY = "key";
        private static final String AMOUNT = "amount";

        @Override
        public JsonElement serializeField(KeyStorage value, Context<KeyStorage> context) {
            JsonArray json = new JsonArray();
            for (var entry : value.storage.object2LongEntrySet()) {
                JsonObject element = new JsonObject();
                element.add(KEY, AEKey.CODEC
                        .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), entry.getKey())
                        .getOrThrow());
                element.addProperty(AMOUNT, entry.getLongValue());
                json.add(element);
            }
            return json;
        }

        @Override
        public KeyStorage deserializeField(JsonElement value, Context<KeyStorage> context) {
            KeyStorage storage = requireCurrent(context);
            if (!value.isJsonArray()) {
                throw new IllegalArgumentException("Sync: AE2 key storage field " + context.fieldName() +
                        " must be encoded as an array");
            }

            storage.storage.clear();
            for (JsonElement element : value.getAsJsonArray()) {
                JsonObject json = element.getAsJsonObject();
                storage.storage.put(AEKey.CODEC
                        .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json.get(KEY))
                        .getOrThrow(), json.get(AMOUNT).getAsLong());
            }
            storage.onChanged();
            return storage;
        }
    }

    private static final class GridNodeHolderCodec implements ContextualFieldCodec<GridNodeHolder> {

        private static final GridNodeHolderCodec INSTANCE = new GridNodeHolderCodec();

        @Override
        public JsonElement serializeField(GridNodeHolder value, Context<GridNodeHolder> context) {
            return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, value.getMainNode().serializeNBT(context.lookup()));
        }

        @Override
        public GridNodeHolder deserializeField(JsonElement value, Context<GridNodeHolder> context) {
            GridNodeHolder holder = requireCurrent(context);
            holder.getMainNode().deserializeNBT(context.lookup(), (CompoundTag) JsonOps.INSTANCE
                    .convertTo(NbtOps.INSTANCE, value));
            return holder;
        }
    }

    private static final class ExportOnlyAESlotCodec<T extends ExportOnlyAESlot> implements ContextualFieldCodec<T> {

        private static final ExportOnlyAESlotCodec<ExportOnlyAEItemSlot> ITEM = new ExportOnlyAESlotCodec<>();
        private static final ExportOnlyAESlotCodec<ExportOnlyAEFluidSlot> FLUID = new ExportOnlyAESlotCodec<>();

        private static final String CONFIG = "config";
        private static final String STOCK = "stock";

        @Override
        public JsonElement serializeField(T value, Context<T> context) {
            JsonObject json = new JsonObject();
            json.add(CONFIG, encodeStack(context.lookup(), value.getConfig()));
            json.add(STOCK, encodeStack(context.lookup(), value.getStock()));
            return json;
        }

        @Override
        public T deserializeField(JsonElement value, Context<T> context) {
            T slot = requireCurrent(context);
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Sync: AE2 slot field " + context.fieldName() +
                        " must be encoded as an object");
            }

            JsonObject json = value.getAsJsonObject();
            slot.setConfig(decodeStack(context.lookup(), json.get(CONFIG)));
            slot.setStock(decodeStack(context.lookup(), json.get(STOCK)));
            return slot;
        }
    }

    private static final class InternalSlotCodec
                                                 implements
                                                 ContextualFieldCodec<MEPatternBufferPartMachine.InternalSlot> {

        private static final InternalSlotCodec INSTANCE = new InternalSlotCodec();
        private static final String ITEMS = "items";
        private static final String FLUIDS = "fluids";
        private static final String STACK = "stack";
        private static final String AMOUNT = "amount";

        @Override
        public JsonElement serializeField(MEPatternBufferPartMachine.InternalSlot value,
                                          Context<MEPatternBufferPartMachine.InternalSlot> context) {
            JsonObject json = new JsonObject();
            JsonArray items = new JsonArray();
            for (var entry : value.getItemInventoryForSerialization().object2LongEntrySet()) {
                JsonObject element = new JsonObject();
                element.add(STACK, ItemStack.OPTIONAL_CODEC
                        .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), entry.getKey())
                        .getOrThrow());
                element.addProperty(AMOUNT, entry.getLongValue());
                items.add(element);
            }
            json.add(ITEMS, items);

            JsonArray fluids = new JsonArray();
            for (var entry : value.getFluidInventoryForSerialization().object2LongEntrySet()) {
                JsonObject element = new JsonObject();
                element.add(STACK, FluidStack.OPTIONAL_CODEC
                        .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), entry.getKey())
                        .getOrThrow());
                element.addProperty(AMOUNT, entry.getLongValue());
                fluids.add(element);
            }
            json.add(FLUIDS, fluids);
            return json;
        }

        @Override
        public MEPatternBufferPartMachine.InternalSlot deserializeField(
                                                                        JsonElement value,
                                                                        Context<MEPatternBufferPartMachine.InternalSlot> context) {
            MEPatternBufferPartMachine.InternalSlot slot = requireCurrent(context);
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Sync: pattern buffer internal slot field " + context.fieldName() +
                        " must be encoded as an object");
            }

            JsonObject json = value.getAsJsonObject();
            Object2LongOpenCustomHashMap<ItemStack> itemInventory = slot.getItemInventoryForSerialization();
            itemInventory.clear();
            for (JsonElement element : json.getAsJsonArray(ITEMS)) {
                JsonObject stackJson = element.getAsJsonObject();
                var stack = ItemStack.OPTIONAL_CODEC
                        .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), stackJson.get(STACK))
                        .getOrThrow();
                if (!stack.isEmpty()) {
                    itemInventory.put(stack, stackJson.get(AMOUNT).getAsLong());
                }
            }

            Object2LongOpenCustomHashMap<FluidStack> fluidInventory = slot.getFluidInventoryForSerialization();
            fluidInventory.clear();
            for (JsonElement element : json.getAsJsonArray(FLUIDS)) {
                JsonObject stackJson = element.getAsJsonObject();
                var stack = FluidStack.OPTIONAL_CODEC
                        .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), stackJson.get(STACK))
                        .getOrThrow();
                if (!stack.isEmpty()) {
                    fluidInventory.put(stack, stackJson.get(AMOUNT).getAsLong());
                }
            }
            slot.onContentsChanged();
            return slot;
        }
    }

    private static JsonElement encodeStack(HolderLookup.Provider lookup, @Nullable GenericStack stack) {
        if (stack == null) {
            return JsonNull.INSTANCE;
        }
        return GenericStack.CODEC
                .encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), stack)
                .getOrThrow();
    }

    private static @Nullable GenericStack decodeStack(HolderLookup.Provider lookup, @Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return GenericStack.CODEC
                .parse(lookup.createSerializationContext(JsonOps.INSTANCE), value)
                .getOrThrow();
    }

    private static <T> T requireCurrent(ContextualFieldCodec.Context<T> context) {
        T current = context.currentValue();
        if (current == null) {
            String message = "Sync: field " + context.fieldName() + " requires an existing AE2 object";
            GTCEu.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        return current;
    }
}
