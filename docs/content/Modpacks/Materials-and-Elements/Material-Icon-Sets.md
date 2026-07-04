---
title: "Icon Sets（图标集）"
---


# Icon Sets（图标集）

Material 系统使用 Icon Sets 来决定生成方块和物品的贴图。


## 可用 Icon Sets

默认可用以下 Icon Sets：

- `GTMaterialIconSet.BRIGHT`
- `GTMaterialIconSet.CERTUS`
- `GTMaterialIconSet.DIAMOND`
- `GTMaterialIconSet.DULL`
- `GTMaterialIconSet.EMERALD`
- `GTMaterialIconSet.FINE`
- `GTMaterialIconSet.FLINT`
- `GTMaterialIconSet.FLUID`
- `GTMaterialIconSet.GAS`
- `GTMaterialIconSet.GEM_HORIZONTAL`
- `GTMaterialIconSet.GEM_VERTICAL`
- `GTMaterialIconSet.GLASS`
- `GTMaterialIconSet.LAPIS`
- `GTMaterialIconSet.LIGNITE`
- `GTMaterialIconSet.MAGNETIC`
- `GTMaterialIconSet.METALLIC`
- `GTMaterialIconSet.NETHERSTAR`
- `GTMaterialIconSet.OPAL`
- `GTMaterialIconSet.QUARTZ`
- `GTMaterialIconSet.ROUGH`
- `GTMaterialIconSet.RUBY`
- `GTMaterialIconSet.SAND`
- `GTMaterialIconSet.SHINY`
- `GTMaterialIconSet.WOOD`


## 自定义 Icon Sets

也可以使用 `gtceu:matieral_icon_set` 事件指定自定义 iconsets：

```js title="custom_iconsets.js"
GTCEuStartupEvents.registry('gtceu:material_icon_set', event => {
    event.create('starry')
        .parent('shiny')
})
```
