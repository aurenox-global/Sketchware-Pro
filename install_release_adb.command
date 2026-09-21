#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    printf '%s\n' "$ANDROID_SDK_ROOT/platform-tools/adb"
    return 0
  fi

  if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    printf '%s\n' "$ANDROID_HOME/platform-tools/adb"
    return 0
  fi

  if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    printf '%s\n' "$HOME/Library/Android/sdk/platform-tools/adb"
    return 0
  fi

  return 1
}

pause_and_exit() {
  echo
  echo "Presiona Enter para cerrar..."
  read -r
  exit "$1"
}

if [ ! -f "$APK_PATH" ]; then
  echo "==> No se encontro la APK release:"
  echo "$APK_PATH"
  echo
  echo "==> Compila primero con: ./compile_release.command"
  pause_and_exit 1
fi

if ! ADB_BIN="$(find_adb)"; then
  echo "==> No se encontro adb."
  echo "==> Instala Android Platform Tools o define ANDROID_SDK_ROOT/ANDROID_HOME."
  pause_and_exit 1
fi

echo "==> Usando adb: $ADB_BIN"
"$ADB_BIN" start-server >/dev/null

DEVICE_LINES="$("$ADB_BIN" devices | sed '1d' | grep -E '[[:space:]]device$' || true)"
DEVICE_COUNT="$(printf '%s\n' "$DEVICE_LINES" | sed '/^$/d' | wc -l | tr -d ' ')"

if [ "$DEVICE_COUNT" = "0" ]; then
  echo "==> No hay dispositivos adb conectados."
  echo "==> Activa Depuracion USB, acepta la clave RSA en el telefono y vuelve a intentar."
  echo
  "$ADB_BIN" devices
  pause_and_exit 1
fi

ADB_TARGET=()
if [ -n "${ADB_SERIAL:-}" ]; then
  ADB_TARGET=(-s "$ADB_SERIAL")
  echo "==> Instalando en dispositivo ADB_SERIAL=$ADB_SERIAL"
elif [ "$DEVICE_COUNT" = "1" ]; then
  DEVICE_SERIAL="$(printf '%s\n' "$DEVICE_LINES" | awk '{print $1}')"
  ADB_TARGET=(-s "$DEVICE_SERIAL")
  echo "==> Instalando en dispositivo: $DEVICE_SERIAL"
else
  echo "==> Hay varios dispositivos conectados:"
  printf '%s\n' "$DEVICE_LINES"
  echo
  echo "==> Ejecuta indicando uno, por ejemplo:"
  echo "ADB_SERIAL=<serial> ./install_release_adb.command"
  pause_and_exit 1
fi

echo "==> APK: $APK_PATH"
echo "==> Instalando Sketchware Pro via adb..."
"$ADB_BIN" "${ADB_TARGET[@]}" install -r -d "$APK_PATH"

echo
echo "==> Instalacion finalizada."
pause_and_exit 0