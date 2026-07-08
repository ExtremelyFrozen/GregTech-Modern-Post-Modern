package com.gregtechceu.gtceu.utils.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TagCompatibilityFixer {

    public static void fixMachineAutoOutputTag(CompoundTag machineTag) {
        if (!machineTag.contains("autoOutput")) {
            var outputTag = new CompoundTag();
            Tag itemOutputDirection = machineTag.get("outputFacingItems");
            Tag fluidOutputDirection = machineTag.get("outputFacingFluids");
            Tag autoOutputItems = machineTag.get("autoOutputItems");
            Tag autoOutputFluids = machineTag.get("autoOutputFluids");
            Tag allowInputItems = machineTag.get("allowInputFromOutputSideItems");
            Tag allowInputFluids = machineTag.get("allowInputFromOutputSideFluids");
            if (itemOutputDirection != null) outputTag.put("itemOutputDirection", itemOutputDirection);
            if (fluidOutputDirection != null) outputTag.put("fluidOutputDirection", fluidOutputDirection);
            if (autoOutputItems != null) outputTag.put("autoOutputItems", autoOutputItems);
            if (autoOutputFluids != null) outputTag.put("autoOutputFluids", autoOutputFluids);
            if (allowInputItems != null) outputTag.put("allowItemInputFromOutputSide", allowInputItems);
            if (allowInputFluids != null) outputTag.put("allowFluidInputFromOutputSide", allowInputFluids);
            machineTag.put("autoOutput", outputTag);
        }
    }
}
