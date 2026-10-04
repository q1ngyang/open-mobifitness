#!/usr/bin/env python3
"""Verify complete translations, placeholder parity and independent application identity."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
resources = root / "app/src/main/res"
files = [resources / folder for folder in
         ("values", "values-b+zh+Hans", "values-b+zh+Hant", "values-ja", "values-ko", "values-de")]
def strings(path):
    items = [node for file in path.glob("*.xml") for node in ET.parse(file).getroot().findall("string")]
    result = {node.attrib["name"]: "".join(node.itertext()) for node in items}
    assert len(result) == len(items), f"Duplicate resource: {path}"
    return result
reference = strings(files[0])
for path in files[1:]:
    translated = strings(path)
    assert translated.keys() == reference.keys(), f"Missing/excess keys in {path}"
    for key, value in reference.items():
        assert sorted(re.findall(r"%\d+\$[dsf]", value)) == sorted(re.findall(r"%\d+\$[dsf]", translated[key])), (path, key)
        assert translated[key].strip(), (path, key)
for path in (root / "app/src/main/java").rglob("*.kt"):
    for key in re.findall(r"R\.string\.(\w+)", path.read_text()):
        assert key in reference, (path, key)
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
assert not manifest.findall(f".//activity[@{android}screenOrientation]")
assert "org.openmobifitness.app" in (root / "app/build.gradle.kts").read_text()
assert "supportsPictureInPicture" not in (root / "app/src/main/AndroidManifest.xml").read_text()
print(f"OK: {len(reference)} keys, 6 complete language sets, identity and responsive manifest")

debug_resources = root / "app/src/debug/res"
debug_reference = strings(debug_resources / "values")
for folder in ("values-b+zh+Hans", "values-b+zh+Hant", "values-ja", "values-ko", "values-de"):
    translated = strings(debug_resources / folder)
    assert translated.keys() == debug_reference.keys(), (folder, "debug keys")
    for key, value in debug_reference.items():
        assert sorted(re.findall(r"%\d+\$[dsf]", value)) == sorted(re.findall(r"%\d+\$[dsf]", translated[key])), (folder, key)
        assert translated[key].strip(), (folder, key)
assert not (set(debug_reference) & set(reference)), "Debug strings must remain variant-only"
print(f"OK: {len(debug_reference)} debug-only keys, 6 complete language sets")
