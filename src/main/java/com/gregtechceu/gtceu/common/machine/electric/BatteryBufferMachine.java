package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.energy.IEnergyStorage;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BatteryBufferMachine extends TieredEnergyMachine
                                  implements IControllable, LDLib2FancyUIMachine, IMonitorComponent {

    public static final long AMPS_PER_BATTERY_NORMAL = 2L;
    public static final long AMPS_PER_BATTERY_CHARGER = 4L;
    private static final int SLOT_SIZE = 18;
    private static final int TEMPLATE_PADDING = 8;
    private static final int TEMPLATE_SLOT_OFFSET = 4;
    private static final int ENERGY_BAR_WIDTH = 18;
    private static final int ENERGY_BAR_HEIGHT = 60;
    private static final int ENERGY_BAR_X = 3;
    private static final int PAGE_MIN_WIDTH = 172;
    private static final int PAGE_ENERGY_TEMPLATE_GAP = 4;

    public enum State implements StringRepresentable {

        IDLE("idle"),
        RUNNING("running"),
        FINISHED("finished");

        @Getter
        private final String serializedName;

        State(String name) {
            this.serializedName = name;
        }
    }

    public static final EnumProperty<State> STATE_PROPERTY = GTMachineModelProperties.CHARGER_STATE;

    @SaveField
    @Getter
    private boolean isWorkingEnabled;
    @Getter
    private final int inventorySize;
    @Getter
    @SaveField
    protected final CustomItemStackHandler batteryInventory;
    private final boolean chargerMode;

    @Getter
    @SyncToClient
    @RerenderOnChanged
    private State state;

    public BatteryBufferMachine(BlockEntityCreationInfo info, int tier, int inventorySize) {
        this(info, tier, inventorySize, AMPS_PER_BATTERY_NORMAL, inventorySize);
    }

    public BatteryBufferMachine(BlockEntityCreationInfo info, int tier, int inventorySize, long inputAmpsPerItem,
                                long outputAmps) {
        super(info, tier, new EnergyBatteryTrait(tier, inventorySize, inputAmpsPerItem, outputAmps));
        this.isWorkingEnabled = true;
        this.inventorySize = inventorySize;
        this.chargerMode = outputAmps == 0;
        this.batteryInventory = createBatteryInventory();
        this.batteryInventory.setOnContentsChanged(energyContainer::checkOutputSubscription);
        this.state = State.IDLE;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected CustomItemStackHandler createBatteryInventory() {
        var handler = new CustomItemStackHandler(this.inventorySize) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        handler.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));
        return handler;
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                getLDLib2PageWidth(), getLDLib2PageHeight());

        UIElement energyBar = createLDLib2EnergyBar();
        UITemplate.setLDLib2Bounds(energyBar, ENERGY_BAR_X,
                (getLDLib2PageHeight() - ENERGY_BAR_HEIGHT) / 2, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT);
        root.addChild(energyBar);

        UIElement template = UITemplate.setLDLib2Bounds(new UIElement(),
                getLDLib2BatteryTemplateX(), getLDLib2BatteryTemplateY(),
                getLDLib2BatteryTemplateWidth(), getLDLib2BatteryTemplateHeight());
        template.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        int index = 0;
        for (int y = 0; y < getLDLib2BatteryColSize(); y++) {
            for (int x = 0; x < getLDLib2BatteryRowSize(); x++) {
                template.addChild(createLDLib2BatterySlot(index++, TEMPLATE_SLOT_OFFSET + x * SLOT_SIZE,
                        TEMPLATE_SLOT_OFFSET + y * SLOT_SIZE));
            }
        }

        root.addChild(template);
        return root;
    }

    @Override
    public int getLDLib2PageWidth() {
        return Math.max(ENERGY_BAR_WIDTH + getLDLib2BatteryTemplateWidth() + PAGE_ENERGY_TEMPLATE_GAP +
                TEMPLATE_PADDING, PAGE_MIN_WIDTH);
    }

    @Override
    public int getLDLib2PageHeight() {
        return Math.max(getLDLib2BatteryTemplateHeight() + TEMPLATE_PADDING, ENERGY_BAR_HEIGHT + TEMPLATE_PADDING);
    }

    private GTItemSlotElement createLDLib2BatterySlot(int index, int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(batteryInventory, index)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT,
                        chargerMode ? GuiTextures.CHARGER_OVERLAY : GuiTextures.BATTERY_OVERLAY));
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private int getLDLib2BatteryTemplateX() {
        return (getLDLib2PageWidth() - ENERGY_BAR_WIDTH - PAGE_ENERGY_TEMPLATE_GAP -
                getLDLib2BatteryTemplateWidth()) / 2 + 2 + ENERGY_BAR_WIDTH + 2;
    }

    private int getLDLib2BatteryTemplateY() {
        return (getLDLib2PageHeight() - getLDLib2BatteryTemplateHeight()) / 2;
    }

    private int getLDLib2BatteryTemplateWidth() {
        return SLOT_SIZE * getLDLib2BatteryRowSize() + TEMPLATE_PADDING;
    }

    private int getLDLib2BatteryTemplateHeight() {
        return SLOT_SIZE * getLDLib2BatteryColSize() + TEMPLATE_PADDING;
    }

    private int getLDLib2BatteryRowSize() {
        if (inventorySize == 8) {
            return 4;
        }
        return (int) Math.sqrt(inventorySize);
    }

    private int getLDLib2BatteryColSize() {
        if (inventorySize == 8) {
            return 2;
        }
        return getLDLib2BatteryRowSize();
    }

    //////////////////////////////////////
    // ****** Battery Logic ******//
    //////////////////////////////////////

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        isWorkingEnabled = workingEnabled;
        energyContainer.checkOutputSubscription();
    }

    private List<Object> getNonFullBatteries() {
        List<Object> batteries = new ArrayList<>();
        for (int i = 0; i < batteryInventory.getSlots(); i++) {
            var batteryStack = batteryInventory.getStackInSlot(i);
            var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
            if (electricItem != null) {
                if (electricItem.getCharge() < electricItem.getMaxCharge()) {
                    batteries.add(electricItem);
                }
            } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                IEnergyStorage energyStorage = GTCapabilityHelper.getForgeEnergyItem(batteryStack);
                if (energyStorage != null) {
                    if (energyStorage.getEnergyStored() < energyStorage.getMaxEnergyStored()) {
                        batteries.add(energyStorage);
                    }
                }
            }
        }
        return batteries;
    }

    private List<IElectricItem> getNonEmptyBatteries() {
        List<IElectricItem> batteries = new ArrayList<>();
        for (int i = 0; i < batteryInventory.getSlots(); i++) {
            var batteryStack = batteryInventory.getStackInSlot(i);
            var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
            if (electricItem != null) {
                if (electricItem.canProvideChargeExternally() && electricItem.getCharge() > 0) {
                    batteries.add(electricItem);
                }
            }
        }
        return batteries;
    }

    private List<Object> getAllBatteries() {
        List<Object> batteries = new ArrayList<>();
        for (int i = 0; i < batteryInventory.getSlots(); i++) {
            var batteryStack = batteryInventory.getStackInSlot(i);
            var electricItem = GTCapabilityHelper.getElectricItem(batteryStack);
            if (electricItem != null) {
                batteries.add(electricItem);
            } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
                IEnergyStorage energyStorage = GTCapabilityHelper.getForgeEnergyItem(batteryStack);
                if (energyStorage != null) {
                    batteries.add(energyStorage);
                }
            }
        }
        return batteries;
    }

    private void changeState(State newState) {
        if (state == newState) return;
        state = newState;
        syncDataHolder.markClientSyncFieldDirty("state");
        MachineRenderState renderState = getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.CHARGER_STATE)) {
            setRenderState(renderState.setValue(GTMachineModelProperties.CHARGER_STATE, newState));
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        batteryInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.BUTTON_CHECK; // temporary
    }

    protected static class EnergyBatteryTrait extends NotifiableEnergyContainer {

        private final int tier;
        private final long inputAmpsPerItem;
        private final long outputAmps;

        protected EnergyBatteryTrait(int tier, int inventorySize, long inputAmpsPerItem, long outputAmps) {
            super(GTValues.V[tier] * inventorySize * 32L, GTValues.V[tier],
                    inventorySize * inputAmpsPerItem, outputAmps == 0 ? 0 : GTValues.V[tier], outputAmps);
            this.tier = tier;
            this.inputAmpsPerItem = inputAmpsPerItem;
            this.outputAmps = outputAmps;
            this.setSideInputCondition(
                    side -> (outputAmps == 0 || side != getMachine().getFrontFacing()) &&
                            getMachine().isWorkingEnabled());
            this.setSideOutputCondition(
                    side -> outputAmps > 0 && side == getMachine().getFrontFacing() && getMachine().isWorkingEnabled());
        }

        @Override
        public BatteryBufferMachine getMachine() {
            return (BatteryBufferMachine) super.getMachine();
        }

        @Override
        protected List<Class<?>> validMachineClasses() {
            return List.of(BatteryBufferMachine.class);
        }

        @Override
        public void checkOutputSubscription() {
            updateChargerState();
            if (getMachine().isWorkingEnabled()) {
                super.checkOutputSubscription();
            } else if (outputSubs != null) {
                outputSubs.unsubscribe();
                outputSubs = null;
            }
        }

        private void updateChargerState() {
            if (outputAmps > 0) return;
            long capacity = getEnergyCapacity();
            if (capacity == 0) {
                getMachine().changeState(State.IDLE);
            } else if (capacity == getEnergyStored()) {
                getMachine().changeState(State.FINISHED);
            }
        }

        @Override
        public void serverTick() {
            if (outputAmps <= 0) {
                return;
            }
            var outFacing = getMachine().getFrontFacing();
            var energyContainer = GTCapabilityHelper.getEnergyContainer(getLevel(),
                    getBlockPos().relative(outFacing),
                    outFacing.getOpposite());
            if (energyContainer == null) {
                return;
            }

            var voltage = getOutputVoltage();
            var batteries = getMachine().getNonEmptyBatteries();
            if (!batteries.isEmpty()) {
                // Prioritize as many packets as available of energy created
                long internalAmps = Math.abs(Math.min(0, getInternalStorage() / voltage));
                long genAmps = Math.max(0, Math.min(outputAmps, batteries.size()) - internalAmps);
                long outAmps = 0L;

                if (genAmps > 0) {
                    outAmps = energyContainer.acceptEnergyFromNetwork(outFacing.getOpposite(), voltage, genAmps);
                    if (outAmps == 0 && internalAmps == 0)
                        return;
                }

                long energy = (outAmps + internalAmps) * voltage;
                long distributed = energy / batteries.size();

                boolean changed = false;
                for (IElectricItem electricItem : batteries) {
                    var charged = electricItem.discharge(distributed, tier, false, true, false);
                    if (charged > 0) {
                        changed = true;
                    }
                    energy -= charged;
                    energyOutputPerSec += charged;
                }

                if (changed) {
                    checkOutputSubscription();
                }

                // Subtract energy created out of thin air from the buffer
                setEnergyStored(getInternalStorage() + internalAmps * voltage - energy);
            }
        }

        @Override
        public long acceptEnergyFromNetwork(@Nullable Direction side, long voltage, long amperage) {
            var latestTimeStamp = getMachine().getOffsetTimer();
            if (lastTimeStamp < latestTimeStamp) {
                amps = 0;
                lastTimeStamp = latestTimeStamp;
            }
            if (amperage <= 0 || voltage <= 0)
                return 0;

            var batteries = getMachine().getNonFullBatteries();
            var leftAmps = batteries.size() * inputAmpsPerItem - amps;
            var usedAmps = Math.min(leftAmps, amperage);
            if (leftAmps <= 0)
                return 0;

            if (side == null || inputsEnergy(side)) {
                if (voltage > getInputVoltage()) {
                    GTUtil.doExplosion(getLevel(), getBlockPos(), GTUtil.getExplosionPower(voltage));
                    return usedAmps;
                }

                // Prioritizes as many packets as available from the buffer
                long internalAmps = Math.min(leftAmps, Math.max(0, getInternalStorage() / voltage));

                usedAmps = Math.min(usedAmps, leftAmps - internalAmps);
                amps += usedAmps;

                long energy = (usedAmps + internalAmps) * voltage;
                long distributed = energy / batteries.size();

                boolean changed = false;
                for (Object item : batteries) {
                    long charged = 0;
                    if (item instanceof IElectricItem electricItem) {
                        charged = electricItem.charge(
                                Math.min(distributed, GTValues.V[electricItem.getTier()] * inputAmpsPerItem), tier,
                                true, false);
                    } else if (item instanceof IEnergyStorage energyStorage) {
                        charged = FeCompat.insertEu(energyStorage,
                                Math.min(distributed, GTValues.V[tier] * inputAmpsPerItem), false);
                    }
                    if (charged > 0) {
                        changed = true;
                    }
                    energy -= charged;
                    energyInputPerSec += charged;
                }

                if (changed) {
                    getMachine().changeState(State.RUNNING);
                    checkOutputSubscription();
                }

                // Remove energy used and then transfer overflow energy into the internal buffer
                setEnergyStored(getInternalStorage() - internalAmps * voltage + energy);
                return usedAmps;
            }
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            long energyCapacity = 0L;
            for (Object battery : getMachine().getAllBatteries()) {
                if (battery instanceof IElectricItem electricItem) {
                    energyCapacity += electricItem.getMaxCharge();
                } else if (battery instanceof IEnergyStorage energyStorage) {
                    energyCapacity += FeCompat.toEu(energyStorage.getMaxEnergyStored(), FeCompat.ratio(false));
                }
            }
            return energyCapacity;
        }

        @Override
        public long getEnergyStored() {
            long energyStored = 0L;
            for (Object battery : getMachine().getAllBatteries()) {
                if (battery instanceof IElectricItem electricItem) {
                    energyStored += electricItem.getCharge();
                } else if (battery instanceof IEnergyStorage energyStorage) {
                    energyStored += FeCompat.toEu(energyStorage.getEnergyStored(), FeCompat.ratio(false));
                }
            }
            return energyStored;
        }

        private long getInternalStorage() {
            return energyStored;
        }
    }
}
