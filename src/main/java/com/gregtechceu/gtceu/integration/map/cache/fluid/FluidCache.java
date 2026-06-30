package com.gregtechceu.gtceu.integration.map.cache.fluid;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.FluidProspectionCache;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.FluidRenderLayer;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.List;

public class FluidCache {

    private final Table<ResourceKey<Level>, ChunkPos, ProspectorMode.FluidInfo> fluidCache = HashBasedTable.create();

    public void addFluid(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.FluidInfo fluid) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (!fluidCache.contains(dim, pos)) {
            fluidCache.put(dim, pos, fluid);
            GroupingMapRenderer.getInstance().addMarker(FluidRenderLayer.getName(fluid).getString(),
                    FluidRenderLayer.getId(fluid, pos), dim, pos, fluid);
        }
    }

    public void readComponents(DataComponentMap components) {
        FluidProspectionCache cache = components.getOrDefault(GTDataComponents.FLUID_PROSPECTION_CACHE.get(),
                FluidProspectionCache.EMPTY);
        for (FluidProspectionCache.Entry entry : cache.entries()) {
            fluidCache.put(entry.dimension(), entry.pos(), entry.fluid());

            GroupingMapRenderer.getInstance().addMarker(FluidRenderLayer.getName(entry.fluid()).getString(),
                    FluidRenderLayer.getId(entry.fluid(), entry.pos()), entry.dimension(), entry.pos(), entry.fluid());
        }
    }

    public DataComponentMap saveComponents() {
        List<FluidProspectionCache.Entry> entries = new ArrayList<>();
        for (var dimensions : fluidCache.rowMap().entrySet()) {
            for (var entry : dimensions.getValue().entrySet()) {
                entries.add(new FluidProspectionCache.Entry(dimensions.getKey(), entry.getKey(), entry.getValue()));
            }
        }
        if (entries.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(GTDataComponents.FLUID_PROSPECTION_CACHE.get(), new FluidProspectionCache(entries))
                .build();
    }

    public void clear() {
        fluidCache.clear();
    }
}
