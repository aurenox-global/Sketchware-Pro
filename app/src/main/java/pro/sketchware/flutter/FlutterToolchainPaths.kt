package pro.sketchware.flutter

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Rutas y constantes del toolchain Flutter/Dart que se instala **en el propio dispositivo**
 * (nunca se descarga un SDK de host: en Android no existe `gen_snapshot` host).
 *
 * Contrato Fase 7 (carril C). Todo cuelga de `<context.filesDir>/flutter-toolchain`, que es
 * el único sitio con permiso de escritura garantizado sin permisos de almacenamiento.
 *
 * Evidencia (spike, ver `flutter-contrato-fase7.md`):
 * - `dart 3.13.4` de Termux (ELF Bionic, `interpreter /system/bin/linker64`) trae
 *   `bin/dart`, `bin/dartvm`, `bin/dartaotruntime`, `bin/utils/gen_snapshot` y los snapshots
 *   AOT de `gen_kernel`/`frontend_server`.
 * - El engine de Flutter 3.47.5 es `af7e796e161ae0bb1ff0758c71a7105418bd9ded` y comparte
 *   exactamente la misma revisión de Dart (mismo timestamp de build) que el paquete de Termux.
 */
object FlutterToolchainPaths {

    /** Versión del paquete `dart` de Termux. */
    const val DART_VERSION = "3.13.4"

    /** Versión de Flutter cuyo engine se empaqueta. */
    const val FLUTTER_VERSION = "3.47.5"

    /** Hash del engine de Flutter 3.47.5 (bin/internal/engine.version). */
    const val ENGINE_VERSION = "af7e796e161ae0bb1ff0758c71a7105418bd9ded"

    /** Versión de release de los artefactos del engine (`1.0.0-<engine>`). */
    const val ENGINE_RELEASE_VERSION = "1.0.0-$ENGINE_VERSION"

    const val ABI_ARM64_V8A = "arm64-v8a"
    const val ABI_X86_64 = "x86_64"

    /* -------------------------------------------------------------------------------------- */
    /* Ejecutables empaquetados en jniLibs (carril I, Fase 8)                                   */
    /* -------------------------------------------------------------------------------------- */

    /**
     * **Por que los ejecutables van en `jniLibs`**: con `targetSdk >= 29` SELinux deniega
     * `execute_no_trans` sobre `app_data_file` (todo lo que la app escribe en `filesDir`), asi que
     * un ELF extraido del `.deb` **no se puede ejecutar** desde ahi. Solo `nativeLibraryDir`
     * (`/data/app/…/lib/<abi>/`, etiqueta `apk_data_file`) es ejecutable para la app.
     *
     * Evidencia (emulador arm64 API 34, informe AOT §6.1): un binario empaquetado como
     * `lib/<abi>/libgensnapshot.so` se ejecuta con `exit=0` desde la propia app (uid de app,
     * dominio `untrusted_app`); la misma copia en `filesDir` da `error=13, Permission denied`.
     */
    const val PACKAGED_DART_AOT_RUNTIME = "libdartaotruntime.so"

    /**
     * Nuestro `gen_snapshot`, build `--arch arm64c --mode product` en arm64-v8a y
     * `--arch x64c --mode product` en x86_64 (compressed pointers en las dos): es **el unico**
     * `gen_snapshot` que produce un `libapp.so` que el engine release oficial acepta (informe AOT §2).
     */
    const val PACKAGED_GEN_SNAPSHOT = "libfluttergensnapshot.so"

    /** `bin/dartaotruntime` del `dart_3.13.4_aarch64.deb`: 5.684.384 B (ELF aarch64, stripped). */
    const val PACKAGED_DART_AOT_RUNTIME_SIZE = 5_684_384L
    const val PACKAGED_DART_AOT_RUNTIME_SHA256 =
        "e2ea1775e28bc92ce738c5df3b4ac4f95103ee2b13aefee87d90972dad3d1eb1"

    /** `gen_snapshot_arm64c_android_product`: 4.991.592 B sha256 `9921983f…` (informe AOT §7.1). */
    const val PACKAGED_GEN_SNAPSHOT_SIZE = 4_991_592L
    const val PACKAGED_GEN_SNAPSHOT_SHA256 =
        "9921983f8765fe10e45e2010b73c6599d6a3ed13ea564f96190ba00587354233"

    /* --- x86_64 (mismo trato que arm64-v8a: sus dos ejecutables van empaquetados) ----------- */

    /**
     * `lib/dart-sdk/bin/dartaotruntime` del `dart_3.13.4_x86_64.deb`: 5.873.176 B
     * (ELF x86-64, `/system/bin/linker64`, stripped, mismo build 3.13.4 que el de arm64).
     *
     * OJO con la ruta: en las **dos** arquitecturas el `bin/dartaotruntime` que aparece en
     * `usr/bin/` es un script de shell de 115 B (`exec .../lib/dart-sdk/bin/dartaotruntime "$@"`),
     * no un ELF. El que se empaqueta es el ELF de `lib/dart-sdk/bin/`.
     */
    const val PACKAGED_DART_AOT_RUNTIME_X86_64_SIZE = 5_873_176L
    const val PACKAGED_DART_AOT_RUNTIME_X86_64_SHA256 =
        "f23e06adf40d2cd451c856cb270f7aff439ef5d3e0c007b216191b37a2eff97f"

    /**
     * `gen_snapshot_x64c_android_product`: nuestro `gen_snapshot` para Android x86_64, build
     * `--arch x64c --mode product --os android` (compressed pointers, igual que el `arm64c`).
     *
     * Evidencia de que Android x64 **tambien** usa compressed pointers: el `gen_snapshot` oficial del
     * engine para `android-x64-release/darwin-x64.zip` produce un snapshot que declara
     * `product no-asan no-msan no-tsan no-shared_data no-code_comments no-dwarf_stack_traces x64 android compressed-pointers`
     * (el de `android-arm64` declara lo mismo con `arm64`), y el VM lo exige al cargar.
     */
    const val PACKAGED_GEN_SNAPSHOT_X86_64_SIZE = 5_123_768L
    const val PACKAGED_GEN_SNAPSHOT_X86_64_SHA256 =
        "2f37d68799dd91b8d6458bbada3dfc53e22d9f77b35500632431bb6d2b705678"

    private const val TERMUX_DART_POOL =
        "https://packages.termux.dev/apt/termux-main/pool/main/d/dart/"

