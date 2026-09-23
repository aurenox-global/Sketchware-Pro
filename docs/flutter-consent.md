# Flutter: la descarga de Dart ya es una decisión tuya (v7.0.9.0)

Fecha: 2026-09-23 · Versión: **v7.0.9.0** (versionCode 166) · Repo: `Sketchware-Pro-main`

Release: <https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.9.0>

Antes, al compilar un proyecto Flutter (modo experimental, detrás del flag `FLUTTER_EXPERIMENTAL_ENABLE`) la app
podía **descargar sola** el toolchain de Dart (~307 MB) sin avisar ni preguntar. Ahora la descarga **no ocurre nunca
en silencio**: hay un diálogo que muestra qué falta, cuánto ocupa, dónde se guarda, y la decide el usuario.

## 1. Qué se ve en el diálogo

Menú del editor de lógica → **Flutter: estado del toolchain**. El estado se calcula en un hilo de fondo (ejecuta los
binarios y mide el disco) y el diálogo Material muestra:

- **Estado**: instalado o no (con una línea de resumen).
- **Lista de los componentes que faltan, cada uno con su tamaño**: SDK de Dart (`dart_3.13.4_aarch64.deb`, 91,6 MB),
  embedding del engine (1,5 MB), engine `libflutter.so` para arm64-v8a (172,2 MB), *patched SDK* (3,9 MB), framework
  Dart de Flutter 3.47.5 (33,0 MB), los paquetes de pub (characters, collection, `material_color_utilities`, meta,
  `vector_math`) y los `material_fonts`.
- **`Descarga necesaria: ~307.2 MB`** — el número real medido en el emulador, sumando solo lo que **falta** (no el
  toolchain entero). El tarball del framework es el único tamaño que no venía en el código: se midió a mano
  (34.580.315 B) porque GitHub no publica `content-length`.
- **Aviso de red**: se descarga por internet, mejor con Wi-Fi; son unos 307,2 MB y puede tardar varios minutos.
- **Dónde se guarda**: `/data/user/0/pro.sketchware/files/flutter-toolchain`.
- **Y que después funciona sin conexión**: una vez instalado, compilar y ejecutar Flutter va en local.

Botones del diálogo:

| Botón | Qué hace |
| --- | --- |
| **Descargar ahora** | Único camino que toca la red. Descarga solo los componentes que faltan. |
| **Borrar toolchain (liberar X MB)** | Solo aparece si hay algo instalado; pide **confirmación** aparte y libera el espacio ocupado. |
| **Cancelar** | Cierra sin descargar y sin tocar el disco. |

![Diálogo de consentimiento del toolchain de Dart: estado, lista de componentes que faltan con su tamaño, "Descarga necesaria: ~307.2 MB", aviso de Wi-Fi, ruta de guardado y los botones Descargar ahora / Borrar toolchain / Cancel](assets/flutter-consent.png)

## 2. Compilar y ejecutar también pide permiso

El camino **Flutter: compilar y ejecutar** pide el **mismo consentimiento** antes de arrancar el build (bloquea el
hilo de compilación y muestra el diálogo). Si el usuario cancela, la compilación **no se ejecuta**, el log dice
`Build abortado: no se ha autorizado la descarga del toolchain.` y **no se escribe nada en disco**.

A nivel de código, `ensureInstalled(...)` — la variante que usan **todos** los caminos de build — ya **no descarga**:
solo deja el mensaje `Para compilar Flutter hace falta el toolchain de Dart: instalalo desde Flutter > Estado del
toolchain` y devuelve `false`. La descarga vive en la variante nueva `ensureInstalled(..., allowDownload = true)`,
que solo se invoca **después** de que el usuario acepte. El efecto colateral deseado es que
`DesignActivity`/`ExportProjectActivity` (vía `FlutterCompilerBridge`) tampoco descarguen a espaldas del usuario.

## 3. Información del proyecto

**Flutter: información del proyecto** incluye ahora si el toolchain está instalado, lo que falta y el espacio ocupado
(`Toolchain: no instalado (~307.2 MB)` o `Toolchain: listo`, `Dart instalado: …`, `Espacio ocupado por el toolchain:
…`). Todo el cálculo se movió a un hilo de fondo (antes `isReady` ejecutaba binarios en el hilo de UI).

## 4. Pendientes honestos

- **La descarga real de los 307 MB no se ejecutó** en la prueba (era opcional): el flujo "aceptar → descargar" no
  llegó a completarse en el emulador. Que el toolchain completo instala y compila después ya estaba verificado en
  fases anteriores.
- El **borrado** se probó con un toolchain **simulado de 5 MB**, no con uno real de 300 MB (el botón y la confirmación
  dependen solo de que haya bytes instalados, así que el camino es el mismo).
- La evidencia es de un **emulador** arm64 API 34 (`uiautomator` + capturas); no se ha probado en móvil físico.
- Los diálogos salen en inglés (`Cancel`) porque el emulador estaba en inglés; con el sistema en español se ve
  "Cancelar".

## 5. Reproducirlo

```bash
./gradlew assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
# FLUTTER_EXPERIMENTAL_ENABLE=true en las prefs de la app (con la app parada)
# menú -> Flutter: estado del toolchain  ->  Descargar ahora / Borrar toolchain / Cancelar
# Flutter: compilar y ejecutar -> Cancelar -> "Build abortado: no se ha autorizado la descarga del toolchain."
```
