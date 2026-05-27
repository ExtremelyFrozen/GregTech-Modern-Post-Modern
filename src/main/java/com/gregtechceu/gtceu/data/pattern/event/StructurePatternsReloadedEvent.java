package com.gregtechceu.gtceu.data.pattern.event;

import com.gregtechceu.gtceu.data.pattern.StructureDefinitionType;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after structure definition caches have been reloaded.
 * <p>
 * This event is fired on the {@link NeoForge#EVENT_BUS}.
 */
public class StructurePatternsReloadedEvent extends Event {

    private final @Nullable StructureDefinitionType type;
    private final @Nullable ResourceLocation id;
    @Getter
    private int refreshedPatterns;

    @ApiStatus.Internal
    public StructurePatternsReloadedEvent(@Nullable StructureDefinitionType type, @Nullable ResourceLocation id) {
        this.type = type;
        this.id = id;
    }

    public static StructurePatternsReloadedEvent all() {
        return new StructurePatternsReloadedEvent(null, null);
    }

    public static StructurePatternsReloadedEvent type(StructureDefinitionType type) {
        return new StructurePatternsReloadedEvent(type, null);
    }

    public static StructurePatternsReloadedEvent entry(StructureDefinitionType type, ResourceLocation id) {
        return new StructurePatternsReloadedEvent(type, id);
    }

    public static StructurePatternsReloadedEvent definition(ResourceLocation id) {
        return new StructurePatternsReloadedEvent(null, id);
    }

    public @Nullable StructureDefinitionType getType() {
        return type;
    }

    public @Nullable ResourceLocation getId() {
        return id;
    }

    public boolean affects(ResourceLocation id) {
        return this.id == null || this.id.equals(id);
    }

    public void addRefreshedPatterns(int count) {
        refreshedPatterns += count;
    }
}