    private const val DOWNLOAD_FLUTTER_IO = "https://storage.googleapis.com/download.flutter.io"

    private const val FLUTTER_INFRA_RELEASE =
        "https://storage.googleapis.com/flutter_infra_release/flutter"

    /** `dart_3.13.4_aarch64.deb` (96.035.948 B). */
    const val DART_DEB_ARM64_SIZE = 96_035_948L
    const val DART_DEB_ARM64_SHA256 =
        "29d4a6d716518cee9dc4cf5ef12d098ed397c6e3f2db87c3ac6b0c05d8799146"

    /** `dart_3.13.4_x86_64.deb` (137.243.692 B). */
    const val DART_DEB_X86_64_SIZE = 137_243_692L
    const val DART_DEB_X86_64_SHA256 =
        "8125cade40c62d60006fcb9f52d46c9b3ee50f97d2377cb9f813a4168a44e974"

    /** Tamaños verificados con `curl -sI` (HTTP 200, `content-length`), 2026-09-23. */
    const val EMBEDDING_JAR_SIZE = 1_584_072L
    const val NATIVE_JAR_ARM64_SIZE = 42_168_248L
    const val NATIVE_JAR_X86_64_SIZE = 42_484_435L
    const val PATCHED_SDK_ZIP_SIZE = 4_108_245L
    const val ANDROID_ARTIFACTS_ZIP_SIZE = 41_281_693L

    /**
     * Variantes **debug** del engine — imprescindibles para el modo [FlutterBuildMode.DEBUG_JIT].
     *
     * Por que no valen las release: el `BuildConfig` que va dentro del jar del embedding decide el
     * camino de carga. `FlutterLoader.java` hace `if (BuildConfig.DEBUG || BuildConfig.JIT_RELEASE)`
     * -> extrae y usa `kernel_blob.bin` (JIT); **else** -> `--aot-shared-library-name=libapp.so`.
     * Con el jar release, un `kernel_blob.bin` no se usa nunca y el engine release no lo acepta.
     *
     * Tamaños verificados con `curl -sI` (HTTP 200, `content-length`) el 2026-09-23.
     */
    const val EMBEDDING_JAR_DEBUG_SIZE = 1_589_924L
    const val NATIVE_JAR_ARM64_DEBUG_SIZE = 180_591_438L
    const val NATIVE_JAR_X86_64_DEBUG_SIZE = 119_914_309L

    /**
     * `android-arm64/artifacts.zip` del engine **debug**: 179.695.162 B (HTTP 200).
     *
     * OJO con la ruta: el bucket **no** usa sufijo `-debug` para el engine debug (`android-arm64/`
     * a secas); `android-arm64-debug/artifacts.zip` da **404**. El release si lleva sufijo
     * (`android-arm64-release/`). Comprobado con `curl -sI` el 2026-09-23.
     */
    const val ANDROID_ARTIFACTS_ZIP_DEBUG_SIZE = 179_695_162L
    const val ANDROID_ARTIFACTS_ZIP_X86_64_DEBUG_SIZE = 119_677_140L
    const val ANDROID_ARTIFACTS_ZIP_X86_64_SIZE = 41_546_114L

    /** `flutter_patched_sdk_product.zip` (pendiente de probar; mismo uso que el no-product). */
    const val PATCHED_SDK_PRODUCT_ZIP_SIZE = 4_108_252L

    /**
     * Tamaño del tarball del framework (`flutter-3.47.5.tar.gz`).
     *
     * A diferencia del resto de artefactos, GitHub **no** publica `content-length` para este fichero
     * (se sirve en *chunked*, ver [frameworkSourceTarball]), así que el descargador no puede
     * verificarlo. Este valor **no** se usa para la descarga: es la medida real del fichero
     * (`curl -sL -o /tmp/flutter-3475.tar.gz …` -> 34.580.315 B el 2026-09-23) y se usa solo para
     * calcular el espacio aproximado que ocupa / falta, que es lo que muestra el dialogo de
     * consentimiento antes de descargar.
     */
    const val FRAMEWORK_TARBALL_ESTIMATED_SIZE = 34_580_315L

    /** `languageVersion` que declara `packages/flutter/pubspec.yaml` (dato de la prueba E2E). */
    const val FLUTTER_FRAMEWORK_LANGUAGE_VERSION = "3.11"

    /** `languageVersion` de reserva si el `pubspec.yaml` del proyecto no declara `environment.sdk`. */
    const val DEFAULT_DART_LANGUAGE_VERSION = "3.5"

    private const val PUB_DEV_ARCHIVES = "https://pub.dev/api/archives/"

    private const val FLUTTER_FRAMEWORK_TARBALL =
        "https://github.com/flutter/flutter/archive/refs/tags/"

    /** Prefijo de directorio que trae el tarball del tag en GitHub (`flutter-3.47.5/`). */
    const val FLUTTER_TARBALL_ROOT_PREFIX = "flutter-$FLUTTER_VERSION/"

    /**
     * Dependencia de `pub.dev` que el framework necesita para compilar.
     *
     * Las versiones y los `languageVersion` salen del pubspec del framework 3.47.5 tal y como los
     * resolvio la prueba E2E (§3 del informe); no se resuelven en el dispositivo (no hay `dart pub`).
     */
    class PubDependencySpec(
        val name: String,
        val version: String,
        val sizeBytes: Long,
        val languageVersion: String,
    ) {
        /** `https://pub.dev/api/archives/<name>-<version>.tar.gz` (HTTP 200, content-length). */
        val url: String = "$PUB_DEV_ARCHIVES$name-$version.tar.gz"

        /** Directorio raiz dentro del tarball y carpeta destino (`<name>-<version>`). */
        val directoryName: String = "$name-$version"
    }

    /** Especificación de un `.deb` descargable. */
    class DartPackageSpec(
        @JvmField val abi: String,
        @JvmField val fileName: String,
        @JvmField val url: String,
        @JvmField val sizeBytes: Long,
        @JvmField val sha256: String,
    )

    /** Especificación de un artefacto remoto del engine. */
    class RemoteArtifact(
        @JvmField val url: String,
        @JvmField val sizeBytes: Long,
    )

