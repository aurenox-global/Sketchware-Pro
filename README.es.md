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
  <img alt="version" src="https://img.shields.io/badge/version-v7.0.14.0-008dcd">
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

### 2026-09-24 — Ronda A: firma con tu propio keystore, y elegir APK o AAB en un solo dialogo

- **v7.0.14.0 (versionCode 176) — el IDE ya firma con *tu* keystore, y compilar es un solo dialogo: APK o AAB ×
  keystore guardado / fichero de keystore / testkey / sin firmar.** De donde se partia, medido, no supuesto: la
  herramienta *Sign an APK file* de Ajustes **nunca miraba tu keystore** — sus dos llamadas pasaban
  `useTestkey=true` hardcodeado, asi que firmaba siempre con la **testkey de AOSP** (`apksigner verify` →
  `a40da80a…`, no tu certificado); el dialogo de firma asumia una **ruta fija**
  (`/storage/emulated/0/sketchware/keystore/release_key.jks`) con **un solo campo de contrasena**, asi que la misma
  contrasena tenia que ser la del store y la del alias (`GetKeyStoreCredentialsDialog` construia
  `new Credentials(alg, etPassword, etAlias, etPassword)`), y la rama APK de `ExportProjectActivity` **ignoraba la
  ruta del dialogo y reutilizaba la contrasena de alias como contrasena de store**. **Gestor de keystores nuevo**
  (Ajustes → *General*): importa `.jks` / `.keystore` / `.bks` / `.p12` validando las credenciales antes de guardar,
  copia el fichero al **almacenamiento privado de la app** (`filesDir/keystores/`, no `/sdcard`, que es legible por
  cualquiera), guarda alias + contrasenas de store y de clave **cifradas** y muestra el certificado de cada uno — el
  SHA-256 que pinta es **identico al de `keytool`** (`B1:46:48:5F:…:C4`). El dialogo de firma compartido ya tiene
  **ruta explicita de keystore**, **contrasena de store separada de la de alias**, selector de keystore guardado que
  rellena todo y **ya no se cierra cuando la validacion falla** (antes perdias lo escrito). **Dialogo unico "Compile
  project"**, accesible desde el menu ▾ del editor (nuevo *Compile APK / AAB...*, junto a `Run ▶`) y desde Export
  Project: *What to build* = APK (debug) / APK (release) / AAB, *Signing mode* = keystore guardado / fichero de
  keystore / testkey / sin firmar; **recuerda la ultima eleccion**, no vuelve a pedir contrasenas y al terminar
  muestra **la ruta, con que se firmo y el certificado**, y ofrece **Install** si es un APK firmado. **El build
  on-device bloqueaba todo esto, y eran bugs reales, no los proyectos de prueba:** `DexMerger` reventaba con
  **`java.nio.BufferOverflowException`** en *cualquier* build release/AAB — los dex de libreria que inyecta el IDE
  **comparten un mismo `debug_info_item` entre hasta 116 metodos** mientras el merger escribia una copia nueva por
  `code_item` y reservaba el hueco con los bytes ya deduplicados; arreglado deduplicando por (dex de entrada,
  offset) — **2.297 → 361 debug info items**; el **`Export AAB` estaba roto por R8** (protobuf resuelve los getters
  generados **por reflexion**, asi que `getBundletool` habia desaparecido del dex release, 0 → 7 ocurrencias tras el
  `-keep`); el **APK release era V1-only** y no se instalaba en Android 11+
  (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`), ahora **apksig V1+V2+V3**; y el **AAB llevaba digests SHA-1** (`jarsigner`
  lo trataba como no firmado) → **SHA-256**, `jar verified.` **Otros dos bloqueos arreglados de paso:** un XML de
  recurso escrito **solo con la cabecera** (`NONE.xml`) tumbaba el build **entero** en aapt2 → plantillas validas por
  carpeta, validacion de nombre y una guardia que aparta los XML sin raiz a `.invalid-xml-skipped/` con aviso; y R8
  borraba el **`<init>` de sus propios proveedores de threading**, que ese mismo R8 embebido instancia **por
  reflexion** (`Failure creating provider for the threading module`) → `-keep class com.android.tools.r8.threading.**
  { *; }` (**+1.196 B**, ~0,001 % del APK). **Verificado en el APK release (R8):** un APK release firmado con **tu**
  certificado (`apksigner verify`: `Verifies`, V1+V2+V3, `b146485f…`) e **instalado desde el boton Install del propio
  dialogo**; un AAB con la estructura de bundle completa y `jarsigner -verify` → *jar verified*; `Run ▶` intacto
  (debug, testkey `a40da80a…`); el modo sin firmar escribe `.unsigned`. **Honesto:** el AAB **no** se ha probado
  contra `bundletool`/Play (en esta maquina no hay `bundletool`), los modos *keystore file* y *testkey* del dialogo
  **no** se ejecutaron en release (solo UI), y la conversion de proyectos de Android Studio/GitHub sigue
  **pendiente** (siguiente ronda: viabilidad + MVP). Pagina de la release:
  [v7.0.14.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.14.0). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda A).

### 2026-09-24 — Vista previa, ronda 12: los colores que eliges se escribian transparentes en el XML

- **v7.0.13.0 (versionCode 175) — los colores que eliges en el editor salian transparentes, en la vista previa *y*
  en la app compilada.** Un bug **heredado del Sketchware Pro original**, y **determinista**, no un caso raro:
  **cualquier color de fondo elegido desde la paleta hexadecimal** se escribia en el XML como `#00RRGGBB` — alfa
  **00, totalmente transparente** — asi que la vista se dibujaba sin color y la app no avisaba de nada (*"Preview
  OK"*). Reproducido en el **flujo real** (editor → `Ox` → preview), no solo inyectando XML: con `button1` =
  `#2196F3` y `linear1` = `#4CAF50` la preview real mostraba **0 px azules y 0 px verdes**, y **ese mismo XML
  inyectado lo reproducia identicamente**; con el alfa corregido paso a **30.524 / 593.519 px**. **Causa raiz, una
  linea:** `a/a/a/Ox.java` hacia `int color = backgroundColor & 0xffffff` **antes** de formatear, y `formatColor`
  imprime 8 digitos cuando el alfa no es `0xFF` — asi que `0xFF2196F3` quedaba en `0x002196F3` → `"#002196F3"`. La
  misma mascara afectaba tambien a `backgroundTint`, `cardBackgroundColor` y `contentScrim`, y a las rutas de texto
  (`textColor`, `textColorHint`). **El arreglo** es dejar de enmascarar el alfa en esos **4 sitios** (`Ox.java:201,
  418, 824, 847`; no se toco `formatColor`). **Medido en el APK release (R8)** con el flujo real: azul **0 →
  30.398 px**, verde **0 → 333.317 px**, texto rojo **0 → 577 px**; el XML ya lleva `#2196F3` / `#4CAF50` / `#F44336`
  (6 digitos), y el **translucido** `#802196F3` conserva su alfa (8 digitos, `0x80` intacto, que la mascara vieja
  habria puesto a cero). La regresion es **exacta**: r8 `caseA`/`caseC` y r11 `caseE` salen **identicos pixel a
  pixel** (356.400 px, mismo bbox). Y el layout que compila el IDE (`mysc/601/…/res/layout/main.xml`) ya lleva
  `#2196F3`, `#4CAF50`, `#802196F3` y `#F44336` en vez de `#00…`. **Honesto:** el enlazado `aapt2` del propio IDE
  sigue fallando despues por un problema **preexistente** de recursos AppCompat de ese proyecto, ajeno a estas 4
  lineas. Pagina de la release: [v7.0.13.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.13.0).
  Todos los detalles: [docs/preview-fix.md](docs/preview-fix.md) (ronda 12).

### 2026-09-24 — Vista previa, ronda 11: copiar el aviso completo, el permiso de almacenamiento y los colores que solo estaban en el `res` generado

- **v7.0.12.0 (versionCode 174) — copiar el aviso completo, el permiso de almacenamiento y los colores del proyecto
  que solo vivian en el `res` generado.** Tres piezas de la vista previa de disenos, cada una pedida o medida, y las
  tres recomprobadas en el APK **release** con R8. (A) **El dialogo de avisos tiene un boton neutro "Copiar"** — en
  la barra parcial y en el dialogo `OK` — con el toast *"Aviso copiado"*. Lo que copia es el **informe completo, sin
  truncar**: el dialogo corta cada grupo a **12 lineas**, el copiado no (**1.119** caracteres con 2 recursos rotos,
  **1.869** con 20 colores rotos, **572** en el caso de estilos/iconos), y es **autosuficiente** (la linea de estado,
  cada grupo con su recuento, cada elemento con su motivo y su *"buscado en:"*, y la firma final). Verificado de
  punta a punta pegandolo de verdad en un campo de texto. (B) **Causa uno, confirmada con prueba: el permiso de
  almacenamiento.** Sin `MANAGE_EXTERNAL_STORAGE` la app no puede leer los ficheros del proyecto y **todo cae a
  valores por defecto en silencio**: el azul del proyecto pasa de **373.070 px a ausente**, logcat muestra
  `EACCES (Permission denied)` y el icono desaparece tambien. Arreglado en tres piezas: una linea **ambar** explicita
  en el dialogo, un dialogo **"Permiso de almacenamiento"** que abre *Acceso a todos los archivos* si falta, y una
  **relectura** del proyecto al volver (verificada por log). Con el permiso concedido la regresion es exacta:
  **373.070 px**, mismo bbox. (C) **Causa dos, la que mas probablemente era la tuya: `colors.xml` solo se leia de
  `values/`.** Los colores definidos en el `res` que genera el build (`@color/colorPrimary`, `colorAccent`, …) salian
  *no encontrados*, asi que la banda seguia blanca y la barra en rojo. Ahora se leen **ambas** ubicaciones, igual que
  ya se hacia con `styles.xml`: **359.604 px blancos → 356.400 px con su color, cero rojo**. (D) **Recomprobado en
  release (R8).** Los cuatro puntos se comportan **igual que en debug**: Copiar (toast + **1.627** caracteres +
  pegado real), permiso (ambar `0xFFB26A00` **15.488 px**, **0 rojo**; con el, teal **356.400 px** y `Preview OK`),
  `@color/colorPrimary` con **356.400 px** y el bbox exacto, y la regresion r8/r5 con recuentos y bboxes
  **identicos pixel a pixel** (purpura 18.933, verde 11.764, azul 90.564, cian 21.428, magenta 18.896). **Honesto:**
  al recrear los fixtures cambio el *texto* del informe (1.627 vs 1.869 caracteres), y el portapapeles solo se puede
  verificar con un pegado real (API 34 no lo expone por shell). Pagina de la release:
  [v7.0.12.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.12.0). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 11).

### 2026-09-23 — Flutter, experimental: release/AOT en el dispositivo, assets completos y pub real (fase 8)

- **El release/AOT en el dispositivo esta desbloqueado.** El Dart SDK para Android se compilo para este fork con
  **compressed pointers** — el flag lo activa el **nombre de la arquitectura** (`arm64c`), no una opcion de linea de
  comandos — y en modo **product**, porque el primer token del feature string del snapshot se decide en tiempo de
  compilacion (`product`, no `release`). Su `gen_snapshot` (4.991.592 B, sha256 `9921983f…`) va empaquetado dentro
  del APK y con el **el propio telefono genera un `libapp.so` que el engine oficial acepta**. Probado: un APK
  **release** arranca en un emulador arm64 Android 14, el contador pasa de 0 a 3 con toques reales y **no hay cinta
  DEBUG**. Control A/B: el mismo APK con el `gen_snapshot` del SDK de Termux falla con *the snapshot requires
  'no-compressed-pointers' but the VM has 'compressed-pointers'*; solo faltaban los compressed pointers.
- **La app puede ejecutar sus propias herramientas.** Con `targetSdk 34` SELinux deniega `execute_no_trans` para los
  binarios de `filesDir` y de `/data/local/tmp`, pero **`nativeLibraryDir` si funciona**: el runtime y el
  `gen_snapshot` van empaquetados como `lib/arm64-v8a/libdartaotruntime.so` y `libfluttergensnapshot.so`. La app hizo
  el AOT completo desde su propio proceso, como app sin privilegios y sin root, en 3,9 s y con el mismo sha256.
- **El bundle de assets esta completo.** Fuente Material (el pin real es `bin/internal/material_fonts.version` →
  `fonts.zip`, no un artefacto del engine), los shaders `ink_sparkle.frag` y `stretch_effect.frag` precompilados en
  el host y embebidos, `AssetManifest.bin` (`StandardMessageCodec`), `FontManifest.json` y `NOTICES.Z`. Verificado
  en el dispositivo: el icono `+` se dibuja y el error del shader desaparece.
- **pub real.** `dart pub get` ya corre en el dispositivo (el cliente de pub viene en el propio SDK) y resuelve
  paquetes de pub.dev de verdad, y el kernel se compila con el `package_config.json` autentico resultante.
- **Plugins: a medias, y se dice.** El fork tiene compilador de plugins Kotlin/Java propio (ECJ mas el
  `K2JVMCompiler` del fork) que genera el `GeneratedPluginRegistrant`, fusiona manifests y resuelve dependencias
  AAR; **todavia no se ha compilado ni arrancado ningun plugin en un dispositivo** — es el primer pendiente.
- **Coste:** los dos ejecutables empaquetados suman **10,18 MB** al APK `arm64-v8a`. Las otras ABIs no tienen
  backend AOT y lo dicen, en vez de fallar en silencio. El modo por defecto sigue siendo **debug/JIT**, esto
  continua siendo experimental y todavia no hay hot reload.
- **v7.0.11.0 (versionCode 173) — los iconos que trae el IDE, el `styles.xml` de tu proyecto y los constructores que
  R8 borraba.** Tres piezas de la vista previa de diseños y una del toolchain de Flutter. (A) **Los iconos estaban
  dentro del APK desde el principio.** El set Material del IDE vive en `assets/icons/icon_pack.zip` (5.335.950 B:
  **2.191 nombres × 5 estilos = 10.955 SVG**, `svg/<nombre>/<estilo>.svg`), y el icono que eliges se convierte a vector
  XML y se guarda en el **almacen de imagenes del proyecto** `.sketchware/resources/images/<sc_id>/`, **no** en
  `files/resource/drawable`. La vista previa no miraba ni lo uno ni lo otro, asi que los iconos salian en **rojo**.
  Ahora resuelve de ambos **y** del `res` generado del build. **Medido:** antes, **2 recursos no encontrados** (barra
  roja, 33.087 px rojos); despues los 2 dibujados y **rojo=0**, con una nota ambar que dice de donde sale cada uno
  (`icon_miscellaneous_services_round -> svg/miscellaneous_services/round.svg`). (B) **El `styles.xml` de tu proyecto
  se lee entero.** El lector no reconocia las etiquetas **autocerradas** `<style ... />` — justo como el IDE genera
  `AppTheme.AppBarOverlay`/`PopupOverlay` — asi que se perdia ese estilo **y se tragaba el siguiente** (se leian 3 de
  5). Ahora lee **5 de 5**, tambien mira en `value/`, `values-v21`, `values-night` y el `res` generado del build, y un
  estilo autocerrado sin items envuelve el contexto con la base del framework de su cadena de padres. Resultado:
  **cero avisos** de estilo y el tema del proyecto se aplica. (C) **La release perdia constructores de vistas.** Con
  R8, **62 clases** pierden `<init>(Context)` y varias vistas de libreria (el `SignInButton` de Google,
  `FlexboxLayout`, `LottieAnimationView`, `SVGImageView`) pierden **los tres**, asi que se dibujaban como contenedor
  aproximado. Arreglado con **5 reglas `-keepclassmembers` acotadas** mas **sustitucion de la reflexion por mapeo
  explicito**; lo no mapeado va al aviso ambar. Delta: **+9.892 B**. Verificado en release: el boton real de Google se
  dibuja y los **7/7 casos en claro y 7/7 en oscuro** mantienen sus colores (caso A `#123456` 162.773 px, `#EE2222`
  111.016 px). **Correccion honesta:** la sospecha inicial ("la reflexion no funciona en release") era **falsa** — el
  aplicador por reflexion nunca llegaba a dispararse, y los colores de la ronda 8 **si** funcionaban en release; lo
  que fallaba eran los constructores de las vistas de libreria. (D) **Toolchain de Flutter para x86_64.**
  `app/src/main/jniLibs/x86_64/` trae ya `libdartaotruntime.so` (5.873.176 B — el ELF real del `.deb` x86_64 de
  Termux, que esta en `lib/dart-sdk/bin/`, no el envoltorio de shell de 115 B) y `libfluttergensnapshot.so`
  (5.123.768 B, compilado) con `--arch x64c --mode product --os android` (Android x64 exige compressed pointers); el
  bloqueo de ABI se sustituye por `abiHasAotBackend` (`arm64-v8a` | `x86_64`) y `armeabi-v7a`/`x86` siguen avisando.
  Coste: **+4,34 MB solo en x86_64** (release 112.136.131 -> 116.476.809); arm64 identico. **Honesto:** la ejecucion
  real en x86_64 **no** esta verificada (no hay imagen x86_64 disponible; inviable en Apple Silicon). Pagina de la
  release: [v7.0.11.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.11.0). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 10) y [docs/flutter-consent.md](docs/flutter-consent.md) (toolchain
  x86_64).
