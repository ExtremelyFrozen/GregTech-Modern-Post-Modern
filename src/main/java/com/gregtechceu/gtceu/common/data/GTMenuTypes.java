package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTCoverUIMenuType;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyUIMenuType;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Menu types owned by GTM's LDLib2 bridge layer.
 */
public final class GTMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU,
            GTCEu.MOD_ID);

    public static final Supplier<MenuType<ModularUIContainerMenu>> COVER_UI = MENUS.register("cover_ui",
            () -> IMenuTypeExtension.create(GTCoverUIMenuType::create));

    private GTMenuTypes() {}

    public static void init(IEventBus modBus) {
        if (GTCEu.Mods.isAE2Loaded()) {
            MEPatternBufferProxyUIMenuType.initialize();
        }
        MENUS.register(modBus);
    }
}
