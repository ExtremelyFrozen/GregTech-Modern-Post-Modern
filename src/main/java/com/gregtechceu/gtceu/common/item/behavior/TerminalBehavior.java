package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.component.IComponentCapability;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSource;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSources;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildProblem;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildRequest;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.TerminalAutoBuildConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;
import java.util.List;

public class TerminalBehavior implements IInteractionItem, IComponentCapability {

    @Override
    public void attachCapabilities(RegisterCapabilitiesEvent event, Item item) {
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, unused) -> new TerminalItemHandler(stack), item);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            Level level = context.getLevel();
            BlockPos blockPos = context.getClickedPos();
            if (context.getPlayer() != null &&
                    MetaMachine.getMachine(level, blockPos) instanceof MultiblockControllerMachine controller) {
                if (!level.isClientSide) {
                    Player player = context.getPlayer();
                    if (player instanceof ServerPlayer serverPlayer) {
                        AutoBuildRequest request = createRequest(context.getItemInHand(), serverPlayer);
                        var result = controller.autoBuild(serverPlayer, request);
                        serverPlayer.displayClientMessage(result.summary(), true);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(ItemStack item, Level level, Player player,
                                                  InteractionHand usedHand) {
        return InteractionResultHolder.pass(item);
    }

    private AutoBuildRequest createRequest(ItemStack terminal, ServerPlayer player) {
        TerminalAutoBuildConfig config = terminal.getOrDefault(GTDataComponents.TERMINAL_AUTO_BUILD,
                TerminalAutoBuildConfig.DEFAULT);
        List<AutoBuildMaterialSource> sources = new ArrayList<>();
        if (config.options().useME()) {
            if (GTCEu.Mods.isAE2Loaded()) {
                sources.add(AutoBuildMaterialSources.me(player));
            } else {
                sources.add(AutoBuildMaterialSources.unavailable(new AutoBuildProblem(
                        AutoBuildProblem.Type.ME_UNAVAILABLE, null,
                        Component.translatable("gtpm.multiblock.autobuild.me_unavailable"))));
            }
        }
        var terminalHandler = terminal.getCapability(Capabilities.ItemHandler.ITEM);
        if (terminalHandler != null) {
            sources.add(AutoBuildMaterialSources.itemHandler(terminalHandler));
        }
        sources.add(AutoBuildMaterialSources.playerInventory(player, terminal));
        return new AutoBuildRequest(config.structureName(), config.options(), sources);
    }
}
