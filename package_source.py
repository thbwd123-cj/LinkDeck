#!/usr/bin/env python3
"""Package source and corresponding native-library sources, excluding local runtimes and APK fixtures."""
from pathlib import Path
import argparse, zipfile
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('destination',type=Path);args=parser.parse_args()
with zipfile.ZipFile(args.destination,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in sorted(root.rglob('*')):
        if not p.is_file() or p.is_symlink() or p.resolve()==args.destination.resolve():continue
        parts=p.relative_to(root).parts
        if any((x.startswith('.') and x != '.gitignore') or x in ('build','dist','__pycache__') for x in parts):continue
        if parts[0]=='signing':continue
        if parts[0]=='previews' and (len(parts)<2 or parts[1] not in ('v0.13','v0.14')):continue
        if parts[:2]==('tests','fixtures') and p.suffix in ('.apk','.json'):continue
        z.write(p,Path('LinkDeck')/p.relative_to(root))
print(args.destination.resolve())
