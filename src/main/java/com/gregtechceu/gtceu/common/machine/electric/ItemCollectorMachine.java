package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author h3tr
 * @date 2023/7/13
 * @implNote FisherMachine
 */
public class ItemCollectorMachine extends TieredEnergyMachine
                                  implements LDLib2FancyUIMachine, IWorkable {

    private static final int MIN_RANGE = 1;
    private static final int SLOT_SIZE = 18;
    private static final int TEMPLATE_PADDING = 8;
    private static final int TEMPLATE_CONTROL_COLUMN_WIDTH = 25;
    private static final int OUTPUT_SLOT_X_OFFSET = 24;
    private static final int TEMPLATE_SLOT_OFFSET = 4;
    private static final int FILTER_SLOT_X = 4;
    private static final int RANGE_INPUT_WIDTH = 80;
    private static final int RANGE_INPUT_HEIGHT = 20;
    private static final int RANGE_INPUT_Y = 5;
    private static final int ENERGY_GROUP_WIDTH = 18;
    private static final int ENERGY_GROUP_HEIGHT = 80;
    private static final int ENERGY_GROUP_X = 3;
    private static final int BATTERY_SLOT_X = 0;
    private static final int BATTERY_SLOT_Y = 61;
    private static final int PAGE_MIN_WIDTH = 172;
    private static final int PAGE_ENERGY_TEMPLATE_GAP = 4;
    private static final int PAGE_RANGE_INPUT_RESERVED_HEIGHT = 30;
    private static final int TEMPLATE_PAGE_Y_OFFSET = 15;
    @Getter
    private static final int[] INVENTORY_SIZES = { 4, 9, 16, 25, 25 };
    private static final double MOTION_MULTIPLIER = 0.04;
    private static final int BASE_EU_CONSUMPTION = 6;

    @SaveField
    protected final NotifiableItemStackHandler output;

    @Getter
    @SaveField
    protected final CustomItemStackHandler chargerInventory;
    @SaveField
    protected final CustomItemStackHandler filterInventory;

    @Nullable
    protected TickableSubscription batterySubs, collectionSubs;
    @Nullable
    protected ISubscription energySubs;
    private final long energyPerTick;

    private final int inventorySize;

    private @Nullable AABB aabb;

    @SaveField
    @Getter
    @SyncBoth
    private int range;

    private boolean rangeDirty = false;

    private final int maxRange;

    @Getter
    @SaveField
    @SyncToClient
    private boolean isWorkingEnabled = true;

    @SyncToClient
    @SaveField
    @Getter
    @RerenderOnChanged
    private boolean active = false;

    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public ItemCollectorMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.inventorySize = INVENTORY_SIZES[Mth.clamp(getTier(), 0, INVENTORY_SIZES.length - 1)];
        this.energyPerTick = (long) BASE_EU_CONSUMPTION * (1L << (tier - 1));
        this.output = attachTrait(createOutputItemHandler());
        this.chargerInventory = createChargerItemHandler();
        this.filterInventory = createFilterItemHandler();
        environmentalExplosionTrait.setEnableEnvironmentalExplosions(false);
        this.autoOutput = attachTrait(AutoOutputTrait.ofItems(output));
        maxRange = (int) Math.pow(2, tier + 2);
        range = maxRange;
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected CustomItemStackHandler createChargerItemHandler() {
        var handler = new CustomItemStackHandler();
        handler.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));
        return handler;
    }

    protected CustomItemStackHandler createFilterItemHandler() {
        var handler = new CustomItemStackHandler();
        handler.setFilter(
                item -> item.is(GTItems.ITEM_FILTER.asItem()) || item.is(GTItems.TAG_FILTER.asItem()));
        return handler;
    }

    protected NotifiableItemStackHandler createOutputItemHandler() {
        return new NotifiableItemStackHandler(inventorySize, IO.BOTH, IO.OUT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) return;

        scheduleForNextServerTick(this::updateCollectionSubscription);

        energySubs = energyContainer.addChangedListener(() -> {
            this.updateBatterySubscription();
            this.updateCollectionSubscription();
        });
        chargerInventory.setOnContentsChanged(this::updateBatterySubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (energySubs != null) {
            energySubs.unsubscribe();
            energySubs = null;
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        chargerInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    //////////////////////////////////////
    // ********* Logic **********//
    //////////////////////////////////////

    public void updateCollectionSubscription() {
        if (drainEnergy(true) && isWorkingEnabled) {
            collectionSubs = subscribeServerTick(collectionSubs, this::update);
            setActive(true);
            active = true;
        } else if (collectionSubs != null) {
            collectionSubs.unsubscribe();
            collectionSubs = null;
            active = false;
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        setRenderState(getRenderState().setValue(GTMachineModelProperties.IS_ACTIVE, active));
    }

    public void update() {
        if (drainEnergy(false)) {
            if (aabb == null || rangeDirty) {
                rangeDirty = false;
                BlockPos pos1 = getBlockPos().offset(-range, 0, -range);
                BlockPos pos2 = getBlockPos().offset(range, 2, range);
                this.aabb = AABB.of(BoundingBox.fromCorners(pos1, pos2));
            }
            moveItemsInRange();
            updateCollectionSubscription();
        }
    }

    public void moveItemsInRange() {
        ItemFilter filter = null;
        if (!filterInventory.getStackInSlot(0).isEmpty())
            filter = ItemFilter.loadFilter(filterInventory.getStackInSlot(0));
        BlockPos centerPos = self().getBlockPos().above();

        List<ItemEntity> itemEntities = getLevel().getEntitiesOfClass(ItemEntity.class, aabb);
        for (ItemEntity itemEntity : itemEntities) {
            if (!itemEntity.isAlive()) continue;
            if (filter != null && !filter.test(itemEntity.getItem())) continue;
            double distX = (centerPos.getX() + 0.5) - itemEntity.position().x;
            double distZ = (centerPos.getZ() + 0.5) - itemEntity.position().z;
            double dist = Math.sqrt(Math.pow(distX, 2) + Math.pow(distZ, 2));
            if (dist >= 0.7f) {
                // ItemEntity.INFINITE_PICKUP_DELAY = 32767
                if (itemEntity.pickupDelay == 32767) continue;
                double dirX = distX / dist;
                double dirZ = distZ / dist;
                Vec3 delta = itemEntity.getDeltaMovement();
                itemEntity.setDeltaMovement(dirX * MOTION_MULTIPLIER * tier, delta.y, dirZ * MOTION_MULTIPLIER * tier);
                itemEntity.setPickUpDelay(1);
            } else {
                ItemStack stack = itemEntity.getItem();
                if (!canFillOutput(stack)) continue;

                ItemStack remainder = fillOutput(stack);
                if (remainder.isEmpty())
                    itemEntity.kill();
                else if (stack.getCount() > remainder.getCount())
                    itemEntity.setItem(remainder);
            }
        }
    }

    private boolean canFillOutput(ItemStack stack) {
        for (int i = 0; i < output.getSlots(); i++) {
            if (output.insertItemInternal(i, stack, true).getCount() < stack.getCount())
                return true;
        }

        return false;
    }

    private ItemStack fillOutput(ItemStack stack) {
        for (int i = 0; i < output.getSlots(); i++) {
            if (output.insertItemInternal(i, stack, true).getCount() < stack.getCount())
                return output.insertItemInternal(i, stack, false);
        }

        return ItemStack.EMPTY;
    }

    public boolean drainEnergy(boolean simulate) {
        long resultEnergy = energyContainer.getEnergyStored() - energyPerTick;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.removeEnergy(energyPerTick);
            return true;
        }
        return false;
    }

    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true))
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false)) {
            updateBatterySubscription();
        }
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public int getMaxProgress() {
        return 0;
    }

    public void setRange(int range) {
        int normalizedRange = normalizeRange(range);
        if (this.range == normalizedRange) {
            return;
        }
        this.range = normalizedRange;
        invalidateCollectionBounds();
    }

    @ServerFieldNormalizer(fieldName = "range")
    private int normalizeRange(int range) {
        if (range < MIN_RANGE || range > maxRange) {
            throw new IllegalArgumentException("Item collector range must be between " + MIN_RANGE + " and " +
                    maxRange + ": " + range);
        }
        return range;
    }

    @ServerFieldChangeListener(fieldName = "range")
    private void onRangeChanged(int oldRange, int newRange) {
        invalidateCollectionBounds();
    }

    protected void invalidateCollectionBounds() {
        rangeDirty = true;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        isWorkingEnabled = workingEnabled;
        updateCollectionSubscription();
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                getLDLib2PageWidth(), getLDLib2PageHeight());

        UIElement energyGroup = UITemplate.setLDLib2Bounds(new UIElement(), ENERGY_GROUP_X, getLDLib2EnergyGroupY(),
                getLDLib2EnergyGroupWidth(), getLDLib2EnergyGroupHeight());
        energyGroup.addChild(createLDLib2EnergyBar());
        energyGroup.addChild(createLDLib2BatterySlot(BATTERY_SLOT_X, BATTERY_SLOT_Y));
        root.addChild(energyGroup);

        UIElement template = UITemplate.setLDLib2Bounds(new UIElement(), getLDLib2TemplateX(), getLDLib2TemplateY(),
                getLDLib2TemplateWidth(), getLDLib2TemplateHeight());
        template.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        int rowSize = getLDLib2TemplateRowSize();
        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                template.addChild(createLDLib2OutputSlot(index, OUTPUT_SLOT_X_OFFSET + x * SLOT_SIZE,
                        TEMPLATE_SLOT_OFFSET + y * SLOT_SIZE));
            }
        }

        template.addChild(createLDLib2FilterSlot(FILTER_SLOT_X, getLDLib2FilterSlotY()));
        root.addChild(template);
        root.addChild(createLDLib2RangeInput(getLDLib2RangeInputX(), RANGE_INPUT_Y));
        return root;
    }

    @Override
    public int getLDLib2PageWidth() {
        return Math.max(getLDLib2EnergyGroupWidth() + getLDLib2TemplateWidth() +
                PAGE_ENERGY_TEMPLATE_GAP + TEMPLATE_PADDING, PAGE_MIN_WIDTH);
    }

    @Override
    public int getLDLib2PageHeight() {
        return Math.max(getLDLib2TemplateHeight() + TEMPLATE_PADDING + PAGE_RANGE_INPUT_RESERVED_HEIGHT,
                getLDLib2EnergyGroupHeight() + TEMPLATE_PADDING);
    }

    private GTItemSlotElement createLDLib2OutputSlot(int index, int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(output.storage, index)
                .setCanTakeItems(true)
                .setCanPutItems(false)
                .setBackgroundTexture(GuiTextures.SLOT);
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private GTItemSlotElement createLDLib2FilterSlot(int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(filterInventory, 0)
                .setCanTakeItems(true)
                .setCanPutItems(true)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.FILTER_SLOT_OVERLAY));
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private GTItemSlotElement createLDLib2BatterySlot(int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(chargerInventory, 0)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY))
                .setOnAddedTooltips((slotElement, tooltips) -> tooltips.addAll(
                        LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                                GTValues.VNF[getTier()], GTValues.VNF[getTier()])));
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private GTIntInputElement createLDLib2RangeInput(int x, int y) {
        return new GTIntInputElement(x, y, RANGE_INPUT_WIDTH, RANGE_INPUT_HEIGHT, this::getRange,
                this::setLDLib2Range)
                .setMin(MIN_RANGE)
                .setMax(maxRange);
    }

    private void setLDLib2Range(int value) {
        setRange(value);
        sendServerSyncChanges();
    }

    private int getLDLib2EnergyGroupWidth() {
        return ENERGY_GROUP_WIDTH;
    }

    private int getLDLib2EnergyGroupHeight() {
        return ENERGY_GROUP_HEIGHT;
    }

    private int getLDLib2EnergyGroupY() {
        return (getLDLib2PageHeight() - getLDLib2EnergyGroupHeight()) / 2;
    }

    private int getLDLib2TemplateX() {
        return (getLDLib2PageWidth() - getLDLib2EnergyGroupWidth() - PAGE_ENERGY_TEMPLATE_GAP -
                getLDLib2TemplateWidth()) / 2 + 2 + getLDLib2EnergyGroupWidth() + 2;
    }

    private int getLDLib2TemplateY() {
        return (getLDLib2PageHeight() - getLDLib2TemplateHeight()) / 2 + TEMPLATE_PAGE_Y_OFFSET;
    }

    private int getLDLib2TemplateWidth() {
        return getLDLib2TemplateRowSize() * SLOT_SIZE + TEMPLATE_PADDING + TEMPLATE_CONTROL_COLUMN_WIDTH;
    }

    private int getLDLib2TemplateHeight() {
        return getLDLib2TemplateRowSize() * SLOT_SIZE + TEMPLATE_PADDING;
    }

    private int getLDLib2TemplateRowSize() {
        return (int) Math.sqrt(inventorySize);
    }

    private int getLDLib2FilterSlotY() {
        return (getLDLib2TemplateHeight() - SLOT_SIZE) / 2;
    }

    private int getLDLib2RangeInputX() {
        return (getLDLib2PageWidth() - RANGE_INPUT_WIDTH) / 2;
    }
}
