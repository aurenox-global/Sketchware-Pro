package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Descarga y cachea **una sola vez** los artefactos del engine de Flutter (carril C).
 *
 * Engine: `af7e796e161ae0bb1ff0758c71a7105418bd9ded` (Flutter 3.47.5).
 *
 * IMPORTANTE (correccion tras la prueba E2E): los artefactos se eligen **por modo**, porque el
 * engine debug y el release no son intercambiables:
 *
 * - `DEBUG_JIT` -> `flutter_embedding_debug` + jar de nativas `<abi>_debug` (engine **debug**,
 *   395 MB de `libflutter.so`). Es el camino con mas recorrido: el jar debug hace que
 *   `FlutterLoader` cargue `kernel_blob.bin` (JIT) y el engine debug lo acepta. Demostrado en el
 *   informe E2E (§4.6, §5.5).
 * - `RELEASE_AOT` -> `flutter_embedding_release` + jar de nativas `<abi>_release` (165 MB de
 *   `libflutter.so`) **y** la plataforma `flutter_patched_sdk_product`. Desbloqueado en la Fase 8
 *   (carril I) con el `gen_snapshot` propio (informe AOT §5): el engine release acepta el
 *   `libapp.so` generado en el dispositivo, siempre que la variante del APK empaquete el binario
 *   (`arm64-v8a` o `x86_64`; son las unicas que lo llevan).
 *
 * Ademas de los artefactos del engine hace falta el **framework Dart** (`package:flutter…`):
 * ni el jar del embedding ni `flutter_patched_sdk.zip` lo incluyen. Se trae del tarball del tag
 * 3.47.5 (solo `packages/flutter/lib…*` + `pubspec.yaml`), junto con las dependencias de pub que
 * declara su pubspec, exactamente como se hizo en la prueba E2E (§3 del informe).
 *
 * CORRECCIÓN AL CONTRATO: **no existe `icudtl.dat`** en el engine 3.47.5. Se comprobó con
 * `unzip -l` sobre el jar de nativas (1 sola entrada), sobre `flutter_embedding_release.jar`
 * (451 entradas, 0 coincidencias de `icudtl`) y sobre `artifacts.zip` (solo `flutter.jar` +
 * licencia). El ICU va embebido en `libflutter.so`. Por eso `icudtl.dat` es **opcional**.
 * - `flutter_patched_sdk.zip` -> `platform_strong.dill` que consume `gen_kernel` como `--platform`.
 *
 * Todos los tamaños se verifican con `content-length` (HTTP 200 comprobado con `curl -sI`), salvo
 * el tarball del framework: GitHub no publica `content-length` (chunked) y por eso va con tamaño 0.
 *
 * **Fase 8 / carril A2 — ASSETS del bundle.** Ademas de lo anterior se exigen ahora las **fuentes de
 * los assets** que faltaban (`FlutterBundleAssets`): el artefacto `material_fonts` del SDK
 * (`fonts.zip` -> `MaterialIcons-Regular.otf`, sin el los iconos Material salen como caja vacia) y
 * los **shaders precompilados** (`shaders/ink_sparkle.frag`, sin el el ripple del FAB registra
 * `Asset 'shaders/ink_sparkle.frag' not found`). Se descargan/cachean en `<engine>/asset-sources`.
 * Lo unico que cambia de esta clase es un paso extra en [ensureArtifacts] y un termino mas en
 * [areArtifactsReady]; **las firmas que usa el resto del codigo no cambian**
 * (`ensureArtifacts(context, mode, progress)` sigue igual).
 */
object FlutterEngineArtifacts {

    private const val TAG = "FlutterEngineArtifacts"

