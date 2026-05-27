package com.gregtechceu.gtceu.data.pattern;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import org.jetbrains.annotations.Nullable;

final class StructurePatternsReloadedEvent extends Event {

    private final @Nullable ResourceLocation id;
    private int refreshedPatterns;

    StructurePatternsReloadedEvent(@Nullable ResourceLocation id) {
        this.id = id;
    }

    @Nullable
    ResourceLocation getId() {
        return id;
    }

    void addRefreshedPatterns(int count) {
        refreshedPatterns += count;
    }

    int getRefreshedPatterns() {
        return refreshedPatterns;
    }
}
