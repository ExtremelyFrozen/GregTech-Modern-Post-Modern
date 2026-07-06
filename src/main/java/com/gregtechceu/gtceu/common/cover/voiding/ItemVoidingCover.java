package com.gregtechceu.gtceu.common.cover.voiding;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.item.tool.GridHighlightTexture;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.item.GTItemAbilities;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ItemVoidingCover extends ConveyorCover implements IControllable, LDLib2CoverUIProvider {

    private static final ResourceLocation SET_ITEM_VOIDING_COVER_CONFIG_ACTION = GTCEu
            .id("set_item_voiding_cover_config");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new ItemVoidingCoverConfigActionHandler());
    }

    public ItemVoidingCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide, 0);
        setWorkingEnabled(false);
    }

    @Override
    protected boolean isSubscriptionActive() {
        return isWorkingEnabled();
    }

    //////////////////////////////////////////////
    // *********** COVER LOGIC ***********//
    //////////////////////////////////////////////

    @Override
    protected void update() {
        if (coverHolder.getOffsetTimer() % 5 != 0)
            return;

        doVoidItems();
        subscriptionHandler.updateSubscription();
    }

    protected void doVoidItems() {
        IItemHandler handler = getOwnItemHandler();
        if (handler == null) {
            return;
        }
        voidAny(handler);
    }

    void voidAny(IItemHandler handler) {
        ItemFilter filter = filterHandler.getFilter();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack sourceStack = handler.extractItem(slot, Integer.MAX_VALUE, true);
            if (sourceStack.isEmpty() || !filter.test(sourceStack)) {
                continue;
            }
            handler.extractItem(slot, Integer.MAX_VALUE, false);
        }
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 202);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label());
        root.addChild(new GTToggleButtonElement(10, 20, 20, 20, GuiTextures.BUTTON_POWER,
                this::isWorkingEnabled, enabled -> setLDLib2WorkingEnabled(player, holder, enabled)));
        buildAdditionalLDLib2UI(root, player, holder);
        root.addChild(filterHandler.createFilterSlotLDLib2UI(148, 91));
        root.addChild(filterHandler.createFilterConfigLDLib2UI(10, 50, 126, 60));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 120, true));
        return UI.of(root);
    }

    protected void buildAdditionalLDLib2UI(UIElement root, Player player, UICoverHolder holder) {}

    private GTLabelElement createLDLib2Label() {
        GTLabelElement label = new GTLabelElement(10, 5, 156, 10, getUITitle(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void setLDLib2WorkingEnabled(Player player, UICoverHolder holder, boolean enabled) {
        setWorkingEnabled(enabled);
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetItemVoidingCoverConfigAction(enabled));
        }
    }

    private static SyncActionData createSetItemVoidingCoverConfigAction(boolean enabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
        return new SyncActionData(SET_ITEM_VOIDING_COVER_CONFIG_ACTION, enabled ? 1 : 0, payload);
    }

    @NotNull
    protected String getUITitle() {
        return "cover.item.voiding.title";
    }

    @Override
    public InteractionResult onSoftMalletClick(ExtendedUseOnContext context) {
        if (!context.getItemInHand().canPerformAction(GTItemAbilities.MALLET_PAUSE)) {
            return InteractionResult.PASS;
        }
        if (!isRemote()) {
            setWorkingEnabled(!isWorkingEnabled);
            context.getPlayer().sendSystemMessage(Component.translatable(isWorkingEnabled() ?
                    "cover.voiding.message.enabled" : "cover.voiding.message.disabled"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    // TODO: Decide grid behavior
    @Override
    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held,
                                    Set<GTToolType> toolTypes) {
        return super.shouldRenderGrid(player, pos, state, held, toolTypes);
    }

    @Override
    public @Nullable GridHighlightTexture sideTips(Player player, BlockPos pos, BlockState state,
                                                   Set<GTToolType> toolTypes, ItemStack held, Direction side) {
        var superTips = super.sideTips(player, pos, state, toolTypes, held, side);
        if (superTips != null) return superTips;
        if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            return isWorkingEnabled() ? GridHighlightTexture.TOOL_START : GridHighlightTexture.TOOL_PAUSE;
        }
        return null;
    }

    private static final class ItemVoidingCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ITEM_VOIDING_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof ItemVoidingCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, WORKING_ENABLED_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ItemVoidingCover cover)) {
                throw new IllegalStateException("Item voiding cover config action received a non-item-voiding cover.");
            }
            cover.setWorkingEnabled(requireBoolean(context.payload(), WORKING_ENABLED_FIELD));
        }
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Item voiding cover config action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Item voiding cover config action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
