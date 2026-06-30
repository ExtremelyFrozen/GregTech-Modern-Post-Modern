package com.gregtechceu.gtceu.api.transfer;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.SyncSerializationTarget;
import com.gregtechceu.gtceu.api.sync_system.TypeDeclaration;
import com.gregtechceu.gtceu.api.sync_system.codecs.CustomFluidTankCodec;
import com.gregtechceu.gtceu.api.sync_system.codecs.CustomItemStackHandlerCodec;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class TransferComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "TransferComponent")
    public static void fluidTankComponentsRoundTrip(GameTestHelper helper) {
        CustomFluidTank tank = new CustomFluidTank(2000);
        tank.setFluid(GTMaterials.Steam.getFluid(750));

        DataComponentMap components = componentNetworkRoundTrip(helper, tank.exportComponents());
        TransferData.FluidTank data = components.get(GTDataComponents.TRANSFER_FLUID_TANK.get());
        helper.assertTrue(data != null, "fluid tank did not export typed component data");
        helper.assertTrue(data.capacity() == 2000, "fluid tank capacity did not round-trip");

        CustomFluidTank decoded = new CustomFluidTank(2000);
        decoded.importComponents(components);
        helper.assertTrue(FluidStack.isSameFluidSameComponents(decoded.getFluid(), tank.getFluid()),
                "fluid tank fluid identity did not round-trip");
        helper.assertTrue(decoded.getFluidAmount() == 750, "fluid tank amount did not round-trip");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "TransferComponent")
    public static void itemHandlerComponentsRoundTrip(GameTestHelper helper) {
        CustomItemStackHandler handler = new CustomItemStackHandler(2);
        handler.setStackInSlot(0, new ItemStack(Items.DIAMOND, 13));
        handler.setStackInSlot(1, new ItemStack(Items.REDSTONE, 42));

        DataComponentMap components = componentNetworkRoundTrip(helper, handler.exportComponents());
        TransferData.ItemHandler data = components.get(GTDataComponents.TRANSFER_ITEM_HANDLER.get());
        helper.assertTrue(data != null, "item handler did not export typed component data");
        helper.assertTrue(data.slots() == 2, "item handler slot count did not round-trip");

        CustomItemStackHandler decoded = new CustomItemStackHandler(2);
        decoded.importComponents(components);
        helper.assertTrue(ItemStack.isSameItemSameComponents(decoded.getStackInSlot(0), new ItemStack(Items.DIAMOND)),
                "first item identity did not round-trip");
        helper.assertTrue(decoded.getStackInSlot(0).getCount() == 13, "first item count did not round-trip");
        helper.assertTrue(ItemStack.isSameItemSameComponents(decoded.getStackInSlot(1), new ItemStack(Items.REDSTONE)),
                "second item identity did not round-trip");
        helper.assertTrue(decoded.getStackInSlot(1).getCount() == 42, "second item count did not round-trip");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "TransferComponent")
    public static void fluidHandlerListKeepsHandlerComponentBoundaries(GameTestHelper helper) {
        CustomFluidTank first = new CustomFluidTank(1000);
        CustomFluidTank second = new CustomFluidTank(4000);
        first.setFluid(GTMaterials.Steam.getFluid(500));
        second.setFluid(GTMaterials.Water.getFluid(1250));
        FluidHandlerList list = new FluidHandlerList(first, second);

        DataComponentMap components = componentNetworkRoundTrip(helper, list.exportComponents());
        TransferData.FluidHandlers data = components.get(GTDataComponents.TRANSFER_FLUID_HANDLERS.get());
        helper.assertTrue(data != null, "fluid handler list did not export handler component list");
        helper.assertTrue(data.handlers().size() == 2, "fluid handler list did not keep handler count");

        FluidHandlerList decoded = new FluidHandlerList(new CustomFluidTank(1000), new CustomFluidTank(4000));
        decoded.importComponents(components);
        helper.assertTrue(decoded.getFluidInTank(0).getAmount() == 500,
                "first fluid handler amount did not round-trip");
        helper.assertTrue(decoded.getFluidInTank(1).getAmount() == 1250,
                "second fluid handler amount did not round-trip");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "TransferComponent")
    public static void transferSyncCodecsUseComponentPayloads(GameTestHelper helper) {
        ContextualFieldCodec.Context<CustomFluidTank> fluidContext = new ContextualFieldCodec.Context<>(
                new Object(), new TypeDeclaration(CustomFluidTank.class), null, "tank", true, true,
                helper.getLevel().registryAccess(), SyncSerializationTarget.DATA_COMPONENTS);
        CustomFluidTank tank = new CustomFluidTank(3000);
        tank.setFluid(GTMaterials.Steam.getFluid(900));
        JsonElement fluidJson = CustomFluidTankCodec.INSTANCE.serializeField(tank, fluidContext);
        CustomFluidTank decodedTank = CustomFluidTankCodec.INSTANCE.deserializeField(fluidJson, fluidContext);
        helper.assertTrue(decodedTank.getFluidAmount() == 900, "fluid sync codec did not round-trip component data");

        ContextualFieldCodec.Context<CustomItemStackHandler> itemContext = new ContextualFieldCodec.Context<>(
                new Object(), new TypeDeclaration(CustomItemStackHandler.class), null, "items", true, true,
                helper.getLevel().registryAccess(), SyncSerializationTarget.DATA_COMPONENTS);
        CustomItemStackHandler handler = new CustomItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        JsonElement itemJson = CustomItemStackHandlerCodec.INSTANCE.serializeField(handler, itemContext);
        CustomItemStackHandler decodedHandler = CustomItemStackHandlerCodec.INSTANCE.deserializeField(itemJson,
                itemContext);
        helper.assertTrue(decodedHandler.getStackInSlot(0).getCount() == 3,
                "item sync codec did not round-trip component data");
        helper.succeed();
    }

    private static DataComponentMap componentNetworkRoundTrip(GameTestHelper helper, DataComponentMap components) {
        JsonElement json = DataComponentMap.CODEC
                .encodeStart(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap jsonDecoded = DataComponentMap.CODEC
                .parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow(GameTestAssertException::new);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.OTHER);
        try {
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, jsonDecoded);
            return SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
