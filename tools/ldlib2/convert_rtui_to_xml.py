#!/usr/bin/env python3
"""Convert legacy LDLib1 RTUI NBT files to LDLib2 XML.

The converter intentionally preserves legacy widget ids and a small set of
legacy attributes so GTM's LDLib2 recipe UI binding can keep the existing
`progress`, `item_in_#`, `item_out_#`, `fluid_in_#`, and `fluid_out_#` names.
"""

from __future__ import annotations

import argparse
import gzip
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from xml.sax.saxutils import escape


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


@dataclass(frozen=True)
class NbtList:
    element_type: int
    items: list[Any]


class NbtReader:
    """Minimal Minecraft NBT reader for LDLib1 UI project files."""

    def __init__(self, data: bytes) -> None:
        self.data = data
        self.offset = 0

    def read(self, length: int) -> bytes:
        end = self.offset + length
        if end > len(self.data):
            raise EOFError("Unexpected end of NBT data")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def read_unsigned_byte(self) -> int:
        return self.read(1)[0]

    def read_byte(self) -> int:
        return struct.unpack(">b", self.read(1))[0]

    def read_short(self) -> int:
        return struct.unpack(">h", self.read(2))[0]

    def read_unsigned_short(self) -> int:
        return struct.unpack(">H", self.read(2))[0]

    def read_int(self) -> int:
        return struct.unpack(">i", self.read(4))[0]

    def read_long(self) -> int:
        return struct.unpack(">q", self.read(8))[0]

    def read_float(self) -> float:
        return struct.unpack(">f", self.read(4))[0]

    def read_double(self) -> float:
        return struct.unpack(">d", self.read(8))[0]

    def read_string(self) -> str:
        length = self.read_unsigned_short()
        data = self.read(length)
        try:
            return data.decode("utf-8")
        except UnicodeDecodeError:
            return decode_modified_utf8(data)

    def read_root(self) -> dict[str, Any]:
        tag_type = self.read_unsigned_byte()
        if tag_type != TAG_COMPOUND:
            raise ValueError(f"Expected root compound tag, got {tag_type}")
        self.read_string()
        value = self.read_payload(tag_type)
        if not isinstance(value, dict):
            raise ValueError("Root tag is not a compound")
        return value

    def read_payload(self, tag_type: int) -> Any:
        if tag_type == TAG_END:
            return None
        if tag_type == TAG_BYTE:
            return self.read_byte()
        if tag_type == TAG_SHORT:
            return self.read_short()
        if tag_type == TAG_INT:
            return self.read_int()
        if tag_type == TAG_LONG:
            return self.read_long()
        if tag_type == TAG_FLOAT:
            return self.read_float()
        if tag_type == TAG_DOUBLE:
            return self.read_double()
        if tag_type == TAG_BYTE_ARRAY:
            return self.read(self.read_int())
        if tag_type == TAG_STRING:
            return self.read_string()
        if tag_type == TAG_LIST:
            element_type = self.read_unsigned_byte()
            length = self.read_int()
            return NbtList(element_type, [self.read_payload(element_type) for _ in range(length)])
        if tag_type == TAG_COMPOUND:
            compound: dict[str, Any] = {}
            while True:
                child_type = self.read_unsigned_byte()
                if child_type == TAG_END:
                    return compound
                child_name = self.read_string()
                compound[child_name] = self.read_payload(child_type)
        if tag_type == TAG_INT_ARRAY:
            return [self.read_int() for _ in range(self.read_int())]
        if tag_type == TAG_LONG_ARRAY:
            return [self.read_long() for _ in range(self.read_int())]
        raise ValueError(f"Unknown NBT tag type {tag_type}")


def decode_modified_utf8(data: bytes) -> str:
    units: list[int] = []
    index = 0
    while index < len(data):
        first = data[index]
        if first & 0x80 == 0:
            units.append(first)
            index += 1
        elif first & 0xE0 == 0xC0:
            second = data[index + 1]
            units.append(((first & 0x1F) << 6) | (second & 0x3F))
            index += 2
        elif first & 0xF0 == 0xE0:
            second = data[index + 1]
            third = data[index + 2]
            units.append(((first & 0x0F) << 12) | ((second & 0x3F) << 6) | (third & 0x3F))
            index += 3
        else:
            return data.decode("utf-8", errors="replace")
    raw = b"".join(unit.to_bytes(2, "big") for unit in units)
    return raw.decode("utf-16-be", errors="replace")


def read_nbt(path: Path) -> dict[str, Any]:
    data = path.read_bytes()
    if data.startswith(b"\x1f\x8b"):
        data = gzip.decompress(data)
    return NbtReader(data).read_root()


def nbt_list_items(value: Any) -> list[Any]:
    if isinstance(value, NbtList):
        return value.items
    if isinstance(value, list):
        return value
    return []


def int_property(data: dict[str, Any], compound_name: str, property_name: str, fallback: int) -> int:
    compound = data.get(compound_name)
    if isinstance(compound, dict) and isinstance(compound.get(property_name), int):
        return compound[property_name]
    return fallback