    /**
     * `true` si el engine del [mode] + el patched sdk + el framework Dart estan en disco.
     *
     * `icudtl.dat` **no** se exige: el engine 3.47.5 no lo publica (ver cabecera).
     * El framework si se exige: sin el, `package:flutter…` no compila (es el fallo que se
     * documentaba como "ESTADO HONESTO" del carril C).
     *
     * El **patched sdk depende del modo** (Fase 8 / carril I): RELEASE_AOT necesita
     * `flutter_patched_sdk_product` (`--platform` del front-end AOT), DEBUG_JIT el normal.
     */
    @JvmStatic
    fun areArtifactsReady(context: Context, mode: FlutterBuildMode): Boolean {
        return FlutterToolchainPaths.embeddingJar(context, mode).isFile &&
                FlutterToolchainPaths.libFlutterSo(context, mode).isFile &&
                FlutterToolchainPaths.patchedSdkPlatformDillForMode(context, mode).isFile &&
                isFrameworkReady(context) &&
                // Fase 8 / A2: fuente Material + shaders precompilados (ver cabecera).
                FlutterBundleAssets.areAssetSourcesReady(context)
    }

    /** Variante con el modo por defecto ([FlutterProjectDefaults.DEFAULT_MODE]). */
    @JvmStatic
    fun areArtifactsReady(context: Context): Boolean =
        areArtifactsReady(context, FlutterProjectDefaults.DEFAULT_MODE)

    /** `true` si estan `flutter-framework/pubspec.yaml` + `flutter-framework/lib…*`. */
    @JvmStatic
    fun isFrameworkReady(context: Context): Boolean {
        val frameworkDir = FlutterToolchainPaths.frameworkDir(context)
        return File(frameworkDir, "pubspec.yaml").isFile && File(frameworkDir, "lib").isDirectory &&
                arePubDependenciesReady(context)
    }

    /** `true` si todas las dependencias de pub del framework estan extraidas. */
    @JvmStatic
    fun arePubDependenciesReady(context: Context): Boolean {
        return FlutterToolchainPaths.frameworkPubDependencies().all { dependency ->
            File(FlutterToolchainPaths.pubDependencyDir(context, dependency), "lib").isDirectory
        }
    }

    /**
     * Descarga lo que falte para [mode] (incluido el framework Dart). Bloqueante.
     *
     * @param progress callback de progreso, se invoca desde el hilo llamante.
     */
    @JvmStatic
    fun ensureArtifacts(context: Context, mode: FlutterBuildMode, progress: (String) -> Unit): Boolean {
        val abi = FlutterToolchainPaths.resolveSupportedAbi()
        if (abi == null) {
            progress("ABI no soportada para los artefactos del engine")
            return false
        }

        val engineDir = FlutterToolchainPaths.engineDir(context)
        if (!engineDir.mkdirs() && !engineDir.isDirectory) {
            progress("No se pudo crear ${engineDir.absolutePath}")
            return false
        }

        // 1) Embedding (clases del runtime Flutter). El modo decide debug/release: con el jar
        //    equivocado, `FlutterLoader` busca `libapp.so` en vez de `kernel_blob.bin`.
        val embeddingSpec = FlutterToolchainPaths.embeddingJarArtifact(mode)
        val embeddingJar = FlutterToolchainPaths.embeddingJar(context, mode)
        if (!embeddingJar.isFile || embeddingJar.length() != embeddingSpec.sizeBytes) {
            progress("Descargando ${embeddingJar.name} (${embeddingSpec.sizeBytes / 1024} KB)...")
            if (!FlutterToolchainInstaller.download(
                    embeddingSpec.url, embeddingJar, embeddingSpec.sizeBytes, "", progress
                )
            ) {
                return false
            }
        }

        // 2) Nativas de la ABI (engine debug o release).
        if (!ensureNativeLibraries(context, abi, mode, progress)) {
            return false
        }

        // 3) Patched SDK (plataforma Dart para --target=flutter). Depende del modo: el front-end AOT
        //    consume `flutter_patched_sdk_product` (carril K); el JIT, el normal.
        if (!ensurePatchedSdk(context, mode, progress)) {
            return false
        }

        // 4) Framework Dart + dependencias de pub (lo que no trae ningun artefacto del engine).
        if (!ensureFramework(context, progress)) {
            return false
        }

        // 5) ASSETS del bundle (Fase 8 / carril A2): material_fonts + shaders precompilados.
        //    Sin esto el .otf de los iconos y `shaders/ink_sparkle.frag` nunca llegan al APK.
        if (!FlutterBundleAssets.ensureAssetSources(context, progress)) {
            return false
        }

        val ready = areArtifactsReady(context, mode)
        progress(if (ready) "Artefactos del engine ($mode) listos" else "Artefactos del engine incompletos")
        return ready
    }