    /**
     * Devuelve la ABI soportada del dispositivo, o `null` si ninguna lo está.
     * Se consulta [Build.SUPPORTED_ABIS] para respetar el orden de preferencia del sistema.
     */
    @JvmStatic
    fun resolveSupportedAbi(): String? {
        val supported = Build.SUPPORTED_ABIS ?: return null
        for (abi in supported) {
            if (abi == ABI_ARM64_V8A || abi == ABI_X86_64) {
                return abi
            }
        }
        return null
    }

    /** `.deb` correspondiente a [abi] (`arm64-v8a` -> `aarch64`, `x86_64` -> `x86_64`). */
    @JvmStatic
    fun dartPackageSpec(abi: String?): DartPackageSpec? {
        return when (abi) {
            ABI_ARM64_V8A -> DartPackageSpec(
                ABI_ARM64_V8A,
                "dart_${DART_VERSION}_aarch64.deb",
                TERMUX_DART_POOL + "dart_${DART_VERSION}_aarch64.deb",
                DART_DEB_ARM64_SIZE,
                DART_DEB_ARM64_SHA256,
            )
            ABI_X86_64 -> DartPackageSpec(
                ABI_X86_64,
                "dart_${DART_VERSION}_x86_64.deb",
                TERMUX_DART_POOL + "dart_${DART_VERSION}_x86_64.deb",
                DART_DEB_X86_64_SIZE,
                DART_DEB_X86_64_SHA256,
            )
            else -> null
        }
    }

    /**
     * Nombre del artefacto Maven de las nativas (`io.flutter:<artefacto>:<version>`) **por modo**:
     * `DEBUG_JIT` -> `<arch>_debug` (engine debug), `RELEASE_AOT` -> `<arch>_release`.
     */
    @JvmStatic
    fun nativeArtifactName(abi: String?, mode: FlutterBuildMode): String? {
        val arch = when (abi) {
            ABI_ARM64_V8A -> "arm64_v8a"
            ABI_X86_64 -> "x86_64"
            else -> return null
        }
        return when (mode) {
            FlutterBuildMode.DEBUG_JIT -> "${arch}_debug"
            FlutterBuildMode.RELEASE_AOT -> "${arch}_release"
        }
    }

    /** Variante release (compatibilidad con el contrato previo, que solo conocia release). */
    @JvmStatic
    fun nativeArtifactName(abi: String?): String? = nativeArtifactName(abi, FlutterBuildMode.RELEASE_AOT)

