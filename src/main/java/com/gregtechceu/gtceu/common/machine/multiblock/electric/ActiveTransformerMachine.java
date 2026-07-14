package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2BatchModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.abilities;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ActiveTransformerMachine extends WorkableElectricMultiblockMachine
                                      implements IControllable, IDisplayUIMachine, LDLib2MachineUIProvider,
                                      LDLib2FancyActionMachine {

    private EnergyContainerList powerOutput;
    private EnergyContainerList powerInput;
    protected ConditionalSubscriptionHandler converterSubscription;
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public ActiveTransformerMachine(BlockEntityCreationInfo info) {
        super(info);
        this.powerOutput = EnergyContainerList.EMPTY;
        this.powerInput = EnergyContainerList.EMPTY;

        this.converterSubscription = new ConditionalSubscriptionHandler(this, this::convertEnergyTick,
                this::isSubscriptionActive);
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
    }

    public void convertEnergyTick() {
        if (isWorkingEnabled()) {
            getWorkLogic()
                    .setStatus(isSubscriptionActive() ? WorkLogic.Status.WORKING : WorkLogic.Status.SUSPEND);
        }
        if (isWorkingEnabled()) {
            long canDrain = powerInput.getEnergyStored();
            long totalDrained = powerOutput.changeEnergy(canDrain);
            powerInput.removeEnergy(totalDrained);
        }
        converterSubscription.updateSubscription();
    }

    @SuppressWarnings("RedundantIfStatement") // It is cleaner to have the final return true separate.
    protected boolean isSubscriptionActive() {
        if (!isFormed()) return false;

        if (powerInput.getEnergyStored() <= 0) return false;
        if (powerOutput.getEnergyStored() >= powerOutput.getEnergyCapacity()) return false;

        return true;
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        // capture all energy containers
        List<IEnergyContainer> powerInput = new ArrayList<>();
        List<IEnergyContainer> powerOutput = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());

        for (IMultiPart part : getPrioritySortedParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;

                var containers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .toList();

                if (handlerList.getHandlerIO().support(IO.IN)) {
                    powerInput.addAll(containers);
                } else if (handlerList.getHandlerIO().support(IO.OUT)) {
                    powerOutput.addAll(containers);
                }

                traitSubscriptions
                        .add(handlerList.subscribe(converterSubscription::updateSubscription, EURecipeCapability.CAP));
            }
        }

        // Invalidate the structure if there is not at least one output and one input
        if (powerInput.isEmpty() || powerOutput.isEmpty()) {
            this.invalidateStructure(DEFAULT_STRUCTURE);
            return;
        }

        this.powerOutput = new EnergyContainerList(powerOutput);
        this.powerInput = new EnergyContainerList(powerInput);

        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
        converterSubscription.updateSubscription();
    }

    @NotNull
    private List<IMultiPart> getPrioritySortedParts() {
        return getParts().stream().sorted(Comparator.comparingInt(part -> {
            if (part instanceof MetaMachine partMachine) {
                var partBlock = partMachine.getBlockState().getBlock();

                if (PartAbility.OUTPUT_ENERGY.isApplicable(partBlock))
                    return 1;

                if (PartAbility.SUBSTATION_OUTPUT_ENERGY.isApplicable(partBlock))
                    return 2;

                if (PartAbility.OUTPUT_LASER.isApplicable(partBlock))
                    return 3;
            }

            return 4;
        })).toList();
    }

    @Override
    public void invalidateStructure(String structureName) {
        boolean shouldExplode = DEFAULT_STRUCTURE.equals(structureName) &&
                (isWorkingEnabled() && getWorkLogic().getStatus() == WorkLogic.Status.WORKING) &&
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers;
        float explosionStrength = 6f + getTier();
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        if (shouldExplode) {
            GTUtil.doExplosion(getLevel(), getBlockPos(), explosionStrength);
        }
        resetTransferRuntimeState();
    }

    @Override
    public void onUnload() {
        resetTransferRuntimeState();
        super.onUnload();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        resetTransferRuntimeState();
    }

    private void resetTransferRuntimeState() {
        this.powerOutput = EnergyContainerList.EMPTY;
        this.powerInput = EnergyContainerList.EMPTY;
        this.displaySnapshot = List.of();
        getWorkLogic().setStatus(WorkLogic.Status.SUSPEND);
        converterSubscription.unsubscribe();
        displaySnapshotSubscription.unsubscribe();
    }

    public static TraceabilityPredicate getHatchPredicates() {
        return abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1)
                .or(abilities(PartAbility.OUTPUT_ENERGY).setPreviewCount(2))
                .or(abilities(PartAbility.SUBSTATION_INPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.INPUT_LASER).setPreviewCount(1))
                .or(abilities(PartAbility.OUTPUT_LASER).setPreviewCount(1));
    }

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    private void refreshDisplaySnapshot() {
        DisplayState state = captureDisplayState(isFormed(), isWorkingEnabled(), isActive(), powerInput, powerOutput,
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers);
        List<Component> nextSnapshot = createDisplaySnapshot(state);
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    static DisplayState captureDisplayState(boolean formed, boolean workingEnabled, boolean active,
                                            EnergyContainerList powerInput, EnergyContainerList powerOutput,
                                            boolean dangerEnabled) {
        return new DisplayState(formed, workingEnabled, active,
                powerInput.getTotalEUt(), powerOutput.getTotalEUt(),
                Math.abs(powerInput.getInputPerSec() / 20),
                Math.abs(powerOutput.getOutputPerSec() / 20), dangerEnabled);
    }

    static List<Component> createDisplaySnapshot(DisplayState state) {
        if (!state.formed()) {
            return List.of();
        }

        List<Component> text = new ArrayList<>();
        if (!state.workingEnabled()) {
            text.add(Component.translatable("gtpm.multiblock.work_paused"));
        } else if (state.active()) {
            text.add(Component.translatable("gtpm.multiblock.running"));
            text.add(Component.translatable("gtpm.multiblock.active_transformer.max_input",
                    FormattingUtil.formatNumbers(state.maxInput())));
            text.add(Component.translatable("gtpm.multiblock.active_transformer.max_output",
                    FormattingUtil.formatNumbers(state.maxOutput())));
            text.add(Component.translatable("gtpm.multiblock.active_transformer.average_in",
                    FormattingUtil.formatNumbers(state.averageInput())));
            text.add(Component.translatable("gtpm.multiblock.active_transformer.average_out",
                    FormattingUtil.formatNumbers(state.averageOutput())));
            if (state.dangerEnabled()) {
                text.add(Component.translatable("gtpm.multiblock.active_transformer.danger_enabled"));
            }
        } else {
            text.add(Component.translatable("gtpm.multiblock.idling"));
        }
        return List.copyOf(text);
    }

    record DisplayState(boolean formed, boolean workingEnabled, boolean active,
                        long maxInput, long maxOutput, long averageInput, long averageOutput,
                        boolean dangerEnabled) {}

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        ActiveTransformerLDLib2PageImpl page = new ActiveTransformerLDLib2PageImpl(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Active Transformer UI holder must resolve the opened controller.");
        }
    }

    private final class ActiveTransformerLDLib2PageImpl implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private ActiveTransformerLDLib2PageImpl(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(ActiveTransformerMachine.this,
                    player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Active Transformer part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != ActiveTransformerMachine.this) {
                throw new IllegalStateException("Active Transformer page holder no longer resolves its controller.");
            }

            UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

            GTScrollerViewElement screen = new GTScrollerViewElement(4, 4, 182, 117);
            screen.style(style -> style.backgroundTexture(getScreenTexture()));
            screen.viewPort(viewPort -> viewPort
                    .layout(layout -> layout.paddingAll(0))
                    .style(style -> style.backgroundTexture(getScreenTexture())));
            screen.scrollerStyle(style -> style
                    .mode(ScrollerMode.VERTICAL)
                    .verticalScrollDisplay(ScrollDisplay.AUTO)
                    .horizontalScrollDisplay(ScrollDisplay.NEVER));

            GTLabelElement title = new GTLabelElement(4, 5, 174, 10,
                    getBlockState().getBlock().getDescriptionId(), true);
            title.textStyle(style -> style
                    .textColor(0x404040)
                    .textShadow(false)
                    .textAlignHorizontal(Horizontal.LEFT)
                    .textAlignVertical(Vertical.CENTER));
            screen.addScrollViewChild(title);
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17,
                    ActiveTransformerMachine.this::addDisplayText)
                    .setMaxWidthLimit(150)
                    .clickHandler(ActiveTransformerMachine.this::handleDisplayClick));
            root.addChild(screen);
            return root;
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
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(
                    configuratorPanel, ActiveTransformerMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(
                    configuratorPanel, ActiveTransformerMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    ActiveTransformerMachine.this, holder));
        }

        @Override
        public List<LDLib2FancyUIProvider> getSubTabs() {
            return partPages;
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }
    }
}
