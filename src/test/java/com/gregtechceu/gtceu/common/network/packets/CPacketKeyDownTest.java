package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.utils.input.IKeyPressedListener;
import com.gregtechceu.gtceu.utils.input.SyncedKeyMapping;
import com.gregtechceu.gtceu.utils.input.SyncedKeyMappings;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.ints.Int2BooleanLinkedOpenHashMap;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CPacketKeyDownTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CPacketKeyDown")
    public static void rejectsUnknownMappingWithoutApplyingKnownUpdates(GameTestHelper helper) {
        SyncedKeyMapping mapping = SyncedKeyMappings.VANILLA_JUMP;
        int knownId = mapping.getSyncId();
        helper.assertTrue(SyncedKeyMapping.getFromSyncId(knownId) == mapping,
                "registered key mapping was not available by its sync id");
        helper.assertTrue(SyncedKeyMapping.getFromSyncId(Integer.MAX_VALUE) == null,
                "test unknown key mapping id was unexpectedly registered");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        boolean initialKeyState = mapping.isKeyDown(player);
        int[] listenerCalls = { 0 };
        IKeyPressedListener listener = (listenerPlayer, key, isDown) -> listenerCalls[0]++;
        mapping.registerGlobalListener(listener);
        try {
            var invalidBatch = new Int2BooleanLinkedOpenHashMap();
            invalidBatch.put(knownId, true);
            invalidBatch.put(Integer.MAX_VALUE, true);

            helper.assertTrue(!CPacketKeyDown.applyUpdates(invalidBatch, player),
                    "batch containing an unknown key mapping was accepted");
            helper.assertTrue(listenerCalls[0] == 0,
                    "known key update was applied before the unknown mapping rejected the batch");
            helper.assertTrue(mapping.isKeyDown(player) == initialKeyState,
                    "rejected key update changed the server key state");
        } finally {
            mapping.removeGlobalListener(listener);
        }
        helper.succeed();
    }
}
