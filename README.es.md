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
  <img alt="version" src="https://img.shields.io/badge/version-v7.0.5.9-008dcd">
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

1. Descarga el APK que corresponda a tu dispositivo desde la [página de releases](https://github.com/aurenox-global/Sketchware-Pro/releases):
   - `app-arm64-v8a-release.apk` — prácticamente todos los móviles modernos (recomendado)
   - `app-armeabi-v7a-release.apk` — dispositivos antiguos de 32 bits
   - `app-x86_64-release.apk` / `app-x86-release.apk` — emuladores
2. Permite instalar desde orígenes desconocidos para tu navegador o gestor de archivos.
3. Abre el APK e instala. La clave de firma no cambia, así que se instala encima de versiones anteriores.
4. Opcional, por ADB (además conserva tus datos):

```bash
adb install -r app-arm64-v8a-release.apk
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

### 2026-09-22 — arreglada la vista previa de layouts con elementos View

- **La vista previa ya no sale en blanco.** Los disenos hechos con elementos View (AndroidX, widgets) iban por el
  renderizador nativo del editor de diseno, que le pide la raiz del layout a los datos internos del proyecto
  (`view_root`); si el nombre del layout no coincidia con ninguna entrada, devolvia una **raiz vacia** y no se
  pintaba nada, en silencio. Los layouts con HTML/WebView usaban el constructor de vistas reales, y por eso esos si
  se veian.
- Ahora **todos** los layouts se construyen con el constructor de vistas reales (que trabaja solo con el XML), con el
  renderizador nativo como reserva y un mensaje visible si ambos fallan, en vez de una pantalla en blanco muda.
- Version **v7.0.6.0** (versionCode 160), publicada como
  [v7.0.6.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.6.0).

### 2026-09-22 — soporte de Kotlin: autocompletado en ficheros .kt (fase 6 del IDE)

- **El editor de Kotlin ya completa tambien.** Los `.kt` reciben por fin el mismo trato que Java: sugerencias con
  los **simbolos de tu proyecto** (clases, objetos, `fun` y propiedades `val`/`var`), las **palabras clave de
  Kotlin** y las **clases del SDK y de las librerias**.
- El indice aprendio declaraciones de Kotlin (`class`/`interface`/`object` con modificadores, `fun` con receptor y
  genericos, propiedades) y solo aplica esos patrones a ficheros `.kt`.
- Lo demas (resaltado, indentado, emparejado) sigue delegando en el lenguaje TextMate de Kotlin, asi que si algo
  falla el editor se comporta igual que antes. Los **diagnosticos** de Kotlin aun no estan: necesitan el compilador
  de Kotlin, mucho mas pesado que ECJ, y iran en una fase aparte.
- Version **v7.0.5.9** (versionCode 159).

### 2026-09-22 — quick fix: importar la clase que falta (fase 5 del IDE)

- **Los diagnosticos ya no solo avisan: tambien arreglan.** Cuando el compilador no resuelve un tipo
  (`Button cannot be resolved to a type`), el diagnostico ofrece una accion rapida:
  **"Importar android.widget.Button"**, resuelta contra el SDK y las librerias del proyecto. Al elegirla se
  inserta el `import` despues de la linea del `package`.
- Las acciones rapidas van asociadas al diagnostico con la version del documento, asi que las obsoletas se
  descartan solas.
- Version **v7.0.5.8** (versionCode 158).

### 2026-09-22 — navegacion de codigo: ir a definicion y buscar usos (fase 4 del IDE)

- **El editor ya salta entre ficheros.** "Go to definition" y "Find usages" en el menu del editor resuelven el
  simbolo bajo el cursor en **todas las fuentes del proyecto** (no solo el fichero abierto) y abren el resultado;
  si hay varias coincidencias, sale un selector con fichero, linea y vista previa.
- El indice de simbolos guarda ahora la **ubicacion** de cada declaracion (fichero + linea) y la navegacion pasa
  por el andamiaje LSP que ya tenia el repo (`pro.sketchware.lsp`): proveedor nuevo de ambito proyecto con el
  buscador de un solo fichero como reserva, ademas de timeout y ejecucion en segundo plano.
- Version **v7.0.5.7** (versionCode 157).

### 2026-09-22 — completado del SDK y de las librerias (fase 3 del IDE)

- **El autocompletado ya no se limita a tu codigo:** ahora incluye las **clases del SDK de Android** (todo
  `android.jar`) y las de las **librerias que usa el proyecto** (las declaradas en el gestor de librerias mas los
  jars locales que anadas).
- Se inserta el **nombre simple** y en la descripcion va el **nombre completo**.
- El indice se lee una vez, se cachea en memoria y en disco (se invalida cuando cambia el jar) y se precalienta al
  abrir un `.java`. Orden: primero los simbolos de tu proyecto, luego palabras clave y al final SDK/librerias.
- Version **v7.0.5.6** (versionCode 156), publicada como
  [v7.0.5.6](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.6).

### 2026-09-22 — diagnosticos de Java en vivo (fase 2 del IDE)

- **Los errores y avisos se subrayan mientras escribes.** 1,2 s despues de dejar de escribir, la app compila
  *solo el fichero que estas editando* con el compilador de Eclipse (ECJ) que ya lleva dentro, con el classpath
  del proyecto (`android.jar` + `core-lambda-stubs` + tus librerias locales) y `files/java` como sourcepath. Al
  tocar la marca sale el mensaje del compilador.
- El analisis corre fuera del hilo de la interfaz, nunca se acumula (uno en vuelo; los resultados obsoletos se
  descartan) y falla en silencio: si algo va mal no se subraya nada y el editor sigue igual.
- Version **v7.0.5.5** (versionCode 155), publicada como
  [v7.0.5.5](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.5).

### 2026-09-22 — primera pieza del IDE dentro de la app: autocompletado con los simbolos del proyecto

- **El editor de Java ya completa los simbolos de tu proyecto:** mientras escribes sugiere las clases,
  metodos y campos que encuentra en las fuentes de `files/java`, ademas de las palabras clave de Java.
- El indice se construye **sin compilador**: lee los `.java`/`.kt` y extrae declaraciones con expresiones
  regulares; se cachea por proyecto y solo se refresca cuando cambian las fuentes (topes: 400 ficheros,
  4000 simbolos, 512 KB por fichero).
- El lenguaje del editor delega todo lo demas en el `JavaLanguage` de sora-editor, asi que resaltado, indentado
  y emparejado de simbolos siguen igual. En el peor caso, simplemente no aparece la lista de sugerencias.
- Version **v7.0.5.4** (versionCode 154), publicada como
  [v7.0.5.4](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.4). Siguientes: diagnosticos
  en vivo con ECJ, ir a definicion y consola de build integrada.

### 2026-09-21 — los enlaces de GitHub de la app apuntan a este fork

- **Los enlaces de GitHub dentro de la app redirigen ahora aquí** en vez de al upstream: el enlace del
  repositorio, el de releases y la API de commits que alimenta la pantalla de cambios apuntan a
  `aurenox-global/Sketchware-Pro`, así que el aviso de actualizaciones consulta las releases de este fork.
  `_Mod_README.txt` también menciona el fork.
- Verificado dentro del APK compilado: `resources.arsc` lleva las tres URLs nuevas. Versión subida a
  **v7.0.5.3** (versionCode 153) y publicada como [v7.0.5.3](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.3).

### 2026-09-21 — CI en verde, y por qué R8 solo fallaba allí

- **Android CI y Verification Baseline ya pasan.** El último bloqueo era sutil: el jar de `bundletool` que
  descarga GitHub Actions incluye ficheros `classes.dex` embebidos junto al bytecode
  (`com/android/tools/build/bundletool/archive/dex/**`), y R8 rechaza un archive con ambos. La caché local de
  Gradle tiene esa misma versión *sin* ellos, y por eso el build minificado funcionaba en local y fallaba solo
  en CI. Filtrar ese jar no es seguro (se pierde `aapt2-proto` y esos dex son los que bundletool usa para
  construir AABs), así que **el CI compila con `-PskipMinify`** y el build local de release mantiene R8 activo.
- Por el camino: mock de `google-services.json` para la variante release, la testkey pública subida para que el
  CI firme con la misma clave, ABI splits en el workflow, baseline de lint regenerado y un error de lint real corregido.
- **Publicada la [v7.0.5.2](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.2)** con los APK de R8 (~106 MB por arquitectura).

### 2026-09-21 — el repositorio estaba incompleto (y por eso el CI no compilaba)

- **Encontrado y arreglado un fallo del `.gitignore` con consecuencias reales.** El patrón `build/`
  coincidía con *cualquier* carpeta llamada `build` a cualquier profundidad, así que tres paquetes de código
  fuente reales — `mod/hey/studios/build/`, `mod/jbk/build/` y `mod/pranav/build/` — nunca se subieron:
  faltaban 8 ficheros Java/Kotlin del repositorio público. Por eso fallaba la compilación en CI y por eso
  un clon limpio no podía compilar. Las reglas de ignore están ahora acotadas, con excepciones explícitas.
- **El CI puede compilar sin secretos:** `createMockGoogleServices` ahora crea también `app/google-services.json`
  (la variante release lo necesita), y la testkey pública de AOSP queda exceptuada en `.gitignore` y subida al
  repo, para que el CI firme con la misma clave que el build local.
- **Workflow actualizado para los ABI splits:** el paso de renombrado y la ruta del APK para Telegram.
- `docs/dependency-snapshot.lock` regenerado. Versión subida a **v7.0.5.2** (versionCode 152).

### 2026-09-21 — reducción de código con R8 (builds de release)

- **R8 activado en el build de release**, bajando cada APK de ~129 MB a ~106 MB. Hicieron falta tres cosas:
  reempaquetar dos jars en tiempo de build (`kotlinc-for-sketchware` trae clases `dalvik/**` y `kxml2` trae
  `org/xmlpull/**`, ambos ya los aporta Android), las reglas `-dontwarn` que R8 genera para referencias a clases
  que no existen en Android, y desactivar la subida del mapping a Crashlytics (el build local usa un
  `google-services.json` mock). La clave de firma original sigue intacta.
- La reducción de recursos sigue pendiente: los 29 usos de `getIdentifier()` necesitan antes un `res/raw/keep.xml`.

### 2026-09-21 — un APK por arquitectura (ABI splits)

- **ABI splits activados.** `assembleRelease` genera ahora un APK por arquitectura en lugar de uno universal,
  así que el dispositivo deja de descargar los otros tres juegos de librerías nativas. Verificado: cada APK lleva
  solo su propio `lib/<abi>/`, incluido el `aapt2` incluido.
- **Publicado como [v7.0.5.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.5.1)**, firmado con
  la clave original para que se instale encima de versiones anteriores.

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
| Repositorio completo: recuperados los paquetes ocultos por `.gitignore` | hecho |
| CI en verde: Android CI + Verification Baseline | hecho |
| Los enlaces de GitHub de la app apuntan a este fork | hecho |
| IDE dentro de la app: autocompletado con simbolos del proyecto (fase 1) | hecho |
| IDE dentro de la app: diagnosticos en vivo con ECJ (fase 2) | hecho |
| IDE dentro de la app: completado del SDK y librerias (fase 3) | hecho |
| IDE dentro de la app: navegacion (ir a definicion / buscar usos, fase 4) | hecho |
| IDE dentro de la app: quick fixes sobre los diagnosticos (fase 5) | hecho |
| IDE dentro de la app: soporte de Kotlin (fase 6) | hecho |
| IDE dentro de la app: diagnosticos de Kotlin (fase 7) | siguiente |
| R8 funcionando en CI (bloqueado por el jar de `bundletool`) | bloqueado |
| ABI splits (un APK por arquitectura) | hecho |
| Firma de release: se mantiene la clave original (por decisión) | hecho |
| Reducción de código con R8 (builds de release) | hecho |
| Reducción de recursos (`res/raw/keep.xml`) | bloqueado |
| Traducciones, APIs deprecadas, cobertura de tests | planificado |
| Vista previa en blanco con disenos hechos con View | arreglado |

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
