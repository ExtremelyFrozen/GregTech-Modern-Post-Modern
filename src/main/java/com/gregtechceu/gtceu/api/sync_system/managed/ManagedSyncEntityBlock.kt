package com.gregtechceu.gtceu.api.sync_system.managed

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

import org.jetbrains.annotations.Nullable

/**
 * Entity block that implements the default ticker for a {@link ManagedSyncBlockEntity}
 */
interface ManagedSyncEntityBlock : EntityBlock {

	@Nullable
	override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, blockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? = if (!level.isClientSide) {
		BlockEntityTicker { pLevel: Level, pPos: BlockPos, pState: BlockState, pTile: T? ->
			if (pTile is ManagedSyncBlockEntity) {
				pTile.serverTick()
			}
		}
	} else {
		BlockEntityTicker { pLevel: Level, pPos: BlockPos, pState: BlockState, pTile: T? ->
			if (pTile is ManagedSyncBlockEntity) {
				pTile.clientTick()
			}
		}
	}
}
