package com.gregtechceu.gtceu.api.sync_system

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

@ApiStatus.Internal
class TypeDeclaration(type: Type) {
	val rawType: Type = type

	@field:Nullable
	val classValue: Class<*>?
	val genericTypeArgs: Array<TypeDeclaration>
	private val arrayComponentType: TypeDeclaration?

	init {
		when (type) {
			is ParameterizedType -> {
				classValue = type.rawType as Class<*>
				genericTypeArgs = type.actualTypeArguments.map(::TypeDeclaration).toTypedArray()
				arrayComponentType = null
			}

			is GenericArrayType -> {
				classValue = null
				arrayComponentType = TypeDeclaration(type.genericComponentType)
				genericTypeArgs = emptyArray()
			}

			is WildcardType -> {
				classValue = null
				genericTypeArgs = emptyArray()
				arrayComponentType = null
			}

			else -> {
				classValue = type as Class<*>
				genericTypeArgs = emptyArray()
				arrayComponentType = if (classValue.isArray) TypeDeclaration(classValue.componentType) else null
			}
		}
	}

	fun isArray(): Boolean = classValue?.isArray == true || rawType is GenericArrayType

	fun getArrayComponentType(): TypeDeclaration = arrayComponentType
		?: throw IllegalStateException("Attempted to get array component for non-array type $rawType")

	override fun toString(): String = rawType.toString()
}
