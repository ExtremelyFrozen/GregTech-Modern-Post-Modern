package com.gregtechceu.gtceu.common.cover.ender;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;

import java.util.List;

/**
 * Client-side LDLib2 Ender link UI element that accepts one requested channel list response.
 */
public interface EnderLinkChannelListReceiver {

    /**
     * Returns whether the response targets this opened cover UI.
     */
    boolean acceptsEnderLinkChannelList(BlockPos pos, Direction side, ResourceLocation coverDefinitionId);

    /**
     * Rebuilds the visible channel list from registry-aware serialized virtual entries.
     */
    void receiveEnderLinkChannelList(List<JsonElement> entries, HolderLookup.Provider registries);
}
