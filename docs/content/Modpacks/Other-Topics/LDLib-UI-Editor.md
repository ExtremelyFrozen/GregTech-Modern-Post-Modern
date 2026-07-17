---
title: LDLib2 UI Editor
---

# LDLib2 UI Editor

GTCEu Modern uses LDLib2 XML for editable machine and recipe type UIs. Run `/gtceu ui_editor` to open the editor. The
command requires administrator permission.

## Editing a template

Open the **Templates** menu and select either **Machine** or **Recipe**. Selecting an entry creates a GT XML project from
the XML supplied by that template and assigns its runtime target. The selected target also determines the default save
location.

The project view is an XML source editor with a rendered preview. The play/stop control switches the preview into and
out of simulation mode. Edit the XML source directly; it is the authoritative project representation.

!!! note
    This workflow does not provide hierarchy or inspector-based visual editing, and it does not serialize an edited
    `UIElement` tree back to XML.

## Workspace and save paths

The active editor workspace is:

```text
<gameDir>/ldlib2/assets/
```

For a target resource location `<namespace>:<path>`, templates use these default paths:

- Machine: `<assetsRoot>/<namespace>/ui/machine/<path>.xml`
- Recipe type: `<assetsRoot>/<namespace>/ui/recipe_type/<path>.xml`

Saving a recognized Machine or Recipe target under the active assets root invalidates the corresponding runtime XML
cache. The next time that UI is created, GTCEu reads the saved XML. An already open machine or recipe UI is not rebuilt
in place, so reopen it to see the change.

XML files outside the active assets root can still be opened, edited, and saved back to their original location. GTCEu
logs a warning for such saves and does not reload a runtime target. To make it an active override, save the project from
the editor to its canonical path under `<gameDir>/ldlib2/assets/`.

## Runtime XML contracts

Keep the following contracts when editing a generated template:

- Save XML as strict UTF-8 without a byte order mark (BOM). The editor and runtime reject a BOM, and runtime loading
  also rejects malformed UTF-8.
- The XML must be well-formed LDLib2 UI XML. If a custom resource exists but cannot be decoded, parsed, or bound, GTCEu
  logs the error and fails that UI creation instead of silently falling back to the default template.
- The root element of both machine and recipe templates must declare fixed pixel `width` and `height` values. Percentage,
  automatic, or otherwise non-fixed root dimensions cannot provide the size required by the machine shell and recipe
  viewer.
- Recipe progress binding requires at least one `gtm-progress-bar` or `gtm-dual-progress` with `id="progress"`.
- Recipe capability elements that receive runtime storage or content must retain IDs in the form
  `<capability>_<io>_<index>`, where `<io>` is `in` or `out` and `<index>` is zero-based. Examples include `item_in_0`
  and `fluid_out_0`. The element type must still match the capability.
- Machine templates also retain the recipe binding IDs above. A simple tiered machine additionally requires exactly one
  `gtm-item-slot` with `id="battery_slot"`; a simple generator requires exactly one `gtm-progress-bar` with
  `id="energy_container"`.

The generated Machine and Recipe templates already satisfy these contracts. Preserve their binding IDs when changing
layout, styles, or textures.

## Legacy files

LDLib1 `.mui` and `.rtui` files are historical formats and are no longer loaded by the runtime. They cannot be converted
by renaming the extension or treating their contents as LDLib2 XML. Recreate the layout and binding contract in an
LDLib2 XML template, then save it to the canonical Machine or Recipe path above. New custom UI work should not add
LDLib1 `.mui` or `.rtui` assets.