- **v7.0.10.5 (versionCode 172) — rojo solo si la vista no se puede dibujar, y los `@style` y el tema del proyecto
  ya pintan de verdad.** Dos piezas, ambas en la vista previa de diseños. (1) **El rojo mentia.** Un `@style/...`
  que llegaba a `resolveDimen` respondia *"referencia de medida no resoluble"*, y un `@dimen`/`@android:dimen`
  ausente, un `?attr` de tema no resoluble o un `@style` usado como `android:background` se pintaban en **rojo**
  aunque la vista se dibuja perfectamente con su valor por defecto. Ahora **el rojo se reserva a lo que de verdad
  impide dibujar** (una imagen que no existe, una clase sin reserva): toda referencia de estilo/medida/tema que no
  se pueda *aplicar* pasa al grupo ambar *"No aplicado / ajustado · estilos y medidas (no impide dibujar)"*, siempre
  con su motivo; el texto *"referencia de medida"* ya no existe (0 ocurrencias) y la barra solo se pinta roja
  (`0xB3B00020`) cuando hay recursos no encontrados. Los `@style/...` de **tu proyecto** se leen ahora de
  `files/resource/values/styles.xml` (`parent` explicito + herencia implicita por puntos + encadenado hasta 6
  niveles; API nueva `resolveStyle()`/`isStyleReference()`, con respaldo en estilos de app/material/appcompat/
  android), asi que `@style/AppTheme.AppBarOverlay` y `@style/AppTheme.PopupOverlay` dejan de salir como no
  resueltos y un estilo inexistente sale ambar, **nunca rojo**. **Medido:** antes la barra decia **en rojo**
  `Preview PARCIAL: 1 recurso no encontrado · 2 atributos no aplicados`; despues, **ningun bloque rojo** y barra
  ambar (`1 atributo no aplicado · 1 estilo/medida no aplicado`). Regresion de las rondas 1-7 (15 XML): los 11
  `Preview OK` siguen, y `caseR4_mixed` baja de 4 a 3 recursos rojos (uno pasa a ambar). (2) **El tema se aplica,
  no solo se resuelve.** `android:theme` crea ahora la vista *dentro* del tema (`ContextThemeWrapper` en
  `createRealView(bean, contextoDelPadre)`, los hijos heredan el contexto del padre): un estilo **con resId** es un
  tema real, y un estilo **solo del proyecto** (sin resId, no esta compilado en el APK del editor) usa una base del
  framework mas sus items mapeados a la vista — y desaparece el falso ambar *"un tema no se aplica"*.
  `app:tabTextAppearance` se aplica a los `TextView` de las pestañas (incluidas las 3 de ejemplo): `textSize`,
  `textColor` (color y `ColorStateList`), `textStyle`, `textAllCaps`, `fontFamily`, con el estilo del framework
  leido del tema y guarda de tipo. **Medido (recuento de pixeles):** un `AppBarLayout` con
  `theme="@style/AppTheme.AppBarOverlay"` pasa de estar **sin fondo** a tomar el color del tema `#FF112233`; las
  pestañas con un `tabTextAppearance` del proyecto salen en **magenta 24sp negrita** (9 ajustes);
  `@android:style/TextAppearance.Widget.TabWidget` se resuelve por resId 16973901 (3 ajustes) — **red=0 amber=0**
  en las 6 capturas. Regresion de las rondas 5-8 (23 XML) sin perder ningun `Preview OK`. **Honesto:** los estilos
  del proyecto **no tienen resId** (no estan compilados en el APK del editor), asi que su tema se **emula** (base
  del framework + items mapeados); las referencias `?attr/` del framework en `tabTextAppearance` se dejan al tema;
  `popupTheme` y `actionBarTheme` siguen sin aplicarse. Pagina de la release:
  [v7.0.10.5](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.5). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 9).
