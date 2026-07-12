package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.utils.input.SyncedKeyMapping;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CPacketKeyDown implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("key_down");
    public static final Type<CPacketKeyDown> TYPE = new Type<>(ID);
    public static final StreamCodec<ByteBuf, CPacketKeyDown> CODEC = ByteBufCodecs
            .map(size -> (Int2BooleanMap) new Int2BooleanOpenHashMap(size), ByteBufCodecs.VAR_INT, ByteBufCodecs.BOOL)
            .map(CPacketKeyDown::new, packet -> packet.updateKeys);

    private final Int2BooleanMap updateKeys;

    public CPacketKeyDown(Int2BooleanMap updateKeys) {
        this.updateKeys = updateKeys;
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        applyUpdates(updateKeys, player);
    }

    static boolean applyUpdates(Int2BooleanMap updates, ServerPlayer player) {
        var resolvedUpdates = new ArrayList<ResolvedKeyUpdate>(updates.size());
        for (var entry : updates.int2BooleanEntrySet()) {
            int syncId = entry.getIntKey();
            SyncedKeyMapping keyMapping = SyncedKeyMapping.getFromSyncId(syncId);
            if (keyMapping == null) {
                GTCEu.LOGGER.warn("Input sync: rejecting unknown key mapping id {} from {}", syncId,
                        player.getGameProfile().getName());
                return false;
            }
            resolvedUpdates.add(new ResolvedKeyUpdate(keyMapping, entry.getBooleanValue()));
        }

        for (ResolvedKeyUpdate update : resolvedUpdates) {
            update.keyMapping().serverActivate(update.keyDown(), player);
        }
        return true;
    }

    private record ResolvedKeyUpdate(SyncedKeyMapping keyMapping, boolean keyDown) {}

    @Override
    public @NotNull Type<CPacketKeyDown> type() {
        return TYPE;
    }
}
