#!/usr/bin/env bash
# Shell debloater for Android Phones (Linux / macOS / Termux)
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTECTED_FILE="$SCRIPT_DIR/protected.txt"
BLOAT_FILE="$SCRIPT_DIR/bloat_database.txt"
BACKUP_BASE="$SCRIPT_DIR/backups"

ADB="adb"
if ! command -v adb &>/dev/null; then
    if [ -f "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then
        ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
    else
        echo "Error: adb command not found." >&2
        exit 1
    fi
fi

DEVICE="${1:-$($ADB devices | awk 'NR>1 && $2=="device" {print $1; exit}')}"

if [ -z "$DEVICE" ]; then
    echo "No connected Android phone found via ADB."
    exit 1
fi

echo "Connected Device: $DEVICE"
MODEL="$($ADB -s "$DEVICE" shell getprop ro.product.model | tr -d '\r ')"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
SNAPSHOT_DIR="$BACKUP_BASE/${MODEL}_$TIMESTAMP"
mkdir -p "$SNAPSHOT_DIR"

echo "Creating baseline package snapshot in $SNAPSHOT_DIR ..."
$ADB -s "$DEVICE" shell pm list packages > "$SNAPSHOT_DIR/packages_all.txt"

echo "Scanning for bloatware against database..."
grep -v '^#' "$BLOAT_FILE" | grep -v '^$' | while read -r pkg; do
    if grep -q "^package:$pkg$" "$SNAPSHOT_DIR/packages_all.txt"; then
        if grep -q "^$pkg$" "$PROTECTED_FILE"; then
            echo "Skipping protected package: $pkg"
        else
            echo "Debloating: $pkg ..."
            $ADB -s "$DEVICE" shell pm uninstall -k --user 0 "$pkg" || true
        fi
    fi
done

echo "Done."
