package com.gregtechceu.gtceu.common.item.modules;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.api.placeholder.MultiLineComponent;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderContext;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.client.renderer.monitor.IMonitorRenderer;
import com.gregtechceu.gtceu.client.renderer.monitor.MonitorTextRenderer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorTextModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineActionToServer;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.GTStringUtils;

import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.codeeditor.CodeEditorWidget;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TextModuleBehaviour implements IMonitorModuleItem, IAddInformation {

    private static final int EDITOR_WIDTH = 120;
    private static final int EDITOR_HEIGHT = 80;
    private static final int PLACEHOLDER_REFERENCE_X = -100;
    private static final int PLACEHOLDER_REFERENCE_Y = -50;
    private static final int PLACEHOLDER_REFERENCE_WIDTH = 160;
    private static final int PLACEHOLDER_REFERENCE_HEIGHT = 200;

    private void updateText(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        if (!stack.has(GTDataComponents.PLACEHOLDER_UUID)) {
            stack.set(GTDataComponents.PLACEHOLDER_UUID, UUID.randomUUID());
        }
        MultiLineComponent text = PlaceholderHandler.processPlaceholders(
                getPlaceholderText(stack),
                new PlaceholderContext(
                        group.getTargetLevel(machine.getLevel()),
                        group.getTarget(machine.getLevel()),
                        group.getTargetCoverSide(),
                        group.getPlaceholderSlotsHandler(),
                        group.getTargetCover(machine.getLevel()),
                        null,
                        stack.get(GTDataComponents.PLACEHOLDER_UUID)));
        stack.update(GTDataComponents.TEXT_LINE_LIST, TextLineList.EMPTY, lines -> lines.withLines(text.toImmutable()));
    }

    @Override
    public void tick(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        this.updateText(stack, machine, group);
    }

    @Override
    public IMonitorRenderer getRenderer(ItemStack stack) {
        TextLineList lines = stack.getOrDefault(GTDataComponents.TEXT_LINE_LIST, TextLineList.EMPTY);
        return new MonitorTextRenderer(MultiLineComponent.of(lines.lines()), Math.max(lines.scale(), .0001));
    }

    @Override
    public Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        return createUIWidget(stack, machine, group, action -> sendMachineAction(machine, action));
    }

    Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group,
                          Consumer<SyncActionData> actionSender) {
        WidgetGroup builder = new WidgetGroup();
        CodeEditorWidget editor = new CodeEditorWidget(0, 0, 120, 80);
        // editor.codeEditor.setLanguageDefinition(PlaceholderHandler.LANG_DEFINITION);
        TextFieldWidget scaleInput = new TextFieldWidget(
                -50, 47,
                40, 10,
                null,
                null);
        TextEditSession editSession = TextEditSession.open(machine, group, stack);
        ButtonWidget saveButton = new ButtonWidget(-40, 22, 20, 20, click -> {
            if (!click.isRemote) return;
            sendTextChange(editSession, editor.getLines(), scaleInput.getCurrentString(), actionSender);
        });
        saveButton.setButtonTexture(GuiTextures.BUTTON_CHECK);
        List<Boolean> tmp = new ArrayList<>();
        Supplier<String> scaleInputSupplier = () -> {
            if (tmp.isEmpty()) {
                tmp.add(true);
            } else {
                scaleInput.setTextSupplier(null);
            }
            if (!stack.has(GTDataComponents.FORMAT_STRING_LIST)) {
                return "1";
            }
            // noinspection DataFlowIssue
            return String.valueOf(Mth.clamp(stack.get(GTDataComponents.FORMAT_STRING_LIST).scale(), .0001f, 1000f));
        };
        scaleInput.setTextSupplier(scaleInputSupplier);
        scaleInput.setHoverTooltips(Component.translatable("gtpm.gui.central_monitor.text_scale"));
        List<String> formatStringLines = stack.getOrDefault(GTDataComponents.FORMAT_STRING_LIST, TextLineList.EMPTY)
                .lines()
                .stream()
                .map(Component::getString)
                .toList();
        editor.setLines(formatStringLines);
        builder.addWidget(editor);
        builder.addWidget(saveButton);
        Widget placeholderReference = PlaceholderHandler.getPlaceholderHandlerUI("");
        builder.addWidget(scaleInput);
        placeholderReference.setSelfPosition(-100, -50);
        builder.addWidget(placeholderReference);
        return builder;
    }

    @Override
    public UIElement createConfigurationElement(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
        return createConfigurationElement(stack, machine, group, action -> sendMachineAction(machine, action));
    }

    UIElement createConfigurationElement(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group,
                                         Consumer<SyncActionData> actionSender) {
        UIElement builder = new UIElement();
        UITemplate.setLDLib2Bounds(builder, 0, 0, EDITOR_WIDTH, EDITOR_HEIGHT);

        CodeEditor editor = new CodeEditor();
        UITemplate.setLDLib2Bounds(editor, 0, 0, EDITOR_WIDTH, EDITOR_HEIGHT);
        editor.setLines(getFormatStringLines(stack));

        GTTextFieldElement scaleInput = createLDLib2ScaleInput(stack);
        TextEditSession editSession = TextEditSession.open(machine, group, stack);
        GTButtonElement saveButton = new GTButtonElement(-40, 22, 20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CHECK),
                event -> {
                    if (!machine.isRemote()) return;
                    saveLDLib2Text(editSession, editor, scaleInput, actionSender);
                });
        saveButton.noText();

        builder.addChildren(editor, saveButton, scaleInput, createLDLib2PlaceholderReference(""));
        return builder;
    }

    private static GTTextFieldElement createLDLib2ScaleInput(ItemStack stack) {
        GTTextFieldElement scaleInput = new GTTextFieldElement(-50, 47, 40, 10);
        scaleInput.setNumbersOnlyFloat(.0001f, 1000f);
        scaleInput.setText(getScaleText(stack), false);
        scaleInput.style(style -> style.tooltips(Component.translatable("gtpm.gui.central_monitor.text_scale")));
        return scaleInput;
    }

    private static List<String> getFormatStringLines(ItemStack stack) {
        return stack.getOrDefault(GTDataComponents.FORMAT_STRING_LIST, TextLineList.EMPTY)
                .lines()
                .stream()
                .map(Component::getString)
                .toList();
    }

    private static String getScaleText(ItemStack stack) {
        if (!stack.has(GTDataComponents.FORMAT_STRING_LIST)) {
            return "1";
        }
        return String.valueOf(Mth.clamp(stack.get(GTDataComponents.FORMAT_STRING_LIST).scale(), .0001f, 1000f));
    }

    private static void saveLDLib2Text(TextEditSession editSession, CodeEditor editor,
                                       GTTextFieldElement scaleInput, Consumer<SyncActionData> actionSender) {
        if (scaleInput.isError()) {
            GTCEu.LOGGER.error("Invalid monitor text module scale: {}", scaleInput.getRawText());
            return;
        }
        sendTextChange(editSession, Arrays.asList(editor.getValue()), scaleInput.getValue(), actionSender);
    }

    private static void sendTextChange(TextEditSession editSession, List<String> requestedLines,
                                       String scaleText, Consumer<SyncActionData> actionSender) {
        Float requestedScale = readScale(scaleText);
        if (requestedScale == null) {
            return;
        }
        TextLineList requestedConfiguration = CentralMonitorTextModuleActions.createConfiguration(
                requestedLines, requestedScale);
        if (requestedConfiguration.equals(
                editSession.expectedModule.get(GTDataComponents.FORMAT_STRING_LIST))) {
            return;
        }
        long nextRevision = Math.incrementExact(editSession.expectedConfigurationRevision);
        int nextSequence = Math.incrementExact(editSession.sequence);
        var action = CentralMonitorTextModuleActions.createSetTextModuleConfigurationAction(
                editSession.holderIncarnation,
                editSession.groupIdentity,
                editSession.moduleSlotIncarnation,
                editSession.expectedConfigurationRevision,
                editSession.expectedModule,
                requestedConfiguration,
                editSession.sequence);
        actionSender.accept(action);
        editSession.expectedModule.set(GTDataComponents.FORMAT_STRING_LIST, requestedConfiguration);
        editSession.expectedConfigurationRevision = nextRevision;
        editSession.sequence = nextSequence;
    }

    private static void sendMachineAction(CentralMonitorMachine machine, SyncActionData action) {
        PacketDistributor.sendToServer(
                new CPacketMachineActionToServer(machine.getBlockPos(), machine.getDefinition().getId(), action));
    }

    private static final class TextEditSession {

        private final UUID holderIncarnation;
        private final UUID groupIdentity;
        private final UUID moduleSlotIncarnation;
        private final ItemStack expectedModule;
        private long expectedConfigurationRevision;
        private int sequence;

        private TextEditSession(UUID holderIncarnation, UUID groupIdentity, UUID moduleSlotIncarnation,
                                long expectedConfigurationRevision, ItemStack expectedModule) {
            this.holderIncarnation = holderIncarnation;
            this.groupIdentity = groupIdentity;
            this.moduleSlotIncarnation = moduleSlotIncarnation;
            this.expectedConfigurationRevision = expectedConfigurationRevision;
            this.expectedModule = expectedModule;
        }

        private static TextEditSession open(CentralMonitorMachine machine, MonitorGroup group, ItemStack module) {
            return new TextEditSession(
                    machine.getCentralMonitorActionIncarnation(),
                    group.getIdentity(),
                    group.getModuleSlotIncarnation(),
                    group.getTextConfigurationRevision(),
                    CentralMonitorTextModuleActions.captureExpectedModule(module));
        }
    }

    private static @Nullable Float readScale(String text) {
        try {
            float scale = Float.parseFloat(text);
            if (!CentralMonitorTextModuleActions.isValidScale(scale)) {
                GTCEu.LOGGER.error("Monitor text module scale is outside the supported range: {}", text);
                return null;
            }
            return scale;
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid monitor text module scale: {}", text, e);
            return null;
        }
    }

    private static UIElement createLDLib2PlaceholderReference(String filter) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, PLACEHOLDER_REFERENCE_X, PLACEHOLDER_REFERENCE_Y, PLACEHOLDER_REFERENCE_WIDTH,
                PLACEHOLDER_REFERENCE_HEIGHT);

        root.addChild(createLDLib2Label(0, 0, PLACEHOLDER_REFERENCE_WIDTH, 15,
                Component.literal(GTStringUtils.componentsToString(
                        LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.placeholder_reference")))));

        GTScrollerViewElement placeholderReference = new GTScrollerViewElement(0, 15, 100,
                PLACEHOLDER_REFERENCE_HEIGHT - 15);
        int y = 2;
        List<String> placeholders = new ArrayList<>(PlaceholderHandler.getAllPlaceholderNames());
        placeholders.removeIf(placeholder -> placeholder == null || !placeholder.contains(filter));
        placeholders.sort(Comparator.naturalOrder());
        for (String placeholder : placeholders) {
            GTLabelElement placeholderName = createLDLib2Label(0, y, 100, 15, Component.literal(placeholder));
            placeholderName.style(style -> style.tooltips(tooltips(
                    LangHandler.getSingleOrMultiLang("gtpm.placeholder_info." + placeholder))));
            placeholderReference.addChild(placeholderName);
            y += 15;
        }
        root.addChild(placeholderReference);
        return root;
    }

    private static GTLabelElement createLDLib2Label(int x, int y, int width, int height, Component text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static Component[] tooltips(List<MutableComponent> tooltips) {
        return GTStringUtils.toImmutable(tooltips).toArray(Component[]::new);
    }

    @Override
    public String getType() {
        return "text";
    }

    public MultiLineComponent getText(ItemStack stack) {
        return MultiLineComponent.of(stack.getOrDefault(GTDataComponents.TEXT_LINE_LIST, TextLineList.EMPTY).lines());
    }

    public float getScale(ItemStack stack) {
        return Math.max(stack.getOrDefault(GTDataComponents.TEXT_LINE_LIST, TextLineList.EMPTY).scale(), .0001f);
    }

    public void setScale(ItemStack stack, float scale) {
        stack.update(GTDataComponents.TEXT_LINE_LIST, TextLineList.EMPTY, lines -> lines.withScale(scale));
    }

    public TextLineList createPlaceholderConfiguration(ItemStack stack, String text) {
        List<Component> lines = Arrays.stream(text.split("\n"))
                .map(Component::literal)
                .map(Component.class::cast)
                .toList();
        return stack.getOrDefault(GTDataComponents.FORMAT_STRING_LIST, TextLineList.EMPTY).withLines(lines);
    }

    public String getPlaceholderText(ItemStack stack) {
        StringBuilder formatStringLines = new StringBuilder();
        List<Component> lines = stack.getOrDefault(GTDataComponents.FORMAT_STRING_LIST, TextLineList.EMPTY).lines();
        for (Component line : lines) {
            formatStringLines.append(line.getString()).append('\n');
        }
        return formatStringLines.toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Item.TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        if (isAdvanced.isAdvanced()) {
            tooltipComponents.add(Component.literal("Placeholder text:").withStyle(ChatFormatting.GOLD));
            tooltipComponents
                    .addAll(stack.getOrDefault(GTDataComponents.FORMAT_STRING_LIST, TextLineList.EMPTY).lines());
            tooltipComponents.add(Component.literal("Processed text:").withStyle(ChatFormatting.GOLD));
            tooltipComponents.addAll(getText(stack));
        }
    }
}
