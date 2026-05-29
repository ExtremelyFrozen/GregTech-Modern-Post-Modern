package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.api.data.chemical.material.IMaterialRegistry;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.codecs.CoverBehaviorCodec;
import com.gregtechceu.gtceu.api.sync_system.codecs.GTRecipeFieldCodec;
import com.gregtechceu.gtceu.api.sync_system.codecs.MachineTraitHolderCodec;
import com.gregtechceu.gtceu.api.sync_system.codecs.MonitorGroupCodec;
import com.gregtechceu.gtceu.api.sync_system.codecs.NBTSerializableCodec;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public final class FieldCodecs {

    private static final Map<Type, Codec<?>> REGISTERED = new Reference2ReferenceOpenHashMap<>();
    private static final Map<Class<?>, Supplier<Codec<?>>> REGISTERED_SUPPLIERS = new Reference2ReferenceOpenHashMap<>();
    private static final Map<Type, Codec<?>> TYPE_CACHE = new Reference2ReferenceOpenHashMap<>();
    private static final Map<Type, ContextualFieldCodec<?>> CONTEXTUAL_REGISTERED = new Reference2ReferenceOpenHashMap<>();

    private static final Map<Type, Type> PRIMITIVE_TO_BOXED = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            void.class, Void.class);

    private FieldCodecs() {}

    public static @Nullable Codec<?> get(Type type) {
        if (type instanceof Class<?> cls && cls.isPrimitive()) type = PRIMITIVE_TO_BOXED.get(cls);
        return TYPE_CACHE.computeIfAbsent(type, FieldCodecs::generateOrGetCodec);
    }

    public static void register(Type type, Codec<?> codec) {
        REGISTERED.putIfAbsent(type, codec);
        TYPE_CACHE.remove(type);
    }

    public static void registerSupplier(Class<?> type, Supplier<Codec<?>> supplier) {
        REGISTERED_SUPPLIERS.putIfAbsent(type, supplier);
        TYPE_CACHE.clear();
    }

    public static @Nullable ContextualFieldCodec<?> getContextual(Type type) {
        if (type instanceof Class<?> cls && cls.isPrimitive()) type = PRIMITIVE_TO_BOXED.get(cls);
        ContextualFieldCodec<?> registered = CONTEXTUAL_REGISTERED.get(type);
        if (registered != null) return registered;
        TypeDeclaration declaration = new TypeDeclaration(type);
        Class<?> clazz = declaration.getClassValue();
        if (clazz == null) return null;
        registered = CONTEXTUAL_REGISTERED.get(clazz);
        if (registered != null) return registered;
        for (var entry : CONTEXTUAL_REGISTERED.entrySet()) {
            if (entry.getKey() instanceof Class<?> registeredClass && registeredClass.isAssignableFrom(clazz)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static void registerContextual(Type type, ContextualFieldCodec<?> codec) {
        CONTEXTUAL_REGISTERED.putIfAbsent(type, codec);
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable Codec<T> getTyped(Type type) {
        return (Codec<T>) get(type);
    }

    private static @Nullable Codec<?> generateOrGetCodec(Type type) {
        Codec<?> registered = REGISTERED.get(type);
        if (registered != null) return registered;

        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> raw = (Class<?>) parameterizedType.getRawType();
            if (List.class.isAssignableFrom(raw)) return makeListCodec(parameterizedType);
            if (Set.class.isAssignableFrom(raw)) return makeSetCodec(parameterizedType);
            if (Map.class.isAssignableFrom(raw)) return makeMapCodec(parameterizedType);

            for (var entry : REGISTERED_SUPPLIERS.entrySet()) {
                if (entry.getKey().isAssignableFrom(raw)) return entry.getValue().get();
            }
        }

        TypeDeclaration declaration = new TypeDeclaration(type);
        Class<?> clazz = declaration.getClassValue();
        if (clazz == null) return null;

        registered = REGISTERED.get(clazz);
        if (registered != null) return registered;

        if (clazz.isArray()) return makeArrayCodec(clazz.getComponentType());
        if (clazz.isEnum()) return makeEnumCodec(clazz);

        for (var entry : REGISTERED_SUPPLIERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(clazz)) return entry.getValue().get();
        }
        for (var entry : REGISTERED.entrySet()) {
            if (entry.getKey() instanceof Class<?> registeredClass && registeredClass.isAssignableFrom(clazz)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Codec<?> makeListCodec(ParameterizedType type) {
        Codec<?> elementCodec = get(type.getActualTypeArguments()[0]);
        if (elementCodec == null) return null;
        return Codec.list((Codec<Object>) elementCodec);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Codec<?> makeSetCodec(ParameterizedType type) {
        Codec<?> elementCodec = get(type.getActualTypeArguments()[0]);
        if (elementCodec == null) return null;
        return Codec.list((Codec<Object>) elementCodec)
                .xmap(values -> (Set<Object>) new LinkedHashSet<>(values), ArrayList::new);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Codec<?> makeMapCodec(ParameterizedType type) {
        if (type.getActualTypeArguments().length != 2) return null;
        Codec<?> keyCodec = get(type.getActualTypeArguments()[0]);
        Codec<?> valueCodec = get(type.getActualTypeArguments()[1]);
        if (keyCodec == null || valueCodec == null) return null;

        Codec<Pair<Object, Object>> entryCodec = RecordCodecBuilder.create(instance -> instance.group(
                ((Codec<Object>) keyCodec).fieldOf("k").forGetter(Pair::getFirst),
                ((Codec<Object>) valueCodec).fieldOf("v").forGetter(Pair::getSecond))
                .apply(instance, Pair::of));

        return Codec.list(entryCodec).xmap(entries -> {
            Map<Object, Object> map = new LinkedHashMap<>();
            for (Pair<Object, Object> entry : entries) {
                map.put(entry.getFirst(), entry.getSecond());
            }
            return map;
        }, map -> {
            List<Pair<Object, Object>> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                entries.add(Pair.of(entry.getKey(), entry.getValue()));
            }
            return entries;
        });
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Codec<?> makeArrayCodec(Class<?> componentType) {
        Codec<?> elementCodec = get(componentType);
        if (elementCodec == null) return null;
        return Codec.list((Codec<Object>) elementCodec).xmap(
                values -> {
                    Object array = Array.newInstance(componentType, values.size());
                    for (int i = 0; i < values.size(); i++) {
                        Array.set(array, i, values.get(i));
                    }
                    return array;
                },
                array -> {
                    List<Object> values = new ArrayList<>(Array.getLength(array));
                    for (int i = 0; i < Array.getLength(array); i++) {
                        values.add(Array.get(array, i));
                    }
                    return values;
                });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Codec<?> makeEnumCodec(Class<?> clazz) {
        if (StringRepresentable.class.isAssignableFrom(clazz)) {
            Map<String, Enum<?>> valuesByName = new HashMap<>();
            for (Object value : clazz.getEnumConstants()) {
                valuesByName.put(((StringRepresentable) value).getSerializedName(), (Enum<?>) value);
            }
            return Codec.STRING.xmap(name -> {
                Enum<?> value = valuesByName.get(name);
                if (value == null) {
                    value = Enum.valueOf((Class) clazz, name);
                }
                if (value == null) {
                    throw new IllegalArgumentException("No enum constant " + clazz.getName() + "." + name);
                }
                return value;
            }, value -> ((StringRepresentable) value).getSerializedName());
        }
        return Codec.STRING.xmap(name -> Enum.valueOf((Class) clazz, name), value -> ((Enum<?>) value).name());
    }

    private static <T> Codec<T> resourceLocationReferenceCodec(Function<T, ResourceLocation> write,
                                                               Function<ResourceLocation, T> read) {
        return ResourceLocation.CODEC.xmap(read, write);
    }

    static {
        register(Integer.class, Codec.INT);
        register(Long.class, Codec.LONG);
        register(Float.class, Codec.FLOAT);
        register(Double.class, Codec.DOUBLE);
        register(Short.class, Codec.SHORT);
        register(Byte.class, Codec.BYTE);
        register(Boolean.class, Codec.BOOL);
        register(Character.class, Codec.STRING.xmap(value -> value.charAt(0), String::valueOf));

        register(int[].class, Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream));
        register(long[].class, Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream));
        register(byte[].class, Codec.list(Codec.BYTE).xmap(values -> {
            byte[] array = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                array[i] = values.get(i);
            }
            return array;
        }, array -> {
            List<Byte> values = new ArrayList<>(array.length);
            for (byte value : array) {
                values.add(value);
            }
            return values;
        }));

        register(String.class, Codec.STRING);
        register(UUID.class, UUIDUtil.CODEC);
        register(CompoundTag.class, CompoundTag.CODEC);

        register(BlockPos.class, BlockPos.CODEC);
        register(Component.class, ComponentSerialization.CODEC);
        register(ItemStack.class, ItemStack.OPTIONAL_CODEC);
        register(FluidStack.class, FluidStack.OPTIONAL_CODEC);

        register(MachineRenderState.class, MachineRenderState.CODEC);
        register(GTRecipeType.class, resourceLocationReferenceCodec(
                GTRecipeType::getRegistryName,
                id -> (GTRecipeType) Objects.requireNonNull(BuiltInRegistries.RECIPE_TYPE.get(id))));
        register(Material.class, resourceLocationReferenceCodec(
                Material::getResourceLocation,
                ((IMaterialRegistry) GTRegistries.MATERIALS)::getMaterial));
        registerContextual(ISyncManaged.class, SyncDataHolder.SYNC_MANAGED_CODEC);
        registerContextual(INBTSerializable.class, NBTSerializableCodec.INSTANCE);
        registerContextual(GTRecipeFieldCodec.TYPE, GTRecipeFieldCodec.INSTANCE);
        registerContextual(MachineTraitHolderCodec.TYPE, MachineTraitHolderCodec.INSTANCE);
        registerContextual(CoverBehaviorCodec.TYPE, CoverBehaviorCodec.INSTANCE);
        registerContextual(MonitorGroupCodec.TYPE, MonitorGroupCodec.INSTANCE);

    }
}
