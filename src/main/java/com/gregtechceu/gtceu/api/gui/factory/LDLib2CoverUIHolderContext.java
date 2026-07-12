package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.common.data.GTMenuTypes;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 menu holder for cover UIs opened from a coverable block face.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2CoverUIHolderContext implements UICoverHolder, MenuProvider, IContainerUIHolder {

    private final Player player;
    private final BlockPos pos;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;
    @Nullable
    private final LDLib2CoverUIProvider openedProvider;
    private boolean serverCloseNotified;

    public LDLib2CoverUIHolderContext(Player player, CoverBehavior cover) {
        this(player, cover.coverHolder.getBlockPos(), cover.attachedSide, cover.coverDefinition.getId(),
                requireProvider(cover));
    }

    public LDLib2CoverUIHolderContext(Player player, BlockPos pos, Direction side,
                                      ResourceLocation coverDefinitionId) {
        this(player, pos, side, coverDefinitionId, null);
    }

    private LDLib2CoverUIHolderContext(Player player, BlockPos pos, Direction side,
                                       ResourceLocation coverDefinitionId,
                                       @Nullable LDLib2CoverUIProvider openedProvider) {
        this.player = player;
        this.pos = pos;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
        this.openedProvider = openedProvider;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public Direction getSide() {
        return side;
    }

    @Override
    public ResourceLocation getCoverDefinitionId() {
        return coverDefinitionId;
    }

    @Nullable
    @Override
    public CoverBehavior getCover() {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }

        var coverable = GTCapabilityHelper.getCoverable(level, pos, side);
        if (coverable == null) {
            return null;
        }

        CoverBehavior cover = coverable.getCoverAtSide(side);
        if (cover == null || !cover.coverDefinition.getId().equals(coverDefinitionId)) {
            return null;
        }
        return cover;
    }

    @Override
    public boolean isStillValid(Player player) {
        return getCover() instanceof LDLib2CoverUIProvider uiProvider &&
                uiProvider.canCreateLDLib2UI(player, this);
    }

    @Override
    public Component getDisplayName() {
        CoverBehavior cover = getCover();
        if (cover != null && !cover.getAttachItem().isEmpty()) {
            return cover.getAttachItem().getHoverName();
        }
        return Component.translatable(coverDefinitionId.toLanguageKey("cover"));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GTCoverUIContainerMenu(GTMenuTypes.COVER_UI.get(), containerId, playerInventory, this);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(side);
        buffer.writeResourceLocation(coverDefinitionId);
    }

    @Override
    public ModularUI createUI(Player player) {
        if (!(getCover() instanceof LDLib2CoverUIProvider uiProvider)) {
            throw new IllegalStateException("Cover does not expose an LDLib2 UI for the opened holder.");
        }
        if (!uiProvider.canCreateLDLib2UI(player, this)) {
            throw new IllegalStateException("Cover rejected the opened LDLib2 UI holder.");
        }
        UI ui = uiProvider.createLDLib2UI(player, this);
        if (ui == null) {
            throw new IllegalStateException("Cover LDLib2 UI provider returned null.");
        }
        return ModularUI.of(ui, player);
    }

    void close(Player player) {
        if (player.level().isClientSide || serverCloseNotified) {
            return;
        }
        serverCloseNotified = true;
        if (openedProvider == null) {
            return;
        }
        try {
            openedProvider.onUIClosed();
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to close LDLib2 cover UI {} for player {} at {} on {}", coverDefinitionId,
                    player.getGameProfile().getName(), pos, side, exception);
        }
    }

    private static LDLib2CoverUIProvider requireProvider(CoverBehavior cover) {
        if (cover instanceof LDLib2CoverUIProvider provider) {
            return provider;
        }
        throw new IllegalArgumentException("Cover does not expose an LDLib2 UI.");
    }
}
