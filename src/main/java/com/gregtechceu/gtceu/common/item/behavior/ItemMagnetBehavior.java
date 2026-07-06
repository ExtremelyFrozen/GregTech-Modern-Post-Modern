package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.TagItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.item.component.IItemLifeCycle;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tterrag.registrate.util.entry.ItemEntry;
import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Triplet;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ItemMagnetBehavior implements IInteractionItem, IItemLifeCycle, IAddInformation, IItemUIFactory {

    private static final ResourceLocation SET_MAGNET_FILTER_ACTION = GTCEu.id("set_magnet_filter");

    private final int range;
    private final long energyDraw;

    public ItemMagnetBehavior(int range) {
        this.range = range;
        this.energyDraw = GTValues.V[range > 8 ? GTValues.HV : GTValues.LV];
    }

    static {
        NeoForge.EVENT_BUS.register(ItemMagnetBehavior.class);
        SyncActionDispatchers.server().register(new MagnetFilterActionHandler());
    }

    @Override
    public ModularUI createUI(HeldItemUIHolder holder, Player entityPlayer) {
        final ItemStack held = holder.getHeld();
        MagnetComponent magnetData = held.getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY);
        Filter selected = magnetData.filterType();

        HashSet<Triplet<Filter, Widget, Widget>> widgets = new HashSet<>();
        HashMap<Filter, ItemFilter> filters = new HashMap<>();
        ModularUI ui = new ModularUI(176, 157, holder, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new EnumSelectorWidget<>(146, 5, 20, 20,
                        Filter.values(), selected, (val) -> updateSelection(held, val, widgets)))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 75, true));
        for (Filter f : Filter.values()) {
            ItemStack stack = f.getFilter(held);
            ItemFilter filter = ItemFilter.loadFilter(stack);
            filters.put(f, filter);
            LabelWidget description = new LabelWidget(5, 5, stack.getDescriptionId());
            WidgetGroup config = filter.openConfigurator((176 - 80) / 2, (60 - 55) / 2 + 15);
            boolean visible = f == selected;
            description.setVisible(visible);
            config.setVisible(visible);
            widgets.add(new Triplet<>(f, description, config));
            ui.widget(description);
            ui.widget(config);
        }
        ui.registerCloseListener(() -> {
            Filter selection = magnetData.filterType();
            selection.saveFilter(held, filters.get(selection));
        });
        return ui;
    }

    @Override
    public boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        ItemStack held = holder.getHeld();
        return isMagnet(held) && ItemStack.isSameItem(held, holder.getOpenedStack()) &&
                held.getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY)
                        .filterType()
                        .loadFilter(held)
                        .supportsLDLib2Configurator();
    }

    @Override
    public boolean isLDLib2UIStillValid(HeldItemUIHolder holder, Player entityPlayer) {
        return isMagnet(holder.getHeld()) && ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack());
    }

    @Override
    public UI createLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        ItemStack held = holder.getHeld();
        Filter selected = held.getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY).filterType();
        List<LDLib2FilterPanel> panels = new ArrayList<>();

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 157);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        for (Filter filterType : Filter.values()) {
            LDLib2FilterPanel panel = createLDLib2FilterPanel(holder, held, filterType, filterType == selected);
            panels.add(panel);
            root.addChild(panel.panel());
        }
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 75, true));
        root.addChild(createLDLib2FilterButton(holder, selected, panels));
        return UI.of(root);
    }

    private void updateSelection(ItemStack stack, Filter filter, Collection<Triplet<Filter, Widget, Widget>> widgets) {
        stack.update(GTDataComponents.MAGNET, MagnetComponent.EMPTY, c -> new MagnetComponent(c.active(), filter));
        widgets.forEach(tri -> {
            var visible = tri.getA() == filter;
            tri.getB().setVisible(visible);
            tri.getC().setVisible(visible);
        });
    }

    private static GTButtonElement createLDLib2FilterButton(HeldItemUIHolder holder, Filter selected,
                                                            List<LDLib2FilterPanel> panels) {
        GTButtonElement button = new GTButtonElement();
        button.noText();
        updateLDLib2FilterButton(button, selected);
        button.setOnClick(event -> {
            Filter next = nextFilter(holder.getHeld()
                    .getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY)
                    .filterType());
            setLDLib2FilterSelection(holder, next, panels, button);
        });
        UITemplate.setLDLib2Bounds(button, 146, 5, 20, 20);
        return button;
    }

    private static LDLib2FilterPanel createLDLib2FilterPanel(HeldItemUIHolder holder, ItemStack held, Filter filterType,
                                                             boolean visible) {
        UIElement panel = new UIElement();
        UITemplate.setLDLib2Bounds(panel, 0, 0, 176, 75);
        panel.setVisible(visible);

        ItemStack filterStack = filterType.getFilter(held);
        ItemFilter filter = ItemFilter.loadFilter(filterStack);
        filter.setOnUpdated(updated -> filterType.saveFilter(holder.getHeld(), updated));
        panel.addChild(createLDLib2Label(filterStack.getDescriptionId()));
        panel.addChild(filter.openLDLib2Configurator((176 - 80) / 2, (60 - 55) / 2 + 15));
        return new LDLib2FilterPanel(filterType, panel);
    }

    private static TextElement createLDLib2Label(String descriptionId) {
        TextElement label = new TextElement();
        label.setText(descriptionId, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        UITemplate.setLDLib2Bounds(label, 5, 5, 136, 10);
        return label;
    }

    private static void setLDLib2FilterSelection(HeldItemUIHolder holder, Filter filter,
                                                 Collection<LDLib2FilterPanel> panels, GTButtonElement button) {
        ItemStack held = holder.getHeld();
        setMagnetFilter(held, filter);
        for (LDLib2FilterPanel panel : panels) {
            panel.panel().setVisible(panel.filter() == filter);
        }
        updateLDLib2FilterButton(button, filter);
        if (holder.getPlayer().level().isClientSide()) {
            HeldItemUIHelper.sendAction(holder, createSetMagnetFilterAction(held, filter));
        }
    }

    private static void updateLDLib2FilterButton(GTButtonElement button, Filter filter) {
        IGuiTexture texture = GuiTextures.group(GuiTextures.VANILLA_BUTTON, filter.getIcon());
        button.setButtonTexture(texture);
        button.style(style -> style.tooltips(Component.translatable(filter.getTooltip())));
    }

    private static Filter nextFilter(Filter filter) {
        Filter[] values = Filter.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == filter) {
                return values[(i + 1) % values.length];
            }
        }
        throw new IllegalStateException("Unknown magnet filter: " + filter);
    }

    private static void setMagnetFilter(ItemStack stack, Filter filter) {
        stack.update(GTDataComponents.MAGNET, MagnetComponent.EMPTY,
                current -> new MagnetComponent(current.active(), filter));
    }

    private static SyncActionData createSetMagnetFilterAction(ItemStack stack, Filter filter) {
        MagnetComponent current = stack.getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY);
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.MAGNET.get(), new MagnetComponent(current.active(), filter))
                .build();
        return new SyncActionData(SET_MAGNET_FILTER_ACTION, filter.ordinal(), payload);
    }

    private static boolean isKnownFilter(Filter filter) {
        for (Filter value : Filter.values()) {
            if (value == filter) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(ItemStack item, Level world, @NotNull Player player,
                                                  InteractionHand hand) {
        if (!player.level().isClientSide && player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable(toggleActive(player.getItemInHand(hand)) ?
                    "behavior.item_magnet.enabled" : "behavior.item_magnet.disabled"), true);
        } else {
            IItemUIFactory.super.use(item, world, player, hand);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static boolean isActive(ItemStack stack) {
        if (stack == ItemStack.EMPTY) {
            return false;
        }
        return stack.getOrDefault(GTDataComponents.MAGNET, MagnetComponent.EMPTY).active();
    }

    private static boolean toggleActive(ItemStack stack) {
        MutableBoolean active = new MutableBoolean();
        stack.update(GTDataComponents.MAGNET, MagnetComponent.EMPTY,
                c -> {
                    active.setValue(!c.active);
                    return new MagnetComponent(!c.active, c.filterType);
                });
        return active.booleanValue();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Adapted logic from Draconic Evolution
        // https://github.com/Draconic-Inc/Draconic-Evolution/blob/1.12.2/src/main/java/com/brandon3055/draconicevolution/items/tools/Magnet.java
        if (!entity.isShiftKeyDown() && entity.tickCount % 10 == 0 && isActive(stack) &&
                entity instanceof Player player) {
            Level world = entity.level();
            if (!drainEnergy(true, stack, energyDraw)) {
                return;
            }

            List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class,
                    new AABB(entity.getX(), entity.getY(), entity.getZ(), entity.getX(), entity.getY(), entity.getZ())
                            .inflate(range, range, range));

            ItemFilter filter = null;
            boolean didMoveEntity = false;
            for (ItemEntity itemEntity : items) {
                if (itemEntity.isRemoved()) {
                    continue;
                }

                CompoundTag itemTag = itemEntity.getPersistentData();
                if (itemTag.contains("PreventRemoteMovement")) {
                    continue;
                }

                if (itemEntity.getOwner() != null && itemEntity.getOwner().equals(entity) &&
                        itemEntity.hasPickUpDelay()) {
                    continue;
                }

                Player closest = world.getNearestPlayer(itemEntity, 4);
                if (closest != null && closest != entity) {
                    continue;
                }

                if (!world.isClientSide) {
                    if (filter == null) {
                        filter = stack.get(GTDataComponents.MAGNET).filterType().loadFilter(stack);
                    }

                    if (!filter.test(itemEntity.getItem())) {
                        continue;
                    }

                    if (itemEntity.hasPickUpDelay()) {
                        itemEntity.setNoPickUpDelay();
                    }
                    itemEntity.setDeltaMovement(0, 0, 0);
                    itemEntity.setPos(entity.getX() - 0.2 + (world.random.nextDouble() * 0.4), entity.getY() - 0.6,
                            entity.getZ() - 0.2 + (world.random.nextDouble() * 0.4));
                    didMoveEntity = true;
                }
            }

            if (didMoveEntity) {
                world.playSound(null, entity, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                        0.1F, 0.5F * ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 2F));
            }

            List<ExperienceOrb> xp = world.getEntitiesOfClass(ExperienceOrb.class,
                    new AABB(entity.getX(), entity.getY(), entity.getZ(), entity.getX(), entity.getY(), entity.getZ())
                            .inflate(4, 4, 4));

            for (ExperienceOrb orb : xp) {
                if (!world.isClientSide && !orb.isRemoved()) {
                    if (player.takeXpDelay == 0) {
                        if (NeoForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, orb)).isCanceled()) {
                            continue;
                        }
                        world.playSound(null, entity, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                                0.1F, 0.5F * ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.8F));
                        player.take(orb, 1);
                        player.giveExperiencePoints(orb.value);
                        orb.discard();
                        didMoveEntity = true;
                    }
                }
            }

            if (didMoveEntity) {
                drainEnergy(false, stack, energyDraw);
            }
        }
    }

    @SubscribeEvent
    public static void onItemToss(@NotNull ItemTossEvent event) {
        if (event.getPlayer() == null) return;
        if (hasMagnet(event.getPlayer())) {
            event.getEntity().setPickUpDelay(60);
        }
    }

    private static boolean hasMagnet(@NotNull Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stackInSlot = inventory.getItem(i);
            if (isMagnet(stackInSlot) && isActive(stackInSlot)) {
                return true;
            }
        }

        if (!GTCEu.Mods.isCuriosLoaded()) {
            return false;
        }
        return CuriosUtils.hasMagnetCurios(player);
    }

    private static boolean isMagnet(@NotNull ItemStack stack) {
        if (stack.getItem() instanceof IComponentItem metaItem) {
            for (var behavior : metaItem.getComponents()) {
                if (behavior instanceof ItemMagnetBehavior) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean drainEnergy(boolean simulate, @NotNull ItemStack stack, long amount) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem == null)
            return false;

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, List<Component> lines,
                                TooltipFlag isAdvanced) {
        lines.add(Component
                .translatable(isActive(itemStack) ? "behavior.item_magnet.enabled" : "behavior.item_magnet.disabled"));
    }

    private static class CuriosUtils {

        public static boolean hasMagnetCurios(Player player) {
            return CuriosApi.getCuriosInventory(player)
                    .map(curios -> curios.findFirstCurio(i -> isMagnet(i) && isActive(i)).isPresent())
                    .orElse(false);
        }
    }

    public enum Filter implements EnumSelectorWidget.SelectableEnum, StringRepresentable {

        SIMPLE(GTItems.ITEM_FILTER, "item_filter"),
        TAG(GTItems.TAG_FILTER, "item_tag_filter");

        public static final Codec<Filter> CODEC = StringRepresentable.fromEnum(Filter::values);

        public final ItemEntry<ComponentItem> item;
        public final String name;

        Filter(ItemEntry<ComponentItem> item, String name) {
            this.item = item;
            this.name = name;
        }

        public ItemStack getFilter(ItemStack magnet) {
            var mockStack = new ItemStack(item.asItem());
            switch (this) {
                case SIMPLE -> mockStack.set(GTDataComponents.SIMPLE_ITEM_FILTER,
                        magnet.get(GTDataComponents.SIMPLE_ITEM_FILTER));
                case TAG -> mockStack.set(GTDataComponents.TAG_FILTER_EXPRESSION,
                        magnet.get(GTDataComponents.TAG_FILTER_EXPRESSION));
            }
            return mockStack;
        }

        public ItemFilter loadFilter(ItemStack magnet) {
            var stack = getFilter(magnet);
            return ItemFilter.loadFilter(stack);
        }

        public void saveFilter(ItemStack stack, ItemFilter filter) {
            switch (this) {
                case SIMPLE -> {
                    if (filter instanceof SimpleItemFilter simple) {
                        stack.set(GTDataComponents.SIMPLE_ITEM_FILTER, simple);
                    }
                }
                case TAG -> {
                    if (filter instanceof TagItemFilter tag) {
                        stack.set(GTDataComponents.TAG_FILTER_EXPRESSION, tag.getTagFilterExpression());
                    }
                }
            }
        }

        public static Filter get(int ordinal) {
            return Filter.values()[ordinal];
        }

        @Override
        public @NotNull String getTooltip() {
            return item.asItem().getDescriptionId();
        }

        @Override
        public @NotNull IGuiTexture getIcon() {
            return GuiTextures.resource("gtpm:textures/item/" + name + ".png");
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record MagnetComponent(boolean active, Filter filterType) {

        public static final Codec<MagnetComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.orElse(false).fieldOf("active").forGetter(MagnetComponent::active),
                Filter.CODEC.fieldOf("filter_type").forGetter(MagnetComponent::filterType))
                .apply(instance, MagnetComponent::new));
        public static final StreamCodec<ByteBuf, MagnetComponent> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, MagnetComponent::active,
                ByteBufCodecs.VAR_INT, c -> c.filterType().ordinal(),
                (active, ordinal) -> new MagnetComponent(active, Filter.get(ordinal)));

        public static final MagnetComponent EMPTY = new MagnetComponent(false, Filter.SIMPLE);
    }

    private record LDLib2FilterPanel(Filter filter, UIElement panel) {}

    private static final class MagnetFilterActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_MAGNET_FILTER_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack) || context.openedStack() == null) {
                return false;
            }
            return isMagnet(stack) && isMagnet(context.openedStack()) &&
                    ItemStack.isSameItem(stack, context.openedStack());
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            MagnetComponent requested = payload.get(GTDataComponents.MAGNET.get());
            return requested != null && isKnownFilter(requested.filterType());
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack)) {
                throw new IllegalStateException("Magnet filter action received a non-item holder.");
            }
            MagnetComponent requested = context.payload().get(GTDataComponents.MAGNET.get());
            if (requested == null) {
                throw new IllegalStateException("Magnet filter action payload is missing.");
            }
            setMagnetFilter(stack, requested.filterType());
        }
    }
}
