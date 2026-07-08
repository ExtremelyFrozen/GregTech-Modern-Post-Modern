package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author h3tr
 * @date 2023/7/15
 * @implNote BlockBreakerMachine
 */
public class BlockBreakerMachine extends TieredEnergyMachine
                                 implements LDLib2FancyUIMachine, IControllable {

    private static final int SLOT_SIZE = 18;
    private static final int TEMPLATE_PADDING = 8;
    private static final int TEMPLATE_SLOT_OFFSET = 4;
    private static final int ENERGY_BAR_WIDTH = 18;
    private static final int ENERGY_BAR_HEIGHT = 60;
    private static final int ENERGY_GROUP_X = 3;
    private static final int ENERGY_GROUP_CHARGER_HEIGHT = 20;
    private static final int ENERGY_GROUP_CHARGER_GAP = 1;
    private static final int PAGE_MIN_WIDTH = 172;
    private static final int PAGE_ENERGY_TEMPLATE_GAP = 4;

    @SaveField
    protected final NotifiableItemStackHandler cache;
    @Getter
    @SaveField
    protected final CustomItemStackHandler chargerInventory;
    @Nullable
    protected TickableSubscription batterySubs, breakerSubs;
    @Nullable
    protected ISubscription energySubs;
    private final int inventorySize;
    @SyncToClient
    private int blockBreakProgress = 0;
    private float currentHardness;
    private final long energyPerTick;
    public final float efficiencyMultiplier;
    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    @Getter
    @SaveField
    @SyncToClient
    private boolean isWorkingEnabled = true;

    public BlockBreakerMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.inventorySize = (tier + 1) * (tier + 1);
        this.cache = attachTrait(createCacheItemHandler());
        this.chargerInventory = createChargerItemHandler();
        this.energyPerTick = GTValues.V[tier - 1];
        this.efficiencyMultiplier = 1.0f - getEfficiencyMultiplier(tier);
        this.autoOutput = attachTrait(AutoOutputTrait.ofItems(cache));
        environmentalExplosionTrait.setEnableEnvironmentalExplosions(false);
    }

    public static float getEfficiencyMultiplier(int tier) {
        float efficiencyMultiplier = 1.0f - 0.2f * (tier - 1.0f);
        // Clamp efficiencyMultiplier
        if (efficiencyMultiplier > 1.0f)
            efficiencyMultiplier = 1.0f;
        else if (efficiencyMultiplier < .1f)
            efficiencyMultiplier = .1f;
        efficiencyMultiplier = 1.0f - efficiencyMultiplier;
        return efficiencyMultiplier;
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

    protected NotifiableItemStackHandler createCacheItemHandler() {
        return new NotifiableItemStackHandler(inventorySize, IO.BOTH, IO.OUT);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            scheduleForNextServerTick(this::updateBreakerSubscription);
            energySubs = energyContainer.addChangedListener(() -> {
                this.updateBatterySubscription();
                this.updateBreakerSubscription();
            });
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

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        chargerInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateBreakerSubscription();
    }

    //////////////////////////////////////
    // ********* Logic **********//
    //////////////////////////////////////

    public void updateBreakerSubscription() {
        if (drainEnergy(true) && !getLevel().getBlockState(getBlockPos().relative(getFrontFacing())).isAir() &&
                isWorkingEnabled) {
            breakerSubs = subscribeServerTick(breakerSubs, this::breakerUpdate);
        } else if (breakerSubs != null) {
            blockBreakProgress = 0;
            breakerSubs.unsubscribe();
            breakerSubs = null;
        }
    }

    public void breakerUpdate() {
        if (this.blockBreakProgress > 0) {
            --this.blockBreakProgress;
            drainEnergy(false);

            if (blockBreakProgress == 0) {
                var pos = getBlockPos().relative(getFrontFacing());
                var blockState = getLevel().getBlockState(pos);
                float hardness = blockState.getBlock().defaultDestroyTime();
                if (hardness >= 0.0f && Math.abs(hardness - currentHardness) < .5f) {
                    var drops = tryDestroyBlockAndGetDrops(pos);
                    for (ItemStack drop : drops) {
                        var remainder = tryFillCache(drop);
                        if (!remainder.isEmpty()) {
                            if (autoOutput.getItemOutputDirection() == null) {
                                Block.popResource(getLevel(), getBlockPos(), remainder);
                            } else {
                                Block.popResource(getLevel(),
                                        getBlockPos().relative(autoOutput.getItemOutputDirection()),
                                        remainder);
                            }
                        }
                    }
                }
                this.currentHardness = 0f;
            }
        }

        if (blockBreakProgress == 0) {
            var pos = getBlockPos().relative(getFrontFacing());
            var blockState = getLevel().getBlockState(pos);
            float hardness = blockState.getBlock().defaultDestroyTime();
            boolean skipBlock = blockState.isAir();
            if (hardness >= 0f && !skipBlock) {
                int ticksPerOneDurability = 5;
                int totalTicksPerBlock = (int) Math.ceil(ticksPerOneDurability * hardness);
                this.blockBreakProgress = (int) Math.ceil(totalTicksPerBlock * this.efficiencyMultiplier);
                this.currentHardness = hardness;
            }
        }

        syncDataHolder.markClientSyncFieldDirty("blockBreakProgress");
        updateBreakerSubscription();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        super.clientTick();
        if (blockBreakProgress > 0) {
            var pos = getBlockPos().relative(getFrontFacing());
            var blockState = getLevel().getBlockState(pos);
            getLevel().addDestroyBlockEffect(pos, blockState);
        }
    }

    private List<ItemStack> tryDestroyBlockAndGetDrops(BlockPos pos) {
        List<ItemStack> drops = Block.getDrops(getLevel().getBlockState(pos),
                (ServerLevel) getLevel(), pos, null, null, ItemStack.EMPTY);
        getLevel().destroyBlock(pos, false);
        return drops;
    }

    private ItemStack tryFillCache(ItemStack stack) {
        for (int i = 0; i < cache.getSlots(); i++) {
            if (cache.insertItemInternal(i, stack, true).getCount() == stack.getCount())
                continue;
            return tryFillCache(cache.insertItemInternal(i, stack, false));
        }
        return stack;
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

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////

    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true))
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false))
            updateBatterySubscription();
    }

    public void setWorkingEnabled(boolean workingEnabled) {
        isWorkingEnabled = workingEnabled;
        syncDataHolder.markClientSyncFieldDirty("isWorkingEnabled");
        updateBreakerSubscription();
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
        energyGroup.addChild(createLDLib2ChargerSlot(getLDLib2ChargerSlotX(),
                ENERGY_BAR_HEIGHT + ENERGY_GROUP_CHARGER_GAP));
        root.addChild(energyGroup);

        UIElement template = UITemplate.setLDLib2Bounds(new UIElement(), getLDLib2CacheTemplateX(),
                getLDLib2CacheTemplateY(), getLDLib2CacheTemplateWidth(), getLDLib2CacheTemplateHeight());
        template.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        int rowSize = getLDLib2CacheRowSize();
        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                template.addChild(createLDLib2CacheSlot(index, TEMPLATE_SLOT_OFFSET + x * SLOT_SIZE,
                        TEMPLATE_SLOT_OFFSET + y * SLOT_SIZE));
            }
        }
        root.addChild(template);
        return root;
    }

    @Override
    public int getLDLib2PageWidth() {
        return Math.max(getLDLib2EnergyGroupWidth() + getLDLib2CacheTemplateWidth() +
                PAGE_ENERGY_TEMPLATE_GAP + TEMPLATE_PADDING, PAGE_MIN_WIDTH);
    }

    @Override
    public int getLDLib2PageHeight() {
        return Math.max(getLDLib2CacheTemplateHeight() + TEMPLATE_PADDING,
                getLDLib2EnergyGroupHeight() + TEMPLATE_PADDING);
    }

    private GTItemSlotElement createLDLib2CacheSlot(int index, int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(cache, index)
                .setCanTakeItems(true)
                .setCanPutItems(false)
                .setBackgroundTexture(GuiTextures.SLOT);
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private GTItemSlotElement createLDLib2ChargerSlot(int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(chargerInventory, 0)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY))
                .setOnAddedTooltips((slotElement, tooltips) -> tooltips.addAll(
                        LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                                GTValues.VNF[getTier()], GTValues.VNF[getTier()])));
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private int getLDLib2EnergyGroupWidth() {
        return ENERGY_BAR_WIDTH;
    }

    private int getLDLib2EnergyGroupHeight() {
        return ENERGY_BAR_HEIGHT + ENERGY_GROUP_CHARGER_HEIGHT;
    }

    private int getLDLib2EnergyGroupY() {
        return (getLDLib2PageHeight() - getLDLib2EnergyGroupHeight()) / 2;
    }

    private int getLDLib2ChargerSlotX() {
        return (ENERGY_BAR_WIDTH - SLOT_SIZE) / 2;
    }

    private int getLDLib2CacheTemplateX() {
        return (getLDLib2PageWidth() - PAGE_ENERGY_TEMPLATE_GAP - getLDLib2CacheTemplateWidth()) / 2 +
                PAGE_ENERGY_TEMPLATE_GAP;
    }

    private int getLDLib2CacheTemplateY() {
        return (getLDLib2PageHeight() - getLDLib2CacheTemplateHeight()) / 2;
    }

    private int getLDLib2CacheTemplateWidth() {
        return getLDLib2CacheRowSize() * SLOT_SIZE + TEMPLATE_PADDING;
    }

    private int getLDLib2CacheTemplateHeight() {
        return getLDLib2CacheRowSize() * SLOT_SIZE + TEMPLATE_PADDING;
    }

    private int getLDLib2CacheRowSize() {
        return (int) Math.sqrt(inventorySize);
    }
}
