package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.MachineUIXmlTemplates;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2AutoOutputFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.ISubscription;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    @SuppressWarnings("UnstableApiUsage")
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, recipeType) -> new EditableMachineUI("simple", path,
                    () -> MachineUIXmlTemplates.createSimpleMachineXml(
                            recipeType.getRecipeUI().createLDLib2TemplateDocument()),
                    (root, machine) -> {
                        if (!(machine instanceof SimpleTieredMachine tieredMachine)) {
                            throw new IllegalArgumentException("Simple machine XML cannot bind " +
                                    machine.getClass().getName());
                        }
                        tieredMachine.bindLDLib2RecipeElements(root, tieredMachine);
                        tieredMachine.bindLDLib2BatterySlot(MachineUIXmlTemplates.requireElement(root,
                                MachineUIXmlTemplates.BATTERY_SLOT_ID, GTItemSlotElement.class));
                    }));

    /**
     * Binds a parsed LDLib2 battery slot to this machine's charger inventory.
     */
    protected GTItemSlotElement bindLDLib2BatterySlot(GTItemSlotElement slot) {
        return slot.bind(chargerInventory, 0)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setOnAddedTooltips((slotElement, tooltips) -> tooltips.addAll(
                        LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                                GTValues.VNF[tier], GTValues.VNF[tier])));
    }

    /**
     * Create an LDLib2 battery slot element.
     */
    protected GTItemSlotElement createLDLib2BatterySlot() {
        var slot = bindLDLib2BatterySlot(new GTItemSlotElement())
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY));
        return UITemplate.setLDLib2Bounds(slot, 0, 0, 18, 18);
    }
}
