package pro.sketchware.flutter

import android.util.Log
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Instalador del toolchain Dart/Flutter on-device (carril C).
 *
 * Flujo:
 * 1. Descarga el `.deb` de Termux con [HttpURLConnection] verificando **tamaño** y **sha256**.
 * 2. Parsea el contenedor `ar` a mano (cabecera de 60 bytes por miembro).
 * 3. Descomprime `data.tar.xz` con `org.tukaani:xz` ([XZInputStream]).
 * 4. Extrae del tar **solo** los binarios/snapshots/`.dill` necesarios.
 *
 * Nada de esto usa `gradle`, `flutter_tools` ni un SDK de host.
 *
 * LIMITACIÓN CONOCIDA (W^X, Android 10+): los binarios se extraen en `<filesDir>/flutter-toolchain`
 * (datos privados de la app) y se marcan con [File.setExecutable]. En Android 10+ (API 29) el
 * `execve` de ficheros escritos por la app en su propio data dir está bloqueado por SELinux para
 * apps con `targetSdk >= 29`.
 *
 * **Fase 8 / carril I (RESUELTO en el codigo):** los dos ejecutables que la app necesita en tiempo
 * de build (`dartaotruntime` y nuestro `gen_snapshot`) viajan **empaquetados** en
 * `app/src/main/jniLibs/<abi>/` como `libdartaotruntime.so` y `libfluttergensnapshot.so` (hoy,
 * `arm64-v8a` y `x86_64`); el
 * instalador de Android los deja en `nativeLibraryDir`, que **si** es ejecutable (informe AOT §6.1:
 * `exit=0` desde la app, uid `untrusted_app`). La copia de `filesDir` se conserva como respaldo
 * (otras ABIs, diagnostico) y [FlutterToolchainManager.isReady] comprueba con una ejecucion real
 * cual de las dos rutas funciona.
 */
object FlutterToolchainInstaller {

    private const val TAG = "FlutterToolchain"

    private const val AR_MAGIC = "!<arch>\n"
    private const val AR_HEADER_SIZE = 60
    private const val TAR_BLOCK_SIZE = 512
    private const val TAR_MAGIC_OFFSET = 257
    private const val TAR_MAGIC = "ustar"

    /** Directorio raíz dentro del tar del `.deb`. */
    private const val SDK_PREFIX = "lib/dart-sdk/"

    private val REQUIRED_ENTRIES = setOf(
        "bin/dart",
        "bin/dartvm",
        "bin/dartaotruntime",
        "bin/utils/gen_snapshot",
        "bin/snapshots/gen_kernel_aot.dart.snapshot",
        "bin/snapshots/frontend_server_aot.dart.snapshot",
        // Cliente de `dart pub` (carril P): es el `dartdev` AOT del SDK. Comprobado dentro del
        // `data.tar.xz` de `dart_3.13.4_aarch64.deb` (16.384.904 B) y ejecutandolo en el emulador.
        "bin/snapshots/dartdev_aot.dart.snapshot",
        "lib/_internal/vm_platform.dill",
    )

    private val EXECUTABLE_ENTRIES = setOf(
        "bin/dart",
        "bin/dartvm",
        "bin/dartaotruntime",
        "bin/utils/gen_snapshot",
        "bin/snapshots/gen_kernel_aot.dart.snapshot",
        "bin/snapshots/frontend_server_aot.dart.snapshot",
    )

    /** Instala el SDK Dart para la ABI del dispositivo. Bloqueante; llamar en hilo de fondo. */
    @JvmStatic
    fun install(context: android.content.Context, progress: (String) -> Unit): Boolean {
        val abi = FlutterToolchainPaths.resolveSupportedAbi()
        if (abi == null) {
            progress("ABI no soportada: se necesita arm64-v8a o x86_64")
            return false
        }
        return installAbi(context, abi, progress)
    }

