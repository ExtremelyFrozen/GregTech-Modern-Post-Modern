package com.gregtechceu.gtceu.api.gui.texture;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;

/**
 * Parses GTM-owned texture metadata emitted during the LDLib2 XML migration.
 */
public final class GuiTextureMetadata {

    private static final String RESOURCE_TEXTURE_PREFIX = "resource_texture:";
    private static final String BORDER_TEXTURE_PREFIX = "border_texture:";
    private static final String SUB_TEXTURE_SEPARATOR = "@";

    private GuiTextureMetadata() {}

    /**
     * Resolves a static image texture from XML metadata.
     */
    public static IGuiTexture parseImageTexture(String metadata) {
        var value = metadata.trim();
        if (value.isEmpty() || value.equals("empty")) {
            return IGuiTexture.EMPTY;
        }
        if (value.startsWith(RESOURCE_TEXTURE_PREFIX)) {
            return parseResourceTexture(value.substring(RESOURCE_TEXTURE_PREFIX.length()), metadata);
        }
        if (value.startsWith(BORDER_TEXTURE_PREFIX)) {
            return parseBorderTexture(value.substring(BORDER_TEXTURE_PREFIX.length()), metadata);
        }
        GTCEu.LOGGER.error("Unsupported GTM image texture metadata '{}'", metadata);
        throw new IllegalArgumentException("Unsupported GTM image texture metadata: " + metadata);
    }

    private static ResourceTexture parseResourceTexture(String value, String metadata) {
        var areaStart = value.lastIndexOf(SUB_TEXTURE_SEPARATOR);
        if (areaStart < 0) {
            return new ResourceTexture(parseResourceLocation(value, metadata));
        }
        var location = parseResourceLocation(value.substring(0, areaStart), metadata);
        var area = parseArea(value.substring(areaStart + SUB_TEXTURE_SEPARATOR.length()), metadata);
        return new ResourceTexture(location, area[0], area[1], area[2], area[3]);
    }

    private static ResourceBorderTexture parseBorderTexture(String value, String metadata) {
        var location = parseResourceLocation(value, metadata);
        if (location.getPath().endsWith("slot.png") || location.getPath().endsWith("fluid_slot.png")) {
            return new ResourceBorderTexture(location.toString(), 18, 18, 1, 1);
        }
        return new ResourceBorderTexture(location.toString(), 16, 16, 4, 4);
    }

    private static ResourceLocation parseResourceLocation(String value, String metadata) {
        var location = ResourceLocation.tryParse(value);
        if (location == null) {
            GTCEu.LOGGER.error("Invalid GTM image texture location '{}' in metadata '{}'", value, metadata);
            throw new IllegalArgumentException("Invalid GTM image texture location: " + value);
        }
        return location;
    }

    private static float[] parseArea(String value, String metadata) {
        var parts = value.split(",");
        if (parts.length != 4) {
            GTCEu.LOGGER.error("Invalid GTM image texture area '{}' in metadata '{}'", value, metadata);
            throw new IllegalArgumentException("Invalid GTM image texture area: " + value);
        }
        var area = new float[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                area[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                GTCEu.LOGGER.error("Invalid GTM image texture area number '{}' in metadata '{}'", parts[i], metadata,
                        e);
                throw e;
            }
        }
        return area;
    }
}