- **v7.0.10.4 (versionCode 171) — "los colores no se ven en ninguno", y la causa era una lista blanca de 19
  atributos.** La vista previa resolvia los atributos `app:*`/`android:*` con un `switch` fijo de **19 nombres** y
  **descartaba todo lo demas en silencio** — ni aviso ni log — mientras el editor de diseno (`ViewPane`) si los
  aplicaba con sus propios handlers; el motor bueno era el del editor y la vista previa usaba casi siempre el otro
  camino (`tryInflateRealLayout`). Por eso ningun color configurado en un widget — indicador de tab, borde del
  circulo, stroke del card, tinte de la barra, divider del spinner — llegaba a verse. **Que ha cambiado:** los
  appliers de `TabLayout`, `CircleImageView`, `MaterialButton` y `CardView` se extraen del editor a un unico helper
  compartido (`pro.sketchware.utility.WidgetInjectApplier`) para que los dos motores apliquen lo mismo, y el resto
  pasa por un **aplicador generico por tipo de vista** (TextView, ImageView, Progress/Seek/Rating, CompoundButton,
  List/Grid/Spinner, BottomNavigationView, TextInputLayout, Calendar/Date/TimePicker, SearchView, LinearLayout…) con
  **ultimo recurso por reflexion** (`app:loQueSea` -> `setLoQueSea`); y lo que no se puede aplicar sale ahora en la
  **barra ambar con su motivo** — nunca mas en silencio. Encontrado por el camino: soporte de `@dimen` (nuevo
  `resolveDimen`), `@drawable`/`@color` en esos atributos, `<size>` de un shape con solo alto (el divider invisible),
  `textColor`/`textSize` de `AnalogClock`/`DigitalClock` (el parser los tiraba), un `TabLayout` sin pestanas (se
  anaden 3 de ejemplo, exactamente como hace el editor) y `ProgressBar` horizontal cuando el XML lo pide.
  **Verificado (PIL/numpy, 7 casos, uno por familia, en claro y en oscuro, contando los pixeles del color dominante
  donde debe estar), antes -> despues:** stroke del card **0 -> 19.988 px**; indicador de tab **0 -> 1.496**; texto
  seleccionado del tab **0 -> 689**; borde del circulo **0 -> 11.924**; fondo del circulo **0 -> 90.660**;
  `progressTint` **0 -> 18.907**; un `divider` con `@drawable` del proyecto **0 -> 11.880**; `DigitalClock`
  `#CC0000` **0 -> 3.060** — **7/7 casos en claro y 7/7 en oscuro**. La barra ambar dice `Preview OK` sin atributos
  pendientes en los casos A/B/C/D/G, en E lista solo los cinco atributos de libreria que una clase ausente no puede
  recibir (con el motivo) y en F no queda ningun atributo pendiente. Regresion de las rondas 1-7: **17/17 iguales**
  al resumen del baseline (incluidos el `CircleImageView` de la ronda 7 y las diez vistas de la ronda 6), y de paso
  se corrige un **aviso falso de `fontFamily`** que desviaba dos casos. El editor de diseno queda intacto
  (`ViewPane.java` sin tocar, `DesignActivity` abre sin crash). **Honesto:** no se ha probado con *tu* proyecto ni tu
  APK; el "antes" es la release previa y el "despues" el debug de esta ronda; los widgets cuya clase no esta en el
  editor (Library/Google/ads/map/lottie) conservan fondo y tamano pero no sus atributos propios (se listan); y no se
  han medido grosores ni radios por pixel, solo colores. Pagina de la release:
  [v7.0.10.4](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.4). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 8).
