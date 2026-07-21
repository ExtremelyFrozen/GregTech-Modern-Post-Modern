package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

public record StructureDir(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {

    public void check() {
        int flags = 0;
        switch (charDir) {
            case UP, DOWN -> flags |= 0x1;
            case LEFT, RIGHT -> flags |= 0x2;
            case FRONT, BACK -> flags |= 0x4;
        }
        switch (stringDir) {
            case UP, DOWN -> flags |= 0x1;
            case LEFT, RIGHT -> flags |= 0x2;
            case FRONT, BACK -> flags |= 0x4;
        }
        switch (aisleDir) {
            case UP, DOWN -> flags |= 0x1;
            case LEFT, RIGHT -> flags |= 0x2;
            case FRONT, BACK -> flags |= 0x4;
        }
        if (flags != 0x7) throw new IllegalArgumentException("Must have 3 different axes!");
    }
}
