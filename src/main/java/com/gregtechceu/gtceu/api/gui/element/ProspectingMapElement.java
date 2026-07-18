package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.gui.misc.PacketProspecting;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.ProspectingTexture;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.common.item.behavior.ProspectorScannerBehavior;
import com.gregtechceu.gtceu.common.network.packets.SPacketProspectingMapData;
import com.gregtechceu.gtceu.integration.map.WaypointManager;
import com.gregtechceu.gtceu.integration.map.cache.client.GTClientCache;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;
import com.gregtechceu.gtceu.integration.map.layer.builtin.OreRenderLayer;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ProspectingMapElement extends UIElement implements SearchComponent.ISearchUI<Object> {

    private final HeldItemUIHolder holder;
    private final int chunkRadius;
    private final ProspectorMode mode;
    private final int scanTick;
    private final int playerChunkX;
    private final int playerChunkZ;
    private final int playerBlockX;
    private final int playerBlockZ;
    private final float playerDirection;
    private final int imageHeight;
    private final int itemListWidth;
    private final GTScrollerViewElement itemList;
    private final SearchComponent<Object> searchComponent;
    @Getter
    private boolean darkMode;
    @OnlyIn(Dist.CLIENT)
    private ProspectingTexture texture;
    private int chunkIndex;
    private int serverTicks;
    private String selectedUniqueId = ProspectingTexture.SELECTED_ALL;
    private final Queue<PacketProspecting> packetQueue = new LinkedBlockingQueue<>();
    private final Set<Object> items = new CopyOnWriteArraySet<>();
    private final Map<String, GTButtonElement> selectedMap = new ConcurrentHashMap<>();
    private final Map<String, Object> itemByUniqueId = new ConcurrentHashMap<>();

    public ProspectingMapElement(int x, int y, int width, int height, int chunkRadius, @NotNull ProspectorMode mode,
                                 int scanTick, HeldItemUIHolder holder) {
        this.holder = holder;
        this.chunkRadius = chunkRadius;
        this.mode = mode;
        this.scanTick = Math.max(scanTick, 1);
        this.playerChunkX = holder.getPlayer().chunkPosition().x;
        this.playerChunkZ = holder.getPlayer().chunkPosition().z;
        this.playerBlockX = holder.getPlayer().getBlockX();
        this.playerBlockZ = holder.getPlayer().getBlockZ();
        this.playerDirection = holder.getPlayer().getVisualRotationYInDegrees();
        int imageWidth = (chunkRadius * 2 - 1) * 16;
        this.imageHeight = (chunkRadius * 2 - 1) * 16;
        int sidePanelWidth = width - (imageWidth + 10);
        this.itemListWidth = sidePanelWidth - 8;

        UITemplate.setLDLib2Bounds(this, x, y, width, height);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMapMouseDown);

        UIElement mapBackground = new UIElement();
        UITemplate.setLDLib2Bounds(mapBackground, 0, (height - imageHeight) / 2 - 4,
                imageWidth + 8, imageHeight + 8);
        mapBackground.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        addChild(mapBackground);

        UIElement sidePanel = new UIElement();
        UITemplate.setLDLib2Bounds(sidePanel, imageWidth + 10, 0, sidePanelWidth, height);
        sidePanel.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        addChild(sidePanel);

        itemList = new GTScrollerViewElement(4, 28, itemListWidth, height - 32);
        itemList.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        itemList.viewPort(viewPort -> {
            viewPort.layout(layout -> layout.paddingAll(0));
            viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        });
        itemList.verticalScroller(scroller -> {
            scroller.layout(layout -> layout.width(2));
            scroller.getStyle().backgroundTexture(ColorPattern.T_WHITE.rectTexture().setRadius(1));
        });
        sidePanel.addChild(itemList);

        searchComponent = new SearchComponent<>(this);
        searchComponent.setCandidateUIProvider(this::createSearchCandidate);
        searchComponent.searchStyle(style -> style
                .maxItemCount(8)
                .scrollerViewHeight(96)
                .closeAfterSelect(true));
        UITemplate.setLDLib2Bounds(searchComponent, 6, 6, sidePanelWidth - 12, 18);
        sidePanel.addChild(searchComponent);

        addNewItem(ProspectingTexture.SELECTED_ALL, Component.literal("all resources"), IGuiTexture.EMPTY, -1, null);
    }

    public boolean acceptsProspectingPacket(InteractionHand hand, ItemStack openedStack, ProspectorMode<?> packetMode) {
        return holder.getHand() == hand && mode == packetMode &&
                ItemStack.matches(holder.getOpenedStack(), openedStack);
    }

    public void receiveProspectingPacket(PacketProspecting packet) {
        packetQueue.add(packet);
        if (mode == ProspectorMode.FLUID && packet.data[0][0].length > 0) {
            GTClientCache.instance.addFluid(holder.getPlayer().level().dimension(), packet.chunkX, packet.chunkZ,
                    (ProspectorMode.FluidInfo) packet.data[0][0][0]);
        }
    }

    public void setDarkMode(boolean mode) {
        if (darkMode != mode) {
            darkMode = mode;
            if (texture != null) {
                texture.setDarkMode(darkMode);
            }
        }
    }

    @Override
    public void serverTick() {
        super.serverTick();
        var modularUI = getModularUI();
        if (modularUI == null || !(modularUI.player instanceof ServerPlayer player)) {
            return;
        }
        scanNextChunk(player);
        validateScannerEnergy(player);
    }

    private void scanNextChunk(ServerPlayer player) {
        if (serverTicks++ % scanTick != 0 || chunkIndex >= (chunkRadius * 2 - 1) * (chunkRadius * 2 - 1)) {
            return;
        }

        int row = chunkIndex / (chunkRadius * 2 - 1);
        int column = chunkIndex % (chunkRadius * 2 - 1);
        int ox = column - chunkRadius + 1;
        int oz = row - chunkRadius + 1;

        LevelChunk chunk = player.level().getChunk(playerChunkX + ox, playerChunkZ + oz);
        if (mode == ProspectorMode.ORE) {
            ServerCache.instance.prospectAllInChunk(player.level().dimension(), chunk.getPos(), player);
        }
        PacketProspecting packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, mode);
        mode.scan(packet.data, chunk);
        PacketDistributor.sendToPlayer(player,
                new SPacketProspectingMapData(holder.getHand(), holder.getOpenedStack(), packet));
        chunkIndex++;
    }

    private void validateScannerEnergy(ServerPlayer player) {
        ItemStack held = player.getItemInHand(holder.getHand());
        if (!(held.getItem() instanceof IComponentItem componentItem)) {
            return;
        }
        for (var component : componentItem.getComponents()) {
            if (component instanceof ProspectorScannerBehavior prospector &&
                    !player.isCreative() &&
                    !prospector.drainEnergy(held, false)) {
                player.closeContainer();
                return;
            }
        }
    }

    @Override
    public void screenTick() {
        super.screenTick();
        ensureTexture();
        int max = 10;
        while (max-- > 0 && !packetQueue.isEmpty()) {
            PacketProspecting packet = packetQueue.poll();
            if (packet != null && texture != null) {
                texture.updateTexture(packet);
                addOresToList(packet.data);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void ensureTexture() {
        if (texture == null) {
            texture = new ProspectingTexture(playerChunkX, playerChunkZ, playerBlockX, playerBlockZ, playerDirection,
                    mode, chunkRadius, darkMode);
        }
    }

    private void addOresToList(Object[][][] data) {
        var newItems = new HashSet<>();
        for (int x = 0; x < mode.cellSize; x++) {
            for (int z = 0; z < mode.cellSize; z++) {
                for (var item : data[x][z]) {
                    newItems.add(item);
                    addNewItem(mode.getUniqueID(item), mode.getDescription(item), mode.getItemIcon(item),
                            mode.getItemColor(item), item);
                }
            }
        }
        items.addAll(newItems);
    }

    private void addNewItem(String uniqueID, MutableComponent renderingName, IGuiTexture icon, int color,
                            @Nullable Object item) {
        if (selectedMap.containsKey(uniqueID)) {
            return;
        }
        int index = selectedMap.size();
        GTButtonElement button = new GTButtonElement();
        button.noText();
        button.buttonStyle(style -> {
            style.baseTexture(rowBaseTexture(uniqueID));
            style.hoverTexture(ColorPattern.T_GRAY.rectTexture());
            style.pressedTexture(ColorPattern.WHITE.borderTexture(-1));
        });
        button.setOnClick(event -> selectUniqueId(uniqueID));
        UITemplate.setLDLib2Bounds(button, 0, index * 15, itemListWidth - 4, 15);

        UIElement iconElement = new UIElement();
        UITemplate.setLDLib2Bounds(iconElement, 0, 0, 15, 15);
        iconElement.style(style -> style.backgroundTexture(icon));
        button.addChild(iconElement);

        GTLabelElement label = new GTLabelElement(15, 0, itemListWidth - 19, 15, renderingName);
        label.setTextAlignHorizontal(Horizontal.LEFT);
        label.setTextAlignVertical(Vertical.CENTER);
        label.setTextShadow(false);
        label.setTextColor(color == -1 ? 0x404040 : color);
        button.addChild(label);

        itemList.addScrollViewChild(button);
        selectedMap.put(uniqueID, button);
        if (item != null) {
            itemByUniqueId.put(uniqueID, item);
        }
        updateListHeight();
    }

    private void updateListHeight() {
        int height = Math.max(15, selectedMap.size() * 15);
        itemList.viewContainer.layout(layout -> layout.height(height));
    }

    private IGuiTexture rowBaseTexture(String uniqueID) {
        return selectedUniqueId.equals(uniqueID) ? ColorPattern.WHITE.borderTexture(-1) : IGuiTexture.EMPTY;
    }

    private void selectUniqueId(String uniqueID) {
        selectedUniqueId = uniqueID;
        if (texture != null) {
            texture.setSelected(uniqueID);
        }
        for (var entry : selectedMap.entrySet()) {
            entry.getValue().buttonStyle(style -> style.baseTexture(rowBaseTexture(entry.getKey())));
        }
        if (ProspectingTexture.SELECTED_ALL.equals(uniqueID)) {
            searchComponent.setValue(null, false);
        }
    }

    private UIElement createSearchCandidate(@Nullable Object item) {
        if (item == null) {
            return new GTLabelElement(Component.translatable("text_field.empty"));
        }
        UIElement row = new UIElement();
        row.layout(layout -> {
            layout.widthPercent(100);
            layout.height(15);
        });

        UIElement iconElement = new UIElement();
        UITemplate.setLDLib2Bounds(iconElement, 0, 0, 15, 15);
        iconElement.style(style -> style.backgroundTexture(mode.getItemIcon(item)));
        row.addChild(iconElement);

        GTLabelElement label = new GTLabelElement(15, 0, 100, 15, mode.getDescription(item));
        label.layout(layout -> layout.widthPercent(100));
        label.setTextAlignHorizontal(Horizontal.LEFT);
        label.setTextAlignVertical(Vertical.CENTER);
        label.setTextShadow(false);
        row.addChild(label);

        return row;
    }

    @Override
    public @NotNull String resultText(@NotNull Object value) {
        return mode.getDescription(value).getString();
    }

    @Override
    public void onResultSelected(@Nullable Object value) {
        if (value != null) {
            selectUniqueId(mode.getUniqueID(value));
        }
    }

    @Override
    public void search(String word, IResultHandler<Object> searchHandler) {
        var added = new HashSet<String>();
        for (var item : items) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            var id = mode.getUniqueID(item);
            if (added.contains(id)) {
                continue;
            }
            added.add(id);
            var localized = LocalizationUtils.format(resultText(item));
            if (item.toString().toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT)) ||
                    localized.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))) {
                itemByUniqueId.put(id, item);
                searchHandler.acceptResult(item);
            }
        }
    }

    @Override
    public void drawBackgroundAdditional(@NotNull GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
    }

    @Override
    public void drawBackgroundOverlay(@NotNull GUIContext guiContext) {
        super.drawBackgroundOverlay(guiContext);
        if (texture == null) {
            return;
        }
        int x = getMapX();
        int y = getMapY();
        texture.draw(guiContext.graphics, x, y);
        int cX = (guiContext.mouseX - x) / 16;
        int cZ = (guiContext.mouseY - y) / 16;
        if (isInsideMap(cX, cZ)) {
            DrawerHelper.drawSolidRect(guiContext.graphics, cX * 16 + x, cZ * 16 + y, 16, 16, 0x4B6C6C6C);
        }

        if (!isInsideMap(cX, cZ)) {
            return;
        }
        List<Component> tooltips = new ArrayList<>();
        tooltips.add(Component.translatable(mode.unlocalizedName));
        List<Object[]> tooltipItems = new ArrayList<>();
        for (int i = 0; i < mode.cellSize; i++) {
            for (int j = 0; j < mode.cellSize; j++) {
                Object[] hovered = texture.data[cX * mode.cellSize + i][cZ * mode.cellSize + j];
                if (hovered != null) {
                    tooltipItems.add(hovered);
                }
            }
        }
        mode.appendTooltips(tooltipItems, tooltips, texture.getSelected());
        guiContext.modularUI.setHoverTooltip(tooltips, ItemStack.EMPTY, null, null);
    }

    private boolean isInsideMap(int cX, int cZ) {
        return cX >= 0 && cZ >= 0 && cX < chunkRadius * 2 - 1 && cZ < chunkRadius * 2 - 1;
    }

    private int getMapX() {
        return Math.round(getPositionX()) + 3;
    }

    private int getMapY() {
        return Math.round(getPositionY() + (getSizeHeight() - imageHeight) / 2 - 1);
    }

    private void onMapMouseDown(UIEvent event) {
        if (event.button != 0 || texture == null) {
            return;
        }
        var clickedItem = getClickedVein(event.x, event.y);
        if (clickedItem == null) {
            return;
        }
        event.stopPropagation();
        if (!WaypointManager.isActive()) {
            return;
        }
        MutableComponent veinName = Component.literal(clickedItem.name());
        veinName.setStyle(veinName.getStyle().withColor(clickedItem.color));
        WaypointManager.setWaypoint(new ChunkPos(clickedItem.position).toString(),
                clickedItem.name,
                clickedItem.color,
                holder.getPlayer().level().dimension(),
                clickedItem.position.getX(), clickedItem.position.getY(), clickedItem.position.getZ());
        holder.getPlayer().displayClientMessage(
                Component.translatable("behavior.prospector.added_waypoint", veinName), false);
        UISoundUtils.playButtonClickSound();
    }

    @Nullable
    private WaypointItem getClickedVein(double mouseX, double mouseY) {
        int x = getMapX();
        int y = getMapY();

        int cX = (int) (mouseX - x) / 16;
        int cZ = (int) (mouseY - y) / 16;
        if (!isInsideMap(cX, cZ)) {
            return null;
        }
        int offsetX = Math.abs((int) (mouseX - x) % 16);
        int offsetZ = Math.abs((int) (mouseY - y) % 16);
        int xDiff = cX - (chunkRadius - 1);
        int zDiff = cZ - (chunkRadius - 1);

        int xPos = ((holder.getPlayer().chunkPosition().x + xDiff) << 4) + offsetX;
        int zPos = ((holder.getPlayer().chunkPosition().z + zDiff) << 4) + offsetZ;
        var blockPos = new BlockPos(xPos,
                holder.getPlayer().level().getHeight(Heightmap.Types.WORLD_SURFACE, xPos, zPos), zPos);

        if (!texture.getSelected().equals(ProspectingTexture.SELECTED_ALL)) {
            Object selectedItem = itemByUniqueId.get(texture.getSelected());
            if (selectedItem != null) {
                var name = mode.getDescription(selectedItem).getString();
                var color = mode.getItemColor(selectedItem);
                return new WaypointItem(blockPos, name, color);
            }
        }

        var hoveredItem = texture.data[cX * mode.cellSize + (offsetX * mode.cellSize / 16)][cZ * mode.cellSize +
                (offsetZ * mode.cellSize / 16)];
        if (hoveredItem != null && hoveredItem.length != 0) {
            var name = mode.getDescription(hoveredItem[0]).getString();
            var color = mode.getItemColor(hoveredItem[0]);
            return new WaypointItem(blockPos, name, color);
        }

        var vein = GTClientCache.instance.getNearbyVeins(holder.getPlayer().level().dimension(), blockPos, 32);
        if (!vein.isEmpty()) {
            vein.sort((o1, o2) -> (int) (o1.center().distToCenterSqr(xPos, o1.center().getY(), zPos) -
                    o2.center().distToCenterSqr(xPos, o2.center().getY(), zPos)));
            var name = OreRenderLayer.getName(vein.getFirst()).getString();
            var materials = vein.getFirst().definition().value().veinGenerator().getAllMaterials();
            var mostCommonItem = materials.getLast();
            var color = mostCommonItem.getMaterialRGB();
            return new WaypointItem(blockPos, name, color);
        }

        return new WaypointItem(blockPos, "Depleted Vein", 0x990000);
    }

    private record WaypointItem(BlockPos position, String name, int color) {}
}