- **v7.0.10.3 (versionCode 170) — el `CircleImageView` que rompia la vista previa ya no puede romper tampoco la app
  que compilas.** El aviso era `CircleImageView: scaleType CENTER no admitido`, con
  `java.lang.IllegalArgumentException: ScaleType FIT_CENTER not supported` y `Preview PARCIAL: 1 atributo no aplicado`,
  y tenia **tres causas a la vez**. (1) **Mayusculas:** el bean guarda `scaleType="CENTER"` (el nombre del enum, en
  mayusculas) mientras el XML usa `center`/`centerCrop`, y el traductor de la vista previa era **sensible a
  mayusculas** — `"CENTER"` no coincidia con ningun `case` y caia al valor por defecto `FIT_CENTER`, por eso la
  excepcion dice `FIT_CENTER` y no `CENTER`. (2) **El generador:** para el `CircleImageView` el XML generado **no
  llevaba `android:scaleType`** (el filtro de `Ox` descarta el atributo en todo widget cuyo nombre de clase lleve
  punto), pero un bean antiguo o importado con el nombre corto si pasa ese filtro y `Ox` escribia
  `android:scaleType="center"` — justo el valor que **peta tambien en la app compilada**, no solo en la vista previa.
  (3) **La libreria es mas estricta de lo esperado:** `de.hdodenhof:circleimageview:3.1.0` admite **solo
  `CENTER_CROP`** (comprobado con `dexdump` sobre el APK release: compara contra un unico campo estatico, y
  `CENTER_INSIDE` tambien lanza excepcion). **Que ha cambiado:** el generador escribe siempre `centerCrop` para un
  `CircleImageView` **y sanea un `scaleType` no soportado escrito a mano en `inject`**; una clase nueva y pura,
  `ScaleTypeCompat` (60 comprobaciones en JVM, idempotente), concentra la logica; los proyectos ya existentes se
  normalizan en **cuatro** puntos (compilar/generar, leer el XML, abrir el editor de diseno y previsualizar); la vista
  previa aplica el respaldo soportado y el aviso ambar ya **dice el valor** (`scaleType MATRIX -> ajustado a
  CENTER_CROP (el widget solo admite CENTER_CROP)`); y el selector de propiedades ofrece para un `CircleImageView`
  **solo `CENTER_CROP`** (antes ofrecia los siete valores). **Verificado:** el XML generado del proyecto real lleva
  `android:scaleType="centerCrop"` y cierra con `Preview OK · vistas: 5` sin ningun aviso, mientras el `ImageView`
  normal conserva `center`; en el emulador RV_API34 con el release R8 el circulo se dibuja con **fraccion de area
  0.768** (≈ π/4) y las esquinas con el fondo del padre; la regresion de las rondas 1-6 sale identica (9 casos mas los
  cuatro `caseR6_*`). **Diferencia intencionada:** las `ImageView` normales con `CENTER` ya no se dibujan estiradas en
  la vista previa (antes caia a `FIT_CENTER`), ahora coinciden con la app. **Pendientes honestos:** no se ha probado
  la app que compila el usuario ni una compilacion completa on-device, no se ha recorrido a mano el dialogo del
  selector para un `CircleImageView`, y la evidencia sale del proyecto de pruebas 601. Pagina de la release:
  [v7.0.10.3](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.3). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 7).
