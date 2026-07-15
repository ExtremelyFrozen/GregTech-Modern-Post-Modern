package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;

import java.util.List;

public class ParallelHatchPartMachine extends TieredPartMachine
                                      implements LDLib2MachineUIProvider, LDLib2FancyPartUIProvider {

    private static final int MIN_PARALLEL = 1;
    private static final int PAGE_WIDTH = 100;
    private static final int PAGE_HEIGHT = 20;

    private final int maxParallel;

    @SaveField
    @SyncBoth
    @Getter
    private int currentParallel = 1;

    public ParallelHatchPartMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.maxParallel = (int) Math.pow(4, tier - GTValues.EV);
        this.currentParallel = maxParallel;
    }

    public void setCurrentParallel(int parallelAmount) {
        int normalizedParallel = normalizeCurrentParallel(parallelAmount);
        if (this.currentParallel == normalizedParallel) {
            return;
        }
        this.currentParallel = normalizedParallel;
        markControllerRecipesDirty();
    }

    @ServerFieldNormalizer(fieldName = "currentParallel")
    private int normalizeCurrentParallel(int parallelAmount) {
        return Mth.clamp(parallelAmount, MIN_PARALLEL, this.maxParallel);
    }

    @ServerFieldChangeListener(fieldName = "currentParallel")
    private void onCurrentParallelChanged(int oldParallel, int newParallel) {
        markControllerRecipesDirty();
    }

    protected void markControllerRecipesDirty() {
        for (MultiblockControllerMachine controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().markLastRecipeDirty();
            }
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingLDLib2Holder(holder);
        return UI.of(createLDLib2ParallelInput());
    }

    /** Creates a new holder-scoped page for one surrounding multiblock UI opening. */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        requireMatchingLDLib2Holder(holder);
        return new ParallelHatchLDLib2Page(player, holder);
    }

    private UIElement createLDLib2ParallelInput() {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.addChild(new GTIntInputElement(0, 0, PAGE_WIDTH, PAGE_HEIGHT, this::getCurrentParallel,
                this::setLDLib2CurrentParallel)
                .setMin(MIN_PARALLEL)
                .setMax(maxParallel));
        return root;
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Parallel Hatch page holder must resolve the opened machine.");
        }
    }

    private void setLDLib2CurrentParallel(int value) {
        setCurrentParallel(value);
        sendServerSyncChanges();
    }

    /** Owns the contextual Parallel Hatch page state for exactly one menu opening. */
    private final class ParallelHatchLDLib2Page implements LDLib2FancyUIProvider {

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private ParallelHatchLDLib2Page(Player player, MachineUIHolder holder) {
            requireMatchingLDLib2Holder(holder);
            this.holder = holder;
            directionalPage = new LDLib2DirectionalFancyConfigurator(ParallelHatchPartMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != ParallelHatchPartMachine.this) {
                throw new IllegalStateException("Parallel Hatch page holder no longer resolves its opened machine.");
            }
            return createLDLib2ParallelInput();
        }

        @Override
        public IGuiTexture getTabIcon() {
            return GuiTextures.itemStack(getDefinition().getItem());
        }

        @Override
        public Component getTitle() {
            return Component.translatable(getDefinition().getDescriptionId());
        }

        @Override
        public int getLDLib2PageWidth() {
            return PAGE_WIDTH;
        }

        @Override
        public int getLDLib2PageHeight() {
            return PAGE_HEIGHT;
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            tabs.attachSubTab(directionalPage);
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(ParallelHatchPartMachine.this);
            getTraitHolder().getAllTraits().stream()
                    .filter(IFancyTooltip.class::isInstance)
                    .map(IFancyTooltip.class::cast)
                    .forEach(tooltipsPanel::attachTooltips);
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(getTitle());
        }
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }
}
