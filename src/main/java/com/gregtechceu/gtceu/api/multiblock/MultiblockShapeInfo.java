package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MultiblockShapeInfo {

    private final MultiblockBlockInfo[][][] blocks; // [z][y][x]

    public MultiblockShapeInfo(MultiblockBlockInfo[][][] blocks) {
        this.blocks = blocks;
    }

    public MultiblockBlockInfo[][][] getBlocks() {
        return blocks;
    }

    public static ShapeInfoBuilder builder() {
        return new ShapeInfoBuilder();
    }

    public static class ShapeInfoBuilder {

        private final List<String[]> shape = new ArrayList<>();
        private final Map<Character, MultiblockBlockInfo> symbolMap = new HashMap<>();

        public ShapeInfoBuilder aisle(String... aisle) {
            shape.add(aisle);
            return this;
        }

        public ShapeInfoBuilder where(char symbol, MultiblockBlockInfo blockInfo) {
            symbolMap.put(symbol, blockInfo);
            return this;
        }

        public ShapeInfoBuilder shallowCopy() {
            ShapeInfoBuilder builder = new ShapeInfoBuilder();
            builder.shape.addAll(shape);
            builder.symbolMap.putAll(symbolMap);
            return builder;
        }

        public ShapeInfoBuilder where(char symbol, BlockState blockState) {
            return where(symbol, MultiblockBlockInfo.fromBlockState(blockState));
        }

        public ShapeInfoBuilder where(char symbol, Supplier<? extends Block> block) {
            return where(symbol, block.get());
        }

        public ShapeInfoBuilder where(char symbol, Block block) {
            return where(symbol, block.defaultBlockState());
        }

        public ShapeInfoBuilder where(char symbol, Supplier<? extends MetaMachineBlock> machine, Direction facing) {
            return where(symbol, machine.get(), facing);
        }

        public ShapeInfoBuilder where(char symbol, MetaMachineBlock machine, Direction facing) {
            return where(symbol, machine.getRotationState() == RotationState.NONE ?
                    machine.defaultBlockState() :
                    machine.defaultBlockState().setValue(machine.getRotationState().property, facing));
        }

        private MultiblockBlockInfo[][][] bake() {
            MultiblockBlockInfo[][][] blocks = new MultiblockBlockInfo[shape.size()][][];
            for (int z = 0; z < shape.size(); z++) {
                String[] aisle = shape.get(z);
                blocks[z] = new MultiblockBlockInfo[aisle.length][];
                for (int y = 0; y < aisle.length; y++) {
                    String row = aisle[y];
                    blocks[z][y] = new MultiblockBlockInfo[row.length()];
                    for (int x = 0; x < row.length(); x++) {
                        blocks[z][y][x] = symbolMap.getOrDefault(row.charAt(x), MultiblockBlockInfo.EMPTY);
                    }
                }
            }
            return blocks;
        }

        public MultiblockShapeInfo build() {
            return new MultiblockShapeInfo(bake());
        }
    }
}
