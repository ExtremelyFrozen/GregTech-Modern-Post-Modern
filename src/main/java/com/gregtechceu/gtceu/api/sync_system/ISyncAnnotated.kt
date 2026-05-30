package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener

import net.neoforged.neoforge.common.util.INBTSerializable

/**
 * Represents a class with fields that have sync annotations. <br></br>
 *
 * This interface has no functionality on its own, it just marks that a class has sync annotations. <br></br>
 *
 * A sync annotated class cannot be synced on its own, it must be a field of an [ISyncManaged] class. <br></br>
 *
 * All [ISyncAnnotated] classes should have a no-args constructor. <br></br>
 *
 * [ClientFieldChangeListener] does not work for fields in [ISyncAnnotated] classes.
 *
 *
 * A field of type `T` can be marked with sync annotations if:
 * <ul>
 * <li>`T` is primitive</li>
 * <li>`T` has a [FieldCodecs] codec registered</li>
 * <li>`T` implements [INBTSerializable]</li>
 * <li>`T` is an [ISyncAnnotated] class</li>
 * </ul>
 *
 * @see ISyncManaged
 */
interface ISyncAnnotated
