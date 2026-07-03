package com.gregtechceu.gtceu.api.multiblock.autobuild;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Factory methods for automatic multiblock material sources.
 */
public final class AutoBuildMaterialSources {

    private static Function<ServerPlayer, AutoBuildMaterialSource> meSourceFactory;

    private AutoBuildMaterialSources() {}

    public static AutoBuildMaterialSource playerInventory(Player player) {
        return new ItemHandlerAutoBuildSource(player.getCapability(Capabilities.ItemHandler.ENTITY));
    }

    public static AutoBuildMaterialSource playerInventory(Player player, ItemStack excludedContainer) {
        return new ItemHandlerAutoBuildSource(player.getCapability(Capabilities.ItemHandler.ENTITY),
                excludedContainer);
    }

    public static AutoBuildMaterialSource itemHandler(IItemHandler handler) {
        return new ItemHandlerAutoBuildSource(handler);
    }

    public static AutoBuildMaterialSource itemHandler(IItemHandler handler, ItemStack excludedContainer) {
        return new ItemHandlerAutoBuildSource(handler, excludedContainer);
    }

    public static AutoBuildMaterialSource unavailable(AutoBuildProblem problem) {
        return new UnavailableAutoBuildSource(problem);
    }

    public static void registerMESource(Function<ServerPlayer, AutoBuildMaterialSource> factory) {
        meSourceFactory = factory;
    }

    public static AutoBuildMaterialSource me(ServerPlayer player) {
        if (meSourceFactory == null) {
            return unavailable(new AutoBuildProblem(AutoBuildProblem.Type.ME_UNAVAILABLE, null,
                    Component.translatable("gtpm.multiblock.autobuild.me_unavailable")));
        }
        return meSourceFactory.apply(player);
    }

    private record UnavailableAutoBuildSource(AutoBuildProblem problem) implements AutoBuildMaterialSource {

        @Override
        public Session openSession() {
            throw new IllegalStateException(problem.message().getString());
        }

        @Override
        public @Nullable AutoBuildProblem unavailableProblem() {
            return problem;
        }
    }
}