    /** Variante con el modo por defecto ([FlutterProjectDefaults.DEFAULT_MODE]). */
    @JvmStatic
    fun ensureArtifacts(context: Context, progress: (String) -> Unit): Boolean =
        ensureArtifacts(context, FlutterProjectDefaults.DEFAULT_MODE, progress)

    /**
     * Descarga y extrae el **framework Dart** de Flutter (paquete `flutter`) y sus dependencias
     * de pub. Bloqueante.
     *
     * Replica lo que funciono en la prueba E2E (§3 del informe): del tarball del tag se extraen
     * solo `packages/flutter/lib…*` y `packages/flutter/pubspec.yaml` (23 MB, no los 32 MB del
     * repo ni los ~700 MB del SDK de escritorio).
     *
     * @return `true` si `frameworkDir` y `pub-deps/` quedaron completos.
     */
    @JvmStatic
    fun ensureFramework(context: Context, progress: (String) -> Unit): Boolean {
        val frameworkDir = FlutterToolchainPaths.frameworkDir(context)
        val pubDepsDir = FlutterToolchainPaths.pubDepsDir(context)

        if (!isFrameworkReady(context)) {
            val tarball = FlutterToolchainPaths.frameworkTarball(context)
            val spec = FlutterToolchainPaths.frameworkSourceTarball()
            progress("Descargando el framework Dart de Flutter ${FlutterToolchainPaths.FLUTTER_VERSION}...")
            // sizeBytes = 0 -> GitHub no publica content-length (chunked); sin verificacion de tamano.
            if (!FlutterToolchainInstaller.download(spec.url, tarball, spec.sizeBytes, "", progress)) {
                progress("No se pudo descargar el framework desde ${spec.url}")
                return false
            }

            val staging = File(FlutterToolchainPaths.engineDir(context), "flutter-framework.tmp")
            FlutterToolchainInstaller.deleteRecursively(staging)
            if (!staging.mkdirs() && !staging.isDirectory) {
                progress("No se pudo crear ${staging.absolutePath}")
                return false
            }

            progress("Extrayendo packages/flutter/lib del tarball...")
            val extracted = extractTarGz(
                tarball = tarball,
                stripPrefix = FlutterToolchainPaths.FLUTTER_TARBALL_ROOT_PREFIX,
                targetDir = staging,
                mapRelativePath = { inner ->
                    when {
                        inner == "packages/flutter/pubspec.yaml" -> "pubspec.yaml"
                        // `packages/flutter/lib/...` -> `lib/...` (asi `frameworkDir` ES el paquete)
                        inner.startsWith("packages/flutter/lib/") -> inner.removePrefix("packages/flutter/")
                        else -> null
                    }
                },
                progress = progress,
            )
            if (extracted <= 0 || !File(staging, "pubspec.yaml").isFile || !File(staging, "lib").isDirectory) {
                progress("El tarball no traia packages/flutter/lib (entradas=$extracted)")
                FlutterToolchainInstaller.deleteRecursively(staging)
                return false
            }

            FlutterToolchainInstaller.deleteRecursively(frameworkDir)
            if (!staging.renameTo(frameworkDir)) {
                staging.copyRecursively(frameworkDir, overwrite = true)
                FlutterToolchainInstaller.deleteRecursively(staging)
            }
            progress("Framework Dart listo en ${frameworkDir.absolutePath} ($extracted ficheros)")

            // 25-35 MB que ya no hacen falta: se borra para no engordar el toolchain del movil.
            if (tarball.isFile && !tarball.delete()) {
                Log.w(TAG, "No se pudo borrar ${tarball.absolutePath}")
            }
        } else {
            progress("Framework Dart ya cacheado")
        }

        // Dependencias de pub (characters, collection, material_color_utilities, meta, vector_math).
        var downloaded = 0
        for (dependency in FlutterToolchainPaths.frameworkPubDependencies()) {
            val target = FlutterToolchainPaths.pubDependencyDir(context, dependency)
            if (File(target, "lib").isDirectory) {
                continue
            }
            val cache = File(pubDepsDir, "${dependency.directoryName}.tar.gz")
            progress("Descargando pub ${dependency.name} ${dependency.version}...")
            if (!FlutterToolchainInstaller.download(
                    dependency.url, cache, dependency.sizeBytes, "", progress
                )
            ) {
                progress("No se pudo descargar ${dependency.url}")
                return false
            }
            FlutterToolchainInstaller.deleteRecursively(target)
            if (!target.mkdirs() && !target.isDirectory) {
                progress("No se pudo crear ${target.absolutePath}")
                return false
            }
            // Los archivos de pub.dev van SIN prefijo de directorio; `extractTarGz` lo tolera
            // (reintenta sin prefijo si el indicado no encaja en ninguna entrada).
            val entries = extractTarGz(
                tarball = cache,
                stripPrefix = "${dependency.directoryName}/",
                targetDir = target,
                mapRelativePath = { inner -> inner },
                progress = progress,
            )
            if (entries <= 0 || !File(target, "lib").isDirectory) {
                progress("El paquete ${dependency.name} ${dependency.version} no se extrajo bien")
                return false
            }
            downloaded++
        }
        if (downloaded > 0) {
            progress("Dependencias de pub listas ($downloaded nuevas)")
        }

        return isFrameworkReady(context)
    }

