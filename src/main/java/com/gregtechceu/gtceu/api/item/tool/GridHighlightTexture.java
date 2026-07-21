package com.gregtechceu.gtceu.api.item.tool;

import net.minecraft.resources.ResourceLocation;

/**
 * Describes a texture used by in-world tool grid highlights without depending on LDLib UI texture classes.
 */
public record GridHighlightTexture(ResourceLocation imageLocation, float offsetX, float offsetY, float imageWidth,
                                   float imageHeight) {

    public static final GridHighlightTexture TOOL_FRONT_FACING_ROTATION = fullImage(
            "gtpm:textures/gui/overlay/tool_front_facing_rotation.png");
    public static final GridHighlightTexture TOOL_IO_FACING_ROTATION = fullImage(
            "gtpm:textures/gui/overlay/tool_io_facing_rotation.png");
    public static final GridHighlightTexture TOOL_PAUSE = fullImage("gtpm:textures/gui/overlay/tool_pause.png");
    public static final GridHighlightTexture TOOL_START = fullImage("gtpm:textures/gui/overlay/tool_start.png");
    public static final GridHighlightTexture TOOL_COVER_SETTINGS = fullImage(
            "gtpm:textures/gui/overlay/tool_cover_settings.png");
    public static final GridHighlightTexture TOOL_MUTE = fullImage("gtpm:textures/gui/overlay/tool_mute.png");
    public static final GridHighlightTexture TOOL_SOUND = fullImage("gtpm:textures/gui/overlay/tool_sound.png");
    public static final GridHighlightTexture TOOL_ALLOW_INPUT = fullImage(
            "gtpm:textures/gui/overlay/tool_allow_input.png");
    public static final GridHighlightTexture TOOL_ATTACH_COVER = fullImage(
            "gtpm:textures/gui/overlay/tool_attach_cover.png");
    public static final GridHighlightTexture TOOL_REMOVE_COVER = fullImage(
            "gtpm:textures/gui/overlay/tool_remove_cover.png");
    public static final GridHighlightTexture TOOL_PIPE_BLOCK = fullImage(
            "gtpm:textures/gui/overlay/tool_pipe_block.png");
    public static final GridHighlightTexture TOOL_PIPE_CONNECT = fullImage(
            "gtpm:textures/gui/overlay/tool_pipe_connect.png");
    public static final GridHighlightTexture TOOL_WIRE_BLOCK = fullImage(
            "gtpm:textures/gui/overlay/tool_wire_block.png");
    public static final GridHighlightTexture TOOL_WIRE_CONNECT = fullImage(
            "gtpm:textures/gui/overlay/tool_wire_connect.png");
    public static final GridHighlightTexture TOOL_AUTO_OUTPUT = fullImage(
            "gtpm:textures/gui/overlay/tool_auto_output.png");
    public static final GridHighlightTexture TOOL_SWITCH_CONVERTER_NATIVE = TOOL_WIRE_BLOCK;
    public static final GridHighlightTexture TOOL_SWITCH_CONVERTER_EU = TOOL_WIRE_CONNECT;

    public static GridHighlightTexture fullImage(String imageLocation) {
        return new GridHighlightTexture(ResourceLocation.parse(imageLocation), 0, 0, 1, 1);
    }

    public GridHighlightTexture getSubTexture(float offsetX, float offsetY, float width, float height) {
        return new GridHighlightTexture(imageLocation,
                this.offsetX + this.imageWidth * offsetX,
                this.offsetY + this.imageHeight * offsetY,
                this.imageWidth * width,
                this.imageHeight * height);
    }
}