    /** Igual que [install] pero forzando la ABI (útil para diagnóstico). */
    @JvmStatic
    fun installAbi(context: android.content.Context, abi: String, progress: (String) -> Unit): Boolean {
        val spec = FlutterToolchainPaths.dartPackageSpec(abi)
        if (spec == null) {
            progress("No hay paquete Dart para la ABI $abi")
            return false
        }

        val debFile = File(FlutterToolchainPaths.downloadDir(context), spec.fileName)
        progress("Descargando ${spec.fileName} (${spec.sizeBytes / 1024 / 1024} MB)...")
        if (!download(spec.url, debFile, spec.sizeBytes, spec.sha256, progress)) {
            progress("Fallo la descarga o la verificacion de ${spec.url}")
            return false
        }
        progress("sha256 verificado: ${spec.sha256}")

        val dartDir = FlutterToolchainPaths.dartDir(context)
        deleteRecursively(dartDir)
        if (!dartDir.mkdirs() && !dartDir.isDirectory) {
            progress("No se pudo crear ${dartDir.absolutePath}")
            return false
        }

        progress("Parseando contenedor ar + data.tar.xz...")
        val extracted = try {
            extractDartSdk(debFile, dartDir)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo extrayendo el SDK Dart", e)
            progress("Error extrayendo el .deb: ${e.message}")
            return false
        }

        if (!REQUIRED_ENTRIES.all { File(dartDir, it).isFile }) {
            val missing = REQUIRED_ENTRIES.filter { !File(dartDir, it).isFile }
            progress("Faltan ficheros tras la extraccion: $missing")
            return false
        }

        // `vm_platform_strong.dill` es un symlink ABSOLUTO al prefijo de Termux: se rompe.
        // Se materializa como copia real de `vm_platform.dill`.
        val vmPlatform = FlutterToolchainPaths.vmPlatformDill(context)
        val vmPlatformStrong = FlutterToolchainPaths.vmPlatformStrongDill(context)
        vmPlatform.copyTo(vmPlatformStrong, overwrite = true)

        // El chmod es **solo informativo**: un ELF en `filesDir` con bit x sigue sin poder
        // ejecutarse en `targetSdk >= 29` (SELinux deniega `execute_no_trans` sobre
        // `app_data_file`, dominio `untrusted_app`). Los que se ejecutan de verdad son los
        // empaquetados en `nativeLibraryDir` (`libdartaotruntime.so` / `libfluttergensnapshot.so`).
        for (entry in EXECUTABLE_ENTRIES) {
            val file = File(dartDir, entry)
            if (!file.setExecutable(true, false)) {
                Log.d(TAG, "No se pudo marcar x en ${file.absolutePath} (no se ejecuta desde ahi igualmente)")
            }
        }

        writeMarker(context, spec, extracted)

        // Lo que define si el SDK esta INSTALADO son los DATOS extraidos (marcador + snapshots AOT
        // + `vm_platform.dill`). Los ejecutables son otra cosa:
        //  - `libdartaotruntime.so` / `libfluttergensnapshot.so` viajan en el APK y se lanzan desde
        //    `nativeLibraryDir`;
        //  - `bin/dart`, `bin/dartvm` y `bin/dartaotruntime` del `.deb` quedan en `filesDir` y SELinux
        //    NO deja ejecutarlos (y la app no los necesita: los snapshots AOT —`gen_kernel`, `pub`—
        //    los lanza `dartaotruntime`).
        // Un `bin/dart` que no arranca NO puede convertir una extraccion correcta en "no instalado".
        val runtime = probeDartRuntime(context)
        if (runtime.runnable) {
            progress(
                "SDK Dart ${FlutterToolchainPaths.DART_VERSION} instalado: ${extracted.files} ficheros " +
                    "(${formatMb(extracted.bytes)}) en ${dartDir.absolutePath}; runtime ejecutable: " +
                    "${runtime.executable?.absolutePath} -> ${runtime.versionLine}"
            )
        } else {
            progress(
                "SDK Dart ${FlutterToolchainPaths.DART_VERSION} extraido (${extracted.files} ficheros, " +
                    "${formatMb(extracted.bytes)}): los datos estan completos, pero NINGUN runtime de Dart " +
                    "se puede ejecutar desde este APK. Detalle pieza a pieza:"
            )
            runtime.attempts.forEach { attempt -> progress("  - $attempt") }
            if (!FlutterToolchainPaths.isDartAotRuntimePackaged(context)) {
                progress(
                    "  El APK instalado (ABI '${FlutterToolchainPaths.deviceAbiName()}') no empaqueta " +
                        "`lib/<abi>/${FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME}`; " +
                        "la app solo puede ejecutar binarios de nativeLibraryDir. Instala una variante " +
                        "con ejecutables (arm64-v8a o x86_64; el .deb ya esta descargado: no se vuelve a bajar)."
                )
            }
        }
        // Estado intermedio real (p.ej. AOT en una ABI sin `libfluttergensnapshot.so`): aviso
        // accionable, nunca un fallo silencioso.
        FlutterToolchainPaths.aotBackendUnavailableReason(context)?.let { reason ->
            progress("Aviso: el AOT on-device no esta disponible en esta instalacion. $reason")
        }
        return true
    }