def bool_property(data: dict[str, Any], property_name: str) -> str | None:
    value = data.get(property_name)
    if isinstance(value, int):
        return "true" if value != 0 else "false"
    return None


def texture_key(value: Any) -> str | None:
    if not isinstance(value, dict):
        return None
    key = value.get("key")
    if isinstance(key, str) and not key.startswith("ldlib."):
        return key
    data = value.get("data")
    texture_type = value.get("type")
    if isinstance(data, dict) and isinstance(texture_type, str):
        location = data.get("imageLocation") or data.get("location") or data.get("texture")
        if isinstance(location, str) and not location.startswith("ldlib:"):
            return f"{texture_type}:{location}"
    return None


def tag_for(legacy_type: str | None) -> str:
    return {
        "gtm_item_slot": "gtm-item-slot",
        "gtm_fluid_slot": "gtm-fluid-slot",
        "dual_progress": "gtm-dual-progress",
        "progress": "progress-bar",
        "image": "gtm-image",
        "group": "element",
        None: "element",
    }.get(legacy_type, "element")


def attr_text(attrs: dict[str, Any]) -> str:
    parts: list[str] = []
    for key, value in attrs.items():
        if value is None or value == "":
            continue
        parts.append(f' {key}="{escape(str(value), {"\"": "&quot;"})}"')
    return "".join(parts)


def unwrap_widget(widget: Any) -> tuple[str | None, dict[str, Any] | None]:
    if not isinstance(widget, dict):
        return None, None
    if "type" in widget and "data" in widget:
        data = widget.get("data")
        return widget.get("type"), data if isinstance(data, dict) else None
    return None, widget


def append_widget(lines: list[str], widget: Any, depth: int, root: bool = False) -> None:
    legacy_type, data = (None, widget) if root else unwrap_widget(widget)
    if not isinstance(data, dict):
        return

    indent = "    " * depth
    tag = "root" if root else tag_for(legacy_type)
    x = int_property(data, "selfPosition", "x", 0)
    y = int_property(data, "selfPosition", "y", 0)
    width = int_property(data, "size", "width", 176 if root else 18)
    height = int_property(data, "size", "height", 166 if root else 18)
    style = (
        f"position: relative; width: {width}; height: {height};"
        if root
        else f"position: absolute; left: {x}; top: {y}; width: {width}; height: {height};"
    )
    attrs = {
        "id": data.get("id"),
        "legacy-type": None if root else legacy_type,
        "source-format": "legacy-nbt-ui" if root else None,
        "style": style,
        "draw-hover-overlay": bool_property(data, "drawHoverOverlay"),
        "draw-hover-tips": bool_property(data, "drawHoverTips"),
        "can-put-items": bool_property(data, "canPutItems"),
        "can-take-items": bool_property(data, "canTakeItems"),
        "show-amount": bool_property(data, "showAmount"),
        "legacy-allow-click-filled": bool_property(data, "allowClickFilled"),
        "legacy-allow-click-drained": bool_property(data, "allowClickDrained"),
        "fill-direction": data.get("fillDirection"),
        "split-point": data.get("splitPoint"),
        "legacy-background": texture_key(data.get("backgroundTexture")),
        "legacy-overlay": texture_key(data.get("overlay")),
        "legacy-progress-texture": texture_key(data.get("progressTexture")),
        "legacy-texture-1": texture_key(data.get("texture1")),
        "legacy-texture-2": texture_key(data.get("texture2")),
    }
    children = nbt_list_items(data.get("children"))
    if not children:
        lines.append(f"{indent}<{tag}{attr_text(attrs)}/>")
        return
    lines.append(f"{indent}<{tag}{attr_text(attrs)}>")
    for child in children:
        append_widget(lines, child, depth + 1)
    lines.append(f"{indent}</{tag}>")


def convert_file(source: Path) -> str:
    root = read_nbt(source).get("root")
    if not isinstance(root, dict):
        raise ValueError(f"{source} does not contain a root UI tag")
    lines = [
        '<?xml version="1.0" encoding="UTF-8" ?>',
        '<ldlib2-ui xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
        '           xsi:noNamespaceSchemaLocation="https://raw.githubusercontent.com/Low-Drag-MC/LDLib2/refs/heads/1.21/ldlib2-ui.xsd">',
        f"    <!-- Generated from legacy recipe NBT UI asset {escape(source.name)}. -->",
        '    <stylesheet location="ldlib2:lss/mc.lss"/>',
    ]
    append_widget(lines, root, 1, root=True)
    lines.append("</ldlib2-ui>")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("src/main/resources/assets/gtpm/ui/recipe_type"),
    )
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()

    output_dir = args.output_dir or args.input_dir
    for source in sorted(args.input_dir.glob("*.rtui")):
        xml = convert_file(source)
        target = output_dir / f"{source.stem}.xml"
        if args.write:
            target.write_text(xml, encoding="utf-8", newline="\n")
        print(f"{source} -> {target}")


if __name__ == "__main__":
    main()
