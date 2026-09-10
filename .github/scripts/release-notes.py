#!/usr/bin/env python3
import re
import sys
from pathlib import Path

version = sys.argv[1] if len(sys.argv) > 1 else ""
path = Path("CHANGELOG.md")
if not path.exists():
    print("_No CHANGELOG.md_")
    raise SystemExit(0)
text = path.read_text(encoding="utf-8")
match = re.search(
    rf"(?ms)^## {re.escape(version)}\s*\n(.*?)(?=^## |\Z)",
    text,
)
body = match.group(1).strip() if match else ""
print(body if body else "_No notes for this version in CHANGELOG.md_")
