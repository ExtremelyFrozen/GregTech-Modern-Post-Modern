package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.multiblock.IBatteryData;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.common.block.BatteryBlock;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.common.machine.multiblock.electric.PowerSubstationMachine.PMC_BATTERY_HEADER;

/**
 * Serialized power substation battery predicate for JSON multiblock patterns.
 *
 * <p>
 * This predicate mirrors {@code Predicates.powerSubstationBatteries()} by matching registered PSS battery blocks and
 * recording matched non-empty battery data in the pattern match context.
 */
public enum PowerSubstationBatteryPredicate implements StructurePredicate {

    INSTANCE;

    /**
     * JSON codec for {@code gtpm:power_substation_batteries}; no extra fields are required.
     */
    public static final MapCodec<PowerSubstationBatteryPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.POWER_SUBSTATION_BATTERIES;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        BlockState state = multiblockState.getBlockState();
        for (Map.Entry<IBatteryData, Supplier<BatteryBlock>> entry : GTCEuAPI.PSS_BATTERIES.entrySet()) {
            if (state.is(entry.getValue().get())) {
                IBatteryData battery = entry.getKey();
                if (mutateCount && battery.getTier() != -1 && battery.getCapacity() > 0) {
                    String key = PMC_BATTERY_HEADER + battery.getBatteryName();
                    PowerSubstationMachine.BatteryMatchWrapper wrapper = multiblockState.getMatchContext().get(key);
                    if (wrapper == null) {
                        wrapper = new PowerSubstationMachine.BatteryMatchWrapper(battery);
                    }
                    multiblockState.getMatchContext().set(key, wrapper.increment());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return sortedBatteries().stream()
                .map(entry -> new MultiblockBlockInfo(entry.getValue().get().defaultBlockState(), null))
                .toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return sortedBatteries().stream()
                .map(entry -> (Block) entry.getValue().get())
                .toList();
    }

    private List<Map.Entry<IBatteryData, Supplier<BatteryBlock>>> sortedBatteries() {
        return GTCEuAPI.PSS_BATTERIES.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().getTier()))
                .toList();
    }
}
