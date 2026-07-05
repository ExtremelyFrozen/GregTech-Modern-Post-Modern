---
title: LDLib2 UI Editor Migration
---


# LDLib2 UI Editor Migration

GTCEu Modern is migrating its customizable UI support from LDLib1 to LDLib2.

The legacy `.rtui` files are LDLib1 runtime UI files. They are Minecraft NBT, not text files; depending on the writer
and asset age they may be raw binary NBT or gzip-compressed NBT. Do not convert them by renaming the extension or by
treating them as XML.

LDLib2 UI definitions use XML. Existing custom machine and recipe type UIs must be converted from the old NBT
structure to LDLib2 XML as a one-time migration. New custom UI work should target the LDLib2 XML format instead of
adding new LDLib1 `.rtui` assets or relying on the old LDLib runtime loader.

Converted assets should use the `gtpm` namespace for GTCEu-provided metadata and textures where possible. Do not add new
`ldlib` metadata sections as a compatibility layer; old LDLib metadata should be translated to GTM metadata or to the
LDLib2 XML UI definition.

During the migration window, the old in-game UI editor may still be available through `/gtceu ui_editor` for existing
projects, but it is a historical compatibility path. It will be removed once GTCEu's editor integration is fully moved
to LDLib2.
