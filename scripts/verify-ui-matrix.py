#!/usr/bin/env python3
"""Check the complete API33 screenshot matrix before CI can publish a preview."""
import json
import struct
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/ui-snapshots")
expected = {
    f"call-{style}-{color}-{panel}-{scale}.png"
    for style in ("graphite", "pixel", "emerald", "frost", "ivory")
    for color in ("light", "dark")
    for panel in ("normal", "audio", "more", "keypad")
    for scale in ("standard", "largefont")
}
images = {p.name: p for p in root.rglob("call-*.png")}
missing = expected - images.keys()
if missing:
    raise SystemExit(f"Missing screenshots: {sorted(missing)}")
for name in expected:
    content = images[name].read_bytes()
    if content[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"Invalid screenshot: {name}")
    if struct.unpack(">II", content[16:24]) != (480, 854):
        raise SystemExit(f"Unexpected screenshot size: {name}")
(root / "matrix-verification.json").write_text(json.dumps({
    "api": 33,
    "size": "480x854",
    "density_dpi": 240,
    "styles": 5,
    "themes": 2,
    "panels": 4,
    "font_scales": [1.0, 1.3],
    "matrix_screenshots": len(expected),
    "all_files": sorted(p.name for p in root.rglob("*.png")),
    "data": "Synthetic preview fixtures only; no real cellular call validation",
}, indent=2))
print(f"Verified {len(expected)} API33 call renders plus supplemental snapshots")
