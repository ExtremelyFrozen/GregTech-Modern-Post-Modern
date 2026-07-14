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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 menu holder for cover UIs opened from a coverable block face.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class LDLib2CoverUIHolderContext implements UICoverHolder, MenuProvider, IContainerUIHolder {

    private final Player player;
    private final BlockPos pos;
    private final BlockPos interactionAnchor;
    private final BooleanSupplier interactionAnchorValid;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;
    @Nullable
    private final CoverBehavior openedCover;
    @Nullable
    private UUID actionSessionId;
    private boolean serverCloseNotified;

    public LDLib2CoverUIHolderContext(Player player, CoverBehavior cover) {
        this(player, cover.coverHolder.getBlockPos(), cover.attachedSide, cover.coverDefinition.getId(),
                requireProvider(cover), null, cover.coverHolder.getBlockPos(), () -> true);
    }

    /**
     * Creates a server holder whose action distance and continued validity belong to a validated interaction anchor.
     */
    LDLib2CoverUIHolderContext(ServerPlayer player, CoverBehavior cover, BlockPos interactionAnchor,
                               BooleanSupplier interactionAnchorValid) {
        this(player, cover.coverHolder.getBlockPos(), cover.attachedSide, cover.coverDefinition.getId(),
                requireProvider(cover), null, interactionAnchor, interactionAnchorValid);
    }

    public LDLib2CoverUIHolderContext(Player player, BlockPos pos, Direction side,
                                      ResourceLocation coverDefinitionId, UUID actionSessionId) {
        this(player, pos, side, coverDefinitionId, null, actionSessionId, pos, () -> true);
    }

    /**
     * Reconstructs the client holder with the server-displayed interaction anchor but no authoritative validity.
     */
    public LDLib2CoverUIHolderContext(Player player, BlockPos pos, BlockPos interactionAnchor, Direction side,
                                      ResourceLocation coverDefinitionId, UUID actionSessionId) {
        this(player, pos, side, coverDefinitionId, null, actionSessionId, interactionAnchor, () -> true);
    }

    private LDLib2CoverUIHolderContext(Player player, BlockPos pos, Direction side,
                                       ResourceLocation coverDefinitionId,
                                       @Nullable CoverBehavior openedCover,
                                       @Nullable UUID actionSessionId,
                                       BlockPos interactionAnchor,
                                       BooleanSupplier interactionAnchorValid) {
        this.player = player;
        this.pos = pos;
        this.interactionAnchor = interactionAnchor;
        this.interactionAnchorValid = interactionAnchorValid;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
        this.openedCover = openedCover;
        this.actionSessionId = actionSessionId;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    /**
     * Returns the server-selected interaction anchor for client display only.
     *
     * <p>
     * Action authorization resolves this value again from the active server menu and never trusts this accessor on a
     * client-created holder.
     */
    @ApiStatus.Internal
    public BlockPos getInteractionAnchor() {
        return interactionAnchor;
    }

    @Override
    public Direction getSide() {
        return side;
    }

    @Override
    public ResourceLocation getCoverDefinitionId() {
        return coverDefinitionId;
    }

    @Override
    public UUID getActionSessionId() {
        UUID sessionId = actionSessionId;
        if (sessionId == null) {
            throw new IllegalStateException("Cover UI holder is not bound to an open action session.");
        }
        return sessionId;
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
        if (openedCover != null && cover != openedCover) {
            return null;
        }
        return cover;
    }

    @Override
    public boolean isStillValid(Player player) {
        if (this.player != player || !isInteractionAnchorValid()) {
            return false;
        }
        boolean providerValid = getCover() instanceof LDLib2CoverUIProvider uiProvider &&
                uiProvider.canCreateLDLib2UI(player, this);
        return providerValid && isInteractionAnchorValid();
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
        if (!(player instanceof ServerPlayer) || this.player != player) {
            throw new IllegalStateException("Cover UI menu must be created for its original server player.");
        }
        requireInteractionAnchorValid("before menu creation");
        if (actionSessionId != null) {
            throw new IllegalStateException("Cover UI holder cannot create more than one menu session.");
        }
        actionSessionId = UUID.randomUUID();
        GTCoverUIContainerMenu menu = new GTCoverUIContainerMenu(
                GTMenuTypes.COVER_UI.get(), containerId, playerInventory, this);
        requireInteractionAnchorValid("after menu creation");
        return menu;
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBlockPos(interactionAnchor);
        buffer.writeEnum(side);
        buffer.writeResourceLocation(coverDefinitionId);
        buffer.writeUUID(getActionSessionId());
    }

    @Override
    public ModularUI createUI(Player player) {
        requireInteractionAnchorValid("before UI creation");
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
        ModularUI modularUI = ModularUI.of(ui, player);
        requireInteractionAnchorValid("after UI creation");
        return modularUI;
    }

    void close(Player player) {
        if (player.level().isClientSide || serverCloseNotified) {
            return;
        }
        serverCloseNotified = true;
        if (!(openedCover instanceof LDLib2CoverUIProvider openedProvider)) {
            return;
        }
        try {
            openedProvider.onUIClosed();
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to close LDLib2 cover UI {} for player {} at {} on {}", coverDefinitionId,
                    player.getGameProfile().getName(), pos, side, exception);
        }
    }

    boolean matchesActionSession(ServerPlayer player, BlockPos pos, Direction side,
                                 ResourceLocation coverDefinitionId, UUID actionSessionId) {
        return openedCover != null && !serverCloseNotified && isInteractionAnchorValid() &&
                this.player == player && this.pos.equals(pos) &&
                this.side == side && this.coverDefinitionId.equals(coverDefinitionId) &&
                actionSessionId.equals(this.actionSessionId) && isStillValid(player) && !serverCloseNotified &&
                getCover() == openedCover && isInteractionAnchorValid();
    }

    @Nullable
    BlockPos getInteractionAnchorForAction(ServerPlayer player, BlockPos pos, Direction side,
                                           ResourceLocation coverDefinitionId, UUID actionSessionId) {
        return matchesActionSession(player, pos, side, coverDefinitionId, actionSessionId) ?
                interactionAnchor : null;
    }

    boolean isInteractionAnchorValid() {
        try {
            return interactionAnchorValid.getAsBoolean();
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to validate cover UI interaction anchor {} for cover {} at {} on {}",
                    interactionAnchor, coverDefinitionId, pos, side, exception);
            return false;
        }
    }

    private void requireInteractionAnchorValid(String phase) {
        if (isInteractionAnchorValid()) {
            return;
        }
        GTCEu.LOGGER.warn("Rejecting cover UI {} because interaction anchor {} is invalid {}",
                coverDefinitionId, interactionAnchor, phase);
        throw new IllegalStateException("Cover UI interaction anchor is invalid " + phase + '.');
    }

    private static CoverBehavior requireProvider(CoverBehavior cover) {
        if (cover instanceof LDLib2CoverUIProvider) {
            return cover;
        }
        throw new IllegalArgumentException("Cover does not expose an LDLib2 UI.");
    }
}
