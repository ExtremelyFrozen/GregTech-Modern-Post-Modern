package com.gregtechceu.gtceu.api.data.worldgen.bedrockore;

import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.Holder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class OreVeinWorldEntry {

    // spotless:off
    public static final Codec<OreVeinWorldEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BedrockOreDefinition.CODEC.optionalFieldOf("vein").forGetter(entry -> Optional.ofNullable(entry.definition)),
            Codec.INT.fieldOf("oreYield").forGetter(OreVeinWorldEntry::getOreYield),
            Codec.INT.fieldOf("operationsRemaining").forGetter(OreVeinWorldEntry::getOperationsRemaining)
    ).apply(instance, OreVeinWorldEntry::newEntry));
    // spotless:on

    @Nullable
    @Getter
    @Setter
    private Holder<BedrockOreDefinition> definition;
    @Getter
    private int oreYield;
    @Getter
    private int operationsRemaining;

    public OreVeinWorldEntry(@Nullable Holder<BedrockOreDefinition> vein, int oreYield, int operationsRemaining) {
        this.definition = vein;
        this.oreYield = oreYield;
        this.operationsRemaining = operationsRemaining;
    }

    @SuppressWarnings("unused")
    public void setOperationsRemaining(int amount) {
        this.operationsRemaining = amount;
    }

    public void decreaseOperations(int amount) {
        operationsRemaining = ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids ? operationsRemaining :
                Math.max(0, operationsRemaining - amount);
    }

    private static OreVeinWorldEntry newEntry(Optional<Holder<BedrockOreDefinition>> definition, int oreYield,
                                              int operationsRemaining) {
        return new OreVeinWorldEntry(definition.orElse(null), oreYield, operationsRemaining);
    }
}
