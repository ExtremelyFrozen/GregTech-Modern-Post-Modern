package com.gregtechceu.gtceu.api.cosmetics;

import com.gregtechceu.gtceu.api.cosmetics.event.RegisterGTCapesEvent;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.CapeData;
import com.gregtechceu.gtceu.common.network.packets.SPacketNotifyCapeChange;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.SneakyThrows;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.gregtechceu.gtceu.common.commands.GTCommands.ERROR_NO_SUCH_CAPE;

public class CapeRegistry extends SavedData {

    /**
     * pseudo-registry lookup map of ID->texture.
     */
    public static final Map<ResourceLocation, ResourceLocation> ALL_CAPES = new HashMap<>();
    /**
     * Set of all the free capes' IDs
     */
    private static final Set<ResourceLocation> FREE_CAPES = new HashSet<>();

    // This map should always have TreeSet values for iteration consistency.
    private static final Map<UUID, Set<ResourceLocation>> UNLOCKED_CAPES = new Object2ObjectOpenHashMap<>();
    private static final Map<UUID, ResourceLocation> CURRENT_CAPES = new Object2ObjectOpenHashMap<>();

    private static final CapeRegistry INSTANCE = new CapeRegistry();

    private CapeRegistry() {}

    private static void initCapes() {
        RegisterGTCapesEvent event = new RegisterGTCapesEvent();
        NeoForge.EVENT_BUS.post(event);
        save();
    }

