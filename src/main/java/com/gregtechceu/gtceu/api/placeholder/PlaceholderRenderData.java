package com.gregtechceu.gtceu.api.placeholder;

import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class PlaceholderRenderData {

    public record Rect(float width, float height, int color) {

        public static final Codec<Rect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("width").forGetter(Rect::width),
                Codec.FLOAT.fieldOf("height").forGetter(Rect::height),
                Codec.INT.fieldOf("color").forGetter(Rect::color))
                .apply(instance, Rect::new));
    }

    public record Quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                       int color1, int color2, int color3, int color4) {

        public static final Codec<Quad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("x1").forGetter(Quad::x1),
                Codec.FLOAT.fieldOf("y1").forGetter(Quad::y1),
                Codec.FLOAT.fieldOf("x2").forGetter(Quad::x2),
                Codec.FLOAT.fieldOf("y2").forGetter(Quad::y2),
                Codec.FLOAT.fieldOf("x3").forGetter(Quad::x3),
                Codec.FLOAT.fieldOf("y3").forGetter(Quad::y3),
                Codec.FLOAT.fieldOf("x4").forGetter(Quad::x4),
                Codec.FLOAT.fieldOf("y4").forGetter(Quad::y4),
                Codec.INT.fieldOf("color1").forGetter(Quad::color1),
                Codec.INT.fieldOf("color2").forGetter(Quad::color2),
                Codec.INT.fieldOf("color3").forGetter(Quad::color3),
                Codec.INT.fieldOf("color4").forGetter(Quad::color4))
                .apply(instance, Quad::new));
    }

    public static DataComponentMap rect(float width, float height, int color) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_RECT_RENDER_DATA.get(), new Rect(width, height, color))
                .build();
    }

    public static DataComponentMap quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4,
                                        float y4, int color1, int color2, int color3, int color4) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_QUAD_RENDER_DATA.get(),
                        new Quad(x1, y1, x2, y2, x3, y3, x4, y4, color1, color2, color3, color4))
                .build();
    }

    public static DataComponentMap itemStack(ItemStack stack) {
        return DataComponentMap.builder()
                .set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), stack.copy())
                .build();
    }

    private PlaceholderRenderData() {}
}
