package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ContentListMap {

    public static final Codec<ContentListMap> CODEC = RecipeCapability.CODEC
            .xmap(ContentListMap::new, ContentListMap::asContentMap);

    private final Map<RecipeCapability<?>, List<Content>> contentsMap;

    public ContentListMap() {
        contentsMap = new Reference2ObjectArrayMap<>();
    }

    public ContentListMap(Map<RecipeCapability<?>, List<Content>> contentsMap) {
        this.contentsMap = new Reference2ObjectArrayMap<>(contentsMap);
    }

    public static ContentListMap copyOf(Map<RecipeCapability<?>, List<Content>> contentsMap) {
        return new ContentListMap(contentsMap).copy();
    }

    public <T> List<Content> get(RecipeCapability<T> capability) {
        return contentsMap.get(capability);
    }

    public <T> void put(RecipeCapability<T> capability, List<Content> contents) {
        contentsMap.put(capability, contents);
    }

    public void putAll(ContentListMap other) {
        contentsMap.putAll(other.contentsMap);
    }

    public <T> List<Content> computeIfAbsent(RecipeCapability<T> capability,
                                             Function<RecipeCapability<T>, List<Content>> function) {
        return contentsMap.computeIfAbsent(capability, cap -> function.apply((RecipeCapability<T>) cap));
    }

    public void remove(RecipeCapability<?> capability) {
        contentsMap.remove(capability);
    }

    public void clear() {
        contentsMap.clear();
    }

    public boolean containsKey(RecipeCapability<?> capability) {
        return contentsMap.containsKey(capability);
    }

    public Set<RecipeCapability<?>> keySet() {
        return contentsMap.keySet();
    }

    public Set<Map.Entry<RecipeCapability<?>, List<Content>>> entrySet() {
        return contentsMap.entrySet();
    }

    public <T> List<Content> getOrDefault(RecipeCapability<T> capability, List<Content> fallback) {
        return contentsMap.getOrDefault(capability, fallback);
    }

    public void forEach(BiConsumer<RecipeCapability<?>, List<Content>> consumer) {
        contentsMap.forEach(consumer);
    }

    public <T> void add(RecipeCapability<T> capability, Content content) {
        contentsMap.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(content);
    }

    public <T> Content getFirst(RecipeCapability<T> capability) {
        List<Content> list = contentsMap.get(capability);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.getFirst();
    }

    public boolean isEmpty() {
        return contentsMap.isEmpty();
    }

    public int size() {
        return contentsMap.size();
    }

    public int sizeOf(RecipeCapability<?> capability) {
        List<Content> contents = contentsMap.get(capability);
        return contents == null ? 0 : contents.size();
    }

    public ObjectIterator<Reference2ObjectMap.Entry<RecipeCapability<?>, List<Content>>> fastIterator() {
        if (contentsMap instanceof Reference2ObjectArrayMap<RecipeCapability<?>, List<Content>> map) {
            return map.reference2ObjectEntrySet().fastIterator();
        }
        throw new IllegalStateException("ContentListMap requires Reference2ObjectArrayMap backing storage");
    }

    public Map<RecipeCapability<?>, List<Content>> asContentMap() {
        return contentsMap;
    }

    public void toNetwork(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(contentsMap.size());
        for (var entry : contentsMap.entrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            buf.writeResourceLocation(GTRegistries.RECIPE_CAPABILITIES.getKey(capability));
            List<Content> contents = entry.getValue();
            buf.writeVarInt(contents.size());
            for (Content content : contents) {
                capability.serializer.toNetworkContent(buf, content);
            }
        }
    }

    public static ContentListMap fromNetwork(RegistryFriendlyByteBuf buf) {
        ContentListMap map = new ContentListMap();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            RecipeCapability<?> capability = GTRegistries.RECIPE_CAPABILITIES.get(buf.readResourceLocation());
            if (capability == null) {
                throw new IllegalArgumentException("Unknown recipe capability in recipe content map");
            }
            int contentSize = buf.readVarInt();
            List<Content> contents = new ArrayList<>(contentSize);
            for (int j = 0; j < contentSize; j++) {
                contents.add(capability.serializer.fromNetworkContent(buf));
            }
            map.contentsMap.put(capability, contents);
        }
        return map;
    }

    public ContentListMap copy() {
        Map<RecipeCapability<?>, List<Content>> newMap = new Reference2ObjectArrayMap<>();
        forEachEntry(new EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<Content> contents) {
                List<Content> copied = new ArrayList<>(contents.size());
                for (Content content : contents) {
                    copied.add(content.copy(capability));
                }
                newMap.put(capability, copied);
            }
        });
        return new ContentListMap(newMap);
    }

    public ContentListMap copy(ContentModifier modifier) {
        Map<RecipeCapability<?>, List<Content>> newMap = new Reference2ObjectArrayMap<>();
        forEachEntry(new EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<Content> contents) {
                List<Content> copied = new ArrayList<>(contents.size());
                for (Content content : contents) {
                    copied.add(content.copy(capability, modifier));
                }
                newMap.put(capability, copied);
            }
        });
        return new ContentListMap(newMap);
    }

    public ContentListMap copyAndAppend(ContentListMap other) {
        ContentListMap copy = copy();
        copy.appendAll(other);
        return copy;
    }

    public void appendAll(ContentListMap other) {
        other.forEachEntry(new EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<Content> contents) {
                List<Content> list = get(capability);
                if (list == null) {
                    put(capability, new ArrayList<>(contents));
                    return;
                }
                list.addAll(contents);
            }
        });
    }

    public ContentListMap copyWithModifier(ContentModifier modifier) {
        return copy(modifier);
    }

    public ContentListMap copyWithMultiplier(int multiplier) {
        return copy(ContentModifier.multiplier(multiplier));
    }

    public void multiply(int multiplier) {
        replaceContents(ContentModifier.multiplier(multiplier));
    }

    public void multiply(double multiplier) {
        replaceContents(ContentModifier.multiplier(multiplier));
    }

    private void replaceContents(ContentModifier modifier) {
        forEachEntry(new EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<Content> contents) {
                contents.replaceAll(content -> content.copy(capability, modifier));
            }
        });
    }

    public void forEachEntry(EntryConsumer consumer) {
        contentsMap.forEach((capability, contents) -> acceptCaptured(consumer, capability, contents));
    }

    private static <T> void acceptCaptured(EntryConsumer consumer, RecipeCapability<?> capability,
                                           List<Content> contents) {
        consumer.accept((RecipeCapability<T>) capability, contents);
    }

    public interface EntryConsumer {

        <T> void accept(RecipeCapability<T> capability, List<Content> contents);
    }

    public interface TypedEntry {

        <T> void accept(EntryConsumer consumer);
    }

    public Iterator<TypedEntry> iterator() {
        Iterator<Map.Entry<RecipeCapability<?>, List<Content>>> iterator = contentsMap.entrySet().iterator();
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public TypedEntry next() {
                Map.Entry<RecipeCapability<?>, List<Content>> entry = iterator.next();
                return new TypedEntry() {

                    @Override
                    public <T> void accept(EntryConsumer consumer) {
                        acceptCaptured(consumer, entry.getKey(), entry.getValue());
                    }
                };
            }

            @Override
            public void remove() {
                iterator.remove();
            }
        };
    }

    public Map<RecipeCapability<?>, List<Content>> immutableView() {
        return Collections.unmodifiableMap(contentsMap);
    }
}