    public static void registerToServer(ServerLevel level) {
        level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(CapeRegistry.INSTANCE::init, CapeRegistry.INSTANCE::load),
                        "gtceu_capes");
    }

    private CapeRegistry init() {
        clearMaps();
        initCapes();
        return this;
    }

    public static void save() {
        INSTANCE.setDirty();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        return writeComponents(registries, exportComponents());
    }

    private CapeRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        init();
        importComponents(readComponents(registries, tag));
        return this;
    }

    static DataComponentMap exportComponents() {
        Map<UUID, List<ResourceLocation>> unlockedCapes = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : UNLOCKED_CAPES.entrySet()) {
            unlockedCapes.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        Map<UUID, ResourceLocation> currentCapes = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<UUID, ResourceLocation> entry : CURRENT_CAPES.entrySet()) {
            if (entry.getValue() != null) {
                currentCapes.put(entry.getKey(), entry.getValue());
            }
        }
        CapeData.Registry registry = new CapeData.Registry(unlockedCapes, currentCapes);
        if (registry.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(GTDataComponents.CAPE_REGISTRY.get(), registry)
                .build();
    }

    static void importComponents(DataComponentMap components) {
        if (components.isEmpty()) {
            return;
        }
        CapeData.Registry registry = components.get(GTDataComponents.CAPE_REGISTRY.get());
        if (registry == null) {
            throw new IllegalArgumentException("Cape registry data is missing root component");
        }
        for (Map.Entry<UUID, List<ResourceLocation>> entry : registry.unlockedCapes().entrySet()) {
            Set<ResourceLocation> capes = UNLOCKED_CAPES.computeIfAbsent(entry.getKey(), CapeRegistry::makeSet);
            capes.addAll(entry.getValue());
        }
        CURRENT_CAPES.putAll(registry.currentCapes());
    }

    private static DataComponentMap readComponents(HolderLookup.Provider registries, CompoundTag tag) {
        return DataComponentMap.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow();
    }

    private static CompoundTag writeComponents(HolderLookup.Provider registries, DataComponentMap components) {
        return (CompoundTag) DataComponentMap.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow();
    }

    @Nullable
    public static ResourceLocation getPlayerCapeId(UUID uuid) {
        return CURRENT_CAPES.get(uuid);
    }

    @Nullable
    public static ResourceLocation getPlayerCapeTexture(UUID uuid) {
        return ALL_CAPES.getOrDefault(getPlayerCapeId(uuid), null);
    }

    /**
     * Allows one to check what capes a specific player has unlocked through CapesRegistry.
     *
     * @param uuid The player data used to get what capes the player has through internal maps.
     * @return A list of ResourceLocations containing the cape textures that the player has unlocked.
     */
    public static Set<ResourceLocation> getUnlockedCapes(UUID uuid) {
        return UNLOCKED_CAPES.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * Registers a cape.<br>
     * use {@link RegisterGTCapesEvent#registerCape(ResourceLocation, ResourceLocation)} instead of calling this
     * directly.
     *
     * @param id      An identifier for the cape
     * @param texture The full path to the cape's texture in a resource pack
     *
     * @see RegisterGTCapesEvent#registerCape(ResourceLocation, ResourceLocation)
     */
    @ApiStatus.Internal
    public static void registerCape(ResourceLocation id, ResourceLocation texture) {
        ALL_CAPES.put(id, texture);
    }

    /**
     * Registers a cape that will always be unlocked for all players.<br>
     * use {@link RegisterGTCapesEvent#registerCape(ResourceLocation, ResourceLocation)} instead of calling this
     * directly.
     *
     * @param id      An identifier for the cape
     * @param texture The full path to the cape's texture in a resource pack
     *
     * @see RegisterGTCapesEvent#registerFreeCape(ResourceLocation, ResourceLocation)
     */
    @ApiStatus.Internal
    public static void registerFreeCape(ResourceLocation id, ResourceLocation texture) {
        registerCape(id, texture);
        FREE_CAPES.add(id);
    }

    /**
     * Automatically makes a cape available to a player.<br>
     * <strong>DOES NOT SAVE AUTOMATICALLY;
     * PLEASE CALL SAVE AFTER THIS FUNCTION IS USED IF THIS DATA IS MEANT TO PERSIST.</strong>
     *
     * @param owner The UUID of the player to give the cape to
     * @param cape  The cape to give
     * @see #removeCape(UUID, ResourceLocation)
     */
    @SneakyThrows(CommandSyntaxException.class)
    public static boolean unlockCape(UUID owner, @NotNull ResourceLocation cape) {
        if (!CapeRegistry.ALL_CAPES.containsKey(cape)) {
            throw ERROR_NO_SUCH_CAPE.create(cape.toString());
        }
        Set<ResourceLocation> capes = UNLOCKED_CAPES.computeIfAbsent(owner, CapeRegistry::makeSet);
        if (capes.contains(cape)) {
            return false;
        }
        capes.add(cape);
        UNLOCKED_CAPES.put(owner, capes);
        return true;
    }

    /**
     * Automatically removes a cape from a player.<br>
     * <strong>DOES NOT SAVE AUTOMATICALLY;
     * PLEASE CALL SAVE AFTER THIS FUNCTION IS USED IF THIS DATA IS MEANT TO PERSIST.</strong>
     *
     * @param owner The UUID of the player to take the cape from
     * @param cape  The cape to take
     * @see #unlockCape(UUID, ResourceLocation)
     */
    @SneakyThrows(CommandSyntaxException.class)
    public static boolean removeCape(UUID owner, @NotNull ResourceLocation cape) {
        if (!CapeRegistry.ALL_CAPES.containsKey(cape)) {
            throw ERROR_NO_SUCH_CAPE.create(cape.toString());
        }
        if (FREE_CAPES.contains(cape)) {
            return false;
        }
        Set<ResourceLocation> capes = UNLOCKED_CAPES.get(owner);
        if (capes == null || !capes.contains(cape)) {
            return false;
        }
        capes.remove(cape);
        UNLOCKED_CAPES.put(owner, capes);
        if (cape.equals(getPlayerCapeId(owner))) {
            setActiveCape(owner, null);
        }
        return true;
    }

    public static void clearMaps() {
        UNLOCKED_CAPES.clear();
        CURRENT_CAPES.clear();
    }

    @SneakyThrows(CommandSyntaxException.class)
    public static void giveRawCape(UUID uuid, @Nullable ResourceLocation cape) {
        if (cape != null && !CapeRegistry.ALL_CAPES.containsKey(cape)) {
            throw ERROR_NO_SUCH_CAPE.create(cape.toString());
        }
        CURRENT_CAPES.put(uuid, cape);
    }

    /**
     * Sets a player's current cape.
     *
     * @param player The UUID of the player
     * @param cape   The cape to set, or {@code null} to remove the current cape.
     */
    @SneakyThrows(CommandSyntaxException.class)
    public static boolean setActiveCape(UUID player, @Nullable ResourceLocation cape) {
        if (cape != null && !CapeRegistry.ALL_CAPES.containsKey(cape)) {
            throw ERROR_NO_SUCH_CAPE.create(cape.toString());
        }
        Set<ResourceLocation> capes = UNLOCKED_CAPES.get(player);
        if (capes == null || cape != null && !capes.contains(cape)) {
            return false;
        }
        CURRENT_CAPES.put(player, cape);
        PacketDistributor.sendToAllPlayers(new SPacketNotifyCapeChange(player, cape));
        save();
        return true;
    }

    // For loading capes when the player logs in, so that it's synced to the clients.
    public static void loadCurrentCapesOnLogin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // sync to others
        PacketDistributor.sendToAllPlayers(new SPacketNotifyCapeChange(uuid, CURRENT_CAPES.get(uuid)));
        // sync to the one who's logging in
        for (ServerPlayer otherPlayer : player.getServer().getPlayerList().getPlayers()) {
            uuid = otherPlayer.getUUID();
            PacketDistributor.sendToPlayer(player, new SPacketNotifyCapeChange(uuid, CURRENT_CAPES.get(uuid)));
        }
    }

    // Runs on login and gives the player all free capes & capes they've already unlocked.
    public static void detectNewCapes(ServerPlayer player) {
        var playerCapes = UNLOCKED_CAPES.get(player.getUUID());
        if (playerCapes == null || !playerCapes.containsAll(FREE_CAPES)) {
            for (ResourceLocation cape : FREE_CAPES) {
                unlockCape(player.getUUID(), cape);
            }
            save();
        }
    }

    private static final Comparator<ResourceLocation> SET_COMPARATOR = (o1, o2) -> {
        int result = o1.compareTo(o2);
        boolean isFirstFree = FREE_CAPES.contains(o1);
        if (isFirstFree ^ FREE_CAPES.contains(o2)) {
            if (isFirstFree) {
                return -1;
            } else {
                return 1;
            }
        } else {
            return result;
        }
    };

    private static Set<ResourceLocation> makeSet(UUID ignored) {
        return new TreeSet<>(SET_COMPARATOR);
    }
}
