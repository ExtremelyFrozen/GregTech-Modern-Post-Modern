package com.gregtechceu.gtceu.api.misc.virtualregistry;

import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@Getter
@Accessors(chain = true)
public abstract class VirtualEntry {

    public static final String DEFAULT_COLOR = "FFFFFFFF";

    @Setter
    @NotNull
    private String description = "";
    private int color = 0xFFFFFFFF;
    private String colorStr = DEFAULT_COLOR;

    public abstract EntryTypes<? extends VirtualEntry> getType();

    public void setColor(String color) {
        this.color = parseColor(color);
        this.colorStr = color.toUpperCase(Locale.ROOT);
    }

    public void setColor(int color) {
        this.color = color;
        this.colorStr = Integer.toHexString(color).toUpperCase(Locale.ROOT);
    }

    public static int parseColor(String colorString) {
        if (colorString.length() < 8) {
            throw new IllegalArgumentException("Invalid color string: " + colorString);
        }

        int red = Integer.parseInt(colorString.substring(0, 2), 16);
        int green = Integer.parseInt(colorString.substring(2, 4), 16);
        int blue = Integer.parseInt(colorString.substring(4, 6), 16);
        int alpha = Integer.parseInt(colorString.substring(6, 8), 16);

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualEntry other)) return false;
        return this.getType() == other.getType() && this.color == other.color &&
                this.description.equals(other.description);
    }

    public DataComponentMap exportComponents(HolderLookup.@NotNull Provider registries) {
        return putBaseComponent(DataComponentMap.builder()).build();
    }

    public JsonElement serializeJson(HolderLookup.@NotNull Provider registries) {
        return encodeJson(registries, DataComponentMap.CODEC, exportComponents(registries));
    }

    public void importComponents(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        VirtualEntryData.Base base = components.getOrDefault(GTDataComponents.VIRTUAL_ENTRY_BASE.get(),
                VirtualEntryData.Base.EMPTY);
        setColor(base.color());
        this.description = base.description();
    }

    public void deserializeJson(HolderLookup.@NotNull Provider registries, JsonElement data) {
        importComponents(registries, decodeJson(registries, DataComponentMap.CODEC, data));
    }

    protected DataComponentMap.Builder putBaseComponent(DataComponentMap.Builder builder) {
        VirtualEntryData.Base base = new VirtualEntryData.Base(this.colorStr, this.description);
        if (!base.isEmpty()) {
            builder.set(GTDataComponents.VIRTUAL_ENTRY_BASE.get(), base);
        }
        return builder;
    }

    protected static <T> JsonElement encodeJson(HolderLookup.Provider registries, Codec<T> codec, T value) {
        return codec.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
    }

    protected static <T> T decodeJson(HolderLookup.Provider registries, Codec<T> codec, JsonElement value) {
        return codec.parse(registries.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
    }

    public boolean canRemove() {
        return this.description.isEmpty();
    }
}
