package com.gregtechceu.gtceu.api.computation;

/**
 * Consumes CWU/t assigned by a computation network.
 */
public interface ComputationConsumer {

    /**
     * @return CWU/t that must be allocated before optional requests are considered
     */
    int getMinimumCWUt();

    /**
     * @return CWU/t the consumer wants for the current network solve
     */
    int getRequestedCWUt();

    /**
     * Applies the CWU/t assigned during the current solve.
     *
     * @param receivedCWUt assigned CWU/t
     */
    default void applyReceivedCWUt(int receivedCWUt) {}

    /**
     * Notifies the consumer that available computation changed.
     */
    default void onComputationChanged() {}
}
