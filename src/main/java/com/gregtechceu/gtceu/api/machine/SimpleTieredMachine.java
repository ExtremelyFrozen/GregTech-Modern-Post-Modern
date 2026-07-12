package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.EditableUI;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.GhostCircuitSlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2AutoOutputFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.google.common.collect.Tables;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

/**
 * All simple single machines are implemented here.
 */
public class SimpleTieredMachine extends WorkableTieredMachine
                                 implements LDLib2RecipeFancyUIMachine, IHasCircuitSlot {

    @Getter
    @SaveField
    protected final CustomItemStackHandler chargerInventory;
    @Getter
    @SaveField
    protected final NotifiableItemStackHandler circuitInventory;
    @Nullable
    protected TickableSubscription batterySubs;
    @Nullable
    protected ISubscription energySubs;
    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public SimpleTieredMachine(BlockEntityCreationInfo info, int tier, Int2IntFunction tankScalingFunction) {
        super(info, tier, tankScalingFunction);

        this.autoOutput = attachTrait(new AutoOutputTrait(List.of(exportItems), List.of(exportFluids)));

        this.chargerInventory = new CustomItemStackHandler() {

            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        chargerInventory.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));

        this.circuitInventory = attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.NONE)
                .shouldDropInventoryInWorld(!ConfigHolder.INSTANCE.machines.ghostCircuit)
                .setFilter(IntCircuitBehaviour::isIntegratedCircuit));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updateBatterySubscription();
            energySubs = energyContainer.addChangedListener(this::updateBatterySubscription);
            chargerInventory.setOnContentsChanged(this::updateBatterySubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (energySubs != null) {
            energySubs.unsubscribe();
            energySubs = null;
        }
    }

    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true)) {
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        } else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false)) {
            updateBatterySubscription();
        }
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////
    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        chargerInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    /// //////////////////////////////////
    // ****** RECIPE LOGIC *******//
    /// //////////////////////////////////

    @Override
    public long getDisplayRecipeVoltage() {
        return GTValues.V[this.tier];
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public SimpleTieredMachine getLDLib2RecipeMachine() {
        return this;
    }

    @Override
    public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
        configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                this, configuratorPanel.getHolder()));
        LDLib2AutoOutputFancyConfigurator.attachConfigurators(configuratorPanel, autoOutput);
        if (isCircuitSlotEnabled()) {
            configuratorPanel.attachConfigurators(new LDLib2CircuitFancyConfigurator(
                    this, configuratorPanel.getHolder()));
        }
    }

    @Override
    public void attachLDLib2RecipePageElements(UIElement root, WorkableTieredMachine machine,
                                               LDLib2RecipeUISize recipeSize) {
        GTItemSlotElement batterySlot = createLDLib2BatterySlot();
        UITemplate.setLDLib2Bounds(batterySlot, recipeSize.width() / 2 - 9,
                getLDLib2PageHeight() - 18, 18, 18);
        root.addChild(batterySlot);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, recipeType) -> new EditableMachineUI("simple", path, () -> {
                WidgetGroup template = recipeType.getRecipeUI().createEditableUITemplate(false, false).createDefault();
                SlotWidget batterySlot = createBatterySlot().createDefault();
                WidgetGroup group = new WidgetGroup(0, 0, template.getSize().width,
                        Math.max(template.getSize().height, 78));
                template.setSelfPosition(new Position(0, (group.getSize().height - template.getSize().height) / 2));
                batterySlot.setSelfPosition(new Position(group.getSize().width / 2 - 9, group.getSize().height - 18));
                group.addWidget(batterySlot);
                group.addWidget(template);

                // TODO fix this.
                // if (ConfigHolder.INSTANCE.machines.ghostCircuit) {
                // SlotWidget circuitSlot = createCircuitConfigurator().createDefault();
                // circuitSlot.setSelfPosition(new Position(120, 62));
                // group.addWidget(circuitSlot);
                // }

                return group;
            }, (template, machine) -> {
                if (machine instanceof SimpleTieredMachine tieredMachine) {
                    var storages = Tables.newCustomTable(new EnumMap<>(IO.class),
                            LinkedHashMap<RecipeCapability<?>, Object>::new);
                    storages.put(IO.IN, ItemRecipeCapability.CAP, tieredMachine.importItems.storage);
                    storages.put(IO.OUT, ItemRecipeCapability.CAP, tieredMachine.exportItems.storage);
                    storages.put(IO.IN, FluidRecipeCapability.CAP, tieredMachine.importFluids);
                    storages.put(IO.OUT, FluidRecipeCapability.CAP, tieredMachine.exportFluids);
                    storages.put(IO.IN, CWURecipeCapability.CAP, tieredMachine.importComputation);
                    storages.put(IO.OUT, CWURecipeCapability.CAP, tieredMachine.exportComputation);

                    tieredMachine.getRecipeType().getRecipeUI().createEditableUITemplate(false, false).setupUI(template,
                            new GTRecipeTypeUI.RecipeHolder(tieredMachine.recipeLogic::getProgressPercent,
                                    storages,
                                    DataComponentMap.EMPTY,
                                    Collections.emptyList(),
                                    false, false));
                    createBatterySlot().setupUI(template, tieredMachine);
                    // createCircuitConfigurator().setupUI(template, tieredMachine);
                }
            }));

    /**
     * Create a battery slot widget.
     */
    protected static EditableUI<SlotWidget, SimpleTieredMachine> createBatterySlot() {
        return new EditableUI<>("battery_slot", SlotWidget.class, () -> {
            var slotWidget = new SlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setHandlerSlot(machine.chargerInventory, 0);
            slotWidget.setCanPutItems(true);
            slotWidget.setCanTakeItems(true);
            slotWidget.setHoverTooltips(LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                    GTValues.VNF[machine.getTier()], GTValues.VNF[machine.getTier()]).toArray(Component[]::new));
        });
    }

    /**
     * Create an LDLib2 battery slot element.
     */
    protected GTItemSlotElement createLDLib2BatterySlot() {
        var slot = new GTItemSlotElement(chargerInventory, 0)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY))
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setOnAddedTooltips((slotElement, tooltips) -> tooltips.addAll(
                        LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                                GTValues.VNF[tier], GTValues.VNF[tier])));
        return UITemplate.setLDLib2Bounds(slot, 0, 0, 18, 18);
    }

    /**
     * Create a ghost circuit slot widget.
     */
    protected static EditableUI<GhostCircuitSlotWidget, SimpleTieredMachine> createCircuitConfigurator() {
        return new EditableUI<>("circuit_configurator", GhostCircuitSlotWidget.class, () -> {
            var slotWidget = new GhostCircuitSlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setCircuitInventory(machine.circuitInventory);
            slotWidget.setCanPutItems(false);
            slotWidget.setCanTakeItems(false);
            slotWidget.setHoverTooltips(
                    LangHandler.getMultiLang("gtpm.gui.configurator_slot.tooltip").toArray(Component[]::new));
        });
    }

    // Method provided to override
    protected IGuiTexture getCircuitSlotOverlay() {
        return GuiTextures.INT_CIRCUIT_OVERLAY;
    }
}
