package com.gregtechceu.gtceu.api.sync_system.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instructs the sync system to save and load this field to and from item data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemSave {

    /**
     * Specifies the NBT key the data should be stored under, defaulting to the field name.
     * This is only used by legacy NBT boundaries.
     */
    String nbtKey() default "";

    /**
     * Specifies the field id inside the sync-field data component, defaulting to {@link #nbtKey()} or the field name.
     * A path without namespace is resolved in the GTCEu namespace.
     */
    String component() default "";
}
