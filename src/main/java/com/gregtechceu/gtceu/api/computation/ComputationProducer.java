package com.gregtechceu.gtceu.api.computation;

/**
 * Produces CWU/t for a computation network.
 */
public interface ComputationProducer {

    /**
     * @return CWU/t offered during the current network solve
     */
    int getOfferedCWUt();

    /**
     * @return whether this producer may bridge through a Network Switch
     */
    default boolean canBridgeComputation() {
        return true;
    }

    /**
     * Applies the CWU/t actually consumed from this producer.
     *
     * @param allocatedCWUt allocated CWU/t
     */
    default void applyProducedCWUt(int allocatedCWUt) {}
}
