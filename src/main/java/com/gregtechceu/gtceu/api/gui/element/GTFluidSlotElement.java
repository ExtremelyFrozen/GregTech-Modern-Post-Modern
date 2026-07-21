package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRole;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRoleLDLib2Adapter;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.emi.LDLibEMIPlugin;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.tterrag.registrate.util.RegistrateDistExecutor;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 fluid slot element for GTM recipe XML metadata with GTM-controlled bucket interactions.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-fluid-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTFluidSlotElement extends FluidSlot {

    private IGuiTexture background = IGuiTexture.EMPTY;
    private IGuiTexture overlay = IGuiTexture.EMPTY;
    private IGuiTexture contentOverlay = IGuiTexture.EMPTY;
    private IGuiTexture hoverOverlay = new ColorRectTexture(0x80FFFFFF);
    private boolean showAmount;
    private boolean showFluidTooltips = true;
    private IngredientIO ingredientIO = IngredientIO.NONE;
    private float xeiChance = 1.0f;
    private int xeiAmount = 1;
    private Supplier<Stream<FluidStack>> xeiFluids = () -> Stream.of(getFluid());
    private IFluidHandler fluidHandler;
    private int tankIndex;
    private Runnable changeListener;
    private BiConsumer<GTFluidSlotElement, List<Component>> onAddedTooltips;

    public GTFluidSlotElement() {
        getStyle().backgroundTexture(IGuiTexture.EMPTY);
        getSlotStyle().fillDirection(FillDirection.DOWN_TO_UP);
        amountLabel.addClass("__gtm-fluid-slot_amount-label__");
        amountLabel.setVisible(false);
    }

    /**
     * Routes LDLib2's standard binding entry through GTM's read-only display binding. The superclass binding is
     * intentionally not called, so its built-in container-click RPC never receives a handler.
     */
    @Override
    public GTFluidSlotElement bind(@Nullable IFluidHandler fluidHandler, int tankIndex) {
        if (fluidHandler == null) {
            this.fluidHandler = null;
            this.tankIndex = 0;
            setCapacity(0);
            setFluid(FluidStack.EMPTY);
            return this;
        }
        return setFluidTank(fluidHandler, tankIndex);
    }

    public GTFluidSlotElement setFluidTank(IFluidHandler fluidHandler, int tankIndex) {
        if (fluidHandler instanceof NotifiableFluidTank notifiable) {
            if (tankIndex < 0 || tankIndex >= notifiable.getStorages().length) {
                throw new IllegalArgumentException("Invalid fluid tank index: " + tankIndex);
            }
            this.fluidHandler = notifiable.getStorages()[tankIndex];
            this.tankIndex = 0;
        } else {
            validateTankIndex(fluidHandler, tankIndex);
            this.fluidHandler = fluidHandler;
            this.tankIndex = tankIndex;
        }
        if (fluidHandler instanceof CycleFluidEntryHandler handler) {
            setCycleFluidDisplay(handler, tankIndex);
        }
        refreshFluidTank();
        return this;
    }

    public GTFluidSlotElement setCycleFluidDisplay(CycleFluidEntryHandler handler, int tankIndex) {
        validateTankIndex(handler, tankIndex);
        xeiFluids = () -> {
            var entry = handler.getEntry(tankIndex);
            return entry == null ? Stream.empty() : entry.getStacks().stream();
        };
        return this;
    }

    @Override
    public GTFluidSlotElement setValue(@Nullable FluidStack fluid, boolean notify) {
        super.setValue(fluid, notify);
        updateAmountLabel();
        return this;
    }

    @Override
    public GTFluidSlotElement setFluid(FluidStack fluid) {
        return setValue(fluid, true);
    }

    @Override
    public GTFluidSlotElement setFluid(FluidStack fluid, boolean notify) {
        return setValue(fluid, notify);
    }

    @Override
    public GTFluidSlotElement setCapacity(int capacity) {
        super.setCapacity(capacity);
        updateAmountLabel();
        return this;
    }

    public GTFluidSlotElement setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
        amountLabel.setVisible(showAmount);
        updateAmountLabel();
        return this;
    }

    @Override
    public GTFluidSlotElement setAllowClickFilled(boolean allowClickFilled) {
        super.setAllowClickFilled(allowClickFilled);
        return this;
    }

    @Override
    public GTFluidSlotElement setAllowClickDrained(boolean allowClickDrained) {
        super.setAllowClickDrained(allowClickDrained);
        return this;
    }

    public GTFluidSlotElement setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
        return this;
    }

    public GTFluidSlotElement setIngredientIO(GTXEIIngredientRole role) {
        return setIngredientIO(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role));
    }

    public GTFluidSlotElement setIngredientIO(IngredientIO ingredientIO) {
        this.ingredientIO = ingredientIO;
        return this;
    }

    public GTFluidSlotElement setXEIChance(float xeiChance) {
        this.xeiChance = xeiChance;
        return this;
    }

    public GTFluidSlotElement setXEIAmount(int xeiAmount) {
        this.xeiAmount = xeiAmount;
        return this;
    }

    public GTFluidSlotElement setXEIPossibleFluids(Supplier<Stream<FluidStack>> xeiFluids) {
        this.xeiFluids = xeiFluids;
        return this;
    }

    public GTFluidSlotElement setXEIPossibleFluids(Stream<FluidStack> xeiFluids) {
        var fluids = xeiFluids.toList();
        return setXEIPossibleFluids(fluids::stream);
    }

    public GTFluidSlotElement setOnAddedTooltips(
                                                 BiConsumer<GTFluidSlotElement, List<Component>> onAddedTooltips) {
        this.onAddedTooltips = onAddedTooltips;
        return this;
    }

    @Override
    public GTFluidSlotElement xeiPhantom() {
        RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (GTCEu.Mods.isJEILoaded()) {
                LDLibJEIPlugin.ghostIngredient(this, NeoForgeTypes.FLUID_STACK, ingredient -> true, this::setFluid);
            }
            if (GTCEu.Mods.isEMILoaded()) {
                LDLibEMIPlugin.renderDragHandler(this, dragged -> dragged instanceof FluidEmiStack);
                LDLibEMIPlugin.dropStackHandler(this,
                        dragged -> dragged instanceof FluidEmiStack,
                        dragged -> {
                            if (dragged instanceof FluidEmiStack droppedFluid) {
                                setFluid(new FluidStack(
                                        ((Fluid) droppedFluid.getKey()).builtInRegistryHolder(),
                                        Math.max(1000, (int) droppedFluid.getAmount()),
                                        droppedFluid.getComponentChanges()));
                            }
                        });
            }
        });
        return this;
    }

    public GTFluidSlotElement xeiRecipeIngredient() {
        return xeiRecipeIngredient(ingredientIO);
    }

    @Override
    public GTFluidSlotElement xeiRecipeIngredient(IngredientIO ingredientIO) {
        this.ingredientIO = ingredientIO;
        addXEIRecipeIngredient(ingredientIO, xeiFluids);
        return this;
    }

    public GTFluidSlotElement xeiRecipeIngredient(GTXEIIngredientRole role) {
        return xeiRecipeIngredient(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role));
    }

    @Override
    public GTFluidSlotElement xeiRecipeIngredient(IngredientIO ingredientIO,
                                                  Supplier<Stream<FluidStack>> allPossibleFluids) {
        this.ingredientIO = ingredientIO;
        this.xeiFluids = allPossibleFluids;
        addXEIRecipeIngredient(ingredientIO, allPossibleFluids);
        return this;
    }

    public GTFluidSlotElement xeiRecipeIngredient(GTXEIIngredientRole role,
                                                  Supplier<Stream<FluidStack>> allPossibleFluids) {
        return xeiRecipeIngredient(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), allPossibleFluids);
    }

    @Override
    public GTFluidSlotElement xeiRecipeSlot() {
        return xeiRecipeSlot(ingredientIO, xeiChance);
    }

    @Override
    public GTFluidSlotElement xeiRecipeSlot(IngredientIO ingredientIO, float xeiChance) {
        this.ingredientIO = ingredientIO;
        this.xeiChance = xeiChance;
        addXEIRecipeSlot(ingredientIO, () -> xeiChance, () -> xeiAmount, xeiFluids);
        return this;
    }

    public GTFluidSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float xeiChance) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), xeiChance);
    }

    public GTFluidSlotElement xeiRecipeSlot(IngredientIO ingredientIO, float xeiChance, int xeiAmount,
                                            Stream<FluidStack> allPossibleFluids) {
        this.ingredientIO = ingredientIO;
        this.xeiChance = xeiChance;
        this.xeiAmount = xeiAmount;
        setXEIPossibleFluids(allPossibleFluids);
        addXEIRecipeSlot(ingredientIO, () -> xeiChance, () -> xeiAmount, xeiFluids);
        return this;
    }

    public GTFluidSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float xeiChance, int xeiAmount,
                                            Stream<FluidStack> allPossibleFluids) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), xeiChance, xeiAmount, allPossibleFluids);
    }

    @Override
    public GTFluidSlotElement xeiRecipeSlot(IngredientIO ingredientIO, float xeiChance, int xeiAmount,
                                            Supplier<Stream<FluidStack>> allPossibleFluids) {
        this.ingredientIO = ingredientIO;
        this.xeiChance = xeiChance;
        this.xeiAmount = xeiAmount;
        this.xeiFluids = allPossibleFluids;
        addXEIRecipeSlot(ingredientIO, () -> xeiChance, () -> xeiAmount, allPossibleFluids);
        return this;
    }

    public GTFluidSlotElement xeiRecipeSlot(GTXEIIngredientRole role, float xeiChance, int xeiAmount,
                                            Supplier<Stream<FluidStack>> allPossibleFluids) {
        return xeiRecipeSlot(GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role), xeiChance, xeiAmount, allPossibleFluids);
    }

    public GTFluidSlotElement setBackgroundTexture(IGuiTexture background) {
        this.background = background;
        return this;
    }

    /**
     * Returns the background drawn by this slot's client renderer.
     *
     * @return the configured slot background
     */
    public IGuiTexture getBackgroundTexture() {
        return background;
    }

    public GTFluidSlotElement setContentOverlay(IGuiTexture overlay) {
        this.contentOverlay = overlay;
        return this;
    }

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("legacy-background")) {
            setBackgroundTexture(GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background")));
        }
        if (element.hasAttribute("legacy-overlay")) {
            overlay = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-overlay"));
        }
        if (element.hasAttribute("draw-hover-overlay") && !XmlUtils.getAsBoolean(element, "draw-hover-overlay", true)) {
            hoverOverlay = IGuiTexture.EMPTY;
        }
        if (element.hasAttribute("draw-hover-tips")) {
            showFluidTooltips = XmlUtils.getAsBoolean(element, "draw-hover-tips", true);
        }
        if (element.hasAttribute("show-amount")) {
            setShowAmount(XmlUtils.getAsBoolean(element, "show-amount", false));
        }
        if (element.hasAttribute("legacy-allow-click-filled")) {
            setAllowClickFilled(XmlUtils.getAsBoolean(element, "legacy-allow-click-filled", true));
        }
        if (element.hasAttribute("legacy-allow-click-drained")) {
            setAllowClickDrained(XmlUtils.getAsBoolean(element, "legacy-allow-click-drained", true));
        }
        if (element.hasAttribute("fill-direction")) {
            getSlotStyle().fillDirection(parseFillDirection(element.getAttribute("fill-direction")));
        }
        super.loadXml(element);
        updateAmountLabel();
    }

    @Override
    public void screenTick() {
        refreshFluidTank();
        super.screenTick();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext guiContext) {
        refreshFluidTank();
        background.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());

        var contentX = getContentX();
        var contentY = getContentY();
        var contentWidth = getContentWidth();
        var contentHeight = getContentHeight();

        if (!getFluid().isEmpty()) {
            drawFluid(guiContext, contentX, contentY, contentWidth, contentHeight);
        }
        overlay.draw(guiContext, contentX, contentY, contentWidth, contentHeight);
        contentOverlay.draw(guiContext, contentX, contentY, contentWidth, contentHeight);
        if (isHover() || isSelfOrChildHover()) {
            hoverOverlay.draw(guiContext, contentX, contentY, contentWidth, contentHeight);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawFluid(GUIContext guiContext, float contentX, float contentY, float contentWidth,
                           float contentHeight) {
        var fluid = getFluid();
        var fillDirection = getSlotStyle().fillDirection();
        double progress = fluid.getAmount() * 1.0 / Math.max(Math.max(fluid.getAmount(), getCapacity()), 1);
        float drawnU = (float) fillDirection.getDrawnU(progress);
        float drawnV = (float) fillDirection.getDrawnV(progress);
        float drawnWidth = (float) fillDirection.getDrawnWidth(progress);
        float drawnHeight = (float) fillDirection.getDrawnHeight(progress);
        DrawerHelper.drawFluidForGui(guiContext.graphics, fluid,
                contentX + drawnU * contentWidth,
                contentY + drawnV * contentHeight,
                contentWidth * drawnWidth,
                contentHeight * drawnHeight, -1);
    }

    @Override
    protected void onHoverTooltips(UIEvent event) {
        if (showFluidTooltips) {
            event.hoverTooltips = new HoverTooltips(getFullTooltipTexts(), null, null, null);
        }
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        event.stopPropagation();
        event.hasHandler = false;
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        var tooltips = new ArrayList<Component>();
        var fluid = getFluid();
        var tooltipCapacity = Math.max(getCapacity(), fluid.getAmount());
        if (!fluid.isEmpty()) {
            tooltips.add(fluid.getHoverName());
            if (showAmount) {
                tooltips.add(Component.translatable("gtpm.fluid.amount",
                        FormattingUtil.formatNumbers(fluid.getAmount()),
                        FormattingUtil.formatNumbers(tooltipCapacity)));
            }
            RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> TooltipsHandler.appendFluidTooltips(fluid, tooltips::add,
                            TooltipFlag.NORMAL, Item.TooltipContext.of(GTRegistries.builtinRegistry())));
        } else {
            tooltips.add(Component.translatable("gtpm.fluid.empty"));
            if (showAmount) {
                tooltips.add(Component.translatable("gtpm.fluid.amount", 0,
                        FormattingUtil.formatNumbers(tooltipCapacity)));
            }
        }
        if (onAddedTooltips != null) {
            onAddedTooltips.accept(this, tooltips);
        }
        tooltips.addAll(getStyle().tooltips().asList());
        return tooltips;
    }

    @Override
    public Component getFluidAmountText() {
        return showAmount ? super.getFluidAmountText() : Component.empty();
    }

    private void updateAmountLabel() {
        amountLabel.setValue(getFluidAmountText());
    }

    private void refreshFluidTank() {
        if (fluidHandler == null) {
            return;
        }
        var refreshedFluid = fluidHandler.getFluidInTank(tankIndex);
        var refreshedCapacity = fluidHandler.getTankCapacity(tankIndex);
        var fluid = getFluid();
        var changed = getCapacity() != refreshedCapacity ||
                !FluidStack.isSameFluidSameComponents(refreshedFluid, fluid) ||
                refreshedFluid.getAmount() != fluid.getAmount();
        setCapacity(refreshedCapacity);
        setFluid(refreshedFluid.copy());
        if (changed && changeListener != null) {
            changeListener.run();
        }
    }

    private void addXEIRecipeIngredient(IngredientIO io, Supplier<Stream<FluidStack>> allPossibleFluids) {
        RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (GTCEu.Mods.isJEILoaded()) {
                LDLibJEIPlugin.recipeIngredient(this, io, () -> allPossibleFluids.get()
                        .map(this::createJEIFluidIngredient)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toList()));
            }
            if (GTCEu.Mods.isEMILoaded()) {
                LDLibEMIPlugin.recipeIngredient(this, io, () -> allPossibleFluids.get()
                        .map(fluid -> EmiStack.of(fluid.getFluid(), fluid.getComponentsPatch(), fluid.getAmount()))
                        .collect(Collectors.toList()));
            }
        });
    }

    private void addXEIRecipeSlot(IngredientIO io, Supplier<Float> chance, IntSupplier amount,
                                  Supplier<Stream<FluidStack>> allPossibleFluids) {
        RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (GTCEu.Mods.isJEILoaded()) {
                LDLibJEIPlugin.recipeSlot(this,
                        () -> createJEIFluidIngredient(getFluid()).orElse(null),
                        () -> allPossibleFluids.get()
                                .map(this::createJEIFluidIngredient)
                                .flatMap(Optional::stream)
                                .collect(Collectors.toList()));
            }
            if (GTCEu.Mods.isEMILoaded()) {
                LDLibEMIPlugin.recipeSlot(this, () -> new ListEmiIngredient(
                        allPossibleFluids.get()
                                .map(fluid -> EmiStack.of(fluid.getFluid(),
                                        fluid.getComponentsPatch(), fluid.getAmount()))
                                .map(stack -> stack.setChance(chance.get()))
                                .collect(Collectors.toList()),
                        amount.getAsInt())
                        .setChance(chance.get()));
            }
        });
    }

    private Optional<ITypedIngredient<?>> createJEIFluidIngredient(FluidStack fluidStack) {
        if (fluidStack.isEmpty()) {
            return Optional.empty();
        }
        return LDLibJEIPlugin.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluidStack)
                .map(ingredient -> ingredient);
    }

    private FillDirection parseFillDirection(String value) {
        try {
            return FillDirection.valueOf(value);
        } catch (IllegalArgumentException e) {
            GTCEu.LOGGER.error("Invalid GTM fluid slot fill direction '{}'", value, e);
            throw e;
        }
    }

    private static void validateTankIndex(IFluidHandler fluidHandler, int tankIndex) {
        if (tankIndex < 0 || tankIndex >= fluidHandler.getTanks()) {
            throw new IllegalArgumentException("Invalid fluid tank index: " + tankIndex);
        }
    }
}