- **v7.0.10.2 (versionCode 169) — las diez vistas que el editor no podia instanciar vuelven a funcionar, y una vista
  que aun no se pueda crear ya no es un hueco rojo mudo.** La vista previa avisaba `Preview PARCIAL: 10 vistas no
  instanciables` para `CoordinatorLayout`, `AppBarLayout`, `CollapsingToolbarLayout`, `MaterialToolbar`,
  `MaterialButton`, `CircleImageView`, `SwipeRefreshLayout`, `TabLayout`, `BottomNavigationView` y `CardView` — y
  **las clases no eran el problema**: las diez estan en el dex de release con el nombre intacto. Lo que R8 habia
  borrado era el constructor que la vista previa pide **por reflexion**: `<init>(Context)` habia desaparecido en
  **9 de las 10** (solo sobrevivia el de inflado `<init>(Context, AttributeSet)`, porque lo piden las propias
  librerias) y el logcat lo decia tal cual — `NoSuchMethodException … <init> [class android.content.Context]`. En la
  decima, `CircleImageView`, la optimizacion de jerarquia hacia que el constructor de un argumento lanzara
  `ClassCastException: CircleImageView cannot be cast to …ItemCircleImageView`. **Arreglo 1:** `InvokeUtil` prueba
  ahora `(Context)` → `(Context, AttributeSet)` → `(Context, AttributeSet, int)` y explica el motivo exacto de cada
  fallo, y tres reglas `-keepclassmembers` acotadas en `app/proguard-rules.pro` devuelven el constructor de un
  argumento: medido en release, **10/10 instanciables** (antes 0/10) por **+49,7 KB (+0,04 %)** de APK. **Arreglo 2
  (degradacion util):** cuando una clase de verdad no esta en el APK del editor ya no hay hueco rojo mudo — un
  contenedor generico conserva el fondo, el padding y el tamano declarados y **dibuja sus hijos** (medido: hijos 0 px
  antes → **1.418 + 1.915 px** despues), con un borde ambar fino y una pastilla `≈ Clase` en vez de rojo de alarma, y
  el motivo exacto en el dialogo de detalle. Arreglado tambien por el camino: un solo atributo no admitido
  (`CircleImageView` con `scaleType` center, que el generador escribe por defecto) abortaba toda la vista previa
  nativa; ahora se captura y se anota en ambar. Regresion de las rondas 1-5 bien **en release** (HTML/WebView igual,
  colores 4.665/6.025 px, fondos 356.400 px, imagenes 10/10), y debug y release ya se comportan igual.
  **Pendiente honesto:** el generador escribe `android:scaleType="center"` por defecto para cualquier imagen y
  `CircleImageView` solo admite `CENTER_CROP`/`CENTER_INSIDE`, asi que ese layout puede seguir fallando en la app que
  compile el usuario. Pagina de la release:
  [v7.0.10.2](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.2). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 6).
