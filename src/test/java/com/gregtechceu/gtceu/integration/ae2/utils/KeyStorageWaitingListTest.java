package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class KeyStorageWaitingListTest {

    private static final String BATCH = "KeyStorageWaitingList";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void hotPathPreservesOrderNotifiesBothListenersAndIgnoresNoOps(GameTestHelper helper) {
        KeyStorage storage = new KeyStorage();
        AEKey apple = requireKey(new ItemStack(Items.APPLE));
        AEKey bread = requireKey(new ItemStack(Items.BREAD));
        int[] contentsNotifications = { 0 };
        List<KeyStorage.ChangeBatch> viewBatches = new ArrayList<>();
        storage.setOnContentsChanged(() -> contentsNotifications[0]++);
        storage.setOnViewChanged(viewBatches::add);

        helper.assertTrue(storage.put(apple, 1), "first key storage write was reported as a no-op");
        helper.assertTrue(!storage.put(apple, 1), "equal key storage write was reported as a change");
        helper.assertTrue(storage.put(bread, 2), "second key storage write was reported as a no-op");
        helper.assertTrue(storage.put(apple, 3), "amount update was reported as a no-op");
        helper.assertTrue(!storage.remove(requireKey(new ItemStack(Items.CARROT))),
                "missing key removal was reported as a change");
        helper.assertTrue(storage.remove(apple), "existing key removal was reported as a no-op");
        helper.assertTrue(storage.put(apple, 4), "re-added key was reported as a no-op");

        List<KeyStorage.Change> snapshot = storage.snapshot();
        helper.assertTrue(snapshot.size() == 2 && snapshot.get(0).key().equals(bread) &&
                snapshot.get(1).key().equals(apple),
                "key storage did not retain first-insertion and remove-readd order");
        helper.assertTrue(contentsNotifications[0] == 5 && viewBatches.size() == 5,
                "real key storage changes did not notify both listeners exactly once");
        helper.assertTrue(viewBatches.get(3).changes().equals(List.of(new KeyStorage.Change(apple, 0))) &&
                viewBatches.get(4).changes().equals(List.of(new KeyStorage.Change(apple, 4))),
                "key storage did not preserve remove-readd operations for the view");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void inventoryInsertionPublishesOneOrderedBatch(GameTestHelper helper) {
        KeyStorage storage = new KeyStorage();
        AEKey apple = requireKey(new ItemStack(Items.APPLE));
        AEKey bread = requireKey(new ItemStack(Items.BREAD));
        storage.put(apple, 10);
        storage.put(bread, 20);
        int[] contentsNotifications = { 0 };
        List<KeyStorage.ChangeBatch> viewBatches = new ArrayList<>();
        storage.setOnContentsChanged(() -> contentsNotifications[0]++);
        storage.setOnViewChanged(viewBatches::add);

        MEStorage inventory = new MEStorage() {

            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return key.equals(apple) ? amount : 5;
            }

            @Override
            public Component getDescription() {
                return Component.literal("Key storage test inventory");
            }
        };
        storage.insertInventory(inventory, IActionSource.empty());

        helper.assertTrue(contentsNotifications[0] == 1 && viewBatches.size() == 1,
                "batched inventory insertion notified listeners more than once");
        helper.assertTrue(viewBatches.getFirst().changes().equals(List.of(
                new KeyStorage.Change(apple, 0), new KeyStorage.Change(bread, 15))),
                "batched inventory insertion lost its ordered final operations");
        helper.assertTrue(storage.snapshot().equals(List.of(new KeyStorage.Change(bread, 15))),
                "batched inventory insertion did not apply its authoritative values");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void replacementPublishesOnlyRealContentOrOrderChanges(GameTestHelper helper) {
        KeyStorage storage = new KeyStorage();
        KeyStorage.Change apple = new KeyStorage.Change(requireKey(new ItemStack(Items.APPLE)), 1);
        KeyStorage.Change bread = new KeyStorage.Change(requireKey(new ItemStack(Items.BREAD)), 2);
        storage.replaceContents(List.of(apple, bread));
        List<KeyStorage.ChangeBatch> batches = new ArrayList<>();
        storage.setOnViewChanged(batches::add);

        storage.replaceContents(List.of(apple, bread));
        helper.assertTrue(batches.isEmpty(), "equal key storage replacement published a redundant batch");
        storage.replaceContents(List.of(bread, apple));
        helper.assertTrue(batches.size() == 1 && batches.getFirst().changes().equals(List.of(
                new KeyStorage.Change(apple.key(), 0), new KeyStorage.Change(bread.key(), 0),
                bread, apple)),
                "key storage replacement did not publish a deterministic order change");
        helper.succeed();
    }

    private static AEKey requireKey(ItemStack stack) {
        AEKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Test item did not create an AE key: " + stack);
        }
        return key;
    }
}
