package com.gregtechceu.gtceu.api.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

data class RelativeOffset(
	private val x: Int,
	private val y: Int,
	private val z: Int,
	private val structureDir: StructureDir,
	private val facing: Direction,
	private val upwardsFacing: Direction,
	private val isFlipped: Boolean,
) {
	fun toBlockPos(): BlockPos {
		val input = intArrayOf(x, y, z)
		val output = IntArray(3)
		val dirs = arrayOf(structureDir.charDir(), structureDir.stringDir(), structureDir.aisleDir())

		fun apply(direction: Direction, value: Int) {
			when (direction) {
				Direction.UP -> output[1] = value
				Direction.DOWN -> output[1] = -value
				Direction.WEST -> output[0] = -value
				Direction.EAST -> output[0] = value
				Direction.NORTH -> output[2] = -value
				Direction.SOUTH -> output[2] = value
			}
		}

		if (facing == Direction.UP || facing == Direction.DOWN) {
			val of = if (facing == Direction.DOWN) upwardsFacing else upwardsFacing.getOpposite()
			for (i in dirs.indices) {
				apply(dirs[i].getActualDirection(of), input[i])
			}
			val xOffset = upwardsFacing.getStepX()
			val zOffset = upwardsFacing.getStepZ()
			if (xOffset == 0) {
				val tmp = output[2]
				output[2] = if (zOffset > 0) output[1] else -output[1]
				output[1] = if (zOffset > 0) -tmp else tmp
			} else {
				val tmp = output[0]
				output[0] = if (xOffset > 0) output[1] else -output[1]
				output[1] = if (xOffset > 0) -tmp else tmp
			}
			if (isFlipped) {
				if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
					output[0] = -output[0]
				} else {
					output[2] = -output[2]
				}
			}
		} else {
			for (i in dirs.indices) {
				apply(dirs[i].getActualDirection(facing), input[i])
			}
			if (upwardsFacing == Direction.WEST || upwardsFacing == Direction.EAST) {
				val xOffset = if (upwardsFacing == Direction.EAST) {
					facing.getClockWise().getStepX()
				} else {
					facing.getClockWise().getOpposite().getStepX()
				}
				val zOffset = if (upwardsFacing == Direction.EAST) {
					facing.getClockWise().getStepZ()
				} else {
					facing.getClockWise().getOpposite().getStepZ()
				}
				if (xOffset == 0) {
					val tmp = output[2]
					output[2] = if (zOffset > 0) -output[1] else output[1]
					output[1] = if (zOffset > 0) tmp else -tmp
				} else {
					val tmp = output[0]
					output[0] = if (xOffset > 0) -output[1] else output[1]
					output[1] = if (xOffset > 0) tmp else -tmp
				}
			} else if (upwardsFacing == Direction.SOUTH) {
				output[1] = -output[1]
				if (facing.getStepX() == 0) {
					output[0] = -output[0]
				} else {
					output[2] = -output[2]
				}
			}
			if (isFlipped) {
				if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
					if (facing == Direction.NORTH || facing == Direction.SOUTH) {
						output[0] = -output[0]
					} else {
						output[2] = -output[2]
					}
				} else {
					output[1] = -output[1]
				}
			}
		}
		return BlockPos(output[0], output[1], output[2])
	}
}
