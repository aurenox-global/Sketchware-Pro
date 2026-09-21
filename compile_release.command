#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -x "./gradlew" ]; then
  chmod +x ./gradlew
fi

TEMP_GOOGLE_SERVICES=0
if [ ! -f "$SCRIPT_DIR/app/google-services.json" ]; then
  if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
    printf '%s' "$GOOGLE_SERVICES_JSON" > "$SCRIPT_DIR/app/google-services.json"
    TEMP_GOOGLE_SERVICES=1
    echo "==> Se creo app/google-services.json temporal desde GOOGLE_SERVICES_JSON"
  elif [ -f "$SCRIPT_DIR/app/src/debug/google-services.json" ]; then
    cp "$SCRIPT_DIR/app/src/debug/google-services.json" "$SCRIPT_DIR/app/google-services.json"
    TEMP_GOOGLE_SERVICES=1
    echo "==> Se creo app/google-services.json temporal desde app/src/debug/google-services.json"
  fi
fi

cleanup() {
  if [ "$TEMP_GOOGLE_SERVICES" -eq 1 ]; then
    rm -f "$SCRIPT_DIR/app/google-services.json"
    echo "==> Se elimino app/google-services.json temporal"
  fi
}
trap cleanup EXIT

echo "==> Compilando Sketchware Pro (Release APK firmado)..."
echo "==> Firma usada: signingConfigs.debug (testkey.keystore)"
./gradlew --no-daemon :app:assembleRelease

echo
echo "==> Compilacion release finalizada. APK(s) generado(s):"
find "$SCRIPT_DIR/app/build/outputs/apk/release" -name "*.apk" -print

echo
echo "Presiona Enter para cerrar..."
read -r
