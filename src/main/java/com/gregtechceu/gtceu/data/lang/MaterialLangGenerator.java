package com.gregtechceu.gtceu.data.lang;

import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class MaterialLangGenerator {

    public static void generate(RegistrateLangProvider provider, final String modId) {
        GTRegistries.MATERIALS.stream()
                .filter(mat -> mat.getModid().equals(modId))
                .forEach(material -> {
                    provider.add(material.getUnlocalizedName(), material.getDefaultTranslation());
                });
    }
}
