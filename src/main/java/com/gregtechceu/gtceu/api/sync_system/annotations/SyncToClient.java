package com.gregtechceu.gtceu.api.sync_system.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instructs the sync system to sync any changes to this field with clients.
 * <p>
 * Immutable value changes are detected by the automatic sync scan. Mutable holders with internal state should expose
 * their own contextual codec or sync-managed child state.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SyncToClient {}
