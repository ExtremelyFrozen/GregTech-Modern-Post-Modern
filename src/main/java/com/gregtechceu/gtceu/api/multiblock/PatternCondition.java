package com.gregtechceu.gtceu.api.multiblock;

import java.util.function.Predicate;

public record PatternCondition(Predicate<MultiblockState> condition, String translateKey) {}