    /** Descarga y extrae el patched SDK que corresponde a [mode] (product en AOT). Bloqueante. */
    @JvmStatic
    fun ensurePatchedSdk(context: Context, mode: FlutterBuildMode, progress: (String) -> Unit): Boolean {
        val platformDill = FlutterToolchainPaths.patchedSdkPlatformDillForMode(context, mode)
        if (platformDill.isFile) {
            return true
        }
        val product = mode == FlutterBuildMode.RELEASE_AOT
        val sdkDir = if (product) {
            FlutterToolchainPaths.patchedSdkProductExtractDir(context)
        } else {
            FlutterToolchainPaths.patchedSdkExtractDir(context)
        }
        val zip = if (product) {
            FlutterToolchainPaths.patchedSdkProductZip(context)
        } else {
            FlutterToolchainPaths.patchedSdkZip(context)
        }
        val spec = if (product) {
            FlutterToolchainPaths.patchedSdkProductArtifact()
        } else {
            FlutterToolchainPaths.patchedSdkArtifact()
        }

        progress("Descargando ${zip.name} (${spec.sizeBytes / 1024} KB)...")
        if (!FlutterToolchainInstaller.download(spec.url, zip, spec.sizeBytes, "", progress)) {
            return false
        }
        FlutterToolchainInstaller.deleteRecursively(sdkDir)
        if (!sdkDir.mkdirs() && !sdkDir.isDirectory) {
            progress("No se pudo crear ${sdkDir.absolutePath}")
            return false
        }
        if (!unzip(zip, sdkDir, progress)) {
            return false
        }
        if (!platformDill.isFile) {
            progress(
                "${zip.name} no contiene " +
                    "${platformDill.parentFile?.name}/platform_strong.dill"
            )
            return false
        }
        return true
    }