- **v7.0.10.1 (versionCode 168) — el toolchain ya no dice "no instalado", y los nombres heredados de iconos se
  distinguen.** El fallo del toolchain de Flutter era nuestro, no tuyo: la app comprobaba la instalacion **ejecutando**
  `<filesDir>/flutter-toolchain/dart/bin/dart --version`, y SELinux prohibe ejecutar un ELF que vive en el directorio de
  datos de la app (`execute_no_trans`, `error=13`, dominio `untrusted_app`), asi que el instalador abortaba **antes** de
  bajar el engine y terminaba con **"Toolchain de Flutter no instalado"** despues de 91 MB. Ahora **"instalado" es un
  criterio de datos** (marcador `installed.properties` + `gen_kernel_aot.dart.snapshot` + `dartdev_aot.dart.snapshot` +
  `lib/_internal/vm_platform.dill`) y **"puede compilar" anade una sonda de ejecucion real** lanzada desde el
  directorio de librerias nativas (`libdartaotruntime.so`), asi que `bin/dart` no se vuelve a ejecutar y todos los
  mensajes dicen **la pieza, la ruta y la causa**. Verificado en un emulador arm64 API 34: la instalacion completa
  (`.deb` de 91 MB + artefactos del engine) ahora acaba en **"Toolchain Flutter: instalado (Dart 3.13.4) · 813.7 MB"**,
  y el estado intermedio tambien es honesto (`SDK Dart 3.13.4 extraido … pero NO listo para compilar`) en vez de un
  falso "no instalado". **Un segundo bug, pre-existente, salio a la luz y quedo arreglado:** las dependencias de pub se
  extraian con el prefijo `<paquete>-<version>/` que los tarballs de pub.dev **no** llevan, asi que se escribian 0
  ficheros y la instalacion moria con *"El paquete characters 1.4.1 no se extrajo bien"*; ahora reintenta sin prefijo.
  El dialogo de consentimiento ademas avisa del espacio real en disco (la descarga son ~307 MB, pero al extraerlo ocupa
  ~800 MB). Honesto: la **compilacion completa** (pub get + build) dentro de la app no se pudo automatizar en el
  emulador, asi que no se afirma; lo que **si** esta probado es que el binario empaquetado se ejecuta dentro del propio
  proceso de la app. **Nombres heredados de iconos:** `ic_tune_white` (y su familia) no existe en **ninguna** fuente de
  este IDE — se volcaron 2.382 drawables y el equivalente actual es `ic_tune_24`/`ic_mtrl_tune` —, asi que la vista
  previa ahora resuelve esos nombres viejos como **ultimo recurso**, solo despues de proyecto, assets, librerias e IDE,
  normalizando prefijos (`ic_`, `img_`, `icon_`) y sufijos de color/tamano; si hay coincidencia **unica** la usa y si
  hay varias lo considera ambiguo y sigue en rojo. Y **nada en silencio**: grupo propio en el dialogo de detalle
  (`Nombre heredado resuelto -> @drawable/ic_tune_white -> @drawable/ic_tune_24`), **barra ambar** informativa cuando
  no hay fallos reales, y log. Medido: el caso real pasa de **2 bloques rojos (2.080 px) a 1 (1.040 px)** y la barra
  dice `1 recurso no encontrado · 1 nombre heredado resuelto`; regresion intacta. Honesto: cubre los nombres cuyo
  concepto sigue existiendo (~19/44 de las familias probadas); typos, mipmaps y conceptos sin icono actual siguen en
  rojo. Pagina de la release: [v7.0.10.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.1).
  Todos los detalles: [docs/flutter-consent.md](docs/flutter-consent.md) (estado/instalacion del toolchain) y
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 5).
- **v7.0.10.0 (versionCode 167) — la descarga de Dart que no encontrabas, y la ronda 4 de la vista previa.** La queja
  era que el toolchain no aparecia por ningun lado, asi que el flag `FLUTTER_EXPERIMENTAL_ENABLE` ahora viene **activado
  por defecto** (quien ya lo haya cambiado conserva su valor), una **tarjeta "Flutter (Dart)"** en los ajustes del
  proyecto muestra el estado real (`Toolchain Flutter: no instalado (~307.2 MB)` o `instalado (Dart <v>) · <N MB>`) y
  abre el dialogo de estado/descarga/borrado, y el **drawer de la pantalla de diseno** tiene un item nuevo
  ("Flutter: estado del toolchain") que abre ese mismo dialogo. Si el flag esta apagado, las dos entradas siguen
  visibles y explican como activarlo, con un boton que abre Feature flags — nada desaparece en silencio. Verificado en
  un emulador arm64 API 34 con capturas y `uiautomator` y sin descargar nada; honesto: la rama "instalado" no llego a
  verse en pantalla (no habia toolchain y no se descargaron los 307 MB). **Ronda 4 de la vista previa:** el mensaje
  `Preview PARCIAL · vistas: 32 · no disponibles: @drawable/ic_tune_white` se leia como "32 vistas rotas" cuando las 32
  eran vistas **dibujadas**, asi que el aviso pasa a ser un resumen corto y agrupado
  (`Preview PARCIAL: N recursos no encontrados · M vistas no instanciables`) con **dialogo de detalle** al pulsar la
  barra (grupos por causa, recuento, motivo y donde se busco, tope de 12 por grupo) y el detalle completo en logcat; y
  se arregla un **fallo real**: la vista previa **no miraba los recursos de las librerias del proyecto** (AAR locales de
  `DependencyResolver`/`ManageLocalLibrary` y librerias integradas ya extraidas) — ahora si, medido con un fixture de
  libreria local: `@drawable/ic_tune_white` pasa de marcador rojo (2.076 px) a icono dibujado (6.174 px) y el caso
  pasa de PARCIAL a `Preview OK · vistas: 32`; las fuentes rotas ya no cuentan como vistas y la barra ya no queda
  tapada por la barra de navegacion; regresion bien (`colorPrimary`, fondos, estilos, imagenes/vectores/gif,
  `MaterialButton`+WebView, layout real). Aclaracion honesta: **`ic_tune_white` no existe en ninguna fuente de este
  IDE** (el APK trae `ic_tune_24`/`ic_mtrl_tune`, era un nombre heredado), asi que ese nombre concreto no se puede
  dibujar — y ahora se dice claro, sin marcar la vista entera. Pagina de la release:
  [v7.0.10.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.10.0). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md) (ronda 4) y [docs/flutter-consent.md](docs/flutter-consent.md).
