#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -x "./gradlew" ]; then
  chmod +x ./gradlew
fi

echo "==> Compilando Sketchware Pro (Debug APK)..."
./gradlew --no-daemon :app:assembleDebug

echo
echo "==> Compilacion finalizada. APK(s) generado(s):"
find "$SCRIPT_DIR/app/build/outputs/apk" -name "*.apk" -print

echo
echo "Presiona Enter para cerrar..."
read -r
