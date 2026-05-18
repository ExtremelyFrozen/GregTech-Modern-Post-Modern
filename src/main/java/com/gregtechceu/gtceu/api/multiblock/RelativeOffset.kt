package com.gregtechceu.gtceu.api.multiblock

import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * 将结构定义中的相对坐标 (x, y, z) 根据多方块的方向和 StructureDir 转换为绝对世界坐标偏移。
 * 完全复用 RelativeDirection 的坐标变换逻辑，无数组操作。
 */
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
        // 步骤1: 将 (x, y, z) 转换为标准偏移量 (leftOffset, upOffset, forwardOffset)
        // 标准偏移量的含义：正 left 对应 RelativeDirection.LEFT，正 up 对应 RelativeDirection.UP，正 forward 对应 RelativeDirection.FRONT
        var leftOffset = 0
        var upOffset = 0
        var forwardOffset = 0

        // 辅助函数：根据相对方向将一个值加到对应的标准偏移量上
        fun addToStandard(relDir: RelativeDirection, value: Int) {
            when (relDir) {
                RelativeDirection.LEFT -> leftOffset += value
                RelativeDirection.RIGHT -> leftOffset -= value
                RelativeDirection.UP -> upOffset += value
                RelativeDirection.DOWN -> upOffset -= value
                RelativeDirection.FRONT -> forwardOffset += value
                RelativeDirection.BACK -> forwardOffset -= value
            }
        }

        // 原代码中，structureDir 的三个轴分别对应输入 (x, y, z)
        // 注意：原 switch 中，如果得到 WEST 就给 x1 减，EAST 给 x1 加，这正好是 left 方向（WEST=左负，EAST=左正）
        // 因此可以直接用 addToStandard 处理
        addToStandard(structureDir.charDir, x)
        addToStandard(structureDir.stringDir, y)
        addToStandard(structureDir.aisleDir, z)

        // 步骤2: 利用 RelativeDirection.offsetPos 将标准偏移量转换为绝对偏移
        // offsetPos 内部已正确处理 facing 为 UP/DOWN 时的旋转以及 isFlipped 翻转
        val basePos = BlockPos.ZERO
        val result = RelativeDirection.offsetPos(
            basePos,
            facing,
            upwardsFacing,
            isFlipped,
            upOffset,
            leftOffset,
            forwardOffset,
        )
        return result
    }
}
