#!/usr/bin/env python3
"""Read the repository reference DOCX without creating/editing any files."""
from pathlib import Path
import sys
import zipfile
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
source = root / "docs/reference/ai_job_search_platform_architecture_cost_optimized.docx"
ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
with zipfile.ZipFile(source) as archive:
    parts = ["word/document.xml"] + sorted(
        name for name in archive.namelist()
        if name.startswith(("word/header", "word/footer")) and name.endswith(".xml")
    )
    for part in parts:
        print(f"\nSOURCE PART: {part}")
        document = ET.fromstring(archive.read(part))
        for index, paragraph in enumerate(document.findall(".//w:p", ns), 1):
            text = "".join(node.text or "" for node in paragraph.findall(".//w:t", ns))
            if text.strip():
                print(f"{index}: {text}")
