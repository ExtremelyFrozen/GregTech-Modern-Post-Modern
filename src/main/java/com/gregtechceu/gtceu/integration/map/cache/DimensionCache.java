package com.gregtechceu.gtceu.integration.map.cache;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.OreProspectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.ChunkPos;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DimensionCache {

    @Getter
    private final ConcurrentMap<GridPos, GridCache> cache = new ConcurrentHashMap<>();

    public boolean dirty;

    public boolean addVein(int gridX, int gridZ, GeneratedVeinMetadata vein) {
        GridPos key = new GridPos(gridX, gridZ);
        if (!cache.containsKey(key)) {
            cache.put(key, new GridCache());
        }
        boolean added = cache.get(key).addVein(vein);
        dirty = added || dirty;
        return added;
    }

    public DataComponentMap saveComponents(HolderLookup.Provider registries) {
        List<OreProspectionCache.Entry> entries = new ArrayList<>();
        for (var gridEntry : cache.entrySet()) {
            entries.add(new OreProspectionCache.Entry(gridEntry.getKey().x, gridEntry.getKey().z,
                    gridEntry.getValue().getVeins()));
        }
        if (entries.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(GTDataComponents.ORE_PROSPECTION_CACHE.get(), new OreProspectionCache(entries))
                .build();
    }

    public void readComponents(DataComponentMap components, HolderLookup.Provider provider) {
        OreProspectionCache oreCache = components.getOrDefault(GTDataComponents.ORE_PROSPECTION_CACHE.get(),
                OreProspectionCache.EMPTY);
        for (OreProspectionCache.Entry entry : oreCache.entries()) {
            GridPos key = new GridPos(entry.gridX(), entry.gridZ());
            if (!cache.containsKey(key)) {
                cache.put(key, new GridCache());
            }
            cache.get(key).addVeins(entry.veins());
        }
    }

    public List<GeneratedVeinMetadata> getNearbyVeins(BlockPos pos, int blockRadius) {
        return getVeinsInBounds(pos.offset(-blockRadius, 0, -blockRadius), pos.offset(blockRadius, 0, blockRadius));
    }

    public List<GeneratedVeinMetadata> getVeinsInBounds(BlockPos topLeftBlock, BlockPos bottomRightBlock) {
        GridPos topLeft = new GridPos(topLeftBlock);
        GridPos bottomRight = new GridPos(bottomRightBlock);
        List<GeneratedVeinMetadata> found = new ArrayList<>();
        for (int i = topLeft.x; i <= bottomRight.x; i++) {
            for (int j = topLeft.z; j <= bottomRight.z; j++) {
                GridPos curPos = new GridPos(i, j);
                if (cache.containsKey(curPos)) {
                    found.addAll(cache.get(curPos)
                            .getVeinsMatching(vein -> vein.center().getX() >= topLeftBlock.getX() &&
                                    vein.center().getX() <= bottomRightBlock.getX() &&
                                    vein.center().getZ() >= topLeftBlock.getZ() &&
                                    vein.center().getZ() <= bottomRightBlock.getZ()));
                }
            }
        }
        return found;
    }

    public List<GeneratedVeinMetadata> getVeinsInChunk(ChunkPos pos) {
        GridPos gPos = new GridPos(pos);
        if (cache.containsKey(gPos)) {
            return cache.get(gPos).getVeinsMatching(vein -> pos.equals(vein.originChunk()));
        }
        return new ArrayList<>();
    }

    public void removeAllInChunk(ChunkPos pos) {
        GridPos gPos = new GridPos(pos);
        if (cache.containsKey(gPos)) {
            cache.get(gPos).removeVeinsMatching(vein -> pos.equals(vein.originChunk()));
        }
    }
}
