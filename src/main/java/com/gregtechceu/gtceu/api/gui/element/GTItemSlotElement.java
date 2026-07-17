package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.transfer.item.NonMutatingItemCapacity;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRole;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRoleLDLib2Adapter;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemEntryHandler;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 item slot element for GTM recipe XML metadata.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-item-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTItemSlotElement extends ItemSlot {

    private final List<Runnable> extraChangeListeners = new ArrayList<>();
    private Predicate<ItemStack> canPlace = stack -> true;
    private Predicate<Player> canTake = player -> true;
    private IngredientIO ingredientIO = IngredientIO.NONE;
    private float xeiChance = 1.0f;
    private int xeiAmount = 1;
    private Supplier<Stream<ItemStack>> xeiStacks = this::getCurrentItemStream;
    private ToIntFunction<ItemStack> itemCountDecorationXOffset = stack -> 0;
    private IGuiTexture contentOverlay = IGuiTexture.EMPTY;
    @Nullable
    private Runnable changeListener;
    @Nullable
    private BiConsumer<GTItemSlotElement, List<Component>> onAddedTooltips;

    public GTItemSlotElement() {
        super();
    }

    public GTItemSlotElement(IItemHandlerModifiable itemHandler, int slotIndex) {
        this();
        bind(itemHandler, slotIndex);
    }

    @Override
    public GTItemSlotElement bind(IItemHandlerModifiable itemHandlerModifiable, int index) {
        validateSlotIndex(itemHandlerModifiable, index);
        var itemHandlerSlot = new CapacityAwareItemHandlerSlot(itemHandlerModifiable, index);
        configureItemHandlerSlot(itemHandlerSlot, true);
        super.bind(itemHandlerSlot);
        if (itemHandlerModifiable instanceof CycleItemEntryHandler handler) {
            setCycleItemDisplay(handler, index);
        }
        return this;
    }

    @Override
    public GTItemSlotElement bind(Slot slot) {
        super.bind(slot);
        if (slot instanceof ItemHandlerSlot itemHandlerSlot) {
            configureItemHandlerSlot(itemHandlerSlot, true);
        }
        return this;
    }

    public GTItemSlotElement setHandlerSlot(IItemHandlerModifiable itemHandler, int slotIndex) {
        return bind(itemHandler, slotIndex);
    }

    public GTItemSlotElement setCycleItemDisplay(CycleItemEntryHandler handler, int slotIndex) {
        validateSlotIndex(handler, slotIndex);
        xeiStacks = () -> handler.getEntry(slotIndex).getStacks().stream()
                .filter(itemStack -> !itemStack.isEmpty());
        return this;
    }

    public GTItemSlotElement setCanPutItems(boolean canPutItems) {
        return setCanPlace(stack -> canPutItems);
    }

    public GTItemSlotElement setCanTakeItems(boolean canTakeItems) {
        return setCanTake(player -> canTakeItems);
    }

    public GTItemSlotElement setCanPut(Predicate<ItemStack> canPut) {
        return setCanPlace(canPut);
    }

    public GTItemSlotElement setCanPlace(Predicate<ItemStack> canPlace) {
        this.canPlace = canPlace;
        applyItemHandlerSlotOptions();
        return this;
    }

    public GTItemSlotElement setCanTake(Predicate<Player> canTake) {
        this.canTake = canTake;
        applyItemHandlerSlotOptions();
        return this;
    }

    public GTItemSlotElement setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
        return this;
    }

    public GTItemSlotElement addChangeListener(Runnable changeListener) {
        this.extraChangeListeners.add(changeListener);
        return this;
    }

    public GTItemSlotElement setIngredientIO(GTXEIIngredientRole ingredientRole) {
        return setIngredientIO(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(ingredientRole));
    }

    public GTItemSlotElement setIngredientIO(IngredientIO ingredientIO) {
        this.ingredientIO = ingredientIO;
        return this;
    }

    public IngredientIO getIngredientIO() {
        return ingredientIO;
    }

    public GTItemSlotElement setXEIChance(float xeiChance) {
        this.xeiChance = xeiChance;
        return this;
    }

    public float getXEIChance() {
        return xeiChance;
    }

    public GTItemSlotElement setXEIAmount(int xeiAmount) {
        this.xeiAmount = xeiAmount;
        return this;
    }

    public GTItemSlotElement setXEIPossibleItems(Supplier<Stream<ItemStack>> xeiStacks) {
        this.xeiStacks = xeiStacks;
        return this;
    }

    public GTItemSlotElement setXEIPossibleItems(Stream<ItemStack> xeiStacks) {
        var items = xeiStacks.toList();
        return setXEIPossibleItems(items::stream);
    }

    public GTItemSlotElement setOnAddedTooltips(
                                                BiConsumer<GTItemSlotElement, List<Component>> onAddedTooltips) {
        this.onAddedTooltips = onAddedTooltips;
        return this;
    }

    public GTItemSlotElement setBackgroundTexture(IGuiTexture backgroundTexture) {
        getStyle().backgroundTexture(backgroundTexture);
        return this;
    }

    public GTItemSlotElement setContentOverlay(IGuiTexture contentOverlay) {
        this.contentOverlay = contentOverlay;
        return this;
    }

    public GTItemSlotElement setItemCountDecorationXOffset(ToIntFunction<ItemStack> offset) {
        itemCountDecorationXOffset = offset;
        return this;
    }

    @Override
    public GTItemSlotElement xeiPhantom() {
        if (GTCEu.Mods.isJEILoaded()) {
            JEISupport.ghostIngredient(this);
        }
        if (GTCEu.Mods.isEMILoaded()) {
            EMISupport.renderDragHandler(this);
            EMISupport.dropStackHandler(this);
        }
        return this;
    }

    public GTItemSlotElement xeiRecipeIngredient() {
        return xeiRecipeIngredient(ingredientIO);
    }

    @Override
    public GTItemSlotElement xeiRecipeIngredient(IngredientIO io) {
        this.ingredientIO = io;
        addXEIRecipeIngredient(io, xeiStacks);
        return this;
    }

    public GTItemSlotElement xeiRecipeIngredient(GTXEIIngredientRole role) {
        return xeiRecipeIngredient(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role));
    }

    public GTItemSlotElement xeiRecipeIngredient(IngredientIO io, Stream<ItemStack> allPossibleItems) {
        this.ingredientIO = io;
        setXEIPossibleItems(allPossibleItems);
        addXEIRecipeIngredient(io, xeiStacks);
        return this;
    }

    public GTItemSlotElement xeiRecipeIngredient(GTXEIIngredientRole role, Stream<ItemStack> allPossibleItems) {
        return xeiRecipeIngredient(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), allPossibleItems);
    }

    public GTItemSlotElement xeiRecipeIngredient(IngredientIO io,
                                                 Supplier<Stream<ItemStack>> allPossibleItems) {
        this.ingredientIO = io;
        this.xeiStacks = allPossibleItems;
        addXEIRecipeIngredient(io, allPossibleItems);
        return this;
    }

    public GTItemSlotElement xeiRecipeIngredient(GTXEIIngredientRole role,
                                                 Supplier<Stream<ItemStack>> allPossibleItems) {
        return xeiRecipeIngredient(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), allPossibleItems);
    }

    @Override
    public GTItemSlotElement xeiRecipeSlot() {
        return xeiRecipeSlot(ingredientIO, xeiChance);
    }

    @Override
    public GTItemSlotElement xeiRecipeSlot(IngredientIO io, float chance) {
        this.ingredientIO = io;
        this.xeiChance = chance;
        addXEIRecipeSlot(io, () -> chance, () -> xeiAmount, xeiStacks);
        return this;
    }

    public GTItemSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float chance) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), chance);
    }

    public GTItemSlotElement xeiRecipeSlot(IngredientIO io, float chance, int amount,
                                           Stream<ItemStack> allPossibleItems) {
        this.ingredientIO = io;
        this.xeiChance = chance;
        this.xeiAmount = amount;
        setXEIPossibleItems(allPossibleItems);
        addXEIRecipeSlot(io, () -> chance, () -> amount, xeiStacks);
        return this;
    }

    public GTItemSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float chance, int amount,
                                           Stream<ItemStack> allPossibleItems) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), chance, amount, allPossibleItems);
    }

    public GTItemSlotElement xeiRecipeSlot(IngredientIO io, float chance, int amount,
                                           Supplier<Stream<ItemStack>> allPossibleItems) {
        this.ingredientIO = io;
        this.xeiChance = chance;
        this.xeiAmount = amount;
        this.xeiStacks = allPossibleItems;
        addXEIRecipeSlot(io, () -> chance, () -> amount, allPossibleItems);
        return this;
    }

    public GTItemSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float chance, int amount,
                                           Supplier<Stream<ItemStack>> allPossibleItems) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), chance, amount, allPossibleItems);
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        var tooltips = new ArrayList<>(super.getFullTooltipTexts());
        if (onAddedTooltips != null) {
            onAddedTooltips.accept(this, tooltips);
        }
        return tooltips;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        contentOverlay.draw(guiContext, getContentX(), getContentY(), getContentWidth(), getContentHeight());
    }

    @Override
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        int decorationXOffset = itemCountDecorationXOffset.applyAsInt(itemStack);
        if (decorationXOffset == 0) {
            super.drawItemStack(guiContext, itemStack);
            return;
        }
        drawItemStack(guiContext, itemStack, decorationXOffset);
    }

    private void drawItemStack(GUIContext guiContext, ItemStack itemStack, int decorationXOffset) {
        if (itemStack.isEmpty()) {
            return;
        }
        var alpha = ColorUtils.alpha(guiContext.elementColor);
        var red = ColorUtils.red(guiContext.elementColor);
        var green = ColorUtils.green(guiContext.elementColor);
        var blue = ColorUtils.blue(guiContext.elementColor);
        RenderSystem.setShaderColor(red, green, blue, alpha);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        var graphics = guiContext.graphics;
        var minecraft = Minecraft.getInstance();
        var font = IClientItemExtensions.of(itemStack).getFont(itemStack, FontContext.ITEM_COUNT);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 232);
        graphics.renderItem(itemStack, 0, 0);
        graphics.renderItemDecorations(font == null ? minecraft.font : font, itemStack, decorationXOffset, 0);
        graphics.pose().popPose();

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
    }

    @Override
    public void loadXml(Element element) {
        super.loadXml(element);
        if (element.hasAttribute("legacy-background")) {
            getStyle().backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background")));
        }
        if (element.hasAttribute("legacy-overlay")) {
            slotStyle(style -> style.slotOverlay(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-overlay"))));
        }
        if (element.hasAttribute("draw-hover-overlay") && !XmlUtils.getAsBoolean(element, "draw-hover-overlay", true)) {
            slotStyle(style -> style.hoverOverlay(IGuiTexture.EMPTY));
        }
        if (element.hasAttribute("draw-hover-tips")) {
            slotStyle(style -> style.showItemTooltips(XmlUtils.getAsBoolean(element, "draw-hover-tips", true)));
        }
        if (element.hasAttribute("can-put-items")) {
            setCanPutItems(XmlUtils.getAsBoolean(element, "can-put-items", true));
        }
        if (element.hasAttribute("can-take-items")) {
            setCanTakeItems(XmlUtils.getAsBoolean(element, "can-take-items", true));
        }
    }

    private void applyItemHandlerSlotOptions() {
        if (getSlot() instanceof ItemHandlerSlot itemHandlerSlot) {
            itemHandlerSlot.setCanPlace(canPlace);
            itemHandlerSlot.setCanTake(canTake);
        }
    }

    private void configureItemHandlerSlot(ItemHandlerSlot itemHandlerSlot, boolean addChangeListener) {
        itemHandlerSlot.setCanPlace(canPlace);
        itemHandlerSlot.setCanTake(canTake);
        if (addChangeListener) {
            itemHandlerSlot.addChangeListener(this::notifyChangeListeners);
        }
    }

    private void notifyChangeListeners() {
        if (changeListener != null) {
            changeListener.run();
        }
        extraChangeListeners.forEach(Runnable::run);
    }

    private void addXEIRecipeIngredient(IngredientIO io, Supplier<Stream<ItemStack>> allPossibleItems) {
        if (GTCEu.Mods.isJEILoaded()) {
            JEISupport.recipeIngredient(this, io, allPossibleItems);
        }
        if (GTCEu.Mods.isEMILoaded()) {
            EMISupport.recipeIngredient(this, io, allPossibleItems);
        }
    }

    private void addXEIRecipeSlot(IngredientIO io, Supplier<Float> chance, IntSupplier amount,
                                  Supplier<Stream<ItemStack>> allPossibleItems) {
        if (GTCEu.Mods.isJEILoaded()) {
            JEISupport.recipeSlot(this, allPossibleItems);
        }
        if (GTCEu.Mods.isEMILoaded()) {
            EMISupport.recipeSlot(this, chance, amount, allPossibleItems);
        }
    }

    private Stream<ItemStack> getCurrentItemStream() {
        var itemStack = getValue();
        return itemStack.isEmpty() ? Stream.empty() : Stream.of(itemStack);
    }

    private static final class CapacityAwareItemHandlerSlot extends ItemHandlerSlot {

        private final int slotIndex;

        private CapacityAwareItemHandlerSlot(IItemHandlerModifiable itemHandler, int slotIndex) {
            super(itemHandler, slotIndex);
            this.slotIndex = slotIndex;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            if (getItemHandler() instanceof NonMutatingItemCapacity itemCapacity &&
                    itemCapacity.isNonMutatingEmptySlotCapacityQueryEnabled()) {
                return itemCapacity.getMaxStackSizeForEmptySlot(slotIndex, stack);
            }
            return super.getMaxStackSize(stack);
        }
    }

    private static void validateSlotIndex(IItemHandlerModifiable itemHandler, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= itemHandler.getSlots()) {
            GTCEu.LOGGER.error("Invalid GTM item slot index {} for handler with {} slots",
                    slotIndex, itemHandler.getSlots());
            throw new IllegalArgumentException("Invalid item slot index: " + slotIndex);
        }
    }
}
