package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.ImageCache;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketImageRequest implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("image_request");
    public static final Type<CPacketImageRequest> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, CPacketImageRequest> CODEC = StreamCodec
            .ofMember(CPacketImageRequest::encode, CPacketImageRequest::new);

    private final String url;
    private final long requestId;

    public CPacketImageRequest(String url, long requestId) {
        this.url = url;
        this.requestId = requestId;
    }

    public CPacketImageRequest(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.requestId = buf.readLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(url);
        buffer.writeLong(requestId);
    }

    public void execute(IPayloadContext context) {
        ImageCache.queryServerImage(url,
                result -> SPacketImageResponse.sendResult(url, requestId, result, context));
    }

    String url() {
        return url;
    }

    long requestId() {
        return requestId;
    }

    @Override
    public @NotNull Type<CPacketImageRequest> type() {
        return TYPE;
    }
}
