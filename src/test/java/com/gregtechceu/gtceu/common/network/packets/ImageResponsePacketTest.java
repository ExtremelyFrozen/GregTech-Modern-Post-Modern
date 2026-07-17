package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.ImageRequestLimits;
import com.gregtechceu.gtceu.api.misc.ImageRequestResult;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ImageResponsePacketTest {

    private static final String BATCH = "ImageResponsePacket";
    private static final String URL = "https://allowed.example/image.png";
    private static final long REQUEST_ID = 42;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void successPayloadKeepsTheExistingPacketSizeAndOrdering(GameTestHelper helper) {
        byte[] image = new byte[ImageRequestLimits.MAX_BYTES_PER_PART * 2 + 7];
        for (int index = 0; index < image.length; index++) {
            image[index] = (byte) index;
        }

        List<SPacketImageResponse> responses = SPacketImageResponse.createResponses(
                URL, REQUEST_ID, ImageRequestResult.success(image));
        helper.assertTrue(responses.size() == 3, "large image was not split into the expected packet count");

        byte[] assembled = new byte[image.length];
        int destinationIndex = 0;
        for (int index = 0; index < responses.size(); index++) {
            SPacketImageResponse response = roundTrip(responses.get(index));
            helper.assertTrue(response.status() == ImageRequestResult.Status.SUCCESS,
                    "successful image part carried a failure status");
            helper.assertTrue(response.url().equals(URL) && response.requestId() == REQUEST_ID,
                    "successful image part changed request identity");
            helper.assertTrue(response.index() == index && response.totalParts() == responses.size(),
                    "successful image part changed part ordering metadata");
            int expectedLength = index < 2 ? ImageRequestLimits.MAX_BYTES_PER_PART : 7;
            helper.assertTrue(response.imagePart().length == expectedLength,
                    "successful image part changed the packet byte limit");
            System.arraycopy(response.imagePart(), 0, assembled, destinationIndex, response.imagePart().length);
            destinationIndex += response.imagePart().length;
        }
        helper.assertTrue(Arrays.equals(assembled, image), "fragmented success response changed image bytes");

        List<SPacketImageResponse> boundary = SPacketImageResponse.createResponses(URL, REQUEST_ID,
                ImageRequestResult.success(new byte[ImageRequestLimits.MAX_BYTES_PER_PART]));
        helper.assertTrue(boundary.size() == 1 && boundary.getFirst().totalParts() == 1,
                "packet-size boundary no longer uses one success response");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void failureResponseAndPacketCodecsPreserveRequestIdentity(GameTestHelper helper) {
        SPacketImageResponse failure = roundTrip(SPacketImageResponse.createResponses(URL, REQUEST_ID,
                ImageRequestResult.failure(ImageRequestResult.Status.DOWNLOAD_FAILED)).getFirst());
        helper.assertTrue(failure.status() == ImageRequestResult.Status.DOWNLOAD_FAILED,
                "failure response changed its status");
        helper.assertTrue(failure.imagePart().length == 0 && failure.totalParts() == 0,
                "failure response included success-part metadata");

        FriendlyByteBuf oversizedPartBuffer = new FriendlyByteBuf(Unpooled.buffer());
        boolean oversizedPartRejected = false;
        try {
            oversizedPartBuffer.writeUtf(URL);
            oversizedPartBuffer.writeLong(REQUEST_ID);
            oversizedPartBuffer.writeEnum(ImageRequestResult.Status.SUCCESS);
            oversizedPartBuffer.writeInt(0);
            oversizedPartBuffer.writeInt(1);
            oversizedPartBuffer.writeByteArray(new byte[ImageRequestLimits.MAX_BYTES_PER_PART + 1]);
            SPacketImageResponse.CODEC.decode(oversizedPartBuffer);
        } catch (RuntimeException exception) {
            oversizedPartRejected = true;
        } finally {
            oversizedPartBuffer.release();
        }
        helper.assertTrue(oversizedPartRejected, "response codec allocated an oversized image part");

        CPacketImageRequest request = new CPacketImageRequest(URL, REQUEST_ID);
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CPacketImageRequest.CODEC.encode(requestBuffer, request);
            CPacketImageRequest decoded = CPacketImageRequest.CODEC.decode(requestBuffer);
            helper.assertTrue(decoded.url().equals(URL) && decoded.requestId() == REQUEST_ID,
                    "request codec changed request identity");
        } finally {
            requestBuffer.release();
        }
        helper.succeed();
    }

    private static SPacketImageResponse roundTrip(SPacketImageResponse response) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SPacketImageResponse.CODEC.encode(buffer, response);
            return SPacketImageResponse.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
