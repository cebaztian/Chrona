"""Converts Phosphor SVG icons into Android vector drawables.

Usage: python tools/phosphor_to_vector.py <phosphor-core-dir> <res-drawable-dir> <weight>:<icon>[,<icon>...] ...
The script refuses any SVG whose children are not filled <path> elements, so an icon can never come out blank.
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

SVG = "{http://www.w3.org/2000/svg}"


def convert(svg_file: pathlib.Path) -> str:
    root = ET.parse(svg_file).getroot()
    if root.tag != SVG + "svg" or root.get("viewBox") != "0 0 256 256":
        raise SystemExit(f"{svg_file}: unexpected root or viewBox")
    paths = []
    for child in root:
        if child.tag != SVG + "path" or not child.get("d"):
            raise SystemExit(f"{svg_file}: unsupported element {child.tag!r}")
        fill_type = ' android:fillType="evenOdd"' if child.get("fill-rule") == "evenodd" else ""
        paths.append(f'    <path android:fillColor="#FF000000"{fill_type} android:pathData="{child.get("d")}" />')
    if not paths:
        raise SystemExit(f"{svg_file}: no paths")
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n    android:height="24dp"\n'
        '    android:viewportWidth="256"\n    android:viewportHeight="256">\n'
        + "\n".join(paths)
        + "\n</vector>\n"
    )


def main() -> None:
    core, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for spec in sys.argv[3:]:
        weight, names = spec.split(":")
        for name in names.split(","):
            src = core / "assets" / weight / (f"{name}.svg" if weight == "regular" else f"{name}-{weight}.svg")
            suffix = "" if weight == "light" else f"_{weight}"
            target = out / f"ic_{name.replace('-', '_')}{suffix}.xml"
            target.write_text(convert(src), encoding="utf-8", newline="\n")
            print("wrote", target.name)


if __name__ == "__main__":
    main()
