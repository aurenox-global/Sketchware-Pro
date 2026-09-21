<p align="center">
  <img src="assets/Sketchware-Pro.png" width="220" alt="Sketchware Pro">
</p>

<h1 align="center">Sketchware Pro</h1>

<p align="center">
  <b>Crea apps Android reales desde tu móvil.</b><br>
  <a href="README.md">🇬🇧 Read in English</a> ·
  <a href="https://aurenox-global.github.io/Sketchware-Pro/es.html">Sitio web</a> ·
  <a href="https://github.com/aurenox-global/Sketchware-Pro/releases">Descargar</a>
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-v7.0.5-008dcd">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-57beee">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-57beee">
  <img alt="license" src="https://img.shields.io/badge/license-source--available-ffc107">
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-1d7a73">
</p>

---

Sketchware Pro es un IDE de Android que funciona sobre Android. Arrastra bloques visuales, escribe Java o
Kotlin, compila en el propio dispositivo y obtén un APK instalable — sin necesidad de ordenador.

Sketchware era una app que permitía crear aplicaciones Android de forma visual, directamente en el teléfono.
El desarrollo se detuvo hace años. **Sketchware Pro** es un mod de la comunidad que lo mantiene vivo, arregla
lo que estaba roto y añade lo que el original nunca tuvo.

> 🔗 **La documentación completa, en inglés y español, está aquí:**
> **https://aurenox-global.github.io/Sketchware-Pro/es.html**

## Índice

