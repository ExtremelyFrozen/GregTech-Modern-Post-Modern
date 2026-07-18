@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.api.data.chemical.material.Material
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry
import com.gregtechceu.gtceu.api.recipe.GTRecipeType
import com.gregtechceu.gtceu.api.registry.GTRegistries
import com.gregtechceu.gtceu.api.sync_system.codecs.CoverBehaviorCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.CustomFluidTankCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.CustomItemStackHandlerCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.DataComponentTransferCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.GTRecipeFieldCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.MachineTraitHolderCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.MonitorGroupCodec
import com.gregtechceu.gtceu.api.sync_system.codecs.VirtualEntryCodec
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged
import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState

import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.FluidStack

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap
import org.jetbrains.annotations.Nullable

import java.lang.reflect.Array
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Arrays
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID
import java.util.function.Supplier
import java.util.stream.IntStream
import java.util.stream.LongStream

object FieldCodecs {
	private val REGISTERED: MutableMap<Type, Codec<*>> = Reference2ReferenceOpenHashMap()
	private val REGISTERED_SUPPLIERS: MutableMap<Class<*>, Supplier<Codec<*>>> = Reference2ReferenceOpenHashMap()
	private val TYPE_CACHE: MutableMap<Type, Codec<*>?> = Reference2ReferenceOpenHashMap()
	private val CONTEXTUAL_REGISTERED: MutableMap<Type, ContextualFieldCodec<*>> = Reference2ReferenceOpenHashMap()
	private val CONTEXTUAL_TYPE_CACHE: MutableMap<Type, ContextualFieldCodec<*>?> = Reference2ReferenceOpenHashMap()

	private val PRIMITIVE_TO_BOXED: Map<Type, Type> = mapOf(
		Boolean::class.javaPrimitiveType!! to java.lang.Boolean::class.java,
		Byte::class.javaPrimitiveType!! to java.lang.Byte::class.java,
		Char::class.javaPrimitiveType!! to Character::class.java,
		Short::class.javaPrimitiveType!! to java.lang.Short::class.java,
		Int::class.javaPrimitiveType!! to Integer::class.java,
		Long::class.javaPrimitiveType!! to java.lang.Long::class.java,
		Float::class.javaPrimitiveType!! to java.lang.Float::class.java,
		Double::class.javaPrimitiveType!! to java.lang.Double::class.java,
		Void.TYPE to Void::class.java,
	)

	@JvmStatic
	@Nullable
	fun get(type: Type): Codec<*>? {
		val normalized = if (type is Class<*> && type.isPrimitive) PRIMITIVE_TO_BOXED[type] ?: type else type
		return TYPE_CACHE.computeIfAbsent(normalized, FieldCodecs::generateOrGetCodec)
	}

	@JvmStatic
	fun register(type: Type, codec: Codec<*>) {
		REGISTERED.putIfAbsent(type, codec)
		TYPE_CACHE.remove(type)
	}

	@JvmStatic
	fun registerSupplier(type: Class<*>, supplier: Supplier<Codec<*>>) {
		REGISTERED_SUPPLIERS.putIfAbsent(type, supplier)
		TYPE_CACHE.clear()
	}

	@JvmStatic
	@Nullable
	fun getContextual(type: Type): ContextualFieldCodec<*>? {
		val normalized = if (type is Class<*> && type.isPrimitive) PRIMITIVE_TO_BOXED[type] ?: type else type
		return CONTEXTUAL_TYPE_CACHE.computeIfAbsent(normalized, FieldCodecs::generateOrGetContextualCodec)
	}

	@Nullable
	private fun generateOrGetContextualCodec(type: Type): ContextualFieldCodec<*>? {
		var registered = CONTEXTUAL_REGISTERED[type]
		if (registered != null) return registered

		if (type is ParameterizedType) {
			val raw = type.rawType as Class<*>
			if (List::class.java.isAssignableFrom(raw)) return makeContextualListCodec(type)
			if (Set::class.java.isAssignableFrom(raw)) return makeContextualSetCodec(type)
			if (Map::class.java.isAssignableFrom(raw)) return makeContextualMapCodec(type)
		}

		val declaration = TypeDeclaration(type)
		val clazz = declaration.classValue ?: return null
		registered = CONTEXTUAL_REGISTERED[clazz]
		if (registered != null) return registered
		if (clazz.isArray) return makeContextualArrayCodec(clazz.componentType)
		for ((key, value) in CONTEXTUAL_REGISTERED) {
			if (key is Class<*> && key.isAssignableFrom(clazz)) {
				return value
			}
		}
		return null
	}

