package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.TextFormattingUtil;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.utils.FluidHelper;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

/**
 * LDLib2 fluid slot element for GTM recipe XML metadata without LDLib2 RPC bucket clicks.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-fluid-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTFluidSlotElement extends UIElement {

    private final Label amountLabel = new Label();
    private IGuiTexture background = IGuiTexture.EMPTY;
    private IGuiTexture overlay = IGuiTexture.EMPTY;
    private IGuiTexture hoverOverlay = new ColorRectTexture(0x80FFFFFF);
    private FluidStack fluid = FluidStack.EMPTY;
    private FillDirection fillDirection = FillDirection.DOWN_TO_UP;
    private int capacity;
    private boolean showAmount;
    private boolean showFluidTooltips = true;
    private boolean allowClickFilled = true;
    private boolean allowClickDrained = true;

    public GTFluidSlotElement() {
        getLayout().width(18);
        getLayout().height(18);
        getLayout().paddingAll(1);
        addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);

        amountLabel.addClass("__gtm-fluid-slot_amount-label__");
        amountLabel.layout(layout -> layout.widthPercent(100).heightPercent(100));
        amountLabel.textStyle(textStyle -> textStyle
                .textAlignVertical(Vertical.BOTTOM)
                .textAlignHorizontal(Horizontal.RIGHT)
                .fontSize(4.5f));
        amountLabel.setVisible(false);
        addChild(amountLabel);
        internalSetup();
    }

    public GTFluidSlotElement setFluid(FluidStack fluid) {
        this.fluid = fluid;
        updateAmountLabel();
        return this;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public GTFluidSlotElement setCapacity(int capacity) {
        this.capacity = capacity;
        updateAmountLabel();
        return this;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isAllowClickFilled() {
        return allowClickFilled;
    }

    public boolean isAllowClickDrained() {
        return allowClickDrained;
    }

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("legacy-background")) {
            background = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background"));
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
            showAmount = XmlUtils.getAsBoolean(element, "show-amount", false);
            amountLabel.setVisible(showAmount);
        }
        if (element.hasAttribute("legacy-allow-click-filled")) {
            allowClickFilled = XmlUtils.getAsBoolean(element, "legacy-allow-click-filled", true);
        }
        if (element.hasAttribute("legacy-allow-click-drained")) {
            allowClickDrained = XmlUtils.getAsBoolean(element, "legacy-allow-click-drained", true);
        }
        if (element.hasAttribute("fill-direction")) {
            fillDirection = parseFillDirection(element.getAttribute("fill-direction"));
        }
        super.loadXml(element);
        updateAmountLabel();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        background.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());

        var contentX = getContentX();
        var contentY = getContentY();
        var contentWidth = getContentWidth();
        var contentHeight = getContentHeight();

        if (!fluid.isEmpty()) {
            drawFluid(guiContext, contentX, contentY, contentWidth, contentHeight);
        }
        overlay.draw(guiContext, contentX, contentY, contentWidth, contentHeight);
        if (isHover() || isSelfOrChildHover()) {
            hoverOverlay.draw(guiContext, contentX, contentY, contentWidth, contentHeight);
        }
    }

    private void drawFluid(GUIContext guiContext, float contentX, float contentY, float contentWidth,
                           float contentHeight) {
        double progress = fluid.getAmount() * 1.0 / Math.max(Math.max(fluid.getAmount(), capacity), 1);
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

    private void onHoverTooltips(UIEvent event) {
        if (showFluidTooltips) {
            event.hoverTooltips = new HoverTooltips(getFullTooltipTexts(), null, null, null);
        }
    }

    private void onMouseDown(UIEvent event) {
        event.stopPropagation();
        event.hasHandler = false;
    }

    private List<Component> getFullTooltipTexts() {
        var tooltips = new ArrayList<Component>();
        var tooltipCapacity = Math.max(capacity, fluid.getAmount());
        if (!fluid.isEmpty()) {
            tooltips.add(FluidHelper.getDisplayName(fluid));
            tooltips.add(Component.translatable("ldlib.fluid.amount", fluid.getAmount(), tooltipCapacity)
                    .append(" " + FluidHelper.getUnit()));
            tooltips.add(Component.translatable("ldlib.fluid.temperature", FluidHelper.getTemperature(fluid)));
            tooltips.add(Component.translatable(FluidHelper.isLighterThanAir(fluid) ?
                    "ldlib.fluid.state_gas" : "ldlib.fluid.state_liquid"));
        } else {
            tooltips.add(Component.translatable("ldlib.fluid.empty"));
            tooltips.add(Component.translatable("ldlib.fluid.amount", 0, tooltipCapacity)
                    .append(" " + FluidHelper.getUnit()));
        }
        tooltips.addAll(getStyle().tooltips().asList());
        return tooltips;
    }

    private void updateAmountLabel() {
        if (!showAmount || fluid.isEmpty()) {
            amountLabel.setValue(Component.empty());
            return;
        }
        amountLabel.setValue(Component.literal(
                TextFormattingUtil.formatLongToCompactStringBuckets(fluid.getAmount(), 3) + "B"));
    }

    private FillDirection parseFillDirection(String value) {
        try {
            return FillDirection.valueOf(value);
        } catch (IllegalArgumentException e) {
            GTCEu.LOGGER.error("Invalid GTM fluid slot fill direction '{}'", value, e);
            throw e;
        }
    }
}
