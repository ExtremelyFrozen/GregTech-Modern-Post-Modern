package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.WeightedMaterial;
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
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.machine.trait.BedrockOreMinerLogic;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BedrockOreMinerMachine extends WorkableElectricMultiblockMachine
                                    implements ITieredMachine, LDLib2MachineUIProvider, LDLib2FancyActionMachine {

    @Getter
    private final int tier;
    @Getter(AccessLevel.PACKAGE)
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public BedrockOreMinerMachine(BlockEntityCreationInfo info, int tier) {
        super(info, new BedrockOreMinerLogic());
        this.tier = tier;
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
    }

    @NotNull
    @Override
    public BedrockOreMinerLogic getRecipeLogic() {
        return (BedrockOreMinerLogic) super.getRecipeLogic();
    }

    public int getEnergyTier() {
        var energyContainers = this.getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (energyContainers.isEmpty()) return this.tier;
        var energyCont = new EnergyContainerList(energyContainers.stream().filter(IEnergyContainer.class::isInstance)
                .map(IEnergyContainer.class::cast).toList());
        return Math.min(this.tier + 1,
                Math.max(this.tier, GTUtil.getFloorTierByVoltage(energyCont.getEffectiveVoltage())));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.initialize(getLevel());
        }
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName) && !isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName) && !isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        clearDisplayRuntimeState();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        clearDisplayRuntimeState();
    }

    private void clearDisplayRuntimeState() {
        displaySnapshot = List.of();
        displaySnapshotSubscription.unsubscribe();
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    void refreshDisplaySnapshot() {
        List<Component> nextSnapshot = createDisplaySnapshot(captureDisplayState());
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    protected DisplayState captureDisplayState() {
        if (!isFormed()) {
            return captureDisplayState(false, tier, null, 0, 0);
        }
        int energyTier = getEnergyTier();
        List<WeightedMaterial> veinMaterials = getRecipeLogic().getVeinMaterials();
        if (veinMaterials == null) {
            return captureDisplayState(true, energyTier, null, 0, 0);
        }
        return captureDisplayState(true, energyTier, veinMaterials, getRecipeLogic().getOreToProduce(),
                getLevel().tickRateManager().tickrate());
    }

    static DisplayState captureDisplayState(boolean formed, int energyTier,
                                            @Nullable List<WeightedMaterial> veinMaterials,
                                            int oreToProduce, float tickRate) {
        float orePerSecond = 0;
        if (formed && veinMaterials != null) {
            orePerSecond = oreToProduce * tickRate;
            orePerSecond = Mth.floor(orePerSecond / FluidDrillLogic.MAX_PROGRESS);
        }
        return new DisplayState(formed, energyTier, veinMaterials, orePerSecond);
    }

    static List<Component> createDisplaySnapshot(DisplayState state) {
        List<Component> text = new ArrayList<>();
        if (state.formed()) {
            long maxVoltage = GTValues.V[state.energyTier()];
            String voltageName = GTValues.VNF[state.energyTier()];
            text.add(Component.translatable("gtpm.multiblock.max_energy_per_tick", maxVoltage, voltageName));
            text.add(Component.translatable("gtpm.multiblock.ore_rig.drilled_ores_list")
                    .withStyle(ChatFormatting.GREEN));

            if (state.veinMaterials() != null) {
                for (WeightedMaterial entry : state.veinMaterials()) {
                    Component oreInfo = entry.material().getLocalizedName().withStyle(ChatFormatting.GREEN);
                    text.add(Component.translatable("gtpm.multiblock.ore_rig.drilled_ore_entry", oreInfo)
                            .withStyle(ChatFormatting.GRAY));
                }
                Component amountInfo = Component.literal(FormattingUtil.formatNumbers(state.orePerSecond()) + "/s")
                        .withStyle(ChatFormatting.BLUE);
                text.add(Component.translatable("gtpm.multiblock.ore_rig.ore_amount", amountInfo)
                        .withStyle(ChatFormatting.GRAY));
            } else {
                Component noOre = Component.translatable("gtpm.multiblock.fluid_rig.no_fluid_in_area")
                        .withStyle(ChatFormatting.RED);
                text.add(Component.translatable("gtpm.multiblock.ore_rig.drilled_ore_entry", noOre)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            Component tooltip = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                    .withStyle(ChatFormatting.GRAY);
            text.add(Component.translatable("gtpm.multiblock.invalid_structure")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))));
        }
        return List.copyOf(text);
    }

    record DisplayState(boolean formed, int energyTier, @Nullable List<WeightedMaterial> veinMaterials,
                        float orePerSecond) {

        DisplayState {
            if (veinMaterials != null) {
                veinMaterials = List.copyOf(veinMaterials);
            }
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        return new BedrockOreMinerFancyPage(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Bedrock Ore Miner UI holder must resolve the opened controller.");
        }
    }

    private final class BedrockOreMinerFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private BedrockOreMinerFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(BedrockOreMinerMachine.this, player,
                    holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Bedrock Ore Miner part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != BedrockOreMinerMachine.this) {
                throw new IllegalStateException("Bedrock Ore Miner page holder no longer resolves its controller.");
            }

            UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            UITemplate.setLDLib2BackgroundTexture(root, GuiTextures.BACKGROUND_INVERSE);

            GTScrollerViewElement screen = new GTScrollerViewElement(4, 4, 182, 117);
            UITemplate.setLDLib2BackgroundTexture(screen, getScreenTexture());
            screen.viewPort.layout(layout -> layout.paddingAll(0));
            UITemplate.setLDLib2BackgroundTexture(screen.viewPort, getScreenTexture());
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
                    BedrockOreMinerMachine.this::addDisplayText)
                    .setMaxWidthLimit(200)
                    .clickHandler(BedrockOreMinerMachine.this::handleDisplayClick));
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel,
                    BedrockOreMinerMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    BedrockOreMinerMachine.this, holder));
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

    public static int getDepletionChance(int tier) {
        if (tier == GTValues.MV)
            return 1;
        if (tier == GTValues.HV)
            return 2;
        if (tier == GTValues.EV)
            return 8;
        return 1;
    }

    public static int getRigMultiplier(int tier) {
        if (tier == GTValues.MV)
            return 1;
        if (tier == GTValues.HV)
            return 4;
        if (tier == GTValues.EV)
            return 16;
        return 1;
    }

    public static Block getCasingState(int tier) {
        if (tier == GTValues.MV)
            return GTBlocks.CASING_STEEL_SOLID.get();
        if (tier == GTValues.HV)
            return GTBlocks.CASING_TITANIUM_STABLE.get();
        if (tier == GTValues.EV)
            return GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get();
        return GTBlocks.CASING_STEEL_SOLID.get();
    }

    @SuppressWarnings("DataFlowIssue")
    public static Block getFrameState(int tier) {
        if (tier == GTValues.MV)
            return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get();
        if (tier == GTValues.HV)
            return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Titanium).get();
        if (tier == GTValues.EV)
            return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.TungstenSteel).get();
        return GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get();
    }

    public static ResourceLocation getBaseTexture(int tier) {
        if (tier == GTValues.MV)
            return GTCEu.id("block/casings/solid/machine_casing_solid_steel");
        if (tier == GTValues.HV)
            return GTCEu.id("block/casings/solid/machine_casing_stable_titanium");
        if (tier == GTValues.EV)
            return GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel");
        return GTCEu.id("block/casings/solid/machine_casing_solid_steel");
    }
}
