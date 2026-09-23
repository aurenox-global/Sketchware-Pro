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

    @JvmStatic
    fun dartExecutable(context: Context): File = File(dartBinDir(context), "dart")

    @JvmStatic
    fun dartVmExecutable(context: Context): File = File(dartBinDir(context), "dartvm")

    /** `dartaotruntime`: lanza los snapshots AOT (`gen_kernel_aot`, `frontend_server_aot`). */
    @JvmStatic
    fun dartAotRuntime(context: Context): File = File(dartBinDir(context), "dartaotruntime")

    /** `bin/utils/gen_snapshot`: produce `libapp.so` (`app-aot-elf`). */
    @JvmStatic
    fun genSnapshot(context: Context): File = File(File(dartBinDir(context), "utils"), "gen_snapshot")

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
}
