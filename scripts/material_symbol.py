#!/usr/bin/env python3
"""Baixa ícones Material Symbols Rounded (wght 400, opsz 24) e grava como vector drawables.

Uso: scripts/material_symbol.py nome [nome ...] [--filled nome ...]
Grava em app/src/main/res/drawable/ic_<nome>.xml (FILL 0) e ic_<nome>_filled.xml (FILL 1).
Licença dos ícones: Apache License 2.0 (Google).
"""
import argparse
import pathlib
import re
import urllib.request

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{n}/materialsymbolsrounded/{n}_{v}24px.svg"
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"
MIRRORED = {"arrow_back", "chevron_right", "arrow_outward"}


def convert(name: str, filled: bool) -> pathlib.Path:
    svg = urllib.request.urlopen(BASE.format(n=name, v="fill1_" if filled else "")).read().decode()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    if not paths:
        raise SystemExit(f"sem <path> em {name}")
    body = "\n".join(
        f'        <path\n            android:fillColor="@android:color/white"\n            android:pathData="{p}" />'
        for p in paths
    )
    target = OUT / (f"ic_{name}" + ("_filled" if filled else "") + ".xml")
    target.write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}" ({'FILL 1' if filled else 'FILL 0'}, wght 400, opsz 24) · Apache License 2.0 · Google -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="{'true' if name in MIRRORED else 'false'}"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
{body}
    </group>
</vector>
''')
    return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("names", nargs="*")
    parser.add_argument("--filled", nargs="*", default=[])
    args = parser.parse_args()
    for n in args.names:
        print(convert(n, False).name)
    for n in args.filled:
        print(convert(n, True).name)
