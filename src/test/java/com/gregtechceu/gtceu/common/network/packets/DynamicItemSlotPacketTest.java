package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotSelection;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DynamicItemSlotPacketTest {

    private static final String BATCH = "DynamicItemSlotPacket";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void allHandshakePacketsRoundTripInProtocolOrder(GameTestHelper helper) {
        DynamicItemSlotManifest manifest = manifest();
        DynamicItemSlotOpeningToken token = DynamicItemSlotOpeningToken.of(7, id(1), manifest);
        DynamicItemSlotSelection selection = new DynamicItemSlotSelection(3, Optional.of(id(4)));
        DynamicItemSlotSelection overviewSelection = new DynamicItemSlotSelection(4, Optional.empty());
        SPacketDynamicItemSlotManifestToClient manifestPacket = new SPacketDynamicItemSlotManifestToClient(token,
                manifest);
        CPacketDynamicItemSlotPreparedToServer preparedPacket = new CPacketDynamicItemSlotPreparedToServer(token);
        SPacketDynamicItemSlotActivationToClient activationPacket = new SPacketDynamicItemSlotActivationToClient(token,
                selection.bindingId());
        SPacketDynamicItemSlotActivationToClient emptyActivationPacket = new SPacketDynamicItemSlotActivationToClient(
                token, Optional.empty());
        CPacketDynamicItemSlotActivatedToServer activatedPacket = new CPacketDynamicItemSlotActivatedToServer(token);
        CPacketDynamicItemSlotSelectionToServer selectionRequest = new CPacketDynamicItemSlotSelectionToServer(token,
                selection);
        CPacketDynamicItemSlotSelectionToServer overviewRequest = new CPacketDynamicItemSlotSelectionToServer(token,
                overviewSelection);
        SPacketDynamicItemSlotSelectionToClient selectionAcknowledgement = new SPacketDynamicItemSlotSelectionToClient(
                token, selection);
        SPacketDynamicItemSlotSelectionToClient overviewAcknowledgement = new SPacketDynamicItemSlotSelectionToClient(
                token, overviewSelection);

        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        try {
            SPacketDynamicItemSlotManifestToClient.CODEC.encode(buffer, manifestPacket);
            CPacketDynamicItemSlotPreparedToServer.CODEC.encode(buffer, preparedPacket);
            SPacketDynamicItemSlotActivationToClient.CODEC.encode(buffer, activationPacket);
            SPacketDynamicItemSlotActivationToClient.CODEC.encode(buffer, emptyActivationPacket);
            CPacketDynamicItemSlotActivatedToServer.CODEC.encode(buffer, activatedPacket);
            CPacketDynamicItemSlotSelectionToServer.CODEC.encode(buffer, selectionRequest);
            CPacketDynamicItemSlotSelectionToServer.CODEC.encode(buffer, overviewRequest);
            SPacketDynamicItemSlotSelectionToClient.CODEC.encode(buffer, selectionAcknowledgement);
            SPacketDynamicItemSlotSelectionToClient.CODEC.encode(buffer, overviewAcknowledgement);

            helper.assertTrue(manifestPacket.equals(SPacketDynamicItemSlotManifestToClient.CODEC.decode(buffer)),
                    "MANIFEST packet codec changed the authoritative layout");
            helper.assertTrue(preparedPacket.equals(CPacketDynamicItemSlotPreparedToServer.CODEC.decode(buffer)),
                    "PREPARED packet codec changed the opening token");
            helper.assertTrue(activationPacket.equals(SPacketDynamicItemSlotActivationToClient.CODEC.decode(buffer)),
                    "ACTIVATE packet codec changed the selected binding");
            helper.assertTrue(emptyActivationPacket.equals(
                    SPacketDynamicItemSlotActivationToClient.CODEC.decode(buffer)),
                    "ACTIVATE packet codec changed an empty selection");
            helper.assertTrue(activatedPacket.equals(CPacketDynamicItemSlotActivatedToServer.CODEC.decode(buffer)),
                    "ACTIVATED packet codec changed the opening token");
            helper.assertTrue(selectionRequest.equals(
                    CPacketDynamicItemSlotSelectionToServer.CODEC.decode(buffer)),
                    "selection request codec changed its sequence or binding");
            helper.assertTrue(overviewRequest.equals(
                    CPacketDynamicItemSlotSelectionToServer.CODEC.decode(buffer)),
                    "overview request codec added a binding or changed its sequence");
            helper.assertTrue(selectionAcknowledgement.equals(
                    SPacketDynamicItemSlotSelectionToClient.CODEC.decode(buffer)),
                    "selection acknowledgement codec changed its sequence or binding");
            helper.assertTrue(overviewAcknowledgement.equals(
                    SPacketDynamicItemSlotSelectionToClient.CODEC.decode(buffer)),
                    "overview acknowledgement codec added a binding or changed its sequence");
            helper.assertTrue(!buffer.isReadable(), "dynamic item-slot packet codecs left unread bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void manifestPacketRejectsMismatchedToken(GameTestHelper helper) {
        DynamicItemSlotManifest manifest = manifest();
        DynamicItemSlotOpeningToken mismatched = new DynamicItemSlotOpeningToken(
                7, id(1), manifest.epoch(), id(99));

        try {
            new SPacketDynamicItemSlotManifestToClient(mismatched, manifest);
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank(),
                    "MANIFEST packet rejection did not describe the mismatch");
            helper.succeed();
            return;
        }
        throw new GameTestAssertException("MANIFEST packet accepted a token for another layout");
    }

    private static DynamicItemSlotManifest manifest() {
        return new DynamicItemSlotManifest(
                0,
                1,
                id(2),
                5,
                36,
                List.of(new DynamicItemSlotBinding(id(4), id(3), id(5), 36, 2, true)));
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.OTHER);
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