	@JvmStatic
	fun registerContextual(type: Type, codec: ContextualFieldCodec<*>) {
		CONTEXTUAL_REGISTERED.putIfAbsent(type, codec)
		CONTEXTUAL_TYPE_CACHE.remove(type)
	}

	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	@Nullable
	fun <T> getTyped(type: Type): Codec<T>? = get(type) as Codec<T>?

	@Nullable
	private fun generateOrGetCodec(type: Type): Codec<*>? {
		var registered = REGISTERED[type]
		if (registered != null) return registered

		if (type is ParameterizedType) {
			val raw = type.rawType as Class<*>
			if (List::class.java.isAssignableFrom(raw)) return makeListCodec(type)
			if (Set::class.java.isAssignableFrom(raw)) return makeSetCodec(type)
			if (Map::class.java.isAssignableFrom(raw)) return makeMapCodec(type)

			for ((key, value) in REGISTERED_SUPPLIERS) {
				if (key.isAssignableFrom(raw)) return value.get()
			}
		}

		val declaration = TypeDeclaration(type)
		val clazz = declaration.classValue ?: return null

		registered = REGISTERED[clazz]
		if (registered != null) return registered

		if (clazz.isArray) return makeArrayCodec(clazz.componentType)
		if (clazz.isEnum) return makeEnumCodec(clazz)

		for ((key, value) in REGISTERED_SUPPLIERS) {
			if (key.isAssignableFrom(clazz)) return value.get()
		}
		for ((key, value) in REGISTERED) {
			if (key is Class<*> && key.isAssignableFrom(clazz)) {
				return value
			}
		}
		return null
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeListCodec(type: ParameterizedType): Codec<*>? {
		val elementCodec = get(type.actualTypeArguments[0]) ?: return null
		return Codec.list(elementCodec as Codec<Any>)
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeSetCodec(type: ParameterizedType): Codec<*>? {
		val elementCodec = get(type.actualTypeArguments[0]) ?: return null
		return Codec.list(elementCodec as Codec<Any>)
			.xmap({ values -> LinkedHashSet(values) as Set<Any> }, { values -> ArrayList(values) })
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeMapCodec(type: ParameterizedType): Codec<*>? {
		if (type.actualTypeArguments.size != 2) return null
		val keyCodec = get(type.actualTypeArguments[0])
		val valueCodec = get(type.actualTypeArguments[1])
		if (keyCodec == null || valueCodec == null) return null

		val entryCodec: Codec<Pair<Any, Any>> = RecordCodecBuilder.create { instance ->
			instance.group(
				(keyCodec as Codec<Any>).fieldOf("k").forGetter(Pair<Any, Any>::getFirst),
				(valueCodec as Codec<Any>).fieldOf("v").forGetter(Pair<Any, Any>::getSecond),
			).apply(instance, Pair<Any, Any>::of)
		}

		return Codec.list(entryCodec).xmap(
			{ entries ->
				val map: MutableMap<Any?, Any?> = LinkedHashMap()
				for (entry in entries) {
					map[entry.first] = entry.second
				}
				map
			},
			{ map ->
				val entries = ArrayList<Pair<Any, Any>>(map.size)
				for (entry in (map as Map<*, *>).entries) {
					entries.add(Pair.of(entry.key, entry.value) as Pair<Any, Any>)
				}
				entries
			},
		)
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeArrayCodec(componentType: Class<*>): Codec<*>? {
		val elementCodec = get(componentType) ?: return null
		return Codec.list(elementCodec as Codec<Any>).xmap(
			{ values ->
				val array = Array.newInstance(componentType, values.size)
				for (i in values.indices) {
					Array.set(array, i, values[i])
				}
				array
			},
			{ array ->
				val values = ArrayList<Any>(Array.getLength(array))
				for (i in 0 until Array.getLength(array)) {
					values.add(Array.get(array, i))
				}
				values
			},
		)
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeContextualListCodec(type: ParameterizedType): ContextualFieldCodec<*>? {
		val elementCodec = getContextual(type.actualTypeArguments[0]) ?: return null
		return object : ContextualFieldCodec<List<*>> {
			override fun serializeField(value: List<*>, context: ContextualFieldCodec.Context<List<*>>): JsonElement {
				val list = JsonArray()
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				for (i in value.indices) {
					val element = value[i]
					if (element == null) {
						list.add(JsonNull.INSTANCE)
					} else {
						list.add(
							typedElementCodec.serializeField(
								element,
								nestedContext(context, type.actualTypeArguments[0], element, context.fieldName + "[" + i + "]"),
							),
						)
					}
				}
				return list
			}

			@Nullable
			override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<List<*>>): List<*>? {
				if (!value.isJsonArray) return null
				val current = context.currentValue
				val result = ArrayList<Any?>(value.asJsonArray.size())
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				for ((i, elementJson) in value.asJsonArray.withIndex()) {
					val currentElement = if (current != null && i < current.size) current[i] else null
					val element = if (elementJson.isJsonNull && !context.parseExplicitNull) {
						null
					} else {
						typedElementCodec.deserializeField(
							elementJson,
							nestedContext(context, type.actualTypeArguments[0], currentElement, context.fieldName + "[" + i + "]"),
						)
					}
					result.add(element)
				}
				return result
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeContextualSetCodec(type: ParameterizedType): ContextualFieldCodec<*>? {
		val elementCodec = getContextual(type.actualTypeArguments[0]) ?: return null
		return object : ContextualFieldCodec<Set<*>> {
			override fun serializeField(value: Set<*>, context: ContextualFieldCodec.Context<Set<*>>): JsonElement {
				val list = JsonArray()
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				for ((index, element) in value.withIndex()) {
					if (element == null) {
						list.add(JsonNull.INSTANCE)
					} else {
						list.add(
							typedElementCodec.serializeField(
								element,
								nestedContext(context, type.actualTypeArguments[0], element, context.fieldName + "[" + index + "]"),
							),
						)
					}
				}
				return list
			}

			@Nullable
			override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Set<*>>): Set<*>? {
				if (!value.isJsonArray) return null
				val result = LinkedHashSet<Any?>()
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				for ((i, elementJson) in value.asJsonArray.withIndex()) {
					val element = if (elementJson.isJsonNull && !context.parseExplicitNull) {
						null
					} else {
						typedElementCodec.deserializeField(
							elementJson,
							nestedContext(context, type.actualTypeArguments[0], null, context.fieldName + "[" + i + "]"),
						)
					}
					result.add(element)
				}
				return result
			}
		}
	}

	@Nullable
	private fun makeContextualMapCodec(type: ParameterizedType): ContextualFieldCodec<*>? {
		if (type.actualTypeArguments.size != 2) return null
		val keyCodec = getContextual(type.actualTypeArguments[0])
		val valueCodec = getContextual(type.actualTypeArguments[1])
		if (keyCodec == null && valueCodec == null) return null
		val regularKeyCodec = if (keyCodec == null) get(type.actualTypeArguments[0]) else null
		val regularValueCodec = if (valueCodec == null) get(type.actualTypeArguments[1]) else null
		if ((keyCodec == null && regularKeyCodec == null) || (valueCodec == null && regularValueCodec == null)) return null
		return ContextualMapCodec(type, keyCodec, valueCodec, regularKeyCodec, regularValueCodec)
	}

	@Suppress("UNCHECKED_CAST")
	@Nullable
	private fun makeContextualArrayCodec(componentType: Class<*>): ContextualFieldCodec<*>? {
		val elementCodec = getContextual(componentType) ?: return null
		return object : ContextualFieldCodec<Any> {
			override fun serializeField(value: Any, context: ContextualFieldCodec.Context<Any>): JsonElement {
				val list = JsonArray()
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				val length = Array.getLength(value)
				for (i in 0 until length) {
					val element = Array.get(value, i)
					if (element == null) {
						list.add(JsonNull.INSTANCE)
					} else {
						list.add(
							typedElementCodec.serializeField(
								element,
								nestedContext(context, componentType, element, context.fieldName + "[" + i + "]"),
							),
						)
					}
				}
				return list
			}

			@Nullable
			override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Any>): Any? {
				if (!value.isJsonArray) return null
				val current = context.currentValue
				val result = if (current != null && Array.getLength(current) == value.asJsonArray.size()) current else Array.newInstance(componentType, value.asJsonArray.size())
				val typedElementCodec = elementCodec as ContextualFieldCodec<Any>
				for ((i, elementJson) in value.asJsonArray.withIndex()) {
					val currentElement = if (current != null && i < Array.getLength(current)) Array.get(current, i) else null
					val element = if (elementJson.isJsonNull && !context.parseExplicitNull) {
						null
					} else {
						typedElementCodec.deserializeField(
							elementJson,
							nestedContext(context, componentType, currentElement, context.fieldName + "[" + i + "]"),
						)
					}
					Array.set(result, i, element)
				}
				return result
			}
		}
	}

	private fun <T> nestedContext(parent: ContextualFieldCodec.Context<*>, type: Type, @Nullable currentValue: T?, fieldName: String): ContextualFieldCodec.Context<T> = ContextualFieldCodec.Context(
		parent.holder,
		TypeDeclaration(type),
		currentValue,
		fieldName,
		parent.isClientSync,
		parent.isClientFullSyncUpdate,
		parent.lookup,
		parent.serializationTarget,
		parent.parseExplicitNull,
		parent.notifyUnchangedOnFullSync,
	)

	@Suppress("UNCHECKED_CAST")
	private class ContextualMapCodec(
		private val type: ParameterizedType,
		@field:Nullable private val keyCodec: ContextualFieldCodec<*>?,
		@field:Nullable private val valueCodec: ContextualFieldCodec<*>?,
		@field:Nullable private val regularKeyCodec: Codec<*>?,
		@field:Nullable private val regularValueCodec: Codec<*>?,
	) : ContextualFieldCodec<Map<*, *>> {
		override fun serializeField(value: Map<*, *>, context: ContextualFieldCodec.Context<Map<*, *>>): JsonElement {
			val list = JsonArray()
			var index = 0
			for ((key, entryValue) in value) {
				val entryJson = JsonObject()
				entryJson.add(
					"k",
					serializeMapElementData(key, keyCodec, regularKeyCodec, context, type.actualTypeArguments[0], context.fieldName + "[" + index + "].key"),
				)
				entryJson.add(
					"v",
					serializeMapElementData(entryValue, valueCodec, regularValueCodec, context, type.actualTypeArguments[1], context.fieldName + "[" + index + "].value"),
				)
				list.add(entryJson)
				index++
			}
			return list
		}

		@Nullable
		override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Map<*, *>>): Map<*, *>? {
			if (!value.isJsonArray) return null
			val result: MutableMap<Any?, Any?> = LinkedHashMap()
			for ((i, element) in value.asJsonArray.withIndex()) {
				if (!element.isJsonObject) continue
				val entryJson = element.asJsonObject
				val key = deserializeMapElementData(entryJson.get("k"), keyCodec, regularKeyCodec, context, type.actualTypeArguments[0], context.fieldName + "[" + i + "].key")
				val entryValue = deserializeMapElementData(entryJson.get("v"), valueCodec, regularValueCodec, context, type.actualTypeArguments[1], context.fieldName + "[" + i + "].value")
				result[key] = entryValue
			}
			return result
		}

		companion object {
			private fun serializeMapElementData(
				@Nullable value: Any?,
				@Nullable contextualCodec: ContextualFieldCodec<*>?,
				@Nullable regularCodec: Codec<*>?,
				context: ContextualFieldCodec.Context<*>,
				type: Type,
				fieldName: String,
			): JsonElement {
				if (value == null) return JsonNull.INSTANCE
				if (contextualCodec != null) {
					return (contextualCodec as ContextualFieldCodec<Any>).serializeField(value, nestedContext(context, type, value, fieldName))
				}
				return (regularCodec as Codec<Any>)
					.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
					.getOrThrow()
			}

			@Nullable
			private fun deserializeMapElementData(
				@Nullable value: JsonElement?,
				@Nullable contextualCodec: ContextualFieldCodec<*>?,
				@Nullable regularCodec: Codec<*>?,
				context: ContextualFieldCodec.Context<*>,
				type: Type,
				fieldName: String,
			): Any? {
				if (value == null || (value.isJsonNull && !context.parseExplicitNull)) return null
				if (contextualCodec != null) {
					return (contextualCodec as ContextualFieldCodec<Any>).deserializeField(value, nestedContext(context, type, null, fieldName))
				}
				return (regularCodec as Codec<Any>)
					.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
					.getOrThrow()
			}
		}
	}

	@Suppress("UNCHECKED_CAST", "rawtypes")
	private fun makeEnumCodec(clazz: Class<*>): Codec<*> {
		if (StringRepresentable::class.java.isAssignableFrom(clazz)) {
			val valuesByName = HashMap<String, Enum<*>>()
			for (value in clazz.enumConstants) {
				valuesByName[(value as StringRepresentable).serializedName] = value as Enum<*>
			}
			return Codec.STRING.xmap(
				{ name ->
					var value = valuesByName[name]
					if (value == null) {
						value = java.lang.Enum.valueOf(clazz as Class<out Enum<*>>, name)
					}
					value ?: throw IllegalArgumentException("No enum constant ${clazz.name}.$name")
				},
				{ value -> (value as StringRepresentable).serializedName },
			)
		}
		return Codec.STRING.xmap(
			{ name -> java.lang.Enum.valueOf(clazz as Class<out Enum<*>>, name) },
			{ value -> (value as Enum<*>).name },
		)
	}

	private fun <T> resourceLocationReferenceCodec(write: (T) -> ResourceLocation, read: (ResourceLocation) -> T): Codec<T> = ResourceLocation.CODEC.xmap(read, write)

	init {
		register(Integer::class.java, Codec.INT)
		register(java.lang.Long::class.java, Codec.LONG)
		register(java.lang.Float::class.java, Codec.FLOAT)
		register(java.lang.Double::class.java, Codec.DOUBLE)
		register(java.lang.Short::class.java, Codec.SHORT)
		register(java.lang.Byte::class.java, Codec.BYTE)
		register(java.lang.Boolean::class.java, Codec.BOOL)
		register(Character::class.java, Codec.STRING.xmap({ value -> value[0] }, { value -> value.toString() }))

		register(IntArray::class.java, Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream))
		register(LongArray::class.java, Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream))
		register(
			ByteArray::class.java,
			Codec.list(Codec.BYTE).xmap(
				{ values ->
					val array = ByteArray(values.size)
					for (i in values.indices) {
						array[i] = values[i]
					}
					array
				},
				{ array ->
					val values = ArrayList<Byte>(array.size)
					for (value in array) {
						values.add(value)
					}
					values
				},
			),
		)

		register(String::class.java, Codec.STRING)
		register(UUID::class.java, UUIDUtil.CODEC)

		register(BlockPos::class.java, BlockPos.CODEC)
		register(BlockState::class.java, BlockState.CODEC)
		register(Component::class.java, ComponentSerialization.CODEC)
		register(ItemStack::class.java, ItemStack.OPTIONAL_CODEC)
		register(FluidStack::class.java, FluidStack.OPTIONAL_CODEC)

		register(MachineRenderState::class.java, MachineRenderState.CODEC)
		register(
			GTRecipeType::class.java,
			resourceLocationReferenceCodec(
				{ recipeType -> recipeType.registryName },
				{ id -> BuiltInRegistries.RECIPE_TYPE.get(id) as GTRecipeType },
			),
		)
		register(
			Material::class.java,
			resourceLocationReferenceCodec(
				Material::getResourceLocation,
				GTRegistries.MATERIALS::getMaterial,
			),
		)
		registerContextual(ISyncManaged::class.java, SyncDataHolder.SYNC_MANAGED_CODEC)
		registerContextual(CustomItemStackHandler::class.java, CustomItemStackHandlerCodec.INSTANCE)
		registerContextual(CustomFluidTank::class.java, CustomFluidTankCodec.INSTANCE)
		registerContextual(DataComponentTransfer::class.java, DataComponentTransferCodec.INSTANCE)
		registerContextual(VirtualEntry::class.java, VirtualEntryCodec.INSTANCE)
		registerContextual(GTRecipeFieldCodec.TYPE, GTRecipeFieldCodec.INSTANCE)
		registerContextual(MachineTraitHolderCodec.TYPE, MachineTraitHolderCodec.INSTANCE)
		registerContextual(CoverBehaviorCodec.TYPE, CoverBehaviorCodec.INSTANCE)
		registerContextual(MonitorGroupCodec.TYPE, MonitorGroupCodec.INSTANCE)
	}
}
