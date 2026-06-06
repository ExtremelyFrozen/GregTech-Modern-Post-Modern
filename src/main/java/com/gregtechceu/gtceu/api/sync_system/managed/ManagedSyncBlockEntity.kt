package com.gregtechceu.gtceu.api.sync_system.managed

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineSyncToServer

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.PacketDistributor

import org.jetbrains.annotations.MustBeInvokedByOverriders
import org.jetbrains.annotations.Nullable

import java.util.*

/**
 * A BlockEntity that manages sync and save data via the `ISyncManaged` syncdata system.
 *
 * @see ISyncManaged
 */
abstract class ManagedSyncBlockEntity :
	BlockEntity,
	ISyncManaged {
	@JvmField
	protected val syncDataHolder: SyncDataHolder = SyncDataHolder(this)
	private var dirty = false

	constructor(info: BlockEntityCreationInfo) : super(info.type(), info.pos(), info.state())

	constructor(type: BlockEntityType<*>, pos: BlockPos, blockState: BlockState) : super(type, pos, blockState)

	override fun getSyncDataHolder(): SyncDataHolder = syncDataHolder

	fun isDirty(): Boolean = dirty

	fun setDirty(dirty: Boolean) {
		this.dirty = dirty
	}

	/**
	 * Saves BE data to world save.
	 */
	final override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
		super.saveAdditional(tag, registries)
		tag.merge(getSyncDataHolder().serializeToSaveNBT(registries))
	}

	/**
	 * Loads BE data from world save.<br></br>
	 * Override this to add logic for modifying saved data before it is loaded (e.g. for cross-version
	 * compatibility).<br></br>
	 * When overriding, `super.loadAdditional(tag, registries)` must be called **AFTER** any custom logic.
	 */
	@MustBeInvokedByOverriders
	override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
		super.loadAdditional(tag, registries)
		getSyncDataHolder().deserializeNBT(registries, tag, false)
	}

	/**
	 * Loads BE data from client update packet
	 */
	@MustBeInvokedByOverriders
	open fun clientLoad(tag: CompoundTag, registries: HolderLookup.Provider) {
		getSyncDataHolder().deserializeNBT(registries, tag, true)
	}

	final override fun handleUpdateTag(tag: CompoundTag, registries: HolderLookup.Provider) {
		clientLoad(tag, registries)
	}

	final override fun onDataPacket(net: Connection, pkt: ClientboundBlockEntityDataPacket, registries: HolderLookup.Provider) {
		val tag = pkt.tag
		if (tag != null) clientLoad(tag, registries)
	}

	/**
	 * Called to gather BE data to be sent when a client loads this BE.
	 */
	override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
		getSyncDataHolder().resyncAllFields()
		return getSyncDataHolder().serializeFullClientSyncNBT(registries)
	}

	/**
	 * Called to get an update packet which is sent to clients to notify them when a loaded BE's data changes.
	 */
	@Nullable
	override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = ClientboundBlockEntityDataPacket.create(this) { _, r -> getSyncDataHolder().serializeNBT(r, true) }

	@Nullable
	override fun getParentSyncObject(): ISyncManaged? = null

	final override fun markAsChanged() {
		dirty = true
	}

	override fun setChanged() {
		level?.blockEntityChanged(blockPos)
	}

	@MustBeInvokedByOverriders
	open fun serverTick() {
		setChanged()
		if (level != null && syncDataHolder.scanAndMarkChanges(level!!.registryAccess())) {
			dirty = true
		}
		if (dirty) {
			Objects.requireNonNull(level)!!.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
			dirty = false
		}
	}

	fun clientTick() {}

	open fun sendServerSyncChanges() {
		if (level == null || !level!!.isClientSide) {
			return
		}

		val changes = syncDataHolder.collectServerNetworkChanges(level!!.registryAccess())
		if (changes.isNotEmpty()) {
			PacketDistributor.sendToServer(CPacketMachineSyncToServer(blockPos, changes))
		}
	}
}
