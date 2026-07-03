---
title: "修改现有 Materials"
---


# 修改现有 Materials

GT 中包含所有周期表元素，但其中一部分没有附加任何属性。你也可以为 EBF 自动生成配方添加 BlastProperty。对于 Obsidian 等其他 Material 也可以这样做。添加方式如下：

```js title="periodic_table_elements.js"
    const $IngotProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.IngotProperty');
    const $DustProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.DustProperty');
    const $BlastProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.BlastProperty');

    GTCEuStartupEvents.registry('gtceu:material', event => {

        // Ingot
        GTMaterials.Zirconium.setProperty(PropertyKey.INGOT, new $IngotProperty());
        GTMaterials.Obsidian.setProperty(PropertyKey.INGOT, new $IngotProperty());

        // Dust
        GTMaterials.Selenium.setProperty(PropertyKey.DUST, new $DustProperty());

        // Blast Property
        GTMaterials.Zirconium.setProperty(PropertyKey.BLAST, new $BlastProperty(8000, 'higher', GTValues.VA[GTValues.MV], 8000));

    });
```

向现有 Materials 添加流体需要配合新的 FluidStorage 系统进行一些处理：

```js title="fluid_property.js"

const $FluidProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty');
const $FluidBuilder = Java.loadClass('com.gregtechceu.gtceu.api.fluids.FluidBuilder');
const $FluidStorageKeys = Java.loadClass('com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys');

GTCEuStartupEvents.registry('gtceu:material', event => {
    addFluid(GTMaterials.Iodine, $FluidStorageKeys.LIQUID); // Can be LIQUID, GAS, PLASMA or MOLTEN
    addFluid(GTMaterials.Oganesson, $FluidStorageKeys.GAS);
}


let addFluid = (mat, key) => {
    let prop = new $FluidProperty();
    prop.getStorage().enqueueRegistration(key, new $FluidBuilder());
    mat.setProperty(PropertyKey.FLUID, prop);
}
```

你甚至可以向现有 Materials 添加矿石：

```js title="ore_property.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {

    const $OreProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.OreProperty');

        // Zinc Ore
        GTMaterials.Zinc.setProperty(PropertyKey.ORE, new $OreProperty());

    });
```

也可以向现有 Materials 添加 flags：

```js title="flags.js"
    GTCEuStartupEvents.registry('gtceu:material', event => {

        GTMaterials.Lead.addFlags(GTMaterialFlags.GENERATE_GEAR); // This is for materials already in GTCEU
        GTMaterials.get('custom_material_name').addFlags(GTMaterialFlags.GENERATE_FOIL); // This only works for materials added by GTCEU addons

    });
```

编辑现有 Material 的颜色：


```js title="material_modification.js"
    GTCEuStartupEvents.materialModification(event => {
        GTMaterials.BismuthBronze.setMaterialARGB(0x82AD92) //(1)
    })
```

1. [``Material`` class](https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/src/main/java/com/gregtechceu/gtceu/api/data/chemical/material/Material.java) 中的大多数方法都可以在 ``materialModification`` 事件中使用
