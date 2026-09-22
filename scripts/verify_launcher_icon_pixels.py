#!/usr/bin/env python3
"""Render shipped M09B resources; fail on crop, alpha, mask, or contrast regressions."""

import base64
import math
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SIZE = 432
SLUGS = ("robot_face", "robot_grid", "pointer_grid", "minimal_green")


def attr(node: ET.Element, name: str) -> str | None:
    return node.get(ANDROID + name)


def color_resources() -> dict[str, str]:
    root = ET.parse(RES / "values/launcher_icon_colors.xml").getroot()
    values = {node.get("name"): (node.text or "").strip() for node in root.findall("color")}
    if any(not value.startswith("#") for value in values.values()):
        raise AssertionError("launcher color resources must be literal and locally auditable")
    return values


def resolve_color(reference: str | None, colors: dict[str, str]) -> str:
    prefix = "@color/"
    if reference is None or not reference.startswith(prefix):
        raise AssertionError(f"invalid color reference {reference!r}")
    name = reference.removeprefix(prefix)
    if name not in colors:
        raise AssertionError(f"missing color resource {name}")
    return colors[name]


def adaptive_resources(slug: str) -> tuple[str, str, str]:
    root = ET.parse(RES / f"mipmap-anydpi-v33/ic_launcher_{slug}.xml").getroot()
    background = root.find("background")
    foreground = root.find("foreground")
    monochrome = root.find("monochrome")
    if background is None or foreground is None or monochrome is None:
        raise AssertionError(f"{slug}: incomplete Android 13 adaptive resource")
    return (
        attr(background, "drawable") or "",
        attr(foreground, "drawable") or "",
        attr(monochrome, "drawable") or "",
    )


def drawable_path(reference: str) -> Path:
    prefix = "@drawable/"
    if not reference.startswith(prefix):
        raise AssertionError(f"invalid drawable reference {reference}")
    name = reference.removeprefix(prefix)
    for extension in ("xml", "png"):
        candidate = RES / f"drawable/{name}.{extension}"
        if candidate.is_file():
            return candidate
    raise AssertionError(f"missing drawable {reference}")


def vector_svg(reference: str, background: str, tint: str | None = None) -> str:
    source = drawable_path(reference)
    root = ET.parse(source).getroot()
    group = root.find("group")
    if group is None or attr(group, "name") != "adaptive_safe_zone":
        raise AssertionError(f"{source.name}: safe-zone group missing")
    scale = float(attr(group, "scaleX") or "0")
    if scale != float(attr(group, "scaleY") or "-1"):
        raise AssertionError(f"{source.name}: non-uniform safe-zone scale")
    paths = []
    for node in group.findall("path"):
        fill, fill_opacity = svg_color(tint or attr(node, "fillColor"))
        stroke, stroke_opacity = svg_color(tint or attr(node, "strokeColor"))
        properties = {
            "d": attr(node, "pathData"),
            "fill": fill or "none",
            "fill-opacity": fill_opacity,
            "stroke": stroke,
            "stroke-opacity": stroke_opacity,
            "stroke-width": attr(node, "strokeWidth"),
            "stroke-linejoin": attr(node, "strokeLineJoin"),
            "fill-rule": attr(node, "fillType"),
        }
        attributes = " ".join(
            f'{key}="{value}"' for key, value in properties.items() if value is not None
        )
        paths.append(f"<path {attributes}/>")
    return svg_document(background, "".join(paths), scale)


def robot_svg(reference: str, background: str) -> str:
    wrapper = drawable_path(reference)
    root = ET.parse(wrapper).getroot()
    if root.tag != "inset":
        raise AssertionError("robot adaptive foreground must be an inset wrapper")
    inset = attr(root, "inset") or ""
    if not inset.endswith("dp"):
        raise AssertionError("robot inset must use dp")
    inset_value = float(inset.removesuffix("dp"))
    source = drawable_path(attr(root, "drawable") or "")
    if source.suffix != ".png":
        raise AssertionError("robot adaptive wrapper must preserve the exact PNG")
    encoded = base64.b64encode(source.read_bytes()).decode()
    extent = 108 - 2 * inset_value
    body = (
        f'<image href="data:image/png;base64,{encoded}" x="{inset_value}" y="{inset_value}" '
        f'width="{extent}" height="{extent}" preserveAspectRatio="xMidYMid meet"/>'
    )
    return svg_document(background, body)


def svg_document(background: str, body: str, scale: float | None = None) -> str:
    if scale is not None:
        body = f'<g transform="translate(54 54) scale({scale}) translate(-54 -54)">{body}</g>'
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" '
        'viewBox="0 0 108 108">'
        f'<rect width="108" height="108" fill="{background}"/>{body}</svg>'
    )


