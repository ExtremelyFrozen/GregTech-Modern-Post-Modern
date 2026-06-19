package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.core.config.GTEarlyConfig;
import com.gregtechceu.gtceu.core.config.Option;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.UnknownNullability;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gregtechceu.gtceu.core.config.GTEarlyConfig.OPTIFINE_PRESENT;

public class GTMixinPlugin implements IMixinConfigPlugin {

    public static final Logger LOGGER = LogManager.getLogger("GregTechCEu");

    private static final String MIXIN_PACKAGE = "com.gregtechceu.gtceu.core.mixins.";
    private static final Map<String, String> MOD_COMPAT_MIXINS = new HashMap<>();

    private static final String DEV_PACKAGE = "dev.";
    private static final String DATAGEN_PACKAGE = "datagen.";

    public static @UnknownNullability GTEarlyConfig CONFIG = null;

    static {
        addModCompatMixin("emi");
        addModCompatMixin("jei");
        addModCompatMixin("top");
        addModCompatMixin("ftbchunks");
        addModCompatMixin("iris");
        addModCompatMixin("xaerominimap");
        addModCompatMixin("xaeroworldmap");
        addModCompatMixin("kubejs");
    }

    public GTMixinPlugin() {
        if (CONFIG != null) {
            return;
        }

        try {
            CONFIG = GTEarlyConfig.load(new File("./config/gtpm/gtpm-early.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Could not load mixin configuration file for GTPM", e);
        }

        if (OPTIFINE_PRESENT) {
            LOGGER.fatal("OptiFine detected. Use of GTPM with OptiFine is not supported.");
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return true;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE.length());

        if (mixin.startsWith(DEV_PACKAGE)) {
            if (FMLLoader.isProduction()) {
                return false;
            }
            mixin = mixin.substring(DEV_PACKAGE.length());
            if (mixin.startsWith(DATAGEN_PACKAGE)) {
                return DatagenModLoader.isRunningDataGen();
            }
            return isOptionEnabled(mixin);
        }

        for (var compatMod : MOD_COMPAT_MIXINS.entrySet()) {
            if (mixin.startsWith(compatMod.getValue()) && !isModLoaded(compatMod.getKey())) {
                return false;
            }
        }

        return isOptionEnabled(mixin);
    }

    public static boolean isOptionEnabled(String mixin) {
        Option option = CONFIG.getEffectiveOptionForMixin(mixin);
        if (option == null) {
            return true;
        }
        return option.isEnabled();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static void addModCompatMixin(String modId) {
        MOD_COMPAT_MIXINS.put(modId, modId + ".");
    }

    private static boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return LoadingModList.get().getModFileById(modId) != null;
        }
        return ModList.get().isLoaded(modId);
    }
}
