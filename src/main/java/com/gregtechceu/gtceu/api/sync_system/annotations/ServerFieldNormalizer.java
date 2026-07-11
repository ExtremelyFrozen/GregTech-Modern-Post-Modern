package com.gregtechceu.gtceu.api.sync_system.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that validates and canonicalizes a decoded client candidate before a bidirectionally synced field is
 * changed.
 *
 * <p>
 * The method must be a non-static member of the class that declares the target field. It must accept exactly one
 * parameter and return exactly the target field type. The target must use {@link SyncBoth}, so the server can always
 * acknowledge the authoritative canonical value. The method must be a pure function and must not modify its holder or
 * any external state. Throwing an exception rejects the complete field-update batch.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ServerFieldNormalizer {

    /**
     * The {@link SyncBoth} field normalized by the annotated method.
     */
    String fieldName();
}