    /** Descarga el jar de nativas de [abi]/[mode] y extrae `libflutter.so` (+ `icudtl.dat` si existiera). */
    private fun ensureNativeLibraries(
        context: Context,
        abi: String,
        mode: FlutterBuildMode,
        progress: (String) -> Unit,
    ): Boolean {
        val libFlutterSo = FlutterToolchainPaths.libFlutterSo(context, mode)
        val icuData = FlutterToolchainPaths.icuDataFile(context)
        val spec = FlutterToolchainPaths.nativeJarArtifact(abi, mode)
        val jar = FlutterToolchainPaths.nativeJar(context, abi, mode)

        // Solo se reutiliza el `libflutter.so` si su jar sigue en disco: al cambiar de modo, el
        // fichero del otro modo no vale (y no debe borrarse: el usuario puede volver a el).
        if (libFlutterSo.isFile && (!jar.isFile || jar.length() == spec?.sizeBytes)) {
            return true
        }
        if (spec == null) {
            progress("No hay jar de nativas para $abi ($mode)")
            return false
        }

        if (!jar.isFile || (spec.sizeBytes > 0 && jar.length() != spec.sizeBytes)) {
            progress("Descargando ${jar.name} (${spec.sizeBytes / 1024 / 1024} MB)...")
            if (!FlutterToolchainInstaller.download(spec.url, jar, spec.sizeBytes, "", progress)) {
                return false
            }
        }

        if (!extractNativeFromJar(jar, libFlutterSo, icuData, progress)) {
            // Fallback documentado: `android-<arch>-<debug|release>/artifacts.zip` trae `flutter.jar`,
            // que contiene `lib/<abi>/libflutter.so`.
            val fallback = FlutterToolchainPaths.androidArtifactsArtifact(abi, mode)
            if (fallback == null) {
                return false
            }
            val zip = File(FlutterToolchainPaths.engineDir(context), "android_${abi}_${mode.name.lowercase()}.zip")
            progress("Fallback: descargando ${zip.name} (${fallback.sizeBytes / 1024 / 1024} MB)...")
            if (!FlutterToolchainInstaller.download(fallback.url, zip, fallback.sizeBytes, "", progress)) {
                return false
            }
            return extractNativeFromZip(zip, libFlutterSo, icuData, progress)
        }
        return true
    }

    /** Extrae `libflutter.so`/`icudtl.dat` del jar Maven de nativas (`lib/<abi>/libflutter.so`). */
    private fun extractNativeFromJar(
        jar: File,
        libFlutterSo: File,
        icuData: File,
        progress: (String) -> Unit,
    ): Boolean {
        var foundSo = false
        var foundIcu = false
        try {
            ZipInputStream(jar.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name.endsWith("libflutter.so") -> {
                            writeEntry(zip, libFlutterSo)
                            foundSo = true
                        }
                        name.endsWith("icudtl.dat") -> {
                            writeEntry(zip, icuData)
                            foundIcu = true
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo extrayendo nativas de ${jar.name}", e)
            progress("Error extrayendo ${jar.name}: ${e.message}")
            return false
        }
        if (!foundIcu) {
            progress("${jar.name}: sin icudtl.dat (esperado en el engine 3.47.5)")
        }
        return foundSo
    }

    /** Igual que [extractNativeFromJar] pero sobre el zip `android-<arch>-<modo>/artifacts.zip`. */
    private fun extractNativeFromZip(
        zipFile: File,
        libFlutterSo: File,
        icuData: File,
        progress: (String) -> Unit,
    ): Boolean {
        var foundSo = false
        var foundIcu = false
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name.endsWith("libflutter.so") -> {
                            writeEntry(zip, libFlutterSo)
                            foundSo = true
                        }
                        name.endsWith("icudtl.dat") -> {
                            writeEntry(zip, icuData)
                            foundIcu = true
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            progress("Error extrayendo ${zipFile.name}: ${e.message}")
            return false
        }
        return foundSo
    }

    private fun writeEntry(input: InputStream, destination: File) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output -> input.copyTo(output, 256 * 1024) }
    }

