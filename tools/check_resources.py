#!/usr/bin/env python3
"""Verify complete translations, placeholder parity and independent application identity."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
resources = root / "app/src/main/res"
files = [resources / folder / "strings.xml" for folder in
         ("values", "values-b+zh+Hans", "values-b+zh+Hant", "values-ja", "values-ko", "values-de")]
def strings(path):
    items = ET.parse(path).getroot().findall("string")
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
