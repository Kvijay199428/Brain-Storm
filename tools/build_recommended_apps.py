"""
Build the app-shipped recommendation catalog from the reviewed CSV.

Usage:  python tools/build_recommended_apps.py

Input:  tools/data/recommended_apps.csv
Output: app/src/main/assets/recommended_apps.json

The asset is the only thing the APK needs; the CSV and this script are kept so the catalog can be
reviewed and regenerated. Package names and name keys are normalised here, so matching on the device
is case-insensitive without any runtime cleanup.
"""

import csv
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "data" / "recommended_apps.csv"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "recommended_apps.json"


def name_key(value: str) -> str:
    """Lowercase, strip accents and punctuation: 'JioHotstar!' and 'jio hotstar' collapse alike."""
    plain = unicodedata.normalize("NFKD", value or "")
    plain = "".join(ch for ch in plain if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]+", "", plain.casefold())


def main() -> None:
    with SOURCE.open(newline="", encoding="utf-8-sig") as handle:
        rows = list(csv.DictReader(handle))

    entries = []
    seen = set()
    for row in rows:
        package = (row.get("package_name") or "").strip().casefold()
        name = (row.get("app_name") or "").strip()
        if not package or not name or package in seen:
            continue
        seen.add(package)
        entries.append(
            {
                "package": package,
                "name": name,
                "nameKey": name_key(name),
                "categories": [c.strip().upper() for c in (row.get("categories") or "").split(";") if c.strip()],
                "priority": (row.get("priority") or "").strip().upper(),
                "status": (row.get("package_status") or "").strip().upper(),
            }
        )

    entries.sort(key=lambda entry: entry["package"])
    payload = {"schema": 1, "count": len(entries), "apps": entries}
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} apps to {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
