"""Read a stopped app's Room database through adb; preserve a snapshot and row hashes."""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("destination", type=Path)
parser.add_argument("--serial", required=True)
parser.add_argument("--package", default="com.lamy.gymapp")
parser.add_argument("--adb", type=Path, default=Path(".toolchain/android-sdk/platform-tools/adb.exe"))
parser.add_argument("--full-app", action="store_true", help="Also archive databases, files, shared preferences and no_backup while the app is stopped")
args = parser.parse_args()
adb = str(args.adb.resolve())
command = [adb, "-s", args.serial]
args.destination.mkdir(parents=True, exist_ok=True)
if args.full_app:
    directories = subprocess.check_output(command + ["shell", "run-as", args.package, "ls"], text=True).split()
    selected = [name for name in ("databases", "files", "shared_prefs", "no_backup") if name in directories]
    archive = subprocess.check_output(command + ["exec-out", "run-as", args.package, "tar", "-cf", "-"] + selected)
    (args.destination / "app-data.tar").write_bytes(archive)
names = subprocess.check_output(command + ["shell", "run-as", args.package, "ls", "databases"], text=True).split()
for name in names:
    if name.startswith("gym_app.db"):
        data = subprocess.check_output(command + ["exec-out", "run-as", args.package, "cat", "databases/" + name])
        (args.destination / name).write_bytes(data)
with sqlite3.connect(args.destination / "gym_app.db") as database:
    summary = {}
    for (table,) in database.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
        rows = database.execute('SELECT * FROM "' + table + '" ORDER BY rowid').fetchall()
        payload = json.dumps(rows, ensure_ascii=False, sort_keys=True).encode()
        summary[table] = {"rows": len(rows), "sha256": hashlib.sha256(payload).hexdigest()}
    (args.destination / "tables.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
print(json.dumps(summary))
