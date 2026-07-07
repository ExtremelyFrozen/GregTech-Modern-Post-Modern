package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
@ForEachTest(groups = "ldlib2PhantomSlotElement")
public class GTPhantomSlotElementTest {

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void itemSupplierValueIsClampedToMaxOnConstructAndRefresh(GameTestHelper helper) {
        ItemStack suppliedStack = new ItemStack(Items.DIAMOND, 64);
        GTPhantomItemSlotElement element = new GTPhantomItemSlotElement(
                () -> suppliedStack,
                stack -> {},
                () -> 16);

        assertItem(helper, element.getValue(), Items.DIAMOND, 16, "constructor should clamp item count to max");

        suppliedStack.setCount(32);
        element.refreshFromSupplier();

        assertItem(helper, element.getValue(), Items.DIAMOND, 16, "refresh should clamp item count to max");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void itemSupplierValueNormalizesToEmptyForZeroCountOrNonPositiveMax(GameTestHelper helper) {
        GTPhantomItemSlotElement zeroCountElement = new GTPhantomItemSlotElement(
                () -> new ItemStack(Items.DIAMOND, 0),
                stack -> {},
                () -> 64);
        GTPhantomItemSlotElement zeroMaxElement = new GTPhantomItemSlotElement(
                () -> new ItemStack(Items.DIAMOND, 1),
                stack -> {},
                () -> 0);
        GTPhantomItemSlotElement negativeMaxElement = new GTPhantomItemSlotElement(
                () -> new ItemStack(Items.DIAMOND, 1),
                stack -> {},
                () -> -1);

        helper.assertTrue(zeroCountElement.getValue().isEmpty(), "zero item count should normalize to empty");
        helper.assertTrue(zeroMaxElement.getValue().isEmpty(), "zero max item count should normalize to empty");
        helper.assertTrue(negativeMaxElement.getValue().isEmpty(), "negative max item count should normalize to empty");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void setItemNotifiesConsumerWithCopy(GameTestHelper helper) {
        AtomicReference<ItemStack> consumedStack = new AtomicReference<>(ItemStack.EMPTY);
        GTPhantomItemSlotElement element = new GTPhantomItemSlotElement(
                () -> ItemStack.EMPTY,
                stack -> {
                    consumedStack.set(stack);
                    stack.setCount(1);
                },
                () -> 64);

        element.setItem(new ItemStack(Items.IRON_INGOT, 9), true);

        helper.assertTrue(consumedStack.get().is(Items.IRON_INGOT), "consumer should receive the normalized item");
        helper.assertTrue(consumedStack.get().getCount() == 1, "consumer mutation should affect only captured copy");
        assertItem(helper, element.getValue(), Items.IRON_INGOT, 9,
                "mutating consumer item stack should not change element value");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void setMaxStackSizeSupplierRefreshesItemNormalization(GameTestHelper helper) {
        AtomicInteger maxStackSize = new AtomicInteger(64);
        GTPhantomItemSlotElement element = new GTPhantomItemSlotElement(
                () -> new ItemStack(Items.COPPER_INGOT, 40),
                stack -> {},
                maxStackSize::get);

        assertItem(helper, element.getValue(), Items.COPPER_INGOT, 40, "initial max should keep supplied item count");

        maxStackSize.set(12);
        element.setMaxStackSizeSupplier(maxStackSize::get);

        assertItem(helper, element.getValue(), Items.COPPER_INGOT, 12,
                "updated max supplier should refresh item normalization");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void fluidSupplierValueIsClampedToMaxOnConstructAndRefresh(GameTestHelper helper) {
        FluidStack suppliedFluid = new FluidStack(Fluids.WATER, 5_000);
        GTPhantomFluidSlotElement element = new GTPhantomFluidSlotElement(
                () -> suppliedFluid,
                fluid -> {},
                () -> 1_000);

        assertFluid(helper, element.getFluid(), Fluids.WATER, 1_000,
                "constructor should clamp fluid amount to max");

        suppliedFluid.setAmount(3_000);
        element.refreshFromSupplier();

        assertFluid(helper, element.getFluid(), Fluids.WATER, 1_000, "refresh should clamp fluid amount to max");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void fluidSupplierValueNormalizesToEmptyForNonPositiveAmountOrMax(GameTestHelper helper) {
        GTPhantomFluidSlotElement zeroAmountElement = new GTPhantomFluidSlotElement(
                () -> new FluidStack(Fluids.WATER, 0),
                fluid -> {},
                () -> 1_000);
        GTPhantomFluidSlotElement zeroMaxElement = new GTPhantomFluidSlotElement(
                () -> new FluidStack(Fluids.WATER, 1),
                fluid -> {},
                () -> 0);
        GTPhantomFluidSlotElement negativeMaxElement = new GTPhantomFluidSlotElement(
                () -> new FluidStack(Fluids.WATER, 1),
                fluid -> {},
                () -> -1);

        helper.assertTrue(zeroAmountElement.getFluid().isEmpty(), "zero fluid amount should normalize to empty");
        helper.assertTrue(zeroMaxElement.getFluid().isEmpty(), "zero max fluid amount should normalize to empty");
        helper.assertTrue(negativeMaxElement.getFluid().isEmpty(), "negative max fluid amount should normalize to empty");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void setFluidNotifiesConsumerWithCopy(GameTestHelper helper) {
        AtomicReference<FluidStack> consumedFluid = new AtomicReference<>(FluidStack.EMPTY);
        GTPhantomFluidSlotElement element = new GTPhantomFluidSlotElement(
                () -> FluidStack.EMPTY,
                fluid -> {
                    consumedFluid.set(fluid);
                    fluid.setAmount(1);
                },
                () -> 1_000);

        element.setFluid(new FluidStack(Fluids.LAVA, 900));

        helper.assertTrue(consumedFluid.get().getFluid() == Fluids.LAVA, "consumer should receive the normalized fluid");
        helper.assertTrue(consumedFluid.get().getAmount() == 1, "consumer mutation should affect only captured copy");
        assertFluid(helper, element.getFluid(), Fluids.LAVA, 900,
                "mutating consumer fluid stack should not change element fluid");
        helper.succeed();
    }

    @TestHolder()
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ldlib2PhantomSlotElement")
    public static void setMaxAmountSupplierRefreshesFluidNormalizationAndCapacity(GameTestHelper helper) {
        AtomicInteger maxAmount = new AtomicInteger(4_000);
        GTPhantomFluidSlotElement element = new GTPhantomFluidSlotElement(
                () -> new FluidStack(Fluids.WATER, 3_000),
                fluid -> {},
                maxAmount::get);

        assertFluid(helper, element.getFluid(), Fluids.WATER, 3_000, "initial max should keep supplied fluid amount");
        helper.assertTrue(element.getCapacity() == 4_000, "initial max amount should set fluid capacity");

        maxAmount.set(1_000);
        element.setMaxAmountSupplier(maxAmount::get);

        assertFluid(helper, element.getFluid(), Fluids.WATER, 1_000,
                "updated max supplier should refresh fluid normalization");
        helper.assertTrue(element.getCapacity() == 1_000, "updated max amount should refresh fluid capacity");
        helper.succeed();
    }

    private static void assertItem(GameTestHelper helper, ItemStack stack, Item item, int count, String message) {
        helper.assertTrue(stack.is(item), message + ": unexpected item");
        helper.assertTrue(stack.getCount() == count, message + ": unexpected count");
    }

    private static void assertFluid(GameTestHelper helper, FluidStack stack, Fluid fluid, int amount, String message) {
        helper.assertTrue(stack.getFluid() == fluid, message + ": unexpected fluid");
        helper.assertTrue(stack.getAmount() == amount, message + ": unexpected amount");
    }
}