    /** Jar del embedding para [mode]; el debug es el que habilita el camino JIT en `FlutterLoader`. */
    @JvmStatic
    fun embeddingJarArtifact(mode: FlutterBuildMode): RemoteArtifact {
        val name = embeddingArtifactName(mode)
        val size = if (mode == FlutterBuildMode.DEBUG_JIT) EMBEDDING_JAR_DEBUG_SIZE else EMBEDDING_JAR_SIZE
        return RemoteArtifact(
            "$DOWNLOAD_FLUTTER_IO/io/flutter/$name/$ENGINE_RELEASE_VERSION/${name}-$ENGINE_RELEASE_VERSION.jar",
            size,
        )
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun embeddingJarArtifact(): RemoteArtifact = embeddingJarArtifact(FlutterBuildMode.RELEASE_AOT)

    @JvmStatic
    fun nativeJarArtifact(abi: String?, mode: FlutterBuildMode): RemoteArtifact? {
        val name = nativeArtifactName(abi, mode) ?: return null
        val size = when (mode) {
            FlutterBuildMode.DEBUG_JIT -> when (abi) {
                ABI_ARM64_V8A -> NATIVE_JAR_ARM64_DEBUG_SIZE
                ABI_X86_64 -> NATIVE_JAR_X86_64_DEBUG_SIZE
                else -> return null
            }
            FlutterBuildMode.RELEASE_AOT -> when (abi) {
                ABI_ARM64_V8A -> NATIVE_JAR_ARM64_SIZE
                ABI_X86_64 -> NATIVE_JAR_X86_64_SIZE
                else -> return null
            }
        }
        return RemoteArtifact(
            "$DOWNLOAD_FLUTTER_IO/io/flutter/$name/$ENGINE_RELEASE_VERSION/$name-$ENGINE_RELEASE_VERSION.jar",
            size,
        )
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun nativeJarArtifact(abi: String?): RemoteArtifact? = nativeJarArtifact(abi, FlutterBuildMode.RELEASE_AOT)

    /**
     * `flutter_patched_sdk.zip`: el `platform_strong.dill` que `gen_kernel` consume como
     * `--platform`. Es el mismo para los dos modos (la diferencia release/product esta en las flags
     * de `gen_kernel`; el camino `_product` todavia no se ha probado).
     */
    @JvmStatic
    fun patchedSdkArtifact(): RemoteArtifact {
        return RemoteArtifact(
            "$FLUTTER_INFRA_RELEASE/$ENGINE_VERSION/flutter_patched_sdk.zip",
            PATCHED_SDK_ZIP_SIZE,
        )
    }

    /** *Fallback* documentado cuando el jar de nativas no trae `libflutter.so`. */
    @JvmStatic
    fun androidArtifactsArtifact(abi: String?, mode: FlutterBuildMode): RemoteArtifact? {
        val abiName = when (abi) {
            ABI_ARM64_V8A -> "android-arm64"
            ABI_X86_64 -> "android-x64"
            else -> return null
        }
        // El engine debug vive SIN sufijo (`android-arm64/artifacts.zip`); el release lo lleva.
        val suffix = if (mode == FlutterBuildMode.DEBUG_JIT) "" else "-release"
        val size = when {
            abi == ABI_ARM64_V8A && mode == FlutterBuildMode.DEBUG_JIT -> ANDROID_ARTIFACTS_ZIP_DEBUG_SIZE
            abi == ABI_X86_64 && mode == FlutterBuildMode.DEBUG_JIT -> ANDROID_ARTIFACTS_ZIP_X86_64_DEBUG_SIZE
            abi == ABI_ARM64_V8A -> ANDROID_ARTIFACTS_ZIP_SIZE
            else -> ANDROID_ARTIFACTS_ZIP_X86_64_SIZE
        }
        return RemoteArtifact(
            "$FLUTTER_INFRA_RELEASE/$ENGINE_VERSION/$abiName$suffix/artifacts.zip",
            size,
        )
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun androidArtifactsArtifact(abi: String?): RemoteArtifact? =
        androidArtifactsArtifact(abi, FlutterBuildMode.RELEASE_AOT)

    /**
     * Tarball con las **fuentes del framework Dart** de Flutter 3.47.5 (`packages/flutter/lib`).
     *
     * Ni el jar del embedding ni `flutter_patched_sdk.zip` incluyen `package:flutter…`: es el
     * fallo que el carril C documentaba como pendiente y que se resolvio en la prueba E2E (§3).
     *
     * `sizeBytes = 0`: GitHub sirve el tarball en *chunked* (redirige a `codeload`) y **no** publica
     * `content-length` (`curl -sIL` -> `content-length: 0`), asi que no se puede verificar el tamano
     * ni hardcodearlo. El descargador interpreta `0` como "sin verificacion de tamano": este es el
     * unico artefacto del toolchain que se acepta sin comprobacion de integridad.
     */
    @JvmStatic
    fun frameworkSourceTarball(): RemoteArtifact = RemoteArtifact(
        "$FLUTTER_FRAMEWORK_TARBALL$FLUTTER_VERSION.tar.gz",
        0L,
    )

    /**
     * Dependencias de pub que necesita `packages/flutter/pubspec.yaml`, con las versiones exactas
     * que resolvio la prueba E2E (§3 del informe).
     *
     * NO se incluyen `sky_engine` (su `dart:ui` viaja dentro de `platform_strong.dill`),
     * `stack_trace` ni `web` (en el framework solo aparecen en comentarios/docs).
     */
    @JvmStatic
    fun frameworkPubDependencies(): List<PubDependencySpec> = listOf(
        PubDependencySpec("characters", "1.4.1", 528_692L, "3.4"),
        PubDependencySpec("collection", "1.19.1", 77_897L, "3.4"),
        PubDependencySpec("material_color_utilities", "0.13.0", 2_115_434L, "3.5"),
        PubDependencySpec("meta", "1.18.3", 16_874L, "3.5"),
        PubDependencySpec("vector_math", "2.4.0", 177_124L, "3.10"),
    )

    /** Nombre del jar del embedding segun el modo. */
    private fun embeddingArtifactName(mode: FlutterBuildMode): String = when (mode) {
        FlutterBuildMode.DEBUG_JIT -> "flutter_embedding_debug"
        FlutterBuildMode.RELEASE_AOT -> "flutter_embedding_release"
    }

    /* -------------------------------------------------------------------------------------- */
    /* Rutas locales                                                                            */
    /* -------------------------------------------------------------------------------------- */

    /** `<context.filesDir>/flutter-toolchain`. */
    @JvmStatic
    fun toolchainDir(context: Context): File = File(context.filesDir, "flutter-toolchain")

    /** `flutter-toolchain/download` (caché de `.deb`/zips descargados). */
    @JvmStatic
    fun downloadDir(context: Context): File = File(toolchainDir(context), "download")

    @JvmStatic
    fun dartDir(context: Context): File = File(toolchainDir(context), "dart")

    @JvmStatic
    fun dartBinDir(context: Context): File = File(dartDir(context), "bin")

    /**
     * `<filesDir>/flutter-toolchain/dart/bin/dart`: **CLI del `.deb`**, solo para diagnostico.
     *
     * NO se puede ejecutar desde aqui con `targetSdk >= 29` (SELinux/W^X sobre `app_data_file`) y la
     * app **no lo usa**: los snapshots AOT de `gen_kernel` y `dart pub` se lanzan con
     * [dartAotRuntimeExecutable] (empaquetado en `nativeLibraryDir`). Ver
     * [FlutterToolchainInstaller.probeDartRuntime].
     */
    @JvmStatic
    fun dartExecutable(context: Context): File = File(dartBinDir(context), "dart")

    /** `bin/dartvm` del `.deb`: dato de diagnostico, **no** ejecutable desde `filesDir` (W^X). */
    @JvmStatic
    fun dartVmExecutable(context: Context): File = File(dartBinDir(context), "dartvm")

    /** `dartaotruntime`: lanza los snapshots AOT (`gen_kernel_aot`, `frontend_server_aot`). */
    @JvmStatic
    fun dartAotRuntime(context: Context): File = File(dartBinDir(context), "dartaotruntime")

    /**
     * Copia **de datos** de `bin/utils/gen_snapshot` dentro de `filesDir` (NO ejecutable por
     * SELinux; se conserva porque el `.deb` la trae y sirve de diagnostico/compatibilidad). Para
     * ejecutar AOT se usa [genSnapshotExecutable].
     */
    @JvmStatic
    fun genSnapshot(context: Context): File = File(File(dartBinDir(context), "utils"), "gen_snapshot")

    /* -------------------------------------------------------------------------------------- */
    /* Ejecutables: nativeLibraryDir primero, filesDir como respaldo                            */
    /* -------------------------------------------------------------------------------------- */

    /**
     * `context.applicationInfo.nativeLibraryDir` (p. ej. `/data/app/~~xxx/pkg-1/lib/arm64`).
     *
     * Es el **unico** directorio desde el que la app puede `execve()` (ver
     * [PACKAGED_DART_AOT_RUNTIME]). Devuelve `null` si el sistema no lo publica.
     */
    @JvmStatic
    fun nativeLibraryDir(context: Context): File? {
        val path = context.applicationInfo?.nativeLibraryDir
        return if (path.isNullOrEmpty()) null else File(path)
    }

    /**
     * Ejecutable empaquetado en `nativeLibraryDir` (`lib<algo>.so` en `jniLibs/<abi>/`), o `null`.
     *
     * Solo las ABIs con backend ([abiHasAotBackend]: [ABI_ARM64_V8A] y [ABI_X86_64]) llevan los
     * ejecutables empaquetados; los APK por ABI generados por `splits.abi` para `armeabi-v7a` y
     * `x86` **no** los incluyen, y ahi el respaldo es la copia de `filesDir` (que en
     * `targetSdk >= 29` no se puede ejecutar: se documenta y se detecta en
     * [FlutterToolchainManager.isReady]).
     */
    @JvmStatic
    fun packagedExecutable(context: Context, name: String): File? {
        val dir = nativeLibraryDir(context) ?: return null
        val file = File(dir, name)
        return if (file.isFile) file else null
    }

    /** `nativeLibraryDir/libdartaotruntime.so` si esta empaquetado, y si no el de `filesDir`. */
    @JvmStatic
    fun dartAotRuntimeExecutable(context: Context): File =
        packagedExecutable(context, PACKAGED_DART_AOT_RUNTIME) ?: dartAotRuntime(context)

    /** `nativeLibraryDir/libfluttergensnapshot.so` si esta empaquetado, y si no el de `filesDir`. */
    @JvmStatic
    fun genSnapshotExecutable(context: Context): File =
        packagedExecutable(context, PACKAGED_GEN_SNAPSHOT) ?: genSnapshot(context)

    /** `true` si el APK que se esta ejecutando trae el `dartaotruntime` en `nativeLibraryDir`. */
    @JvmStatic
    fun isDartAotRuntimePackaged(context: Context): Boolean =
        packagedExecutable(context, PACKAGED_DART_AOT_RUNTIME) != null

    /** `true` si el APK que se esta ejecutando trae nuestro `gen_snapshot` en `nativeLibraryDir`. */
    @JvmStatic
    fun isGenSnapshotPackaged(context: Context): Boolean =
        packagedExecutable(context, PACKAGED_GEN_SNAPSHOT) != null

    /**
     * ABIs cuya variante del APK empaqueta **los dos** ejecutables del toolchain
     * (`libdartaotruntime.so` + `libfluttergensnapshot.so`) en `jniLibs/<abi>/`.
     *
     * Cada una lleva su backend AOT compilado a medida, porque el snapshot tiene que declarar la
     * misma configuracion que el VM del engine:
     * - `arm64-v8a` -> `gen_snapshot --arch arm64c --mode product` (`arm64 android compressed-pointers`);
     * - `x86_64`    -> `gen_snapshot --arch x64c --mode product` (`x64 android compressed-pointers`).
     *
     * `armeabi-v7a` y `x86` no lo llevan: no hay backend AOT on-device para ellas.
     */
    @JvmStatic
    fun abiHasAotBackend(abi: String?): Boolean = abi == ABI_ARM64_V8A || abi == ABI_X86_64

    /**
     * Motivo por el que el backend AOT **no** esta disponible, o `null` si lo esta.
     *
     * Solo las variantes del APK de las ABIs con backend ([abiHasAotBackend]: `arm64-v8a` y
     * `x86_64`) empaquetan `libfluttergensnapshot.so`; en `armeabi-v7a`/`x86` el AOT on-device no se
     * puede hacer (no existe `gen_snapshot` oficial *para Android* y el del `dart` de Termux produce
     * snapshots sin compressed pointers, incompatibles con el engine).
     */
    @JvmStatic
    fun aotBackendUnavailableReason(context: Context): String? {
        if (isGenSnapshotPackaged(context)) {
            return null
        }
        val abi = resolveSupportedAbi() ?: deviceAbiName()
        if (!abiHasAotBackend(abi)) {
            return "El AOT on-device necesita un `gen_snapshot` propio empaquetado como " +
                "`lib/<abi>/$PACKAGED_GEN_SNAPSHOT`, y solo las variantes arm64-v8a y x86_64 del APK " +
                "lo traen (esta instalado un APK de la ABI '$abi'). Compila/instala la variante " +
                "arm64-v8a o x86_64, o usa el modo DEBUG_JIT."
        }
        return "Falta `$PACKAGED_GEN_SNAPSHOT` en ${nativeLibraryDir(context)?.absolutePath ?: "nativeLibraryDir"} " +
            "(no se empaqueto en `app/src/main/jniLibs/$abi/`). Sin ese binario no se puede " +
            "generar un `libapp.so` compatible con el engine release: usa el modo DEBUG_JIT."
    }

    /** Nombre de la ABI tal y como la reporta el sistema (`arm64-v8a`, `x86_64`…) o `desconocida`. */
    @JvmStatic
    fun deviceAbiName(): String {
        val supported = Build.SUPPORTED_ABIS
        return if (supported.isNullOrEmpty()) "desconocida" else supported[0]
    }

    /** `bin/snapshots/gen_kernel_aot.dart.snapshot`. */
    @JvmStatic
    fun genKernelSnapshot(context: Context): File =
        File(File(dartBinDir(context), "snapshots"), "gen_kernel_aot.dart.snapshot")

    /** `bin/snapshots/frontend_server_aot.dart.snapshot`. */
    @JvmStatic
    fun frontendServerSnapshot(context: Context): File =
        File(File(dartBinDir(context), "snapshots"), "frontend_server_aot.dart.snapshot")

    /**
     * `lib/_internal/vm_platform.dill`.
     *
     * NOTA: el SDK trae `vm_platform_strong.dill` como **symlink absoluto** al prefijo de
     * Termux (`/data/data/com.termux/files/usr/...`). Ese symlink se rompe en `filesDir`,
     * por eso el instalador extrae `vm_platform.dill` y crea `vm_platform_strong.dill` como
     * **copia real**.
     */
    @JvmStatic
    fun vmPlatformDill(context: Context): File =
        File(File(dartDir(context), "lib/_internal"), "vm_platform.dill")

    @JvmStatic
    fun vmPlatformStrongDill(context: Context): File =
        File(File(dartDir(context), "lib/_internal"), "vm_platform_strong.dill")

    /** Marcador `<dart>/installed.properties` (version, abi, sha256 verificados). */
    @JvmStatic
    fun dartMarkerFile(context: Context): File = File(dartDir(context), "installed.properties")

    @JvmStatic
    fun engineDir(context: Context): File = File(toolchainDir(context), "engine")

    /**
     * Jar del embedding cacheado **por modo** (`flutter_embedding_debug.jar` /
     * `flutter_embedding_release.jar`). Tenerlos separados evita que al cambiar de modo se reutilice
     * el `BuildConfig` del modo equivocado (que es lo que rompe el arranque JIT).
     */
    @JvmStatic
    fun embeddingJar(context: Context, mode: FlutterBuildMode): File {
        val name = if (mode == FlutterBuildMode.DEBUG_JIT) {
            "flutter_embedding_debug"
        } else {
            "flutter_embedding_release"
        }
        return File(engineDir(context), "$name.jar")
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun embeddingJar(context: Context): File = embeddingJar(context, FlutterBuildMode.RELEASE_AOT)

    /** Jar Maven de la ABI **por modo** (contiene `libflutter.so`; el engine 3.47.5 no trae ICU). */
    @JvmStatic
    fun nativeJar(context: Context, abi: String?, mode: FlutterBuildMode): File {
        val suffix = if (mode == FlutterBuildMode.DEBUG_JIT) "debug" else "release"
        return File(engineDir(context), "native_${abi ?: "unknown"}_$suffix.jar")
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun nativeJar(context: Context, abi: String?): File = nativeJar(context, abi, FlutterBuildMode.RELEASE_AOT)

    /**
     * `libflutter.so` extraido de [nativeJar], **por modo**: `libflutter_debug.so` (395 MB, el que
     * arranca el kernel JIT) o `libflutter.so` (165 MB, el que necesita `libapp.so`).
     */
    @JvmStatic
    fun libFlutterSo(context: Context, mode: FlutterBuildMode): File {
        val name = if (mode == FlutterBuildMode.DEBUG_JIT) "libflutter_debug.so" else "libflutter.so"
        return File(engineDir(context), name)
    }

    /** Variante release (compatibilidad). */
    @JvmStatic
    fun libFlutterSo(context: Context): File = libFlutterSo(context, FlutterBuildMode.RELEASE_AOT)

    /** `icudtl.dat` extraído de [nativeJar]. */
    @JvmStatic
    fun icuDataFile(context: Context): File = File(engineDir(context), "icudtl.dat")

    @JvmStatic
    fun patchedSdkZip(context: Context): File =
        File(engineDir(context), "flutter_patched_sdk.zip")

    /** Directorio donde se descomprime [patchedSdkZip]. */
    @JvmStatic
    fun patchedSdkExtractDir(context: Context): File =
        File(engineDir(context), "flutter_patched_sdk_extracted")

    /**
     * `sdk-root` real de `gen_kernel`: dentro del zip la estructura es
     * `flutter_patched_sdk/platform_strong.dill` + `flutter_patched_sdk/vm_outline_strong.dill`.
     */
    @JvmStatic
    fun patchedSdkRoot(context: Context): File =
        File(patchedSdkExtractDir(context), "flutter_patched_sdk")

    /** `platform_strong.dill`: el `--platform` que espera `gen_kernel`. */
    @JvmStatic
    fun patchedSdkPlatformDill(context: Context): File =
        File(patchedSdkRoot(context), "platform_strong.dill")

    /* -------------------------------------------------------------------------------------- */
    /* flutter_patched_sdk_product (AOT): el front-end DEBE ser el product                        */
    /* -------------------------------------------------------------------------------------- */

    /**
     * `flutter_patched_sdk_product.zip` (4.108.252 B; HTTP 200 comprobado el 2026-09-23).
     *
     * El AOT del informe (carril K) uso exactamente esta plataforma
     * (`--platform=patched_sdk_product/flutter_patched_sdk_product/platform_strong.dill`) para
     * producir el `.dill` que consume el `gen_snapshot` product. Usar la plataforma no-product con
     * `-Ddart.vm.product=true` tambien funciono, pero el camino probado y el que replica
     * `flutter build` es el product.
     */
    @JvmStatic
    fun patchedSdkProductArtifact(): RemoteArtifact = RemoteArtifact(
        "$FLUTTER_INFRA_RELEASE/$ENGINE_VERSION/flutter_patched_sdk_product.zip",
        PATCHED_SDK_PRODUCT_ZIP_SIZE,
    )

    @JvmStatic
    fun patchedSdkProductZip(context: Context): File =
        File(engineDir(context), "flutter_patched_sdk_product.zip")

    @JvmStatic
    fun patchedSdkProductExtractDir(context: Context): File =
        File(engineDir(context), "flutter_patched_sdk_product_extracted")

    /** Dentro del zip: `flutter_patched_sdk_product/{platform_strong.dill,…}`. */
    @JvmStatic
    fun patchedSdkProductRoot(context: Context): File =
        File(patchedSdkProductExtractDir(context), "flutter_patched_sdk_product")

    @JvmStatic
    fun patchedSdkProductPlatformDill(context: Context): File =
        File(patchedSdkProductRoot(context), "platform_strong.dill")

    /**
     * `--platform` de `gen_kernel` segun el modo: el **product** en RELEASE_AOT (lo probo el carril K)
     * y el normal en DEBUG_JIT.
     */
    @JvmStatic
    fun patchedSdkPlatformDillForMode(context: Context, mode: FlutterBuildMode): File =
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            patchedSdkProductPlatformDill(context)
        } else {
            patchedSdkPlatformDill(context)
        }

    /** Directorio `flutter_patched_sdk*` que se pasa a `gen_kernel` para [mode]. */
    @JvmStatic
    fun patchedSdkRootForMode(context: Context, mode: FlutterBuildMode): File =
        if (mode == FlutterBuildMode.RELEASE_AOT) {
            patchedSdkProductPlatformDill(context).parentFile ?: patchedSdkRoot(context)
        } else {
            patchedSdkRoot(context)
        }

    /**
     * Directorio **opcional** con las fuentes Dart del framework Flutter (`package:flutter…`).
     * Si no existe, `FlutterDartCompiler` falla con un diagnóstico claro: los artefactos del
     * engine no incluyen el framework Dart (ver ESTADO HONESTO del informe del carril C).
     */
    @JvmStatic
    fun frameworkDir(context: Context): File = File(engineDir(context), "flutter-framework")

    /** Tarball descargado con las fuentes del framework (`flutter-3.47.5.tar.gz`). */
    @JvmStatic
    fun frameworkTarball(context: Context): File =
        File(engineDir(context), "flutter-${FLUTTER_VERSION}.tar.gz")

    /**
     * Carpeta de las dependencias de pub del framework (`<engine>/pub-deps/<name>-<version>`).
     *
     * Estructura final que consume [FlutterDartCompiler.writePackageConfig]:
     * ```
     * <filesDir>/flutter-toolchain/engine/
     *   flutter-framework/{pubspec.yaml,lib…*}      -> paquete `flutter`
     *   pub-deps/characters-1.4.1/lib…*             -> paquete `characters`
     *   pub-deps/collection-1.19.1/lib…*            -> paquete `collection`
     *   pub-deps/material_color_utilities-0.13.0/lib…*
     *   pub-deps/meta-1.18.3/lib…*
     *   pub-deps/vector_math-2.4.0/lib…*
     * ```
     */
    @JvmStatic
    fun pubDepsDir(context: Context): File = File(engineDir(context), "pub-deps")

    /** Directorio de una dependencia de pub concreta (`pub-deps/<name>-<version>`). */
    @JvmStatic
    fun pubDependencyDir(context: Context, dependency: PubDependencySpec): File =
        File(pubDepsDir(context), dependency.directoryName)

    /* -------------------------------------------------------------------------------------- */
    /* Rutas de build dentro del proyecto del usuario                                           */
    /* -------------------------------------------------------------------------------------- */

    /** `<flutterRoot>/.dart_tool`. */
    @JvmStatic
    fun dartToolDir(flutterRoot: File): File = File(flutterRoot, ".dart_tool")

    /** `<flutterRoot>/.dart_tool/package_config.json`. */
    @JvmStatic
    fun packageConfigFile(flutterRoot: File): File =
        File(dartToolDir(flutterRoot), "package_config.json")

    /** `<flutterRoot>/build`: staging del carril C. */
    @JvmStatic
    fun stagingDir(flutterRoot: File): File = File(flutterRoot, "build")

    /** `<flutterRoot>/build/flutter_assets`. */
    @JvmStatic
    fun flutterAssetsDir(flutterRoot: File): File =
        File(stagingDir(flutterRoot), "flutter_assets")

    /** `<flutterRoot>/build/app.dill` (salida intermedia del AOT). */
    @JvmStatic
    fun aotDillFile(flutterRoot: File): File = File(stagingDir(flutterRoot), "app.dill")

    /** `<flutterRoot>/build/libapp.so`. */
    @JvmStatic
    fun libAppSo(flutterRoot: File): File = File(stagingDir(flutterRoot), "libapp.so")

    /** `<flutterRoot>/build/kernel_blob.bin`. */
    @JvmStatic
    fun kernelBlobFile(flutterRoot: File): File =
        File(flutterAssetsDir(flutterRoot), "kernel_blob.bin")

    /** Log crudo del compilador Dart. */
    @JvmStatic
    fun compilerLogFile(flutterRoot: File): File =
        File(stagingDir(flutterRoot), "flutter_compile.log")

    /* -------------------------------------------------------------------------------------- */
    /* Resolucion pub REAL (carril P, Fase 8)                                                   */
    /* -------------------------------------------------------------------------------------- */

    /**
     * `<toolchain>/pub-cache`: el `PUB_CACHE` dentro del almacenamiento privado de la app.
     *
     * Evidencia (emulador arm64 API 34, 2026-09-23): un `dart pub get` con
     * `PUB_CACHE=/data/local/tmp/carrilP/pubcache` descargo 13 paquetes (14 MB) y genero un
     * `.dart_tool/package_config.json` con rutas `file:///…/hosted/pub.dev/<name>-<version>`.
     */
    @JvmStatic
    fun pubCacheDir(context: Context): File = File(toolchainDir(context), "pub-cache")

    /**
     * `<toolchain>/pub-home`: `HOME` falso para pub (escribe ahi telemetria/credenciales y, en
     * algunos casos, `.dart_tool/pub`). Nunca el HOME real del usuario.
     */
    @JvmStatic
    fun pubHomeDir(context: Context): File = File(toolchainDir(context), "pub-home")

    /**
     * `<toolchain>/tmp`: `TMPDIR` de pub. En Android `Directory.systemTemp` apunta a `/data/local/tmp`
     * (escribible por shell, no por la app); sin `TMPDIR` el cliente de pub puede fallar al
     * descomprimir las descargas.
     */
    @JvmStatic
    fun pubTempDir(context: Context): File = File(toolchainDir(context), "tmp")

    /**
     * `bin/snapshots/dartdev_aot.dart.snapshot`: es el cliente de `dart pub`.
     *
     * Comprobado con `ar`/`tar -tJf` sobre `dart_3.13.4_aarch64.deb`: el paquete de Termux **si**
     * trae el cliente (16.384.904 B) y `bin/dart` lo despacha: `./bin/dart pub get` funciona en el
     * dispositivo. No hace falta descargar nada extra.
     */
    @JvmStatic
    fun dartDevSnapshot(context: Context): File =
        File(File(dartBinDir(context), "snapshots"), "dartdev_aot.dart.snapshot")

    /**
     * `<engine>/flutter-root`: **Flutter SDK sintetico** que `dart pub` necesita como `FLUTTER_ROOT`
     * para resolver `flutter: {sdk: flutter}`.
     *
     * Estructura minima (toda verificada en el emulador; sin ella pub responde
     * "the Flutter SDK is not available"):
     * ```
     * flutter-root/
     *   version                            <- "3.47.5"
     *   bin/cache/flutter.version.json      <- IMPRESCINDIBLE: si falta, pub da el SDK por ausente
     *   bin/cache/pkg/sky_engine/{pubspec.yaml,lib/ui/ui.dart}   <- `sky_engine sdk:flutter`
     *   packages/flutter/{pubspec.yaml,lib}  <- copia del framework (fuentes reales)
     *   packages/{flutter_test,flutter_web_plugins,flutter_localizations,…}/pubspec.yaml
     * ```
     */
    @JvmStatic
    fun syntheticFlutterRoot(context: Context): File = File(engineDir(context), "flutter-root")

    @JvmStatic
    fun syntheticFlutterPackagesDir(context: Context): File =
        File(syntheticFlutterRoot(context), "packages")

    /** `flutter-root/packages/flutter` (framework: `package:flutter/…`). */
    @JvmStatic
    fun syntheticFlutterFrameworkDir(context: Context): File =
        File(syntheticFlutterPackagesDir(context), "flutter")

    @JvmStatic
    fun syntheticFlutterBinCacheDir(context: Context): File =
        File(File(syntheticFlutterRoot(context), "bin"), "cache")

    /**
     * `flutter-root/bin/cache/flutter.version.json`.
     *
     * Evidencia cruda (emulador): sin este fichero `dart pub get` imprimia
     * `FINE: Could not open flutter version file at …/bin/cache/flutter.version.json` y terminaba en
     * `Because <app> depends on flutter from sdk which doesn't exist (the Flutter SDK is not
     * available)`. Con el fichero (frameworkVersion+channel) el mismo comando resolvio 16 paquetes.
     */
    @JvmStatic
    fun flutterVersionJsonFile(context: Context): File =
        File(syntheticFlutterBinCacheDir(context), "flutter.version.json")

    /** `flutter-root/version`. */
    @JvmStatic
    fun syntheticFlutterVersionFile(context: Context): File =
        File(syntheticFlutterRoot(context), "version")

    /** `flutter-root/bin/cache/pkg/sky_engine`: paquete `sky_engine` (`sdk: flutter`). */
    @JvmStatic
    fun syntheticSkyEngineDir(context: Context): File =
        File(File(syntheticFlutterBinCacheDir(context), "pkg"), "sky_engine")

    /** `pubspec.lock` del proyecto del usuario (lo escribe pub). */
    @JvmStatic
    fun pubLockFile(flutterRoot: File): File = File(flutterRoot, "pubspec.lock")

    /** `.dart_tool/package_graph.json` (grafo completo, incluye dev_dependencies). */
    @JvmStatic
    fun packageGraphFile(flutterRoot: File): File =
        File(dartToolDir(flutterRoot), "package_graph.json")

    /** `<flutterRoot>/build/pub_get.log`: salida cruda de `dart pub get`. */
    @JvmStatic
    fun pubLogFile(flutterRoot: File): File = File(stagingDir(flutterRoot), "pub_get.log")

    /**
     * `.dart_tool/flutter_pub_get.done`: marca de que `package_config.json` lo genero **pub**
     * (contiene el hash del `pubspec.yaml` + el `pubspec.lock` con el que se resolvio).
     */
    @JvmStatic
    fun pubGenerationMarker(flutterRoot: File): File =
        File(dartToolDir(flutterRoot), "flutter_pub_get.done")

    /** `.dart_tool/flutter_build/`: salidas auxiliares (registrantes, wrapper de entrada). */
    @JvmStatic
    fun flutterBuildDir(flutterRoot: File): File = File(dartToolDir(flutterRoot), "flutter_build")

    /** `.dart_tool/flutter_build/dart_plugin_registrant.dart` (`_registerPlugins()`). */
    @JvmStatic
    fun dartPluginRegistrantFile(flutterRoot: File): File =
        File(flutterBuildDir(flutterRoot), "dart_plugin_registrant.dart")

    /** `.dart_tool/flutter_build/GeneratedPluginRegistrant.java` (Java/Kotlin, registro nativo). */
    @JvmStatic
    fun generatedPluginRegistrantFile(flutterRoot: File, packagePath: String): File =
        File(File(flutterBuildDir(flutterRoot), packagePath), "GeneratedPluginRegistrant.java")

    /**
     * `.dart_tool/flutter_build/entrypoint.dart`: wrapper que registra los plugins Dart y llama al
     * `main()` del usuario. Es el fichero que se le pasa a `gen_kernel` cuando hay plugins con
     * `dartPluginClass` (el equivalente Flutter es el `--dart-plugin-registrant` del frontend).
     */
    @JvmStatic
    fun pluginEntrypointFile(flutterRoot: File): File =
        File(flutterBuildDir(flutterRoot), "entrypoint.dart")

    /**
     * `pubspec.yaml` minimos de los paquetes `sdk: flutter` que pub tiene que encontrar en
     * `FLUTTER_ROOT/packages/<nombre>/pubspec.yaml`.
     *
     * Se copian tal cual del tag 3.47.5 quitando `resolution: workspace` y `dev_dependencies:`: el
     * `resolution: workspace` solo funciona dentro del monorepo de Flutter (necesita el pubspec raiz
     * con `workspace:`), y las dev_dependencies de una dependencia no participan en la resolucion del
     * proyecto del usuario. Los `dependencies:` **si** se conservan: son las que pub resuelve contra
     * pub.dev (verificado: `dart pub get` en el dispositivo resolvio characters/collection/
     * material_color_utilities/meta/vector_math/test_api/matcher/fake_async… para estos paquetes).
     */
    @JvmStatic
    fun flutterSdkPackagePubspec(name: String): String? = when (name) {
        "flutter_web_plugins" -> """
            name: flutter_web_plugins
            description: Library to register Flutter Web plugins
            homepage: https://flutter.dev

            environment:
              sdk: '>=3.11.0 <4.0.0'

            dependencies:
              flutter:
                sdk: flutter
        """.trimIndent() + "\n"

        "flutter_test" -> """
            name: flutter_test

            environment:
              sdk: '>=3.11.0 <4.0.0'

            dependencies:
              flutter:
                sdk: flutter
              test_api: 0.7.12
              matcher: 0.12.20
              path: ^1.9.1
              fake_async: ^1.3.3
              clock: ^1.1.2
              stack_trace: ^1.12.1
              vector_math: ^2.4.0
              leak_tracker_flutter_testing: ^3.0.10
              collection: ^1.19.1
              meta: ^1.18.3
              stream_channel: ^2.1.4
        """.trimIndent() + "\n"

        "flutter_localizations" -> """
            name: flutter_localizations

            environment:
              sdk: '>=3.11.0 <4.0.0'

            dependencies:
              flutter:
                sdk: flutter
              intl: ^0.20.3
              path: ^1.9.1
        """.trimIndent() + "\n"

        "sky_engine" -> """
            name: sky_engine
            version: 0.0.99
            environment:
              sdk: '>=2.12.0 <4.0.0'
        """.trimIndent() + "\n"

        else -> null
    }

    /**
     * `{frameworkVersion, channel, …}` de `flutter-root/bin/cache/flutter.version.json`.
     *
     * `frameworkVersion` y `channel` son los unicos campos que pub necesita; el resto se escribe
     * para que el fichero sea el mismo que produce `flutter --version --machine`.
     */
    @JvmStatic
    fun flutterVersionJson(): String = """
        {
          "frameworkVersion": "$FLUTTER_VERSION",
          "channel": "stable",
          "repositoryUrl": "https://github.com/flutter/flutter.git",
          "frameworkCommitDate": "2026-09-01T00:00:00.000Z",
          "engineRevision": "$ENGINE_VERSION",
          "dartSdkVersion": "$DART_VERSION",
          "flutterVersion": "$FLUTTER_VERSION"
        }
    """.trimIndent() + "\n"

    /** `sky_engine/lib/ui/ui.dart`: stub. El `dart:ui` real viaja en `platform_strong.dill`. */
    @JvmStatic
    fun skyEngineStubDart(): String =
        "// Stub de sky_engine (carril P): el dart:ui real esta en platform_strong.dill.\n"

    /** `bin/internal/engine.version` del Flutter SDK sintetico. */
    @JvmStatic
    fun syntheticEngineVersionFile(context: Context): File =
        File(File(syntheticFlutterRoot(context), "bin/internal"), "engine.version")
}
