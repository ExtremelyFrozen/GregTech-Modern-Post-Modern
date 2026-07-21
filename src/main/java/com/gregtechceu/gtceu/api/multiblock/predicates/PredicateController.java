package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.TraceabilityPredicate;

public class PredicateController extends TraceabilityPredicate {

    public PredicateController(MachineDefinition definition) {
        super(new PredicateBlocks(definition.get()));
    }

    public PredicateController(TraceabilityPredicate predicate) {
        super(predicate);
    }
}
