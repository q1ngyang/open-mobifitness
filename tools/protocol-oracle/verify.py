#!/usr/bin/env python3
"""Verify regenerated original-DEX tables without changing committed expectations."""
import argparse, hashlib, json
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('tables',type=Path)
a=parser.parse_args()
manifest=json.loads((Path(__file__).parent/'manifest.json').read_text())
failures=[]
for expected in manifest['tables']:
    path=a.tables/Path(expected['file']).name
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=expected['sha256']:
        failures.append(path.name)
if failures:
    raise SystemExit('Mismatched or missing tables: '+', '.join(failures))
print(f"Verified {len(manifest['tables'])} original-DEX tables")
