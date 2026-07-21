package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.ImageRequestLimits;
import com.gregtechceu.gtceu.api.misc.ImageRequestResult;
import com.gregtechceu.gtceu.client.util.ClientImageCache;
import com.gregtechceu.gtceu.utils.GTMath;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SPacketImageResponse implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("packet_image_response");
    public static final Type<SPacketImageResponse> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SPacketImageResponse> CODEC = StreamCodec
            .ofMember(SPacketImageResponse::encode, SPacketImageResponse::new);

    private final ImageRequestResult.Status status;
    private final byte[] imagePart;
    private final String url;
    private final long requestId;
    private final int index;
    private final int totalParts;

    private SPacketImageResponse(String url, long requestId, ImageRequestResult.Status status, byte[] imagePart,
                                 int index, int totalParts) {
        this.url = url;
        this.requestId = requestId;
        this.status = status;
        this.imagePart = imagePart;
        this.index = index;
        this.totalParts = totalParts;
    }

    private SPacketImageResponse(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.requestId = buf.readLong();
        this.status = buf.readEnum(ImageRequestResult.Status.class);
        this.index = buf.readInt();
        this.totalParts = buf.readInt();
        this.imagePart = buf.readByteArray(ImageRequestLimits.MAX_BYTES_PER_PART);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(url);
        buffer.writeLong(requestId);
        buffer.writeEnum(status);
        buffer.writeInt(index);
        buffer.writeInt(totalParts);
        buffer.writeByteArray(imagePart);
    }

    public void execute(IPayloadContext context) {
        if (status == ImageRequestResult.Status.SUCCESS) {
            ClientImageCache.receiveImagePart(url, requestId, imagePart, index, totalParts);
        } else {
            ClientImageCache.receiveImageFailure(url, requestId, status);
        }
    }

    public static void sendResult(String url, long requestId, ImageRequestResult result, IPayloadContext context) {
        for (SPacketImageResponse response : createResponses(url, requestId, result)) {
            context.reply(response);
        }
    }

    static List<SPacketImageResponse> createResponses(String url, long requestId, ImageRequestResult result) {
        if (!result.successful()) {
            return List.of(new SPacketImageResponse(
                    url, requestId, result.status(), result.imageBytes(), 0, 0));
        }

        byte[] imageBytes = result.imageBytes();
        if (imageBytes.length <= ImageRequestLimits.MAX_BYTES_PER_PART) {
            return List.of(new SPacketImageResponse(
                    url, requestId, ImageRequestResult.Status.SUCCESS, imageBytes, 0, 1));
        }

        int packetCount = GTMath.ceilDiv(imageBytes.length, ImageRequestLimits.MAX_BYTES_PER_PART);
        List<SPacketImageResponse> responses = new ArrayList<>(packetCount);
        for (int index = 0; index < packetCount; index++) {
            int from = index * ImageRequestLimits.MAX_BYTES_PER_PART;
            int to = Math.min(from + ImageRequestLimits.MAX_BYTES_PER_PART, imageBytes.length);
            byte[] part = Arrays.copyOfRange(imageBytes, from, to);
            responses.add(new SPacketImageResponse(
                    url, requestId, ImageRequestResult.Status.SUCCESS, part, index, packetCount));
        }
        return responses;
    }

    ImageRequestResult.Status status() {
        return status;
    }

    byte[] imagePart() {
        return imagePart;
    }

    String url() {
        return url;
    }

    long requestId() {
        return requestId;
    }

    int index() {
        return index;
    }

    int totalParts() {
        return totalParts;
    }

    @Override
    public @NotNull Type<SPacketImageResponse> type() {
        return TYPE;
    }
}