- **v7.0.9.0 (versionCode 166) — ronda 3 de la vista previa, y la descarga de Dart la decides tu.** Los cuatro
  sintomas que reportaste eran **cuatro causas raiz distintas**, todas medidas pixel a pixel en un emulador arm64
  API 34. (1) **Color de texto**: el selector del editor guarda `"?" + attr` (p. ej. `?colorPrimary`) y la vista previa
  solo entendia `?attr/...`, asi que el texto caia al color por defecto del tema — medido `(68,71,79)` → `(68,94,145)`.
  (2) **Layouts / lineales**: el mismo fallo de resolucion, pero el respaldo era el marcador blanco opaco
  `0xFFFFFFFF` ("pendiente") del propio IDE, asi que el lineal se pintaba **blanco** — `(255,255,255)` → `(68,94,145)`.
  (3) **Estilos de texto**: solo se aplicaba negrita; ahora tambien cursiva, negrita+cursiva, monoespaciada y las
  fuentes TTF del proyecto (`@font/...`), y una fuente inexistente da aviso. (4) **Imagenes**: solo se buscaba
  `drawable/<n>.{xml,png,jpg}`; ahora tambien webp/jpeg/gif/bmp, carpetas de densidad y subcarpetas, `files/assets`,
  `app:srcCompat`, **vectores** (dibujados con el `PathParser` propio de la vista previa) y la imagen por defecto del
  IDE, y lo irresoluble muestra marcador rojo con el motivo. Encontrado y arreglado por el camino: los atributos
  internos se comparaban con el prefijo `android:` contra nombres locales, asi que **no coincidian nunca**
  (`fontFamily`, `srcCompat`, `alpha`, `gravity`, paddings…). Limites honestos: selector/ripple/layer-list se
  aproximan con su ultima forma, los vectores con transformaciones de grupo no se soportan, los `.9.png` van sin
  parches, los `?atributo` se resuelven con el tema del IDE (no el del proyecto) y nada esta probado en movil fisico.
  Y el toolchain de Dart (~**307,2 MB**, el tamano real) **ya no se descarga en silencio**: `ensureInstalled` — la que
  usan todos los caminos de build — ya no toca la red y solo deja el mensaje para instalarlo desde el menu; solo la
  variante nueva `allowDownload = true`, tras el consentimiento, descarga. Un dialogo Material muestra el estado, los
  componentes que faltan **con su tamano**, "Descarga necesaria: ~307.2 MB", el aviso de Wi-Fi, donde se guarda y que
  despues funciona sin conexion, con **Descargar ahora / Borrar toolchain (liberar X MB, con confirmacion) / Cancelar**;
  "Compilar y ejecutar" pide el mismo consentimiento y, al cancelar, aborta con un mensaje claro y sin escribir nada en
  disco. Pagina de la release: [v7.0.9.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.9.0).
  Todos los detalles: [docs/preview-fix.md](docs/preview-fix.md) (ronda 3) y
  [docs/flutter-consent.md](docs/flutter-consent.md).
- **v7.0.8.3 (versionCode 165) — tres arreglos mas en la vista previa, medidos pixel a pixel.** Ya se **resuelven
  los recursos de color del proyecto**: un fondo escrito como `@color/...` se guardaba como el marcador `0xFFFFFFFF`
  ("pendiente") del parser y la vista previa lo pintaba **blanco**; ahora se resuelven los `@color/...` y `?attr/...`
  del proyecto (verificado con azul puro, `(0,0,255)`). Se arregla el **cuelgue en arranque en frio**: abrir la vista
  previa sin pasar por el editor de diseno moria con un `NullPointerException` (`ColorsEditorManager` lee el
  `DesignActivity.sc_id` global, `null` en un proceso nuevo) y dejaba una pantalla muerta sin mensaje — ahora se fija
  el proyecto antes y un `try/catch` muestra el error en pantalla. Y la vista previa ahora **honra el extra `xml`**,
  asi que abrirla desde el editor de XML de vistas muestra exactamente lo que estas editando, sin guardar. Verificado
  pixel a pixel en un emulador arm64 API 34, en claro y en oscuro: raiz magenta `(255,0,255)`, `MaterialButton`
  visible y texto por defecto de `TextView`/`Button` legible en ambos temas — `(68,71,79)` sobre `(250,249,253)` en
  claro, `(196,198,208)` sobre `(18,19,22)` en oscuro. Pagina de la release:
  [v7.0.8.3](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.3). Todos los detalles:
  [docs/preview-fix.md](docs/preview-fix.md).
