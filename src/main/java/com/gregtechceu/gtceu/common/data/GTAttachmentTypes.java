package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.capability.MedicalConditionTracker;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.jetbrains.annotations.Nullable;

public class GTAttachmentTypes {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(
            NeoForgeRegistries.ATTACHMENT_TYPES, GTCEu.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MedicalConditionTracker>> MEDICAL_CONDITION_TRACKER = ATTACHMENT_TYPES
            .register("hazard_tracker", () -> AttachmentType.builder(GTAttachmentTypes::newMedicalConditionTracker)
                    .serialize(new MedicalConditionTrackerSerializer())
                    .build());

    private static MedicalConditionTracker newMedicalConditionTracker(IAttachmentHolder holder) {
        if (holder instanceof Player player) {
            return new MedicalConditionTracker(player);
        }
        throw new IllegalArgumentException("Medical condition tracker attachment requires a player holder");
    }

    private static class MedicalConditionTrackerSerializer implements
                                                           IAttachmentSerializer<Tag, MedicalConditionTracker> {

        @Override
        public MedicalConditionTracker read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
            MedicalConditionTracker tracker = newMedicalConditionTracker(holder);
            tracker.importComponents(DataComponentMap.CODEC
                    .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                    .getOrThrow());
            return tracker;
        }

        @Override
        public @Nullable Tag write(MedicalConditionTracker attachment, HolderLookup.Provider provider) {
            DataComponentMap components = attachment.exportComponents();
            if (components.isEmpty()) {
                return null;
            }
            return DataComponentMap.CODEC
                    .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), components)
                    .getOrThrow();
        }
    }
}