- [Qué hace](#qué-hace)
- [Características](#características)
- [Instalar](#instalar)
- [Compilar desde el código](#compilar-desde-el-código)
- [Mapa del código](#mapa-del-código)
- [Cambios de este fork](#cambios-de-este-fork)
- [Hoja de ruta](#hoja-de-ruta)
- [Contribuir](#contribuir)
- [Aviso legal](#aviso-legal)

## Qué hace

| | |
|---|---|
| **Editor visual de layouts** | Arrastra widgets, edita propiedades, previsualiza y genera el XML |
| **Editor de bloques / lógica** | Eventos, condiciones, bucles, variables, funciones y paleta con buscador |
| **Editor de código** | Resaltado de sintaxis, autocompletado y árbol de archivos de las fuentes generadas |
| **Java y Kotlin** | Compilación de Kotlin soportada mediante el `kotlinc` incluido |
| **Editor de recursos** | Imágenes, colores, fuentes, sonidos y textos sin salir de la app |
| **Gestor de librerías** | Firebase, Material Components, Glide, Retrofit y muchas más |
| **Compilador en el dispositivo** | Genera el APK en el teléfono, con `aapt2` por arquitectura incluido |
| **Bloques personalizados** | Crea tus propios bloques y compártelos con la comunidad |

Todo lo que haces es Android de verdad: fuentes Java reales, recursos reales y APK reales que son tuyos.

## Características

- **Bloques que producen código real.** Nada queda encerrado en un formato propietario: puedes leerlo, editarlo y exportarlo.
- **Un IDE completo en el dispositivo.** Layout, lógica, recursos, manifest, firma y compilación, todo en local.
- **Catálogo de proyectos.** Empieza desde una plantilla, un proyecto en blanco o un archivo `.swb` compartido. Todo vive en tu almacenamiento.
- **Hecho por la comunidad.** Gratis, sin suscripción, sin anuncios y sin telemetría propia.
- **Depuración integrada.** Logcat, informes de error y, en versiones recientes, depuración real en desarrollo.

## Instalar

No hay versión en tiendas — el APK lo instalas tú.

1. Descarga el APK desde la [página de releases](https://github.com/aurenox-global/Sketchware-Pro/releases).
2. Permite instalar desde orígenes desconocidos para tu navegador o gestor de archivos.
3. Abre el APK e instala. Si ya tenías Sketchware Pro con otra firma, desinstálalo antes.
4. Opcional, por ADB (además conserva tus datos cuando la firma coincide):

```bash
adb install -r app-release.apk
```

**Requisitos:** Android 8.0 (API 26) o superior. Se recomienda un dispositivo ARM64.

## Compilar desde el código

Requisitos:

- **JDK 17** — `java -version` debe reportar 17.x.
- **Android SDK** — plataforma 36 y build-tools 35; apunta `local.properties` a él (`sdk.dir=/ruta/al/android-sdk`).
- **Google Services** — `app/google-services.json` es opcional. Los scripts crean un placeholder temporal desde `app/src/debug/google-services.json` si hace falta.

```bash
# build de debug
./gradlew :app:assembleDebug

# build de release (firmado)
./gradlew :app:assembleRelease

# o usa los scripts de ayuda
./compile_project.command
./compile_release.command
```

> [!WARNING]
> Si no defines `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`
> (entorno o `~/.gradle/gradle.properties`), la build cae a la `testkey.keystore` incluida en el repositorio —
> una clave de test **pública** de AOSP. Ese APK sirve para pruebas locales, pero cualquiera podría firmar una
> actualización que Android aceptaría como tuya. Nunca publiques un APK firmado con ella.

## Mapa del código

| Clase | Función |
| ---------------------------- | ----------------------------------------------------------- |
| `a.a.a.ProjectBuilder` | Ayudante para compilar un proyecto completo |
| `a.a.a.Ix` | Genera el `AndroidManifest.xml` |
| `a.a.a.Jx` | Genera el código fuente de las activities |
| `a.a.a.Lx` | Genera código de componentes: listeners, helpers, etc. |
| `a.a.a.Ox` | Genera los archivos XML de layout |
| `a.a.a.qq` | Registro de dependencias de librerías integradas |
| `a.a.a.tq` | Pasos del diálogo de compilación |
| `a.a.a.yq` | Rutas de archivos de los proyectos |
| `pro.sketchware.*` | Donde deben ir las funciones nuevas, respetando la estructura |
| `mod.*` | Aquí vive la mayoría de aportes de la comunidad |

> [!TIP]
> Las funciones nuevas que no necesiten tocar otros paquetes van en `pro.sketchware`, respetando la estructura
> de directorios y nombres. Prefiere Java antes que Kotlin salvo que Kotlin sea realmente necesario.

## Cambios de este fork

Este repositorio es un fork personal. Cada mejora se añade aquí según entra, y el
[sitio web](https://aurenox-global.github.io/Sketchware-Pro/es.html) se actualiza a la vez.

### 2026-09-21 — un APK por arquitectura (ABI splits)

- **ABI splits activados.** `assembleRelease` genera ahora un APK por arquitectura en lugar de uno universal,
  así que el dispositivo deja de descargar los otros tres juegos de librerías nativas. Verificado: cada APK lleva
  solo su propio `lib/<abi>/`, incluido el `aapt2` incluido, y sigue firmado con la clave privada.

### 2026-09-21 — firma: se mantiene la clave original

- **Las releases conservan la clave de firma original, a propósito.** El APK publicado tiene que poder
  actualizarse encima de las instalaciones existentes, así que el build de release se firma con la
  `testkey.keystore` del propio proyecto (SHA-256 `a40da80a…`), la misma identidad que usa la v7.0.5 publicada.
  Verificado con `apksigner`: los cuatro APK por arquitectura llevan exactamente esa huella.
- Se generó una keystore privada de 4096 bits y se guardó en `~/.android-keys/sketchware-pro/release.jks`
  para el día en que se quiera una identidad de distribución real. Cambiar a ella obligaría a desinstalar y reinstalar.

### 2026-09-21 — primera versión versionada (v7.0.5)

- **La build ya no necesita un repo git.** `git rev-parse` se ejecutaba al configurar y ensuciaba
  `BuildConfig.GIT_HASH`. Ahora lee `GIT_HASH` / `GIT_SHORT_HASH` del entorno y solo usa git cuando existe `.git`.
- **Default de `SKETCHUB_API_KEY`.** Sin la variable de entorno, `BuildConfig` contenía el literal `"null"`.
  Ahora es cadena vacía.
- **Firma del release fuera del árbol de código.** Las credenciales salen de `RELEASE_STORE_*` / `RELEASE_KEY_*`
  (entorno o `~/.gradle/gradle.properties`), con aviso explícito si faltan.
- **64 bloques `catch` vacíos anotados.** 44 archivos se tragaban las excepciones en silencio; ahora registran
  con `Log.d("SketchwarePro", …)`.
- **Builds más rápidas.** Activados `org.gradle.parallel` y `org.gradle.caching`.
- **Repositorio publicado y versionado.** Historial git, `.gitignore` auditado, repo público y primera release
  con el APK adjunto.

## Hoja de ruta

| Punto | Estado |
| ------------------------------------------------ | ----------- |
| Build sin git, default de la API key, catches silenciados | hecho |
| Credenciales de release fuera del código | hecho |
| Historial git, repo público, primera release | hecho |
| Documentación bilingüe y sitio web | hecho |
| ABI splits (un APK por arquitectura) | hecho |
| Firma de release: se mantiene la clave original (por decisión) | hecho |
| Reducción de código con R8 (bloqueada por el jar de `kotlinc`) | bloqueado |
| Reducción de recursos (`res/raw/keep.xml`) | bloqueado |
| Traducciones, APIs deprecadas, cobertura de tests | planificado |

Bloqueos conocidos, con detalle:

- **R8** — `minifyReleaseWithR8` falla porque `kotlinc-for-sketchware` incluye clases `dalvik/**` que R8 se
  niega a tratar como clases de programa. Hay que reempaquetar ese jar primero.
- **Reducción de recursos** — hay 29 usos de `getIdentifier()`, así que se borrarían recursos que parecen
  sin usar. Antes hay que escribir un `res/raw/keep.xml`.
- **`nonTransitiveRClass=true`** — rompe la compilación: `mod/jbk/util/OldResourceIdMapper.java` referencia
  `R.drawable.abc_*` de appcompat, que solo existe con R transitivas.
- **Traducciones** — 2.239 strings y ni una sola carpeta `values-<idioma>`.
- **APIs deprecadas** — `getColor()` ×126, `onActivityResult` ×63, `startActivityForResult` ×47,
  `getExternalStorageDirectory` ×43.
- **Deuda de lint** — un baseline de 24.169 líneas que solo detecta drift, nunca reduce.

## Contribuir

1. Haz un fork de este repositorio.
2. Haz tus cambios.
3. Pruébalos.
4. Abre un pull request.

Los mensajes de commit usan prefijo de tipo: `feat:`, `fix:`, `style:`, `refactor:`, `test:`, `docs:`,
`chore:` — por ejemplo `fix: Fix crash during launch on certain phones`.

## Aviso legal

**Sketchware Pro no es open source.** Es *source-available*: puedes leer el código y enviar cambios, pero no es
tuyo. Parte del código puede infringir el copyright de Sketchware.

Es un mod de la comunidad hecho para mantener Sketchware vivo, por la comunidad y para la comunidad, sin
ninguna intención dañina hacia los desarrolladores originales. **Publicar Sketchware Pro, sin modificar o
modificado, en Google Play o en cualquier otra tienda no está permitido.** Úsalo bajo tu propio criterio.

Dos módulos, `kotlinc` y `build-logic`, vienen de [CodeAssist](https://github.com/tyron12233/CodeAssist) y
están bajo licencia GPL-3.0.

## Créditos

- Proyecto upstream: [Sketchware-Pro/Sketchware-Pro](https://github.com/Sketchware-Pro/Sketchware-Pro)
- Comunidad: [Discord](http://discord.gg/kq39yhT4rX)
- Sketchware original, de sus desarrolladores, que hicieron todo esto posible

---

<sub>Documentación mantenida en inglés y español. Última actualización: 2026-09-21.</sub>
