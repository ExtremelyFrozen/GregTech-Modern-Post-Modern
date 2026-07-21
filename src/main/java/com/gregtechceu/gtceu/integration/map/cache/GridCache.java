package com.gregtechceu.gtceu.integration.map.cache;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GridCache {

    @Getter
    private final List<GeneratedVeinMetadata> veins = new ArrayList<>();

    public boolean addVein(GeneratedVeinMetadata vein) {
        if (veins.contains(vein)) return false;
        veins.add(vein);
        return true;
    }

    public void addVeins(Collection<GeneratedVeinMetadata> veins) {
        for (GeneratedVeinMetadata vein : veins) {
            addVein(vein);
        }
    }

    public List<GeneratedVeinMetadata> getVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        return veins.stream().filter(predicate).collect(Collectors.toList());
    }

    public void removeVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        for (int i = 0; i < veins.size(); i++) {
            if (predicate.test(veins.get(i))) {
                veins.remove(i);
                i--;
            }
        }
    }
}