    /** `123456789` -> `117.7 MB` (solo para los mensajes de progreso). */
    private fun formatMb(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

    /** Descarga [url] a [dest] verificando tamaño y sha256. Devuelve si el fichero quedó válido. */
    @JvmStatic
    fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        expectedSha256: String,
        progress: (String) -> Unit,
    ): Boolean {
        if (dest.isFile && dest.length() == expectedSize && expectedSha256.isNotEmpty()) {
            val cached = sha256OfFile(dest)
            if (cached.equals(expectedSha256, ignoreCase = true)) {
                progress("Cache valida: ${dest.name}")
                return true
            }
        }

        dest.parentFile?.mkdirs()
        val tmp = File(dest.absolutePath + ".part")
        if (tmp.exists() && !tmp.delete()) {
            Log.w(TAG, "No se pudo borrar ${tmp.absolutePath}")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                progress("HTTP $code al descargar $url")
                return false
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var read = 0L
            var lastReportedPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (expectedSize > 0) {
                            val percent = ((read * 100) / expectedSize).toInt()
                            if (percent >= lastReportedPercent + 5) {
                                lastReportedPercent = percent
                                progress("Descargando ${dest.name}: $percent%")
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (expectedSize > 0 && read != expectedSize) {
                progress("Tamano incorrecto en ${dest.name}: $read != $expectedSize")
                tmp.delete()
                return false
            }

            val actualSha = toHex(digest.digest())
            if (expectedSha256.isNotEmpty() && !actualSha.equals(expectedSha256, ignoreCase = true)) {
                progress("sha256 incorrecto en ${dest.name}: $actualSha")
                tmp.delete()
                return false
            }

            if (tmp.renameTo(dest)) {
                true
            } else {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo descargando $url", e)
            progress("Error de red: ${e.message}")
            if (tmp.exists()) tmp.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }

    /* -------------------------------------------------------------------------------------- */
    /* Extraccion                                                                                */
    /* -------------------------------------------------------------------------------------- */

    private class ExtractionSummary {
        var files: Int = 0
        var bytes: Long = 0L
        var skippedUnsupported: Int = 0
    }

    private fun extractDartSdk(deb: File, dartDir: File): ExtractionSummary {
        val member = locateDataTarMember(deb)
            ?: throw IOException("No se encontro el miembro data.tar.* en ${deb.absolutePath}")

        val summary = ExtractionSummary()
        FileInputStream(deb).use { base ->
            base.channel.position(member.offset)
            val bounded = LimitedInputStream(BufferedInputStream(base, 256 * 1024), member.size)
            XZInputStream(bounded).use { xz ->
                val tar = TarStreamReader(xz)
                while (true) {
                    val entry = tar.nextEntry() ?: break
                    val relative = relativeSdkPath(entry.name)
                    val needed = relative != null && isNeeded(relative)

                    if (!needed || entry.typeFlag != '0') {
                        if (entry.typeFlag == '2') {
                            // symlinks del SDK: se ignoran a proposito (vm_platform_strong.dill
                            // apunta al prefijo de Termux y se materializa aparte).
                            summary.skippedUnsupported++
                        }
                        tar.skipCurrentEntry()
                        continue
                    }

                    val destination = File(dartDir, relative!!)
                    destination.parentFile?.mkdirs()
                    val written = tar.copyCurrentEntryTo(destination)
                    if (written != entry.size) {
                        throw IOException("Extraccion incompleta de $relative: $written != ${entry.size}")
                    }
                    summary.files++
                    summary.bytes += written
                }
            }
        }
        return summary
    }

    private fun isNeeded(relative: String): Boolean {
        if (REQUIRED_ENTRIES.contains(relative)) return true
        // TODOS los .dill de vm_platform* (el symlink strong queda cubierto por la copia posterior,
        // pero el paquete tambien trae vm_platform_product.dill como fichero real).
        return relative.startsWith("lib/_internal/vm_platform") && relative.endsWith(".dill")
    }

    /** `./data/data/com.termux/files/usr/lib/dart-sdk/bin/dart` -> `bin/dart`. */
    private fun relativeSdkPath(tarName: String): String? {
        val normalized = when {
            tarName.startsWith("./") -> tarName.substring(2)
            else -> tarName
        }
        val index = normalized.indexOf(SDK_PREFIX)
        if (index < 0) return null
        val relative = normalized.substring(index + SDK_PREFIX.length)
        return relative.ifEmpty { null }
    }

    /** Devuelve offset y tamaño del miembro `data.tar.*` dentro del `.deb` (contenedor `ar`). */
    @JvmStatic
    fun locateDataTarMember(deb: File): ArMember? {
        RandomAccessFile(deb, "r").use { raf ->
            if (raf.length() < AR_HEADER_SIZE) {
                throw IOException("Fichero demasiado corto para ser ar: ${deb.absolutePath}")
            }
            val magic = ByteArray(8)
            raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != AR_MAGIC) {
                throw IOException("Cabecera ar invalida en ${deb.absolutePath}")
            }

            var position = 8L
            val total = raf.length()
            var fallback: ArMember? = null

            while (position + AR_HEADER_SIZE <= total) {
                raf.seek(position)
                val header = ByteArray(AR_HEADER_SIZE)
                raf.readFully(header)

                val name = String(header, 0, 16, Charsets.US_ASCII).trim().trimEnd('/')
                val sizeField = String(header, 48, 10, Charsets.US_ASCII).trim()
                val magicField = String(header, 58, 2, Charsets.US_ASCII)
                if (magicField != "`\n") {
                    throw IOException("Cabecera de miembro ar invalida en offset $position")
                }
                val size = sizeField.toLongOrNull()
                    ?: throw IOException("Tamano de miembro ar ilegible: '$sizeField'")
                val bodyOffset = position + AR_HEADER_SIZE

                if (name.startsWith("data.tar")) {
                    return ArMember(name, bodyOffset, size)
                }
                if (name == "control.tar.xz" || name == "control.tar.gz") {
                    fallback = ArMember(name, bodyOffset, size)
                }

                position = bodyOffset + size + (size % 2)
            }
            return fallback
        }
    }

    /** Miembro de un contenedor `ar`. */
    class ArMember(
        @JvmField val name: String,
        @JvmField val offset: Long,
        @JvmField val size: Long,
    )

    /** Entrada de tar (solo lo que usa el instalador). */
    class TarEntry(
        @JvmField val name: String,
        @JvmField val size: Long,
        @JvmField val typeFlag: Char,
        @JvmField val linkName: String,
    )

    /**
     * Lector de tar **propio** (no hay `commons-compress` en el classpath): soporta ustar y
     * pax/GNU longname (`x`, `g`, `L`, `K`), que es lo que produce `dpkg-deb`.
     *
     * Clase anidada normal (no lleva `@JvmStatic`: esa anotacion solo vale para funciones y
     * propiedades de un `object`/`companion object`; sobre una clase es un error de compilacion).
     * Se instancia como `FlutterToolchainInstaller.TarStreamReader(input)`.
     */
    class TarStreamReader(private val input: InputStream) {

        private var currentSize: Long = 0L
        private var currentConsumed: Long = 0L
        private var pendingLongName: String? = null

        /** Avanza a la siguiente entrada, o `null` en el terminador final. */
        fun nextEntry(): TarEntry? {
            finishCurrentEntry()
            while (true) {
                val header = readBlock() ?: return null
                if (header.all { it == 0.toByte() }) {
                    return null
                }
                val name = readString(header, 0, 100)
                val sizeField = readString(header, 124, 12).trim()
                val size = if (sizeField.isEmpty()) 0L else sizeField.toLongOrNull(8)
                    ?: throw IOException("Tamano octal de tar ilegible: '$sizeField'")
                val typeFlag = header[156].toInt().toChar()
                val linkName = readString(header, 157, 100)
                val prefix = readString(header, 345, 155)

                var effectiveName = if (prefix.isNotEmpty()) "$prefix/$name" else name
                val longName = pendingLongName
                if (longName != null) {
                    effectiveName = longName
                    pendingLongName = null
                }

                when (typeFlag) {
                    'x', 'g' -> {
                        // pax extended header: su contenido no lo usamos
                        skipBytes(size)
                        requirePadding(size)
                        continue
                    }
                    'L' -> {
                        // GNU longname: el nombre real es el contenido
                        pendingLongName = readStringFully(size)
                        requirePadding(size)
                        continue
                    }
                    'K' -> {
                        skipBytes(size)
                        requirePadding(size)
                        continue
                    }
                    else -> {
                        currentSize = size
                        currentConsumed = 0L
                        return TarEntry(effectiveName, size, typeFlag, linkName)
                    }
                }
            }
        }

        /** Copia la entrada actual a [destination]; devuelve bytes escritos (con padding aplicado). */
        fun copyCurrentEntryTo(destination: File): Long {
            var written = 0L
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (written < currentSize) {
                    val toRead = minOf(buffer.size.toLong(), currentSize - written).toInt()
                    val n = input.read(buffer, 0, toRead)
                    if (n <= 0) throw IOException("EOF inesperado leyendo ${destination.name}")
                    output.write(buffer, 0, n)
                    written += n
                }
                output.flush()
            }
            currentConsumed = currentSize
            finishCurrentEntry()
            return written
        }

        /** Salta la entrada actual. */
        fun skipCurrentEntry() {
            finishCurrentEntry()
        }

        private fun finishCurrentEntry() {
            if (currentSize > 0) {
                val remaining = currentSize - currentConsumed
                if (remaining > 0) {
                    skipBytes(remaining)
                }
                requirePadding(currentSize)
            }
            currentSize = 0L
            currentConsumed = 0L
        }

        /** Salta el padding de bloque de tar (los datos van alineados a [TAR_BLOCK_SIZE]). */
        private fun requirePadding(size: Long) {
            val padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
            if (padding > 0L) {
                skipBytes(padding)
            }
        }

        private fun skipBytes(count: Long): Long {
            var skipped = 0L
            val buffer = ByteArray(32 * 1024)
            while (skipped < count) {
                val toRead = minOf(buffer.size.toLong(), count - skipped).toInt()
                val n = input.read(buffer, 0, toRead)
                if (n <= 0) throw IOException("EOF inesperado saltando datos de tar")
                skipped += n
            }
            return skipped
        }

        private fun readStringFully(size: Long): String {
            val bytes = ByteArrayOutputStream()
            var read = 0L
            val buffer = ByteArray(1024)
            while (read < size) {
                val toRead = minOf(buffer.size.toLong(), size - read).toInt()
                val n = input.read(buffer, 0, toRead)
                if (n <= 0) break
                bytes.write(buffer, 0, n)
                read += n
            }
            return bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
        }

        private fun readBlock(): ByteArray? {
            val block = ByteArray(TAR_BLOCK_SIZE)
            var offset = 0
            while (offset < TAR_BLOCK_SIZE) {
                val n = input.read(block, offset, TAR_BLOCK_SIZE - offset)
                if (n <= 0) {
                    if (offset == 0) return null
                    throw IOException("Cabecera de tar truncada ($offset bytes)")
                }
                offset += n
            }
            return block
        }

        private fun readString(block: ByteArray, offset: Int, length: Int): String {
            var end = offset
            val limit = offset + length
            while (end < limit && block[end] != 0.toByte()) {
                end++
            }
            return String(block, offset, end - offset, Charsets.UTF_8)
        }
    }

    /** Envuelve un [InputStream] limitando la lectura a [limit] bytes. */
    private class LimitedInputStream(
        private val delegate: InputStream,
        private var remaining: Long,
    ) : InputStream() {

        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = delegate.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = delegate.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }

        override fun available(): Int = minOf(remaining, delegate.available().toLong()).toInt()

        override fun close() {
            delegate.close()
        }
    }

    /* -------------------------------------------------------------------------------------- */
    /* Helpers                                                                                  */
    /* -------------------------------------------------------------------------------------- */

    private fun writeMarker(
        context: android.content.Context,
        spec: FlutterToolchainPaths.DartPackageSpec,
        summary: ExtractionSummary,
    ) {
        val marker = StringBuilder()
            .append("version=").append(FlutterToolchainPaths.DART_VERSION).append('\n')
            .append("abi=").append(spec.abi).append('\n')
            .append("deb=").append(spec.fileName).append('\n')
            .append("sha256=").append(spec.sha256).append('\n')
            .append("files=").append(summary.files).append('\n')
            .append("bytes=").append(summary.bytes).append('\n')
            .append("installedAt=").append(System.currentTimeMillis()).append('\n')
            .toString()
        FlutterToolchainPaths.dartMarkerFile(context).writeText(marker)
    }

    /**
     * Version de Dart que reporta el runtime que **se puede ejecutar de verdad**
     * ([probeDartRuntime]): `libdartaotruntime.so --version` desde `nativeLibraryDir`.
     *
     * NUNCA se lanza `bin/dart`: es el CLI del `.deb`, vive en `filesDir` y SELinux prohibe
     * ejecutarlo (AVC `execute_no_trans` sobre `app_data_file` en el dominio `untrusted_app`); la app
     * tampoco lo necesita, porque los snapshots AOT (`gen_kernel`, `dart pub`) los lanza
     * `dartaotruntime`.
     *
     * Devuelve `null` solo si **ningun** runtime se puede ejecutar. Eso NO es un fallo de instalacion:
     * los datos del SDK pueden estar completos (ver los motivos en [DartRuntimeProbe.attempts]).
     */
    @JvmStatic
    fun runVersionCheck(context: android.content.Context): String? =
        probeDartRuntime(context).versionLine

    /**
     * Resultado de intentar **ejecutar** un runtime de Dart del toolchain.
     *
     * [runnable] no se deduce de los permisos del fichero: sale de una ejecucion real. Un ELF que la
     * app escribe en `filesDir` existe, tiene bit `x` y **no** se ejecuta con `targetSdk >= 29`.
     */
    class DartRuntimeProbe(
        /** Candidato que SI se ejecuto, o `null` si ninguno. */
        @JvmField val executable: File?,
        /** `true` si [executable] esta en `nativeLibraryDir` (empaquetado en el APK). */
        @JvmField val packaged: Boolean,
        /** Primera linea de `--version` del candidato ejecutado, o `null`. */
        @JvmField val versionLine: String?,
        /** Un motivo por candidato probado, en orden: QUE pieza y POR QUE no se puede ejecutar. */
        @JvmField val attempts: List<String>,
    ) {
        val runnable: Boolean get() = versionLine != null
    }

    /**
     * Prueba, **ejecutando**, los runtimes de Dart disponibles, en orden de preferencia:
     *
     * 1. `nativeLibraryDir/libdartaotruntime.so` — el empaquetado en el APK, unica ubicacion
     *    ejecutable para la app (variante `arm64-v8a`);
     * 2. `<filesDir>/flutter-toolchain/dart/bin/dartaotruntime` — extraido del `.deb`: existe, pero en
     *    `targetSdk >= 29` SELinux deniega `execute_no_trans` sobre `app_data_file`;
     * 3. `<filesDir>/flutter-toolchain/dart/bin/dart` — el CLI del `.deb` (misma limitacion, y la app
     *    no lo usa; se sondea solo para poder explicarlo en el log).
     */
    @JvmStatic
    fun probeDartRuntime(context: android.content.Context): DartRuntimeProbe {
        val nativeDir = FlutterToolchainPaths.nativeLibraryDir(context)
        val candidates = mutableListOf<Pair<String, File>>()
        val packaged = FlutterToolchainPaths.packagedExecutable(
            context, FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME
        )
        if (packaged != null) {
            candidates.add(
                "${FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME} (nativeLibraryDir)" to packaged
            )
        } else {
            candidates.add(
                "dartaotruntime (filesDir; este APK —ABI " +
                    "'${FlutterToolchainPaths.deviceAbiName()}'— no empaqueta " +
                    "${FlutterToolchainPaths.PACKAGED_DART_AOT_RUNTIME})" to
                    FlutterToolchainPaths.dartAotRuntime(context)
            )
        }
        candidates.add("dart (filesDir, CLI del .deb) " to FlutterToolchainPaths.dartExecutable(context))

        val attempts = mutableListOf<String>()
        for ((label, file) in candidates) {
            if (!file.isFile) {
                attempts.add("$label: ${file.absolutePath} no existe")
                continue
            }
            val result = probeExecutableResult(file)
            if (result.output != null) {
                return DartRuntimeProbe(
                    executable = file,
                    packaged = nativeDir != null &&
                        file.absolutePath.startsWith(nativeDir.absolutePath, ignoreCase = false),
                    versionLine = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim(),
                    attempts = attempts,
                )
            }
            attempts.add("$label: ${file.absolutePath} no se pudo ejecutar (${result.failure})")
        }
        return DartRuntimeProbe(null, false, null, attempts)
    }

    /**
     * Resultado de una sonda de ejecucion: la salida, o el motivo exacto del fallo.
     *
     * [failure] distingue "no existe" de "SELinux/W^X no deja ejecutarlo desde aqui"
     * (`Permission denied`, `error=13`), que es lo que hay que poder contarle al usuario.
     */
    class ExecProbeResult(@JvmField val output: String?, @JvmField val failure: String?) {
        val success: Boolean get() = output != null
    }

    /**
     * **Ejecuta** [executable] con [args] y devuelve salida + motivo del fallo (nunca lanza).
     *
     * @param timeoutSeconds margen generoso: `--version` no toca disco y termina en milisegundos,
     *   pero un dispositivo cargado puede tardar; nunca se deja un proceso colgado.
     */
    @JvmStatic
    @JvmOverloads
    fun probeExecutableResult(
        executable: File,
        args: List<String> = listOf("--version"),
        timeoutSeconds: Long = 20L,
    ): ExecProbeResult {
        if (!executable.isFile) {
            return ExecProbeResult(null, "no existe")
        }
        var process: Process? = null
        return try {
            process = ProcessBuilder(listOf(executable.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                Log.w(TAG, "${executable.name} no termino en ${timeoutSeconds} s")
                return ExecProbeResult(null, "no termino en ${timeoutSeconds} s")
            }
            if (process.exitValue() != 0) {
                Log.w(TAG, "${executable.name} salio con ${process.exitValue()}: ${output.take(200)}")
                return ExecProbeResult(
                    null,
                    "salio con codigo ${process.exitValue()}: ${output.take(160)}"
                )
            }
            ExecProbeResult(output, null)
        } catch (e: Exception) {
            // Caso tipico (W^X): java.io.IOException: Cannot run program …: error=13, Permission denied
            // AVC: denied { execute_no_trans } … scontext=u:r:untrusted_app tcontext=…:app_data_file
            val message = e.message ?: e.javaClass.name
            val reason = if (message.contains("Permission denied") || message.contains("error=13")) {
                "SELinux/W^X: un ELF de filesDir no se puede ejecutar con targetSdk >= 29 " +
                    "(AVC execute_no_trans sobre app_data_file, dominio untrusted_app); solo se ejecuta " +
                    "desde nativeLibraryDir"
            } else {
                message
            }
            Log.w(TAG, "No se pudo ejecutar ${executable.absolutePath}: $reason")
            ExecProbeResult(null, reason)
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    /**
     * **Ejecuta** [executable] con [args] y devuelve la salida recortada, o `null` si no se pudo
     * ejecutar (permiso denegado por SELinux, formato/ABI incorrectos, timeout…).
     *
     * Es la comprobacion "de verdad" que usa [FlutterToolchainManager.isReady]: no basta con que el
     * fichero exista, porque un ELF extraido en `filesDir` existe y **no** se puede ejecutar en
     * `targetSdk >= 29` (ver la nota de la clase). Para el motivo del fallo, usar
     * [probeExecutableResult].
     */
    @JvmStatic
    @JvmOverloads
    fun probeExecutable(
        executable: File,
        args: List<String> = listOf("--version"),
        timeoutSeconds: Long = 20L,
    ): String? = probeExecutableResult(executable, args, timeoutSeconds).output

    /** Primera linea no vacia de `--version` de un ejecutable del toolchain, o `null`. */
    @JvmStatic
    fun executableVersion(executable: File): String? =
        probeExecutable(executable)?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return toHex(digest.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            builder.append(Character.forDigit((byte.toInt() shr 4) and 0xF, 16))
            builder.append(Character.forDigit(byte.toInt() and 0xF, 16))
        }
        return builder.toString()
    }

    @JvmStatic
    fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "No se pudo borrar ${file.absolutePath}")
        }
    }
}
