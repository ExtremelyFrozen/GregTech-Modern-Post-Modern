package com.gregtechceu.gtceu.api.transfer;

import net.minecraft.core.component.DataComponentMap;

/**
 * Transfer object data exposed as typed data components.
 *
 * <p>
 * Mutable item and fluid handlers are used in sync, machine runtime, and final block-entity persistence paths. This
 * interface gives GT-owned handlers a single component representation so only final persistence boundaries need to
 * encode the component map into the world's persisted format.
 * </p>
 */
public interface DataComponentTransfer {

    /**
     * Exports the current mutable transfer contents into a typed component map.
     */
    DataComponentMap exportComponents();

    /**
     * Imports mutable transfer contents from a typed component map.
     */
    void importComponents(DataComponentMap components);
}
