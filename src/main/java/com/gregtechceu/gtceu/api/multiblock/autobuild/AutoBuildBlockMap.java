package com.gregtechceu.gtceu.api.multiblock.autobuild;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.block.IFilterType;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.IBatteryData;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.world.level.block.Block;

import com.tterrag.registrate.util.entry.RegistryEntry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Maps selectable multiblock block categories to tier-ordered blocks.
 */
public final class AutoBuildBlockMap {

    public static final String HEATING_COILS = "heating_coils";
    public static final String CLEANROOM_FILTERS = "cleanroom_filters";
    public static final String POWER_SUBSTATION_BATTERIES = "power_substation_batteries";
    public static final String LAMPS = "lamps";
    public static final String BORDERLESS_LAMPS = "borderless_lamps";
    public static final String MUFFLER_HATCHES = "muffler_hatches";
    public static final String ROTOR_HOLDER = "rotor_holder";
    public static final String BATTERIES = "batteries";
    public static final String ROTOR_HOLDERS = "rotor_holders";

    private static final Object LOCK = new Object();
    private static final Object2ObjectOpenHashMap<String, Block[]> CATEGORIES = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<String, String> CANONICAL_CATEGORIES = new Object2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Block, String> BLOCK_CATEGORIES = new Reference2ObjectOpenHashMap<>();
    private static boolean built;

    private AutoBuildBlockMap() {}

    public static Map<String, Block[]> categories() {
        ensureBuilt();
        Map<String, Block[]> categories = new HashMap<>();
        for (var entry : CATEGORIES.entrySet()) {
            categories.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return Map.copyOf(categories);
    }

    @Nullable
    public static Block[] categoryBlocks(String category) {
        ensureBuilt();
        Block[] blocks = CATEGORIES.get(canonicalCategory(category));
        return blocks == null ? null : Arrays.copyOf(blocks, blocks.length);
    }

    @Nullable
    public static String category(Block block) {
        ensureBuilt();
        return BLOCK_CATEGORIES.get(block);
    }

    public static String canonicalCategory(String category) {
        ensureBuilt();
        return CANONICAL_CATEGORIES.getOrDefault(category, category);
    }

    public static void registerCategory(String category, Block[] blocks) {
        synchronized (LOCK) {
            Block[] registeredBlocks = Arrays.stream(blocks)
                    .filter(block -> block != null)
                    .toArray(Block[]::new);
            CATEGORIES.put(category, registeredBlocks);
            CANONICAL_CATEGORIES.put(category, category);
            for (Block block : registeredBlocks) {
                BLOCK_CATEGORIES.put(block, category);
            }
        }
    }

    public static void registerAlias(String alias, String category) {
        synchronized (LOCK) {
            Block[] blocks = CATEGORIES.get(category);
            if (blocks != null) {
                CATEGORIES.put(alias, blocks);
                CANONICAL_CATEGORIES.put(alias, category);
            }
        }
    }

    private static void ensureBuilt() {
        if (built) return;
        synchronized (LOCK) {
            if (built) return;
            registerCategory(HEATING_COILS, sortedBlocks(GTCEuAPI.HEATING_COILS.entrySet(),
                    Comparator.comparingInt(ICoilType::getTier)));
            registerCategory(CLEANROOM_FILTERS, sortedBlocks(GTCEuAPI.CLEANROOM_FILTERS.entrySet(),
                    Comparator.comparing(IFilterType::getSerializedName)));
            registerCategory(POWER_SUBSTATION_BATTERIES, sortedBlocks(GTCEuAPI.PSS_BATTERIES.entrySet(),
                    Comparator.comparingInt(IBatteryData::getTier)));
            registerAlias(BATTERIES, POWER_SUBSTATION_BATTERIES);
            registerCategory(LAMPS, GTBlocks.LAMPS.values().stream().map(RegistryEntry::get).toArray(Block[]::new));
            registerCategory(BORDERLESS_LAMPS,
                    GTBlocks.BORDERLESS_LAMPS.values().stream().map(RegistryEntry::get).toArray(Block[]::new));
            registerCategory(MUFFLER_HATCHES, sortedMachineBlocks(GTMachines.MUFFLER_HATCH));
            registerCategory(ROTOR_HOLDER, sortedMachineBlocks(GTMachines.ROTOR_HOLDER));
            registerAlias(ROTOR_HOLDERS, ROTOR_HOLDER);
            built = true;
        }
    }

    private static <K, V extends Supplier<? extends Block>> Block[] sortedBlocks(
                                                                                 Iterable<Map.Entry<K, V>> entries,
                                                                                 Comparator<? super K> comparator) {
        return stream(entries).sorted((first, second) -> comparator.compare(first.getKey(), second.getKey()))
                .map(entry -> entry.getValue().get())
                .toArray(Block[]::new);
    }

    private static Block[] sortedMachineBlocks(MachineDefinition[] definitions) {
        return Arrays.stream(definitions)
                .filter(definition -> definition != null)
                .sorted(Comparator.comparingInt(MachineDefinition::getTier))
                .map(MachineDefinition::getBlock)
                .toArray(Block[]::new);
    }

    private static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
