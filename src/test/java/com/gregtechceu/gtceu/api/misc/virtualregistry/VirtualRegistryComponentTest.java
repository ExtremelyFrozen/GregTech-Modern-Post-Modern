package com.gregtechceu.gtceu.api.misc.virtualregistry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualItemStorage;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualRedstone;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualTank;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class VirtualRegistryComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "VirtualRegistryComponent")
    public static void virtualEntriesRoundTripThroughDataComponents(GameTestHelper helper) {
        VirtualTank tank = new VirtualTank();
        tank.setColor("11223344");
        tank.setDescription("fluid channel");
        tank.setFluid(new FluidStack(Fluids.WATER, 1000));
        VirtualTank decodedTank = EntryTypes.ENDER_FLUID.createInstance(helper.getLevel().registryAccess(),
                tank.exportComponents(helper.getLevel().registryAccess()));
        helper.assertTrue(decodedTank.getColorStr().equals("11223344"), "tank color did not round-trip");
        helper.assertTrue(decodedTank.getDescription().equals("fluid channel"),
                "tank description did not round-trip");
        helper.assertTrue(FluidStack.isSameFluidSameComponents(decodedTank.getFluidTank().getFluid(),
                new FluidStack(Fluids.WATER, 1000)),
                "tank fluid did not round-trip");

        VirtualItemStorage storage = new VirtualItemStorage();
        storage.getHandler().setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        VirtualItemStorage decodedStorage = EntryTypes.ENDER_ITEM.createInstance(helper.getLevel().registryAccess(),
                storage.exportComponents(helper.getLevel().registryAccess()));
        helper.assertTrue(ItemStack.matches(decodedStorage.getHandler().getStackInSlot(0),
                new ItemStack(Items.DIAMOND, 8)),
                "item storage contents did not round-trip");

        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        VirtualRedstone redstone = new VirtualRedstone();
        redstone.addMember(id);
        redstone.setSignal(id, 13);
        VirtualRedstone decodedRedstone = EntryTypes.ENDER_REDSTONE.createInstance(helper.getLevel().registryAccess(),
                redstone.exportComponents(helper.getLevel().registryAccess()));
        helper.assertTrue(decodedRedstone.getMembers().getShort(id) == 13,
                "redstone member signal did not round-trip");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "VirtualRegistryComponent")
    public static void virtualRegistryMapCodecRoundTripKeepsEntries(GameTestHelper helper) {
        VirtualRegistryMap map = new VirtualRegistryMap();
        VirtualTank tank = new VirtualTank();
        tank.setFluid(new FluidStack(Fluids.LAVA, 250));
        map.addEntry("fluid", tank);

        VirtualItemStorage storage = new VirtualItemStorage();
        storage.getHandler().setStackInSlot(0, new ItemStack(Items.EMERALD, 3));
        map.addEntry("items", storage);

        DataComponentMap components = map.exportComponents(helper.getLevel().registryAccess());
        JsonElement json = DataComponentMap.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap decodedComponents = DataComponentMap.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), json)
                .getOrThrow(GameTestAssertException::new);
        VirtualRegistryMap decoded = new VirtualRegistryMap(helper.getLevel().registryAccess(), decodedComponents);

        VirtualTank decodedTank = decoded.getEntry(EntryTypes.ENDER_FLUID, "fluid");
        VirtualItemStorage decodedStorage = decoded.getEntry(EntryTypes.ENDER_ITEM, "items");
        helper.assertTrue(components.has(GTDataComponents.VIRTUAL_FLUID_ENTRIES.get()),
                "registry map did not export fluid entries component");
        helper.assertTrue(decodedTank != null && FluidStack.isSameFluidSameComponents(decodedTank.getFluidTank()
                .getFluid(), new FluidStack(Fluids.LAVA, 250)), "registry map did not round-trip tank entry");
        helper.assertTrue(decodedStorage != null && ItemStack.matches(decodedStorage.getHandler().getStackInSlot(0),
                new ItemStack(Items.EMERALD, 3)), "registry map did not round-trip item entry");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "VirtualRegistryComponent")
    public static void virtualEnderRegistryRootCodecRoundTripKeepsPublicAndPrivateEntries(GameTestHelper helper) {
        VirtualEnderRegistry registry = new VirtualEnderRegistry();
        VirtualTank publicTank = new VirtualTank();
        publicTank.setFluid(new FluidStack(Fluids.WATER, 1000));
        registry.addEntry(null, "public_tank", publicTank);

        UUID owner = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        VirtualItemStorage privateStorage = new VirtualItemStorage();
        privateStorage.getHandler().setStackInSlot(0, new ItemStack(Items.DIAMOND, 4));
        registry.addEntry(owner, "private_storage", privateStorage);

        DataComponentMap components = registry.exportComponents(helper.getLevel().registryAccess());
        JsonElement json = DataComponentMap.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap decodedComponents = DataComponentMap.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), json)
                .getOrThrow(GameTestAssertException::new);

        VirtualEnderRegistry decoded = new VirtualEnderRegistry();
        decoded.importComponents(helper.getLevel().registryAccess(), decodedComponents);

        VirtualTank decodedPublicTank = decoded.getEntry(null, EntryTypes.ENDER_FLUID, "public_tank");
        VirtualItemStorage decodedPrivateStorage = decoded.getEntry(owner, EntryTypes.ENDER_ITEM, "private_storage");
        helper.assertTrue(components.has(GTDataComponents.VIRTUAL_REGISTRY_ROOT.get()),
                "virtual ender registry did not export root component");
        helper.assertTrue(decodedPublicTank != null && FluidStack.isSameFluidSameComponents(decodedPublicTank
                .getFluidTank().getFluid(), new FluidStack(Fluids.WATER, 1000)),
                "public registry entry did not round-trip");
        helper.assertTrue(decodedPrivateStorage != null && ItemStack.matches(decodedPrivateStorage.getHandler()
                .getStackInSlot(0), new ItemStack(Items.DIAMOND, 4)),
                "private registry entry did not round-trip");
        helper.succeed();
    }
}
