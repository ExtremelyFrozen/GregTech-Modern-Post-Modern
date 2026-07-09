package com.gregtechceu.gtceu.api.machine.feature;

/**
 * Marks a machine holder as eligible for server-side actions exposed by the LDLib2 Fancy configurators.
 *
 * <p>
 * Action authorization only needs an explicit opt-in contract; it must not require the holder to implement the
 * complete LDLib2 Fancy UI provider API. This narrow marker keeps that authorization independent from page
 * construction while still preventing ordinary capability holders from invoking Fancy-only actions.
 */
public interface LDLib2FancyActionMachine {}
