package com.gregtechceu.gtceu.api.computation;

import java.util.Optional;

/**
 * Endpoint participating in a computation network topology.
 */
public interface ComputationPort {

    /**
     * @return routing policy for this port
     */
    ComputationPortPolicy getComputationPortPolicy();

    /**
     * @return producer exposed by this port, if any
     */
    default Optional<ComputationProducer> getComputationProducer() {
        return Optional.empty();
    }

    /**
     * @return consumer exposed by this port, if any
     */
    default Optional<ComputationConsumer> getComputationConsumer() {
        return Optional.empty();
    }

    /**
     * Called when an attached optical pipe route may have changed.
     */
    default void onOpticalRouteChanged() {}
}
