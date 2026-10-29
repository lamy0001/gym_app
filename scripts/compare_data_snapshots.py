"""Verify all original user tables survive an additive Room migration."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("before", type=Path)
parser.add_argument("after", type=Path)
args = parser.parse_args()
before = json.loads((args.before / "tables.json").read_text(encoding="utf-8"))
after = json.loads((args.after / "tables.json").read_text(encoding="utf-8"))
changed = [table for table, digest in before.items() if table != "room_master_table" and after.get(table) != digest]
if changed:
    raise SystemExit("FAIL: changed/missing original tables: " + ", ".join(changed))
print("PASS: all original data table counts and SHA-256 hashes match; Room schema identity excluded.")
