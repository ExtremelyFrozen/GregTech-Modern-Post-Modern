package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.DistinctPart;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LDLib2 Fancy configurator helper for multiblock distinct bus mode.
 *
 * <p>The button sends a GTM machine action for server-side mutation and leaves existing legacy Fancy call sites
 * untouched until their owning machines migrate.
 */
public final class LDLib2DistinctPartFancyConfigurator {

    private static final ResourceLocation SET_DISTINCT_PART_ACTION = GTCEu.id("set_distinct_part");
    private static final ResourceLocation DISTINCT_FIELD = SyncFieldData.key("isDistinct");

    static {
        SyncActionDispatchers.server().register(new DistinctPartActionHandler());
    }

    private LDLib2DistinctPartFancyConfigurator() {}

    /**
     * Attaches a distinct part toggle to a migrated LDLib2 Fancy configurator panel.
     */
    public static void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel, DistinctPart part) {
        configuratorPanel.attachConfigurators(new LDLib2FancyConfiguratorButton.Toggle(
                GuiTextures.BUTTON_DISTINCT_BUSES.getSubTexture(0, 0.5, 1, 0.5),
                GuiTextures.BUTTON_DISTINCT_BUSES.getSubTexture(0, 0, 1, 0.5),
                part::isDistinct,
                (event, pressed) -> {
                    var machine = configuratorPanel.getHolder().getMachine();
                    if (machine != null && machine.isRemote()) {
                        MachineUIHelper.sendAction(configuratorPanel.getHolder(), createSetDistinctAction(pressed));
                        event.stopImmediatePropagation();
                        event.hasHandler = true;
                    }
                })
                .setTooltipsSupplier(pressed -> List.of(
                        Component.translatable("gtpm.multiblock.universal.distinct")
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                                .append(Component.translatable(pressed ? "gtpm.multiblock.universal.distinct.yes" :
                                        "gtpm.multiblock.universal.distinct.no")))));
    }

    private static SyncActionData createSetDistinctAction(boolean distinct) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(DISTINCT_FIELD, new JsonPrimitive(distinct))
                        .build())
                .build();
        return new SyncActionData(SET_DISTINCT_PART_ACTION, distinct ? 1 : 0, payload);
    }

    private static final class DistinctPartActionHandler implements SyncActionHandler {

        @Override
        public @NotNull ResourceLocation actionId() {
            return SET_DISTINCT_PART_ACTION;
        }

        @Override
        public boolean acceptsHolder(@NotNull SyncActionContext context) {
            return context.holder() instanceof DistinctPart &&
                    context.holder() instanceof LDLib2FancyUIMachine;
        }

        @Override
        public boolean acceptsPayload(@NotNull DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readDistinct(fields) != null;
        }

        @Override
        public boolean mayExecute(@NotNull ServerPlayer player, @NotNull SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(@NotNull SyncActionContext context) {
            if (!(context.holder() instanceof DistinctPart part)) {
                throw new IllegalStateException("Distinct part action received a non-distinct holder.");
            }
            part.setDistinct(requireDistinct(context.payload()));
        }
    }

    private static boolean requireDistinct(DataComponentMap payload) {
        Boolean distinct = readDistinct(payload);
        if (distinct == null) {
            throw new IllegalStateException("Distinct part action payload is missing distinct state.");
        }
        return distinct;
    }

    private static @Nullable Boolean readDistinct(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readDistinct(fields);
    }

    private static @Nullable Boolean readDistinct(SyncFieldData fields) {
        JsonElement element = fields.get(DISTINCT_FIELD);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}
