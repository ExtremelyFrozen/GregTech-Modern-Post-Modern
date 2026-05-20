package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import java.util.function.Predicate;

public record PatternCondition(Predicate<MultiblockState> condition, String translateKey) {}
