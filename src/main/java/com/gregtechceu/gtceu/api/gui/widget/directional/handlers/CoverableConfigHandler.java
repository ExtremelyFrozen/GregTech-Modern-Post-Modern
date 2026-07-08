package com.gregtechceu.gtceu.api.gui.widget.directional.handlers;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.CoverPlaceBehavior;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.util.ClickData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class CoverableConfigHandler implements IDirectionalConfigHandler {

    private static final int CONFIG_BUTTON_VISIBILITY_UPDATE_ID = 1;
    private static final IGuiTexture CONFIG_BTN_TEXTURE = GuiTextures.group(GuiTextures.IO_CONFIG_COVER_SETTINGS);

    private final ICoverable machine;
    private CustomItemStackHandler handler;
    private Direction side;

    private ConfiguratorPanel panel;

    private SlotWidget slotWidget;
    private ButtonWidget configButton;
    private CoverBehavior coverBehavior;
    private boolean syncingDisplayedCoverItem;

    public CoverableConfigHandler(ICoverable machine) {
        this.machine = machine;
        this.handler = createItemStackHandler();
    }

    private CustomItemStackHandler createItemStackHandler() {
        var handler = new CustomItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };

        handler.setFilter(itemStack -> {
            if (itemStack.isEmpty()) return true;
            if (this.side == null) return false;
            return CoverPlaceBehavior.isCoverBehaviorItem(itemStack, () -> false,
                    coverDef -> ICoverable.canPlaceCover(coverDef, this.machine));
        });

        return handler;
    }

    @Override
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        WidgetGroup group = new WidgetGroup(0, 0, (18 * 2) + 1, 18);
        this.panel = machineUI.getConfiguratorPanel();

        group.addWidget(slotWidget = new SlotWidget(handler, 0, 19, 0) {

            @Override
            public boolean canPutStack(ItemStack stack) {
                return super.canPutStack(stack) && CoverPlaceBehavior.isCoverBehaviorItem(stack, () -> false,
                        def -> def.createCoverBehavior(machine, side).canAttach());
            }
        }
                .setChangeListener(this::coverItemChanged)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.IO_CONFIG_COVER_SLOT_OVERLAY)));
        configButton = createConfigButton();
        configButton.setVisible(false);
        configButton.setActive(false);
        group.addWidget(configButton);

        checkCoverBehaviour();

        return group;
    }

    private ButtonWidget createConfigButton() {
        return new ButtonWidget(0, 0, 18, 18, CONFIG_BTN_TEXTURE, this::toggleConfigTab) {

            private boolean configButtonVisible;

            @Override
            public void writeInitialData(RegistryFriendlyByteBuf buffer) {
                super.writeInitialData(buffer);
                updateConfigButtonState(hasConfigurableCover());
                buffer.writeBoolean(configButtonVisible);
            }

            @Override
            public void readInitialData(RegistryFriendlyByteBuf buffer) {
                super.readInitialData(buffer);
                updateConfigButtonState(buffer.readBoolean());
            }

            @Override
            public void detectAndSendChanges() {
                super.detectAndSendChanges();
                boolean visible = hasConfigurableCover();
                if (configButtonVisible != visible) {
                    updateConfigButtonState(visible);
                    writeUpdateInfo(CONFIG_BUTTON_VISIBILITY_UPDATE_ID, buf -> buf.writeBoolean(configButtonVisible));
                }
            }

            @Override
            @OnlyIn(Dist.CLIENT)
            public void readUpdateInfo(int id, RegistryFriendlyByteBuf buffer) {
                if (id == CONFIG_BUTTON_VISIBILITY_UPDATE_ID) {
                    updateConfigButtonState(buffer.readBoolean());
                    return;
                }
                super.readUpdateInfo(id, buffer);
            }

            private void updateConfigButtonState(boolean visible) {
                configButtonVisible = visible;
                setVisible(visible);
                setActive(visible);
            }
        };
    }

    // FIXME: This gets called twice in a single tick, causing two covers to exist simultaneously
    private void coverItemChanged() {
        if (syncingDisplayedCoverItem) {
            return;
        }

        if (!(panel.getGui().entityPlayer instanceof ServerPlayer serverPlayer) || side == null)
            return;

        var item = handler.getStackInSlot(0);
        if (machine.getCoverAtSide(side) != null) {
            machine.removeCover(false, side, serverPlayer);
        }

        if (!item.isEmpty() && machine.getCoverAtSide(side) == null) {
            if (item.getItem() instanceof IComponentItem componentItem) {
                for (IItemComponent component : componentItem.getComponents()) {
                    if (component instanceof CoverPlaceBehavior placeBehavior) {
                        machine.placeCoverOnSide(side, item, placeBehavior.coverDefinition(), serverPlayer);
                        break;
                    }
                }
            }
        }

        checkCoverBehaviour();
    }

    @Override
    public void onSideSelected(BlockPos pos, Direction side) {
        this.side = side;
        checkCoverBehaviour();
    }

    private void updateWidgetVisibility() {
        var sideSelected = this.side != null;
        slotWidget.setVisible(sideSelected);
        slotWidget.setActive(sideSelected);
        var configurableCover = hasConfigurableCover();
        configButton.setVisible(configurableCover);
        configButton.setActive(configurableCover);
    }

    public void checkCoverBehaviour() {
        if (side == null) {
            updateWidgetVisibility();
            return;
        }

        var coverBehaviour = machine.getCoverAtSide(side);
        if (coverBehaviour != this.coverBehavior) {
            this.coverBehavior = coverBehaviour;

            var attachItem = coverBehaviour == null ? ItemStack.EMPTY : coverBehaviour.getAttachItem();
            syncingDisplayedCoverItem = true;
            try {
                handler.setStackInSlot(0, attachItem);
            } finally {
                syncingDisplayedCoverItem = false;
            }
        }

        updateWidgetVisibility();
    }

    private void toggleConfigTab(ClickData cd) {
        if (shouldOpenLDLib2CoverUI()) {
            openLDLib2CoverUI(cd);
        }
    }

    private boolean hasConfigurableCover() {
        if (side == null || coverBehavior == null) {
            return false;
        }
        var cover = machine.getCoverAtSide(side);
        if (cover == null) {
            return false;
        }
        return panel.getGui().entityPlayer instanceof Player player &&
                CoverUIHelper.canOpenLDLib2(cover, player);
    }

    private boolean shouldOpenLDLib2CoverUI() {
        if (coverBehavior == null) {
            return false;
        }
        if (!(panel.getGui().entityPlayer instanceof Player player)) {
            return CoverUIHelper.hasUI(coverBehavior);
        }
        return CoverUIHelper.canOpenLDLib2(coverBehavior, player);
    }

    private void openLDLib2CoverUI(ClickData cd) {
        if (cd.isRemote || coverBehavior == null || !(panel.getGui().entityPlayer instanceof ServerPlayer player)) {
            return;
        }
        CoverUIHelper.open(coverBehavior, player);
    }

    @Override
    public ScreenSide getScreenSide() {
        return ScreenSide.RIGHT;
    }
}
