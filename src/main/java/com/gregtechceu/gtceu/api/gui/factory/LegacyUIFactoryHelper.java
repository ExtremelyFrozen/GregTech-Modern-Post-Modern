package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

/**
 * Registers legacy LDLib UI factories while each screen family is moved to LDLib2 menu types.
 */
public final class LegacyUIFactoryHelper {

    private LegacyUIFactoryHelper() {}

    /**
     * Registers the remaining legacy UI factories used by GTM screens.
     */
    public static void register() {
        UIFactory.register(MachineUIFactory.INSTANCE);
        UIFactory.register(GTHeldItemUIFactory.INSTANCE);
        UIFactory.register(GTUIEditorFactory.INSTANCE);
    }
}
