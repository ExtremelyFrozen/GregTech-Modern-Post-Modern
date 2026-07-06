package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTSceneElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockPreviewLevel;
import com.gregtechceu.gtceu.api.multiblock.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;
import com.gregtechceu.gtceu.integration.xei.handlers.item.CycleItemEntryHandler;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.utils.data.ItemStackKey;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.screen.RecipeScreen;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public class PatternPreviewWidget extends UIElement {

    private boolean isLoaded;
    private static MultiblockPreviewLevel LEVEL;
    private static final int REGION_SIZE = 512;
    private static int LAST_OFFSET_INDEX = 0;
    private static final Map<MultiblockMachineDefinition, MBPattern[]> CACHE = new HashMap<>();
    private final GTSceneElement scene;
    private final GTScrollerViewElement scrollableView;
    private final GTButtonElement pageButton;
    private final GTButtonElement layerButton;
    public final MultiblockMachineDefinition controllerDefinition;
    private final MBPattern[] patterns;
    private final List<SimplePredicate> predicates;
    private int index;
    public int layer;
    private GTItemSlotElement[] materialSlots = new GTItemSlotElement[0];
    private GTItemSlotElement[] candidates = new GTItemSlotElement[0];

    protected PatternPreviewWidget(MultiblockMachineDefinition controllerDefinition) {
        layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(160);
            layout.height(160);
        });
        setOverflowVisible(false);
        this.controllerDefinition = controllerDefinition;
        predicates = new ArrayList<>();
        layer = -1;

        scene = new GTSceneElement(3, 3, 150, 150);
        scene.createScene(LEVEL);
        scene.setOnSelected(this::onPosSelected);
        scene.setRenderFacing(false);
        addChild(scene);

        scrollableView = new GTScrollerViewElement(3, 132, 154, 22);
        scrollableView.scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        scrollableView.viewPort(viewPort -> {
            viewPort.layout(layout -> layout.paddingAll(0));
            viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        });
        scrollableView.horizontalScroller(scroller -> {
            scroller.layout(layout -> layout.height(4));
            scroller.getStyle().backgroundTexture(GuiTextures.SLIDER_BACKGROUND);
        });
        addChild(scrollableView);

        if (ConfigHolder.INSTANCE.client.useVBO) {
            if (!RenderSystem.isOnRenderThread()) {
                RenderSystem.recordRenderCall(scene::useCacheBuffer);
            } else {
                scene.useCacheBuffer();
            }
        }

        addChild(createTitle());

        synchronized (CACHE) {
            this.patterns = CACHE.computeIfAbsent(controllerDefinition, definition -> {
                HashSet<ItemStackKey> drops = new HashSet<>();
                drops.add(ItemStackKey.of(this.controllerDefinition.asStack()));
                return controllerDefinition.getMatchingShapes().stream()
                        .map(it -> initializePattern(it, drops))
                        .filter(Objects::nonNull)
                        .toArray(MBPattern[]::new);
            });
        }

        pageButton = createButton(138, 30);
        pageButton.setOnClick(event -> {
            if (patterns.length > 0) {
                setPage(index + 1 >= patterns.length ? 0 : index + 1);
            }
            event.stopPropagation();
        });
        addChild(pageButton);

        layerButton = createButton(138, 50);
        layerButton.setOnClick(event -> {
            updateLayer();
            event.stopPropagation();
        });
        addChild(layerButton);

        setPage(0);
    }

    public static ModularUI createModularUI(MultiblockMachineDefinition controllerDefinition) {
        return ModularUI.of(UI.of(getPatternWidget(controllerDefinition)));
    }

    private GTLabelElement createTitle() {
        GTLabelElement title = new GTLabelElement(3, 3, 154, 10);
        title.setValue(Component.translatable(controllerDefinition.getDescriptionId()));
        title.textStyle(style -> {
            style.textColor(-1);
            style.textShadow(true);
        });
        return title;
    }

    private static GTButtonElement createButton(int x, int y) {
        GTButtonElement button = new GTButtonElement(x, y, 18, 18);
        button.textStyle(style -> {
            style.textColor(-1);
            style.textShadow(false);
        });
        button.setButtonTextures(ColorPattern.T_GRAY.rectTexture(),
                GuiTextures.group(ColorPattern.T_GRAY.rectTexture(), GuiTextures.colorRect(0x4fffffff)),
                ColorPattern.T_GRAY.rectTexture());
        return button;
    }

    private void updateLayer() {
        if (patterns.length == 0) {
            return;
        }
        MBPattern pattern = patterns[index];
        if (layer + 1 >= -1 && layer + 1 <= pattern.maxY - pattern.minY) {
            layer += 1;
            if (pattern.controllerBase.isFormed()) {
                onFormedSwitch(false);
            }
        } else {
            layer = -1;
            if (!pattern.controllerBase.isFormed()) {
                onFormedSwitch(true);
            }
        }
        setupScene(pattern);
        updateButtonText();
    }

    private void setupScene(MBPattern pattern) {
        Stream<BlockPos> stream = pattern.blockMap.keySet().stream()
                .filter(pos -> layer == -1 || layer + pattern.minY == pos.getY());
        if (pattern.controllerBase.isFormed()) {
            LongSet modelDisabled = pattern.controllerBase
                    .getMultiblockState(MultiblockControllerMachine.DEFAULT_STRUCTURE)
                    .getMatchContext()
                    .getOrDefault("renderMask", LongSets.EMPTY_SET);
            if (!modelDisabled.isEmpty()) {
                stream = stream.filter(pos -> !modelDisabled.contains(pos.asLong()));
            }
        }
        scene.setRenderedCore(stream.toList(), null);
    }

    public static PatternPreviewWidget getPatternWidget(MultiblockMachineDefinition controllerDefinition) {
        if (LEVEL == null) {
            if (Minecraft.getInstance().level == null) {
                GTCEu.LOGGER.error("Try to init pattern previews before level load");
                throw new IllegalStateException();
            }
            LEVEL = new MultiblockPreviewLevel();
        }
        return new PatternPreviewWidget(controllerDefinition);
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public void setPage(int index) {
        if (index >= patterns.length || index < 0) return;
        this.index = index;
        this.layer = -1;
        MBPattern pattern = patterns[index];
        setupScene(pattern);
        clearMaterialSlots();
        clearCandidateSlots();
        materialSlots = new GTItemSlotElement[Math.min(pattern.parts.size(), 18)];
        var itemHandler = CycleItemEntryHandler.createFromStacks(pattern.parts);
        int xOffset = 0;
        for (int i = 0; i < materialSlots.length; i++) {
            int padding = 1;
            if (itemHandler.getStackInSlot(i).getCount() / 100_000 >= 1) {
                padding = 10;
            } else if (itemHandler.getStackInSlot(i).getCount() / 10_000 >= 1) {
                padding = 7;
            } else if (itemHandler.getStackInSlot(i).getCount() / 1_000 >= 1) {
                padding = 4;
            }

            materialSlots[i] = createPreviewSlot(itemHandler, i, 4 + xOffset + padding, 0)
                    .setItemCountDecorationXOffset(PatternPreviewWidget::getCountDecorationXOffset);
            xOffset += 18 + (2 * padding);
            scrollableView.addScrollViewChild(materialSlots[i]);
        }
        int finalWidth = Math.max(154, xOffset + 18);
        scrollableView.viewContainer.layout(layout -> {
            layout.width(finalWidth);
            layout.height(18);
        });
        updateButtonText();
    }

    private void onFormedSwitch(boolean isFormed) {
        MBPattern pattern = patterns[index];
        MultiblockControllerMachine controllerBase = pattern.controllerBase;
        if (isFormed) {
            this.layer = -1;
            loadControllerFormed(pattern.blockMap.keySet(), controllerBase);
        } else {
            scene.setRenderedCore(pattern.blockMap.keySet(), null);
            controllerBase.invalidateStructure(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        }
    }

    private void onPosSelected(BlockPos pos, Direction facing) {
        if (index >= patterns.length || index < 0) return;
        TraceabilityPredicate predicate = patterns[index].predicateMap.get(pos);
        if (predicate != null) {
            predicates.clear();
            predicates.addAll(predicate.common);
            predicates.addAll(predicate.limited);
            predicates.removeIf(p -> p == null || p.candidates == null); // why it happens?
            clearCandidateSlots();
            List<List<ItemStack>> candidateStacks = new ArrayList<>();
            List<List<Component>> predicateTips = new ArrayList<>();
            for (SimplePredicate simplePredicate : predicates) {
                List<ItemStack> itemStacks = simplePredicate.getCandidates();
                if (!itemStacks.isEmpty()) {
                    candidateStacks.add(itemStacks);
                    predicateTips.add(simplePredicate.getToolTips(predicate));
                }
            }
            candidates = new GTItemSlotElement[candidateStacks.size()];
            CycleItemEntryHandler itemHandler = CycleItemEntryHandler.createFromStacks(candidateStacks);
            int maxCol = (160 - (((materialSlots.length - 1) / 9 + 1) * 18) - 35) % 18;
            if (maxCol <= 0) {
                maxCol = 1;
            }
            for (int i = 0; i < candidateStacks.size(); i++) {
                int finalI = i;
                candidates[i] = createPreviewSlot(itemHandler, i, 3 + (i / maxCol) * 18, 3 + (i % maxCol) * 18)
                        .setBackgroundTexture(GuiTextures.colorRect(0x4fffffff))
                        .setOnAddedTooltips((slot, list) -> list.addAll(predicateTips.get(finalI)));
                addChild(candidates[i]);
            }
        }
    }

    private GTItemSlotElement createPreviewSlot(CycleItemEntryHandler itemHandler, int slot, int x, int y) {
        GTItemSlotElement element = new GTItemSlotElement(itemHandler, slot);
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(18);
            layout.height(18);
        });
        element.setCanTakeItems(false);
        element.setCanPutItems(false);
        element.setIngredientIO(GTXEIHelper.input());
        element.setBackgroundTexture(ColorPattern.T_GRAY.rectTexture());
        element.xeiRecipeIngredient();
        element.xeiRecipeSlot();
        return element;
    }

    private void clearMaterialSlots() {
        scrollableView.clearAllScrollViewChildren();
        materialSlots = new GTItemSlotElement[0];
    }

    private void clearCandidateSlots() {
        for (GTItemSlotElement candidate : candidates) {
            removeChild(candidate);
        }
        candidates = new GTItemSlotElement[0];
    }

    private void updateButtonText() {
        pageButton.setText("P:" + index);
        layerButton.setText(layer >= 0 ? "L:" + layer : "ALL");
    }

    private static int getCountDecorationXOffset(ItemStack itemStack) {
        int count = itemStack.getCount();
        if (count >= 100_000) {
            return 9;
        }
        if (count >= 10_000) {
            return 6;
        }
        if (count >= 1_000) {
            return 3;
        }
        return 0;
    }

    /**
     * Finds the next section of the dummy preview level to place a multiblock at in a spiral pattern.
     * <p>
     * This results in positions that are considerably closer to the world origin than
     * the one it replaces, which did {@code prevPos.offset(500, 0, 500)},
     * which results in absurdly high offsets for the later multiblocks.
     * </p>
     * The regions being closer to {@code (0,0)} means that Z-fighting should be less likely,
     * since floating point inaccuracies won't be as large of a factor.
     *
     * @return the area to place the current multiblock at
     */
    public static BlockPos locateNextRegion() {
        int currentIndex = LAST_OFFSET_INDEX++;

        // Origin coordinates scaled back to the offset value, from global
        int x = 0, z = 0;
        if (currentIndex > 0) {
            int v = (int) (Mth.sqrt(currentIndex + 0.25f) - 0.5f);
            int nextV = v + 1;
            int spiralBaseIndex = v * nextV;
            // this is 1 or -1 depending on if v is odd or even
            int flipFlop = (v & 1) * 2 - 1;

            int offset = flipFlop * nextV / 2;
            x += offset;
            z += offset;

            int cornerIndex = spiralBaseIndex + nextV;
            if (currentIndex < cornerIndex) {
                x -= flipFlop * (currentIndex - spiralBaseIndex + 1);
            } else {
                x -= flipFlop * nextV;
                z -= flipFlop * (currentIndex - cornerIndex + 1);
            }
        }
        return new BlockPos(x * REGION_SIZE, 50, z * REGION_SIZE);
    }

    @Override
    public void screenTick() {
        super.screenTick();
        // I can only think of this way
        if (!isLoaded && GTCEu.Mods.isEMILoaded() && Minecraft.getInstance().screen instanceof RecipeScreen) {
            setPage(0);
            isLoaded = true;
        }
    }

    @Override
    public void drawBackgroundAdditional(@NotNull GUIContext guiContext) {
        RenderSystem.enableBlend();
        super.drawBackgroundAdditional(guiContext);
    }

    private MBPattern initializePattern(MultiblockShapeInfo shapeInfo, HashSet<ItemStackKey> blockDrops) {
        Map<BlockPos, MultiblockBlockInfo> blockMap = new HashMap<>();
        MultiblockControllerMachine controllerBase = null;
        Set<BlockEntity> blockEntitiesToAdd = new HashSet<>();
        BlockPos multiPos = locateNextRegion();

        MultiblockBlockInfo[][][] blocks = shapeInfo.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            MultiblockBlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                MultiblockBlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    BlockState blockState = column[z].getBlockState();
                    BlockPos pos = multiPos.offset(x, y, z);
                    if (column[z].getBlockEntity(pos,
                            LEVEL.getLevel().registryAccess()) instanceof MultiblockControllerMachine controller) {
                        controller.setLevel(LEVEL);
                        blockEntitiesToAdd.add(controller);
                        controllerBase = controller;
                    }
                    blockMap.put(pos, MultiblockBlockInfo.fromBlockState(blockState));
                }
            }
        }

        blockMap.forEach(LEVEL::addBlock);
        for (BlockEntity blockEntity : blockEntitiesToAdd) {
            LEVEL.setInnerBlockEntity(blockEntity);
        }

        Map<ItemStackKey, PartInfo> parts = gatherBlockDrops(blockMap);
        blockDrops.addAll(parts.keySet());

        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        if (controllerBase != null) {
            loadControllerFormed(predicateMap.keySet(), controllerBase);
            predicateMap = controllerBase.getMultiblockState(MultiblockControllerMachine.DEFAULT_STRUCTURE)
                    .getMatchContext()
                    .get("predicates");
        }
        return controllerBase == null ? null : new MBPattern(blockMap, parts.values().stream().sorted((one, two) -> {
            if (one.isController) return -1;
            if (two.isController) return +1;
            if (one.isTile && !two.isTile) return -1;
            if (two.isTile && !one.isTile) return +1;
            if (one.blockId != two.blockId) return two.blockId - one.blockId;
            return two.amount - one.amount;
        }).map(PartInfo::getItemStack).filter(list -> !list.isEmpty()).collect(Collectors.toList()), predicateMap,
                controllerBase);
    }

    private void loadControllerFormed(Collection<BlockPos> positions, MultiblockControllerMachine controllerBase) {
        BlockPattern pattern = controllerBase.getPattern(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        if (pattern != null &&
                pattern.checkPatternAt(controllerBase.getMultiblockState(MultiblockControllerMachine.DEFAULT_STRUCTURE),
                        true)) {
            controllerBase.formStructure(MultiblockControllerMachine.DEFAULT_STRUCTURE);
        }
        if (controllerBase.isFormed()) {
            LongSet modelDisabled = controllerBase.getMultiblockState(MultiblockControllerMachine.DEFAULT_STRUCTURE)
                    .getMatchContext()
                    .getOrDefault("renderMask", LongSets.EMPTY_SET);
            if (!modelDisabled.isEmpty()) {
                positions = new HashSet<>(positions);
                positions.removeIf(pos -> modelDisabled.contains(pos.asLong()));
            }
            scene.setRenderedCore(positions, null);
        } else {
            GTCEu.LOGGER.warn("Pattern formed checking failed: {}", controllerBase.self().getDefinition());
        }
    }

    private Map<ItemStackKey, PartInfo> gatherBlockDrops(Map<BlockPos, MultiblockBlockInfo> blocks) {
        Map<ItemStackKey, PartInfo> partsMap = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<BlockPos, MultiblockBlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState blockState = PatternPreviewWidget.LEVEL.getBlockState(pos);
            ItemStack itemStack = blockState.getBlock().getCloneItemStack(PatternPreviewWidget.LEVEL, pos, blockState);

            if (itemStack.isEmpty() && !blockState.getFluidState().isEmpty()) {
                Fluid fluid = blockState.getFluidState().getType();
                itemStack = fluid.getBucket().getDefaultInstance();
            }

            ItemStackKey itemStackKey = ItemStackKey.of(itemStack);
            partsMap.computeIfAbsent(itemStackKey, key -> new PartInfo(key, entry.getValue())).amount++;
        }
        return partsMap;
    }

    private static class PartInfo {

        final ItemStackKey itemStackKey;
        boolean isController = false;
        boolean isTile;
        final int blockId;
        int amount = 0;

        PartInfo(final ItemStackKey itemStackKey, final MultiblockBlockInfo blockInfo) {
            this.itemStackKey = itemStackKey;
            this.blockId = Block.getId(blockInfo.getBlockState());
            this.isTile = blockInfo.hasBlockEntity();

            if (blockInfo.getBlockState().getBlock() instanceof MetaMachineBlock block) {
                if (block.definition instanceof MultiblockMachineDefinition)
                    this.isController = true;
            }
        }

        public List<ItemStack> getItemStack() {
            return Arrays.stream(itemStackKey.getItemStack())
                    .map(itemStack -> {
                        var item = itemStack.copy();
                        item.setCount(amount);
                        return item;
                    }).filter((ItemStack item) -> !item.isEmpty()).toList();
        }
    }

    public static class MBPattern {

        @NotNull
        final List<List<ItemStack>> parts;
        @NotNull
        final Map<BlockPos, TraceabilityPredicate> predicateMap;
        @NotNull
        final Map<BlockPos, MultiblockBlockInfo> blockMap;
        @NotNull
        final MultiblockControllerMachine controllerBase;
        final int maxY, minY;

        public MBPattern(@NotNull Map<BlockPos, MultiblockBlockInfo> blockMap, @NotNull List<List<ItemStack>> parts,
                         @NotNull Map<BlockPos, TraceabilityPredicate> predicateMap,
                         @NotNull MultiblockControllerMachine controllerBase) {
            this.parts = parts;
            this.blockMap = blockMap;
            this.predicateMap = predicateMap;
            this.controllerBase = controllerBase;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (BlockPos pos : blockMap.keySet()) {
                min = Math.min(min, pos.getY());
                max = Math.max(max, pos.getY());
            }
            minY = min;
            maxY = max;
        }
    }
}
