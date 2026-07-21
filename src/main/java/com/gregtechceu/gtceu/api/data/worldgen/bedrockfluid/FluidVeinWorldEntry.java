package com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid;

import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.Holder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

public class FluidVeinWorldEntry {

    // spotless:off
    public static final Codec<FluidVeinWorldEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BedrockFluidDefinition.CODEC.optionalFieldOf("vein").forGetter(entry -> Optional.ofNullable(entry.getDefinition())),
            Codec.INT.fieldOf("fluidYield").forGetter(FluidVeinWorldEntry::getFluidYield),
            Codec.INT.fieldOf("operationsRemaining").forGetter(FluidVeinWorldEntry::getOperationsRemaining)
    ).apply(instance, FluidVeinWorldEntry::newEntry));
    // spotless:on

    @Setter
    private Supplier<@Nullable Holder<BedrockFluidDefinition>> definition;
    @Getter
    private int fluidYield;
    @Getter
    private int operationsRemaining;

    public FluidVeinWorldEntry(@Nullable Holder<BedrockFluidDefinition> definition, int fluidYield,
                               int operationsRemaining) {
        this.definition = () -> definition;
        this.fluidYield = fluidYield;
        this.operationsRemaining = operationsRemaining;
    }

    @Nullable
    public Holder<BedrockFluidDefinition> getDefinition() {
        return this.definition.get();
    }

    @SuppressWarnings("unused")
    public void setOperationsRemaining(int amount) {
        this.operationsRemaining = amount;
    }

    public void decreaseOperations(int amount) {
        operationsRemaining = ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids ? operationsRemaining :
                Math.max(0, operationsRemaining - amount);
    }

    private static FluidVeinWorldEntry newEntry(Optional<Holder<BedrockFluidDefinition>> definition, int fluidYield,
                                                int operationsRemaining) {
        return new FluidVeinWorldEntry(definition.orElse(null), fluidYield, operationsRemaining);
    }
}