def svg_color(android_color: str | None) -> tuple[str | None, str | None]:
    if android_color is None:
        return None, None
    value = android_color.removeprefix("#")
    if len(value) == 8:
        return "#" + value[2:8], f"{int(value[0:2], 16) / 255.0:.6f}"
    return android_color, None


def render(svg: str) -> bytes:
    magick = shutil.which("magick")
    if magick is None:
        raise AssertionError("ImageMagick `magick` is required for pixel proof")
    result = subprocess.run(
        [magick, "svg:-", "-resize", f"{SIZE}x{SIZE}!", "-depth", "8", "rgba:-"],
        input=svg.encode(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        raise AssertionError(result.stderr.decode(errors="replace"))
    if len(result.stdout) != SIZE * SIZE * 4:
        raise AssertionError(f"render returned {len(result.stdout)} bytes")
    return result.stdout


def rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.removeprefix("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4))


def linear(channel: int) -> float:
    value = channel / 255.0
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def luminance(color: tuple[int, int, int]) -> float:
    red, green, blue = (linear(value) for value in color)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast(first: tuple[int, int, int], second: tuple[int, int, int]) -> float:
    high, low = sorted((luminance(first), luminance(second)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def masks(x: float, y: float) -> tuple[bool, ...]:
    return (
        x * x + y * y <= 1.0,
        abs(x) ** 4 + abs(y) ** 4 <= 1.0,
        (abs(x) <= 0.88 or abs(y) <= 0.88) and abs(x) <= 1.0 and abs(y) <= 1.0,
        x * x + (y + 0.08) ** 2 <= 1.02,
    )


def verify_pixels(
    label: str,
    pixels: bytes,
    background_hex: str,
    identity_is_high_contrast: bool = False,
    minimum_high_contrast_ratio: float = 0.20,
) -> None:
    background = rgb(background_hex)
    foreground: list[tuple[int, int, tuple[int, int, int]]] = []
    high_contrast = 0
    for y in range(SIZE):
        for x in range(SIZE):
            offset = (y * SIZE + x) * 4
            color = tuple(pixels[offset:offset + 3])
            if pixels[offset + 3] != 255:
                raise AssertionError(f"{label}: non-opaque composed pixel at {x},{y}")
            changed = max(abs(color[index] - background[index]) for index in range(3)) > 4
            meets_contrast = contrast(color, background) >= 3.0
            if changed and (meets_contrast or not identity_is_high_contrast):
                foreground.append((x, y, color))
                high_contrast += int(meets_contrast)
    if len(foreground) < 1_000:
        raise AssertionError(f"{label}: insufficient rendered identity pixels")
    ratio = high_contrast / len(foreground)
    if ratio < minimum_high_contrast_ratio:
        raise AssertionError(f"{label}: insufficient 3:1 contrast pixels ({ratio:.3f})")

    retained = [0, 0, 0, 0]
    for x, y, _ in foreground:
        nx = (x + 0.5 - SIZE / 2) / (SIZE / 2)
        ny = (y + 0.5 - SIZE / 2) / (SIZE / 2)
        if math.hypot(nx, ny) > 0.63:
            raise AssertionError(f"{label}: identity escaped adaptive safe zone at {x},{y}")
        for index, inside in enumerate(masks(nx, ny)):
            retained[index] += int(inside)
    for index, count in enumerate(retained):
        if count / len(foreground) < 0.995:
            raise AssertionError(f"{label}: mask {index} retained only {count}/{len(foreground)}")
    print(f"PASS {label}: pixels={len(foreground)} highContrast={high_contrast}")


def main() -> int:
    try:
        colors = color_resources()
        light_background = colors["launcher_monochrome_light_background"]
        light_tint = colors["launcher_monochrome_light_tint"]
        dark_background = colors["launcher_monochrome_dark_background"]
        dark_tint = colors["launcher_monochrome_dark_tint"]
        for slug in SLUGS:
            background_ref, foreground_ref, monochrome_ref = adaptive_resources(slug)
            background = resolve_color(background_ref, colors)
            color_svg = (
                robot_svg(foreground_ref, background)
                if slug == "robot_face"
                else vector_svg(foreground_ref, background)
            )
            verify_pixels(
                f"{slug}/adaptive",
                render(color_svg),
                background,
                identity_is_high_contrast=slug == "robot_face",
            )
            verify_pixels(
                f"{slug}/monochrome-light",
                render(vector_svg(monochrome_ref, light_background, light_tint)),
                light_background,
                minimum_high_contrast_ratio=0.80,
            )
            verify_pixels(
                f"{slug}/monochrome-dark",
                render(vector_svg(monochrome_ref, dark_background, dark_tint)),
                dark_background,
                minimum_high_contrast_ratio=0.80,
            )
    except (AssertionError, KeyError, ET.ParseError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