- **v7.0.8.2 (versionCode 164) — la vista previa de diseños vuelve a pintar.** La vista previa de los diseños hechos
  con elementos View (la de HTML/WebView siempre ha funcionado) mostraba la zona del diseño vacía — en blanco o en negro
  según el tema — y solo se veían los widgets que se pintan solos (SeekBar, Switch, iconos) y el HTML del WebView. La
  causa era un **centinela**: el modelo de datos del propio IDE guarda `0xffffff` como "sin color elegido"
  (`TextBean.textColor`/`hintColor`, `LayoutBean.backgroundColor`), y `LayoutPreviewActivity` lo aplicaba como color
  real — como `int`, `0xffffff` es `0x00FFFFFF` (alfa 0), así que dejaba el texto totalmente transparente y borraba el
  fondo que el tema del widget habría pintado. `ViewPane` lo agravaba forzando un lienzo **blanco** en modo vista previa
  mientras los widgets se inflaban con el tema del IDE: de ahí el contraste roto (blanco/negro) según el tema. Ahora
  `0xffffff` (y cualquier color con alfa 0) significa "sin definir" y decide el tema, el lienzo de la vista previa usa el
  `colorSurface` del mismo tema que infla las vistas, la raíz inflada mantiene las dimensiones declaradas en el XML y una
  vista que el IDE no puede instanciar muestra un **marcador rojo** y un aviso de "preview parcial" en vez de un hueco
  mudo. Verificado en un emulador arm64 API 34: el mismo diseño, en claro y en oscuro, ya pinta el botón (su zona era
  255,255,255 con 0 glifos antes) y la vista previa con HTML/WebView sigue funcionando
  (`Preview OK · vistas: 3 · WebViews: 1`). Dicho con honestidad: los componentes Material, los proyectos con Material3 y
  los temas personalizados del proyecto **no** están verificados en pantalla, y nada se ha probado en móvil físico.
  Página de la release: [v7.0.8.2](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.2). Todos los
  detalles: [docs/preview-fix.md](docs/preview-fix.md).
- **v7.0.8.1 (versionCode 163) — hotfix de la release minificada.** R8 (el minificado de release) rompía la
  compilación de **cualquier** proyecto dentro de la app: renombraba los campos de `javax.lang.model.SourceVersion`,
  luego la tabla `Messages` de ECJ y por último borraba las clases de `apksig` que firman el APK — tres fallos
  encadenados, todos alcanzados por reflexión que R8 no puede ver. Se arregla con tres reglas `keep` en
  `app/proguard-rules.pro`; verificado en un emulador arm64 API 34 (ECJ → dx → empaquetado → APK firmado V3.0,
  `ExceptionInInitializerError` 0 veces en logcat). Página de la release:
  [v7.0.8.1](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.1).
- Version **v7.0.8.0** (versionCode 162), pagina de la release:
  [v7.0.8.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.8.0). Todos los detalles, los
  comandos, los numeros medidos y lo que queda pendiente: [docs/flutter-fase8.md](docs/flutter-fase8.md).

### 2026-09-23 — soporte de Flutter, experimental: ficheros Dart y compilacion en el dispositivo (fase 7)

- **El editor ya entiende Dart.** Los ficheros `.dart` reciben la gramatica TextMate de Dart (del proyecto
  Dart-Code, licencia MIT), su configuracion de lenguaje, el emparejado de llaves y el **autocompletado de Dart**,
  ademas de un icono de Flutter en los menus.
- **Un tipo de proyecto: Flutter.** Detras del flag `FLUTTER_EXPERIMENTAL_ENABLE` — **apagado por defecto**, en
  Ajustes › Feature flags — al guardar el proyecto se siembra `files/flutter/` con `pubspec.yaml`, un `lib/main.dart`
  Material 3 con contador, `assets/`, `.gitignore` y `android/`. El editor muestra entonces un **menu Flutter**:
  compilar y ejecutar, estado del toolchain e informacion del proyecto.
- **El toolchain se descarga al propio movil.** El Dart SDK para Android (paquete `dart 3.13.4` de Termux, un `.deb`
  que se abre con el codigo ar/XZ/tar propio de la app) y los artefactos del engine de Flutter 3.47.5. Todo se
  ejecuta en el dispositivo; la unica red que se usa es esa primera descarga.
- **Limitacion honesta: el release/AOT en el dispositivo esta bloqueado.** El `gen_snapshot` del SDK de Dart esta
  compilado **sin compressed pointers** y el engine oficial los exige (`Snapshot not compatible ... the snapshot
  requires 'arm64 android no-compressed-pointers' but the VM has '... compressed-pointers'`), y un `gen_snapshot`
  compatible solo se publica para hosts linux-x64/darwin-x64/windows-x64 (404 para arm64/android). **Hoy el unico
  modo usable es debug/JIT**, y la interfaz lo dice.
- **Probado en un dispositivo real.** En un emulador Android 14 arm64 el Dart del propio telefono compilo el kernel
  (`kernel_blob.bin`, 4,2 s) y un APK armado a mano (`aapt2` + `d8` + `zipalign` + `apksigner`) **arranco**:
  FlutterActivity en RESUMED, UI Material renderizada y el contador respondiendo a toques reales. **No** se ha
  probado en movil fisico, ni con ABIs distintas de `arm64-v8a`, ni con hot reload, ni con resolucion real de pub.
- Version **v7.0.7.0** (versionCode 161), pagina de la release:
  [v7.0.7.0](https://github.com/aurenox-global/Sketchware-Pro/releases/tag/v7.0.7.0). Todos los detalles, los numeros
  medidos, los comandos y lo que queda pendiente: [docs/flutter-fase7.md](docs/flutter-fase7.md).

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
| Soporte de Flutter, experimental (editor Dart y compilacion en el dispositivo; solo debug/JIT) | experimental |

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

Las contribuciones son bienvenidas, pero **toda contribución externa tiene que ser aprobada por el
mantenedor antes de fusionarse**:

1. Haz un fork de este repositorio.
2. Haz tus cambios.
3. Pruébalos.
4. Abre un pull request.

`main` es una rama protegida: no se permite empujar directamente y un pull request necesita la
revisión del mantenedor (**1 aprobación**) antes de poder fusionarse. Abrir un pull request no da
derecho a que se fusione — decide el mantenedor, y puede cerrarlo sin fusionarlo. Ver
[CONTRIBUTING.md](CONTRIBUTING.md).

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
