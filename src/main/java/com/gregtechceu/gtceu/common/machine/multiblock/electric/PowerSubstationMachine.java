package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
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
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.IBatteryData;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitType;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.sync_system.FieldCodecs;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

public class PowerSubstationMachine extends WorkableMultiblockMachine
                                    implements IEnergyInfoProvider, IDisplayUIMachine, LDLib2MachineUIProvider,
                                    LDLib2FancyActionMachine {

    // Structure Constants
    public static final int MAX_BATTERY_LAYERS = 18;
    public static final int MIN_CASINGS = 14;

    // Passive Drain Constants
    // 1% capacity per 24 hours
    public static final long PASSIVE_DRAIN_DIVISOR = 20 * 60 * 60 * 24 * 100;
    // no more than 100kEU/t per storage block
    public static final long PASSIVE_DRAIN_MAX_PER_STORAGE = 100_000L;

    // Match Context Headers
    public static final String PMC_BATTERY_HEADER = "PSSBattery_";

    private static final BigInteger BIG_INTEGER_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private @Nullable IMaintenanceMachine maintenance;

    @SaveField
    private PowerStationEnergyBank energyBank;

    private EnergyContainerList inputHatches;
    private EnergyContainerList outputHatches;
    private long passiveDrain;

    // Stats tracked for UI display
    private long netInLastSec;
    @Getter
    private long inputPerSec;
    private long netOutLastSec;
    @Getter
    private long outputPerSec;

    protected final ConditionalSubscriptionHandler tickSubscription;
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public PowerSubstationMachine(BlockEntityCreationInfo info) {
        super(info);
        this.inputHatches = EnergyContainerList.EMPTY;
        this.outputHatches = EnergyContainerList.EMPTY;
        this.tickSubscription = new ConditionalSubscriptionHandler(this, this::transferEnergyTick, this::isFormed);
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
        this.energyBank = attachTrait(new PowerStationEnergyBank(List.of()));
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        this.maintenance = null;
        List<IEnergyContainer> inputs = new ArrayList<>();
        List<IEnergyContainer> outputs = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;

                var containers = handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .toList();

                if (handlerList.getHandlerIO().support(IO.IN)) {
                    inputs.addAll(containers);
                } else if (handlerList.getHandlerIO().support(IO.OUT)) {
                    outputs.addAll(containers);
                }

                traitSubscriptions
                        .add(handlerList.subscribe(tickSubscription::updateSubscription, EURecipeCapability.CAP));
            }
        }
        this.inputHatches = new EnergyContainerList(inputs);
        this.outputHatches = new EnergyContainerList(outputs);

        List<IBatteryData> batteries = new ArrayList<>();
        for (Map.Entry<String, Object> battery : getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().entrySet()) {
            if (battery.getKey().startsWith(PMC_BATTERY_HEADER) &&
                    battery.getValue() instanceof BatteryMatchWrapper wrapper) {
                for (int i = 0; i < wrapper.amount; i++) {
                    batteries.add(wrapper.partType);
                }
            }
        }
        if (batteries.isEmpty()) {
            // only empty batteries found in the structure
            invalidateStructure(structureName);
            return;
        }
        energyBank.rebuild(batteries);
        this.passiveDrain = this.energyBank.getPassiveDrainPerTick();

        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
        tickSubscription.updateSubscription();
    }

    @Override
    public void invalidateStructure(String structureName) {
        // don't null out energyBank since it holds the stored energy, which
        // we need to hold on to across rebuilds to not void all energy if a
        // multiblock part or block other than the controller is broken.
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            resetTransferRuntimeState();
        }
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
        this.maintenance = null;
        this.inputHatches = EnergyContainerList.EMPTY;
        this.outputHatches = EnergyContainerList.EMPTY;
        this.passiveDrain = 0;
        this.netInLastSec = 0;
        this.inputPerSec = 0;
        this.netOutLastSec = 0;
        this.outputPerSec = 0;
        this.displaySnapshot = List.of();
        tickSubscription.unsubscribe();
        displaySnapshotSubscription.unsubscribe();
    }

    protected void transferEnergyTick() {
        if (!getLevel().isClientSide) {
            if (getOffsetTimer() % 20 == 0) {
                // active here is just used for rendering
                getWorkLogic()
                        .setStatus(energyBank.hasEnergy() ? WorkLogic.Status.WORKING : WorkLogic.Status.IDLE);
                inputPerSec = netInLastSec;
                outputPerSec = netOutLastSec;
                netInLastSec = 0;
                netOutLastSec = 0;
            }

            if (isWorkingEnabled() && isFormed()) {
                // Bank from Energy Input Hatches
                long energyBanked = energyBank.fill(inputHatches.getEnergyStored());
                inputHatches.changeEnergy(-energyBanked);
                netInLastSec += energyBanked;

                // Passive drain
                long energyPassiveDrained = energyBank.drain(getPassiveDrain());
                netOutLastSec += energyPassiveDrained;

                // Debank to Dynamo Hatches
                long energyDebanked = energyBank
                        .drain(outputHatches.getEnergyCapacity() - outputHatches.getEnergyStored());
                outputHatches.changeEnergy(energyDebanked);
                netOutLastSec += energyDebanked;
            }
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    void refreshDisplaySnapshot() {
        List<Component> partDisplay = new ArrayList<>();
        for (IMultiPart part : getParts()) {
            part.addMultiText(partDisplay);
        }
        List<Component> additionalDisplay = new ArrayList<>();
        getDefinition().getAdditionalDisplay().accept(this, additionalDisplay);

        boolean formed = isFormed();
        DisplayState state = new DisplayState(formed, isWorkingEnabled(), isActive(), getWorkLogic().isWaiting(),
                energyBank.getStored(), energyBank.getCapacity(), formed ? getPassiveDrain() : 0,
                inputPerSec, outputPerSec, getLevel().tickRateManager().tickrate());
        List<Component> nextSnapshot = createDisplaySnapshot(state, partDisplay, additionalDisplay);
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    static List<Component> createDisplaySnapshot(DisplayState state, List<Component> partDisplay,
                                                 List<Component> additionalDisplay) {
        List<Component> text = new ArrayList<>(partDisplay.size() + additionalDisplay.size() + 8);
        text.addAll(partDisplay);
        if (state.formed()) {
            if (!state.workingEnabled()) {
                text.add(Component.translatable("gtpm.multiblock.work_paused"));
            } else if (state.active()) {
                text.add(Component.translatable("gtpm.multiblock.running"));
            } else {
                text.add(Component.translatable("gtpm.multiblock.idling"));
            }

            Style styleGold = Style.EMPTY.withColor(ChatFormatting.GOLD);
            Style styleDarkRed = Style.EMPTY.withColor(ChatFormatting.DARK_RED);
            Style styleGreen = Style.EMPTY.withColor(ChatFormatting.GREEN);
            Style styleRed = Style.EMPTY.withColor(ChatFormatting.RED);

            if (state.waiting()) {
                text.add(Component.translatable("gtpm.multiblock.waiting").setStyle(styleRed));
            }

            MutableComponent storedComponent = Component.literal(FormattingUtil.formatNumbers(state.stored()));
            text.add(Component.translatable("gtpm.multiblock.power_substation.stored",
                    storedComponent.setStyle(styleGold)));

            MutableComponent capacityComponent = Component.literal(FormattingUtil.formatNumbers(state.capacity()));
            text.add(Component.translatable("gtpm.multiblock.power_substation.capacity",
                    capacityComponent.setStyle(styleGold)));

            MutableComponent passiveDrainComponent = Component.literal(
                    FormattingUtil.formatNumbers(state.passiveDrain()));
            text.add(Component.translatable("gtpm.multiblock.power_substation.passive_drain",
                    passiveDrainComponent.setStyle(styleDarkRed)));

            MutableComponent averageInputComponent = Component.literal(
                    FormattingUtil.formatNumbers(state.inputPerSec() / 20));
            text.add(Component
                    .translatable("gtpm.multiblock.power_substation.average_in",
                            averageInputComponent.setStyle(styleGreen))
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gtpm.multiblock.power_substation.average_in_hover")))));

            MutableComponent averageOutputComponent = Component.literal(
                    FormattingUtil.formatNumbers(Math.abs(state.outputPerSec() / 20)));
            text.add(Component
                    .translatable("gtpm.multiblock.power_substation.average_out",
                            averageOutputComponent.setStyle(styleRed))
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("gtpm.multiblock.power_substation.average_out_hover")))));

            if (state.inputPerSec() > state.outputPerSec()) {
                BigInteger timeToFillSeconds = state.capacity().subtract(state.stored())
                        .divide(BigInteger.valueOf(Mth.floor(
                                (state.inputPerSec() - state.outputPerSec()) / 20.0f * state.tickRate())));
                text.add(Component.translatable("gtpm.multiblock.power_substation.time_to_fill",
                        getTimeToFillDrainText(timeToFillSeconds).setStyle(styleGreen)));
            } else if (state.inputPerSec() < state.outputPerSec()) {
                BigInteger timeToDrainSeconds = state.stored()
                        .divide(BigInteger.valueOf(Mth.floor(
                                (state.outputPerSec() - state.inputPerSec()) / 20.0f * state.tickRate())));
                text.add(Component.translatable("gtpm.multiblock.power_substation.time_to_drain",
                        getTimeToFillDrainText(timeToDrainSeconds).setStyle(styleRed)));
            }
        }
        text.addAll(additionalDisplay);
        return List.copyOf(text);
    }

    record DisplayState(boolean formed, boolean workingEnabled, boolean active, boolean waiting,
                        BigInteger stored, BigInteger capacity, long passiveDrain,
                        long inputPerSec, long outputPerSec, float tickRate) {}

    @VisibleForTesting
    List<Component> getDisplaySnapshot() {
        return displaySnapshot;
    }

    private static MutableComponent getTimeToFillDrainText(BigInteger timeToFillSeconds) {
        if (timeToFillSeconds.compareTo(BIG_INTEGER_MAX_LONG) > 0) {
            // too large to represent in a java Duration
            timeToFillSeconds = BIG_INTEGER_MAX_LONG;
        }

        Duration duration = Duration.ofSeconds(timeToFillSeconds.longValue());
        String key;
        long fillTime;
        if (duration.getSeconds() <= 180) {
            fillTime = duration.getSeconds();
            key = "gtpm.multiblock.power_substation.time_seconds";
        } else if (duration.toMinutes() <= 180) {
            fillTime = duration.toMinutes();
            key = "gtpm.multiblock.power_substation.time_minutes";
        } else if (duration.toHours() <= 72) {
            fillTime = duration.toHours();
            key = "gtpm.multiblock.power_substation.time_hours";
        } else if (duration.toDays() <= 730) { // 2 years
            fillTime = duration.toDays();
            key = "gtpm.multiblock.power_substation.time_days";
        } else if (duration.toDays() / 365 < 1_000_000) {
            fillTime = duration.toDays() / 365;
            key = "gtpm.multiblock.power_substation.time_years";
        } else {
            return Component.translatable("gtpm.multiblock.power_substation.time_forever");
        }

        return Component.translatable(key, FormattingUtil.formatNumbers(fillTime));
    }

    public long getPassiveDrain() {
        if (ConfigHolder.INSTANCE.machines.enableMaintenance) {
            IMaintenanceMachine maintenanceMachine = maintenance;
            if (maintenanceMachine == null) {
                for (IMultiPart part : getParts()) {
                    if (part instanceof IMaintenanceMachine foundMaintenance) {
                        maintenanceMachine = foundMaintenance;
                        this.maintenance = foundMaintenance;
                        break;
                    }
                }
            }
            if (maintenanceMachine == null) {
                throw new IllegalStateException("Formed Power Substation has no maintenance part.");
            }
            int multiplier = 1 + maintenanceMachine.getNumMaintenanceProblems();
            double modifier = maintenanceMachine.getDurationMultiplier();
            return (long) (passiveDrain * multiplier * modifier);
        }
        return passiveDrain;
    }

    public String getStored() {
        return FormattingUtil.formatNumbers(energyBank.getStored());
    }

    public String getCapacity() {
        return FormattingUtil.formatNumbers(energyBank.getCapacity());
    }

    @Override
    public EnergyInfo getEnergyInfo() {
        return new EnergyInfo(energyBank.getCapacity(), energyBank.getStored());
    }

    @Override
    public boolean supportsBigIntEnergyValues() {
        return true;
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        PowerSubstationFancyPage page = new PowerSubstationFancyPage(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Power Substation UI holder must resolve the opened controller.");
        }
    }

    private final class PowerSubstationFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private PowerSubstationFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(PowerSubstationMachine.this,
                    player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Power Substation part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != PowerSubstationMachine.this) {
                throw new IllegalStateException("Power Substation page holder no longer resolves its controller.");
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
                    PowerSubstationMachine.this::addDisplayText)
                    .setMaxWidthLimit(150)
                    .clickHandler(PowerSubstationMachine.this::handleDisplayClick));
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
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    PowerSubstationMachine.this, holder));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            for (IMultiPart part : getParts()) {
                if (part instanceof IMaintenanceMachine maintenanceMachine) {
                    maintenanceMachine.attachLDLib2MaintenanceTooltips(tooltipsPanel);
                }
            }
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

    public static class PowerStationEnergyBank extends MachineTrait {

        public static final MachineTraitType<PowerStationEnergyBank> TYPE = new MachineTraitType<>(
                PowerStationEnergyBank.class);
        public static final Codec<PowerStationEnergyBank> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream).fieldOf("storage")
                        .forGetter(PowerStationEnergyBank::copyStorage),
                        Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream).fieldOf("maximums")
                                .forGetter(PowerStationEnergyBank::copyMaximums))
                .apply(instance, PowerStationEnergyBank::new));

        static {
            FieldCodecs.register(PowerStationEnergyBank.class, CODEC);
        }

        @Override
        public MachineTraitType<PowerStationEnergyBank> getTraitType() {
            return TYPE;
        }

        private long[] storage;
        private long[] maximums;
        @Getter
        private BigInteger capacity;
        private int index;

        public PowerStationEnergyBank(List<IBatteryData> batteries) {
            super();
            setupBatteries(batteries);
        }

        private PowerStationEnergyBank(long[] storage, long[] maximums) {
            if (storage.length != maximums.length) {
                throw new IllegalArgumentException("Power Substation power bank storage and capacity sizes differ");
            }
            this.storage = storage.clone();
            this.maximums = maximums.clone();
            this.capacity = summarize(this.maximums);
            updateIndex();
        }

        public void setupBatteries(List<IBatteryData> batteries) {
            storage = new long[batteries.size()];
            maximums = new long[batteries.size()];
            for (int i = 0; i < batteries.size(); i++) {
                maximums[i] = batteries.get(i).getCapacity();
            }
            capacity = summarize(maximums);
        }

        /**
         * Rebuild the power storage with a new list of batteries.
         * Will use existing stored power and try to map it onto new batteries.
         * If there was more power before the rebuild operation, it will be lost.
         */
        public void rebuild(List<IBatteryData> batteries) {
            if (batteries.isEmpty()) {
                throw new IllegalArgumentException("Cannot rebuild Power Substation power bank with no batteries!");
            }
            long[] oldStorage = storage.clone();
            setupBatteries(batteries);
            for (long stored : oldStorage) {
                fill(stored);
            }
        }

        /**
         * @return Amount filled into storage
         */
        public long fill(long amount) {
            if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative!");
            if (storage.length == 0) return 0;

            // ensure index
            if (index != storage.length - 1 && storage[index] == maximums[index]) {
                index++;
            }

            long maxFill = Math.min(maximums[index] - storage[index], amount);

            // storage is completely full
            if (maxFill == 0 && index == storage.length - 1) {
                return 0;
            }

            // fill this "battery" as much as possible
            storage[index] += maxFill;
            amount -= maxFill;

            // try to fill other "batteries" if necessary
            if (amount > 0 && index != storage.length - 1) {
                return maxFill + fill(amount);
            }

            // other fill not necessary, either because the storage is now completely full,
            // or we were able to consume all the energy in this "battery"
            return maxFill;
        }

        /**
         * @return Amount drained from storage
         */
        public long drain(long amount) {
            if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative!");
            if (storage.length == 0) return 0;

            // ensure index
            if (index != 0 && storage[index] == 0) {
                index--;
            }

            long maxDrain = Math.min(storage[index], amount);

            // storage is completely empty
            if (maxDrain == 0 && index == 0) {
                return 0;
            }

            // drain this "battery" as much as possible
            storage[index] -= maxDrain;
            amount -= maxDrain;

            // try to drain other "batteries" if necessary
            if (amount > 0 && index != 0) {
                index--;
                return maxDrain + drain(amount);
            }

            // other drain not necessary, either because the storage is now completely empty,
            // or we were able to drain all the energy from this "battery"
            return maxDrain;
        }

        public BigInteger getStored() {
            return summarize(storage);
        }

        public boolean hasEnergy() {
            for (long l : storage) {
                if (l > 0) return true;
            }
            return false;
        }

        private long[] copyStorage() {
            return storage.clone();
        }

        private long[] copyMaximums() {
            return maximums.clone();
        }

        private void updateIndex() {
            index = 0;
            for (int i = storage.length - 1; i >= 0; i--) {
                if (storage[i] > 0) {
                    index = i;
                    return;
                }
            }
        }

        private static BigInteger summarize(long[] values) {
            BigInteger retVal = BigInteger.ZERO;
            long currentSum = 0;
            for (long value : values) {
                if (currentSum != 0 && value > Long.MAX_VALUE - currentSum) {
                    // will overflow if added
                    retVal = retVal.add(BigInteger.valueOf(currentSum));
                    currentSum = 0;
                }
                currentSum += value;
            }
            if (currentSum != 0) {
                retVal = retVal.add(BigInteger.valueOf(currentSum));
            }
            return retVal;
        }

        @VisibleForTesting
        public long getPassiveDrainPerTick() {
            long[] maximumsExcl = new long[maximums.length];
            int index = 0;
            int numExcl = 0;
            for (long maximum : maximums) {
                if (maximum / PASSIVE_DRAIN_DIVISOR >= PASSIVE_DRAIN_MAX_PER_STORAGE) {
                    numExcl++;
                } else {
                    maximumsExcl[index++] = maximum;
                }
            }
            maximumsExcl = Arrays.copyOf(maximumsExcl, index);
            BigInteger capacityExcl = summarize(maximumsExcl);

            return capacityExcl.divide(BigInteger.valueOf(PASSIVE_DRAIN_DIVISOR))
                    .add(BigInteger.valueOf(PASSIVE_DRAIN_MAX_PER_STORAGE * numExcl))
                    .longValue();
        }
    }

    @Getter
    public static class BatteryMatchWrapper {

        private final IBatteryData partType;
        private int amount;

        public BatteryMatchWrapper(IBatteryData partType) {
            this.partType = partType;
        }

        public BatteryMatchWrapper increment() {
            amount++;
            return this;
        }
    }
}