    /**
     * Extrae de un `tar.gz` **solo** las entradas que [mapRelativePath] acepta.
     *
     * Reutiliza el lector de tar del instalador (`FlutterToolchainInstaller.TarStreamReader`):
     * nada de `commons-compress` ni de dependencias nuevas. GitHub (`codeload`) y pub.dev emiten
     * tar POSIX/GNU con nombres de tipo `'0'`, que es lo que soporta el lector.
     *
     * @param stripPrefix prefijo del tarball que se descarta (`flutter-3.47.5/`, `<name>-<version>/`).
     * @param mapRelativePath destino relativo dentro de [targetDir], o `null` para ignorar la entrada.
     * @return numero de ficheros escritos, o `-1` si el tar estaba corrupto.
     */
    private fun extractTarGz(
        tarball: File,
        stripPrefix: String,
        targetDir: File,
        mapRelativePath: (String) -> String?,
        progress: (String) -> Unit,
    ): Int {
        val written = extractTarGzPass(tarball, stripPrefix, targetDir, mapRelativePath, progress)
        if (written != 0 || stripPrefix.isEmpty()) {
            return written
        }
        // Los tarballs de **pub.dev** no llevan directorio raiz: sus entradas son `lib/…`,
        // `pubspec.yaml`, … (comprobado con `tar -tzf characters-1.4.1.tar.gz`), asi que con el
        // prefijo `<paquete>-<version>/` no coincide ninguna entrada y se extraeria vacio. Visto en el
        // emulador: "El paquete characters 1.4.1 no se extrajo bien". Se reintenta sin prefijo.
        Log.w(TAG, "${tarball.name}: sin entradas con el prefijo '$stripPrefix'; se reintenta sin prefijo")
        return extractTarGzPass(tarball, "", targetDir, mapRelativePath, progress)
    }

    /** Una pasada de extraccion; devuelve los ficheros escritos (0 si el prefijo no encaja). */
    private fun extractTarGzPass(
        tarball: File,
        stripPrefix: String,
        targetDir: File,
        mapRelativePath: (String) -> String?,
        progress: (String) -> Unit,
    ): Int {
        var written = 0
        try {
            FileInputStream(tarball).use { raw ->
                GZIPInputStream(BufferedInputStream(raw, 256 * 1024)).use { gz ->
                    val tar = FlutterToolchainInstaller.TarStreamReader(gz)
                    while (true) {
                        val entry = tar.nextEntry() ?: break
                        val rawName = entry.name
                        val normalized = if (rawName.startsWith("./")) rawName.substring(2) else rawName
                        if (!normalized.startsWith(stripPrefix)) {
                            tar.skipCurrentEntry()
                            continue
                        }
                        val inner = normalized.substring(stripPrefix.length)
                        val destination = if (isRegularFile(entry.typeFlag) && inner.isNotEmpty()) {
                            mapRelativePath(inner)
                        } else {
                            null
                        }
                        if (destination == null) {
                            tar.skipCurrentEntry()
                            continue
                        }
                        val file = File(targetDir, destination)
                        file.parentFile?.mkdirs()
                        tar.copyCurrentEntryTo(file)
                        written++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo extrayendo ${tarball.name}", e)
            progress("Error extrayendo ${tarball.name}: ${e.message}")
            return -1
        }
        return written
    }

    /** `'0'` es fichero regular en tar POSIX; hay emisores antiguos que usan NUL. */
    private fun isRegularFile(typeFlag: Char): Boolean = typeFlag == '0' || typeFlag == '\u0000'

    /** Descomprime [zipFile] completo en [targetDir] (con protección contra `../`). */
    @JvmStatic
    fun unzip(zipFile: File, targetDir: File, progress: (String) -> Unit): Boolean {
        val targetPath = targetDir.absolutePath
        return try {
            var entries = 0
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val destination = File(targetDir, entry.name)
                    if (!destination.absolutePath.startsWith(targetPath)) {
                        progress("Entrada de zip sospechosa ignorada: ${entry.name}")
                        zip.closeEntry()
                        continue
                    }
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        writeEntry(zip, destination)
                        entries++
                    }
                    zip.closeEntry()
                }
            }
            progress("Descomprimidos $entries ficheros de ${zipFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallo descomprimiendo ${zipFile.name}", e)
            progress("Error descomprimiendo ${zipFile.name}: ${e.message}")
            false
        }
    }
}
