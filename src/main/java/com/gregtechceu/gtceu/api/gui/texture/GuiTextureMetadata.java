package com.gregtechceu.gtceu.api.gui.texture;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;

/**
 * Parses GTM-owned texture metadata emitted during the LDLib2 XML migration.
 */
public final class GuiTextureMetadata {

    private static final String RESOURCE_TEXTURE_PREFIX = "resource_texture:";

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
            return new ResourceTexture(parseResourceLocation(value.substring(RESOURCE_TEXTURE_PREFIX.length()), metadata));
        }
        GTCEu.LOGGER.error("Unsupported GTM image texture metadata '{}'", metadata);
        throw new IllegalArgumentException("Unsupported GTM image texture metadata: " + metadata);
    }

    private static ResourceLocation parseResourceLocation(String value, String metadata) {
        var location = ResourceLocation.tryParse(value);
        if (location == null) {
            GTCEu.LOGGER.error("Invalid GTM image texture location '{}' in metadata '{}'", value, metadata);
            throw new IllegalArgumentException("Invalid GTM image texture location: " + value);
        }
        return location;
    }
}
