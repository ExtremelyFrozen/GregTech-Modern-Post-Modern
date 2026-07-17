package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

/**
 * Registers the legacy LDLib UI factory still used by GTM's UI editor.
 */
public final class LegacyUIFactoryHelper {

    private LegacyUIFactoryHelper() {}

    /**
     * Registers the remaining legacy UI editor factory.
     */
    public static void register() {
        UIFactory.register(GTUIEditorFactory.INSTANCE);
    }
}
