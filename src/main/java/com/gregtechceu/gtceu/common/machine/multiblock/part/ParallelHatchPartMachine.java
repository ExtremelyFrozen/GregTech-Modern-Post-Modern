package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class ParallelHatchPartMachine extends TieredPartMachine implements LDLib2MachineUIProvider {

    private static final int MIN_PARALLEL = 1;
    private static final ResourceLocation SET_PARALLEL_HATCH_CURRENT_PARALLEL_ACTION = GTCEu
            .id("set_parallel_hatch_current_parallel");
    private static final ResourceLocation CURRENT_PARALLEL_FIELD = SyncFieldData.key("currentParallel");

    static {
        SyncActionDispatchers.server().register(new ParallelHatchCurrentParallelActionHandler());
    }

    private final int maxParallel;

    @SaveField
    @Getter
    private int currentParallel = 1;

    public ParallelHatchPartMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.maxParallel = (int) Math.pow(4, tier - GTValues.EV);
        this.currentParallel = maxParallel;
    }

    public void setCurrentParallel(int parallelAmount) {
        this.currentParallel = Mth.clamp(parallelAmount, MIN_PARALLEL, this.maxParallel);
        for (MultiblockControllerMachine controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().markLastRecipeDirty();
            }
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 100, 20);
        root.addChild(new GTIntInputElement(0, 0, 100, 20, this::getCurrentParallel,
                value -> setLDLib2CurrentParallel(player, holder, value))
                .setMin(MIN_PARALLEL)
                .setMax(maxParallel));
        return UI.of(root);
    }

    private void setLDLib2CurrentParallel(Player player, MachineUIHolder holder, int value) {
        setCurrentParallel(value);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetParallelHatchCurrentParallelAction(value));
        }
    }

    private static SyncActionData createSetParallelHatchCurrentParallelAction(int value) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CURRENT_PARALLEL_FIELD, new JsonPrimitive(value))
                        .build())
                .build();
        return new SyncActionData(SET_PARALLEL_HATCH_CURRENT_PARALLEL_ACTION, value, payload);
    }

    private static final class ParallelHatchCurrentParallelActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_PARALLEL_HATCH_CURRENT_PARALLEL_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof ParallelHatchPartMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readInt(fields, CURRENT_PARALLEL_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ParallelHatchPartMachine machine)) {
                throw new IllegalStateException("Parallel hatch action received a non-parallel-hatch machine.");
            }
            machine.setCurrentParallel(requireInt(context.payload(), CURRENT_PARALLEL_FIELD));
        }
    }

    private static int requireInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Parallel hatch action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException("Parallel hatch action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readInt(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        return null;
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }
}
