package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.*;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.behavior.TurbineRotorBehaviour;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RotorHolderPartMachine extends TieredPartMachine
                                    implements LDLib2MachineUIProvider, LDLib2FancyPartUIProvider {

    public static final int SPEED_INCREMENT = 1;
    public static final int SPEED_DECREMENT = 3;
    private static final int PAGE_WIDTH = 34;
    private static final int PAGE_HEIGHT = 34;
    private static final int ROTOR_SLOT_LOCKED_OVERLAY_COLOR = 0x80404040;
    @SaveField
    public final NotifiableItemStackHandler inventory;
    @Getter
    public final int maxRotorHolderSpeed;
    @Getter
    @SaveField
    @SyncToClient
    public int rotorSpeed;
    @SaveField
    @SyncToClient
    public Material rotorMaterial = GTMaterials.NULL; // 0 - no rotor
    @Nullable
    protected TickableSubscription rotorSpeedSubs;
    @Nullable
    protected ISubscription rotorInvSubs;

    public RotorHolderPartMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.inventory = attachTrait(new NotifiableItemStackHandler(1, IO.NONE, IO.BOTH));
        this.maxRotorHolderSpeed = 2000 + 1000 * tier;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index >= 2) {
            return getRotorMaterial().getLayerARGB(index - 2);
        } else if (index <= -103) {
            return getRotorMaterial().getLayerARGB(index + 2);
        }
        return super.tintColor(index);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updateRotorSubscription();
            rotorInvSubs = this.inventory.addChangedListener(this::onRotorInventoryChanged);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (rotorInvSubs != null) {
            rotorInvSubs.unsubscribe();
        }
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }

    /**
     * @return the base efficiency of the rotor holder in %
     */
    static int getBaseEfficiency() {
        return 100;
    }

    //////////////////////////////////////
    // ****** Rotor Holder ******//
    //////////////////////////////////////

    public Material getRotorMaterial() {
        // handles clients trying to get the material before server data sync
        // noinspection ConstantValue
        if (rotorMaterial == null) {
            return GTMaterials.NULL;
        }
        return rotorMaterial;
    }

    private void onRotorInventoryChanged() {
        var stack = getRotorStack();
        var rotorBehaviour = TurbineRotorBehaviour.getBehaviour(stack);
        if (rotorBehaviour != null) {
            this.rotorMaterial = rotorBehaviour.getPartMaterial(stack);

            boolean emissive = this.rotorMaterial.hasProperty(PropertyKey.ORE) &&
                    this.rotorMaterial.getProperty(PropertyKey.ORE).isEmissive();
            setRenderState(getRenderState()
                    .setValue(HAS_ROTOR, true)
                    .setValue(IS_EMISSIVE_ROTOR, emissive));
        } else {
            this.rotorMaterial = GTMaterials.NULL;
            setRenderState(getRenderState()
                    .setValue(HAS_ROTOR, false)
                    .setValue(IS_EMISSIVE_ROTOR, false));
        }
        syncDataHolder.markClientSyncFieldDirty("rotorMaterial");
    }

    public boolean hasRotor() {
        return inventory.getStackInSlot(0) != ItemStack.EMPTY;
    }

    protected void updateRotorSubscription() {
        if (getRotorSpeed() > 0) {
            rotorSpeedSubs = subscribeServerTick(rotorSpeedSubs, this::updateRotorSpeed);
        } else if (rotorSpeedSubs != null) {
            rotorSpeedSubs.unsubscribe();
            rotorSpeedSubs = null;
        }
    }

    private void updateRotorSpeed() {
        if (isFormed() && getControllers().first() instanceof IWorkableMultiController workable) {
            if (workable.getWorkLogic().isWorking()) return;
        }
        if (!hasRotor()) {
            setRotorSpeed(0);
        } else if (getRotorSpeed() > 0) {
            setRotorSpeed(Math.max(0, getRotorSpeed() - SPEED_DECREMENT));
        }
        updateRotorSubscription();
    }

    public void setRotorSpeed(int rotorSpeed) {
        if ((this.rotorSpeed > 0 && rotorSpeed <= 0) || (this.rotorSpeed <= 0 && rotorSpeed > 0)) {
            setRenderState(getRenderState().setValue(IS_ROTOR_SPINNING, rotorSpeed > 0));
        }
        this.rotorSpeed = rotorSpeed;
        syncDataHolder.markClientSyncFieldDirty("rotorSpeed");
    }

    @Override
    public boolean onWorking(IWorkableMultiController controller) {
        if (getRotorSpeed() < getMaxRotorHolderSpeed()) {
            setRotorSpeed(getRotorSpeed() + SPEED_INCREMENT);
            updateRotorSubscription();
        }
        if (self().getOffsetTimer() % 20 == 0) {
            var numMaintenanceProblems = 0;
            if (isFormed() && getControllers().first() instanceof IMaintenanceMachine maintenance) {
                numMaintenanceProblems = maintenance.getNumMaintenanceProblems();
            }
            damageRotor(1 + numMaintenanceProblems);
        }
        return true;
    }

    public int getTierDifference() {
        if (isFormed() && getControllers().first() instanceof ITieredMachine tieredMachine) {
            return getTier() - tieredMachine.getTier();
        }
        return -1;
    }

    public ItemStack getRotorStack() {
        return inventory.getStackInSlot(0);
    }

    public void setRotorStack(ItemStack rotorStack) {
        inventory.setStackInSlot(0, rotorStack);
        inventory.onContentsChanged();
    }

    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        var superResult = super.onUse(context);
        if (superResult != InteractionResult.PASS) return superResult;
        if (!isRemote() && getRotorSpeed() > 0 && !context.getPlayer().isCreative()) {
            TurbineRotorBehaviour behaviour = TurbineRotorBehaviour.getBehaviour(getRotorStack());
            if (behaviour != null) {
                context.getPlayer().hurt(level.damageSources().source(GTDamageTypes.TURBINE),
                        behaviour.getDamage(getRotorStack()));
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingLDLib2Holder(holder);
        return UI.of(createLDLib2RotorPage());
    }

    /**
     * Creates a new holder-scoped Rotor Holder page for one surrounding multiblock UI opening.
     */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new RotorHolderLDLib2Page(player, holder);
    }

    private UIElement createLDLib2RotorPage() {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);

        UIElement container = new UIElement();
        UITemplate.setLDLib2Bounds(container, 4, 4, 26, 26);
        UITemplate.setLDLib2BackgroundTexture(container, GuiTextures.BACKGROUND_INVERSE);
        root.addChild(container);
        root.addChild(createLDLib2RotorSlot());
        root.addChild(createLDLib2RotorLockedOverlay());
        return root;
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Rotor Holder page holder must resolve the opened machine.");
        }
    }

    private GTItemSlotElement createLDLib2RotorSlot() {
        GTItemSlotElement slot = new GTItemSlotElement(inventory.storage, 0)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setContentOverlay(GuiTextures.TURBINE_OVERLAY)
                .setCanPut(stack -> !isRotorSlotBlocked())
                .setCanTake(player -> !isRotorSlotBlocked());
        return UITemplate.setLDLib2Bounds(slot, 8, 8, 18, 18);
    }

    private GTImageElement createLDLib2RotorLockedOverlay() {
        return new GTImageElement(9, 9, 16, 16, GuiTextures.colorRect(ROTOR_SLOT_LOCKED_OVERLAY_COLOR))
                .setVisibleSupplier(this::isRotorSlotBlocked);
    }

    private boolean isRotorSlotBlocked() {
        return rotorSpeed != 0;
    }

    /**
     * Owns the contextual Rotor Holder page state for exactly one menu opening.
     */
    private final class RotorHolderLDLib2Page implements LDLib2FancyUIProvider {

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private RotorHolderLDLib2Page(Player player, MachineUIHolder holder) {
            requireMatchingLDLib2Holder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(RotorHolderPartMachine.this, player,
                    holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != RotorHolderPartMachine.this) {
                throw new IllegalStateException("Rotor Holder page holder no longer resolves its opened machine.");
            }
            return createLDLib2RotorPage();
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
            tooltipsPanel.attachTooltips(createRotorObstructionTooltip());
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(getTitle());
        }
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////
    @Override
    public @Nullable GTRecipe modifyRecipe(GTRecipe recipe) {
        if (!isFrontFaceFree() || !hasRotor()) {
            return null;
        }
        return super.modifyRecipe(recipe);
    }

    //////////////////////////////////////
    // ******* FANCY GUI ********//
    //////////////////////////////////////
    @Override
    public void attachLDLib2FancyTooltipsToController(MultiblockControllerMachine controller,
                                                      LDLib2FancyTooltipsPanelElement tooltipsPanel) {
        tooltipsPanel.attachTooltips(createRotorObstructionTooltip());
    }

    private IFancyTooltip.Basic createRotorObstructionTooltip() {
        return new IFancyTooltip.Basic(
                () -> GuiTextures.INDICATOR_NO_STEAM.get(false),
                () -> List.of(Component.translatable("gtpm.multiblock.universal.rotor_obstructed")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED))),
                () -> !isFrontFaceFree(),
                () -> null);
    }

    /**
     *
     * @return the total power boost to output and consumption the rotor holder and rotor provide in %
     */
    public int getTotalPower() {
        return getHolderPowerMultiplier() * getRotorPower();
    }

    public boolean isRotorSpinning() {
        return getRotorSpeed() > 0;
    }

    /**
     * @return the total efficiency the rotor holder and rotor provide in %
     */
    public int getTotalEfficiency() {
        int rotorEfficiency = getRotorEfficiency();
        if (rotorEfficiency == -1)
            return -1;

        int holderEfficiency = getHolderEfficiency();
        if (holderEfficiency == -1)
            return -1;

        return Math.max(getBaseEfficiency(), rotorEfficiency * holderEfficiency / 100);
    }

    /**
     * @return the efficiency provided by the rotor holder in %
     */
    public int getHolderEfficiency() {
        int tierDifference = getTierDifference();
        if (tierDifference == -1)
            return -1;

        return 100 + 10 * tierDifference;
    }

    /**
     * @return the power multiplier provided by the rotor holder
     */
    public int getHolderPowerMultiplier() {
        int tierDifference = getTierDifference();
        if (tierDifference == -1) return -1;

        return (int) Math.pow(2, getTierDifference());
    }

    /**
     * @return the rotor's efficiency in %
     */
    public int getRotorEfficiency() {
        var stack = getRotorStack();
        var behavior = TurbineRotorBehaviour.getBehaviour(stack);
        if (behavior != null) {
            return behavior.getRotorEfficiency(stack);
        }
        return -1;
    }

    /**
     * @return the rotor's power in %
     */
    public int getRotorPower() {
        var stack = getRotorStack();
        var behavior = TurbineRotorBehaviour.getBehaviour(stack);
        if (behavior != null) {
            return behavior.getRotorPower(stack);
        }
        return -1;
    }

    /**
     * @return the rotor's durability as %
     */
    public int getRotorDurabilityPercent() {
        var stack = getRotorStack();
        var behavior = TurbineRotorBehaviour.getBehaviour(stack);
        if (behavior != null) {
            return behavior.getRotorDurabilityPercent(stack);
        }
        return -1;
    }

    /**
     * damages the rotor
     *
     * @param damageAmount to damage
     */
    public void damageRotor(int damageAmount) {
        var stack = getRotorStack();
        var behavior = TurbineRotorBehaviour.getBehaviour(stack);
        if (behavior != null) {
            behavior.applyRotorDamage(stack, damageAmount);
            setRotorStack(stack);
        }
    }

    /**
     * @return true if the front face is unobstructed
     */
    public boolean isFrontFaceFree() {
        final var facing = self().getFrontFacing();
        final var up = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        final var pos = self().getBlockPos();
        final var level = self().getLevel();
        for (int dLeft = -1; dLeft < 2; dLeft++) {
            for (int dUp = -1; dUp < 2; dUp++) {
                final var checkPos = RelativeDirection.offsetPos(pos, facing, up, false, dUp, dLeft, 1);
                if (!level.getBlockState(checkPos).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }
}
