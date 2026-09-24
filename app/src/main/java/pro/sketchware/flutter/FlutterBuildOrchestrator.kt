package pro.sketchware.flutter

import android.content.Context
import android.util.Log
import a.a.a.ProjectBuilder
import a.a.a.jC
import a.a.a.wq
import a.a.a.yq
import mod.jbk.build.BuildProgressReceiver
import mod.jbk.build.BuiltInLibraries
import java.io.File
import mod.hey.studios.compiler.flutter.FlutterCompilerBridge

/**
 * Orquestador de un build Flutter completo, no interactivo (carril C).
 *
 * Sigue el patrón de `KmpMultiTargetBuildOrchestrator.kt`: un runner sustituible, todo envuelto
 * en `try/catch`, y un resultado tipado ([FlutterBuildResult]) en vez de excepciones.
 *
 * Secuencia (espejo de `DesignActivity.BuildTask.doInBackground`):
 * 1. toolchain on-device ([FlutterToolchainManager.ensureInstalled]). **Sin descargas silenciosas**:
 *    solo descarga si el llamante lo autoriza (`allowToolchainDownload`), que la UI hace despues del
 *    dialogo de consentimiento de la Fase 9.
 * 2. compilación Dart ([FlutterDartCompiler]) -> `libapp.so` (AOT) o `kernel_blob.bin` (JIT).
 * 3. staging Flutter (nativas, `flutter_assets`, embedding, manifest) vía
 *    [FlutterCompilerBridge.compileFlutterCodeIfPossible].
 * 4. pipeline Android existente: AAPT2 -> Java (ecj) -> D8 -> APK -> firma debug.
 *
 * IMPORTANTE: este método es **bloqueante** (`ExportProjectActivity` tarda minutos). Nunca se
 * llama desde el hilo de UI. El carril C no puede ejecutar Gradle ni probar esto end-to-end
 * (ver ESTADO HONESTO del informe).
 */
object FlutterBuildOrchestrator {

    private const val TAG = "FlutterBuildOrch"

    /**
     * Ejecuta el build completo **sin** permiso para descargar el toolchain.
     *
     * Equivale a [build] con `allowToolchainDownload = false`: si falta el toolchain de Dart, el
     * build se aborta con un mensaje claro en el log en vez de descargar cientos de MB por su cuenta.
     * La UI que ya obtuvo el consentimiento del usuario llama a la variante con `true`.
     */
    @JvmStatic
    fun build(
        context: Context,
        scId: String,
        mode: FlutterBuildMode,
        progress: (String) -> Unit,
    ): FlutterBuildResult = build(context, scId, mode, allowToolchainDownload = false, progress = progress)

    /**
     * Ejecuta el build completo. Bloqueante.
     *
     * @param allowToolchainDownload `true` **solo** si el usuario ya ha visto el dialogo de
     *   consentimiento (tamaño de la descarga + aviso de red) y ha aceptado. Con `false`, si falta el
     *   toolchain, no se toca la red: se registra
     *   [FlutterToolchainManager.MESSAGE_TOOLCHAIN_REQUIRES_CONSENT] y el build falla.
     */
    @JvmStatic
    fun build(
        context: Context,
        scId: String,
        mode: FlutterBuildMode,
        allowToolchainDownload: Boolean,
        progress: (String) -> Unit,
    ): FlutterBuildResult {
        val startedAt = System.currentTimeMillis()
        val log = StringBuilder()

        fun emit(message: String) {
            log.append(message).append('\n')
            Log.d(TAG, message)
            try {
                progress(message)
            } catch (e: Exception) {
                Log.w(TAG, "El callback de progreso fallo", e)
            }
        }

        return try {
            emit("Flutter build ($mode) para sc_id=$scId")

            val projectFilesDir = File(wq.b(scId), "files")
            val flutterRoot = FlutterProjectStore.rootDirectory(projectFilesDir)
            if (!FlutterProjectStore.isFlutterProject(projectFilesDir)) {
                return FlutterBuildResult.failure(
                    log.append("No hay pubspec.yaml en ").append(flutterRoot.absolutePath).append('\n').toString(),
                    System.currentTimeMillis() - startedAt,
                )
            }

            emit("Comprobando toolchain on-device...")
            // El toolchain se pide para el modo pedido: engine debug/release + framework Dart.
            // La descarga (cientos de MB) SOLO ocurre si la UI ya tiene el consentimiento del usuario.
            if (!FlutterToolchainManager.ensureInstalled(context, mode, allowToolchainDownload) { message -> emit(message) }) {
                if (!allowToolchainDownload) {
                    emit("Build abortado: falta el toolchain y no hay consentimiento de descarga")
                }
                return FlutterBuildResult.failure(
                    log.append("Toolchain Flutter no disponible\n").toString(),
                    System.currentTimeMillis() - startedAt,
                )
            }

            emit("Compilando Dart ($mode)...")
            // RELEASE_AOT usa nuestro `gen_snapshot` product + compressed pointers (Fase 8, carril I).
            // Si esta instalada una ABI sin backend (armeabi-v7a/x86) o una variante del APK que no
            // empaqueta los ejecutables (arm64-v8a y x86_64 si lo hacen), `compile` falla con un
            // mensaje claro en vez de generar un libapp.so incompatible con el engine.
            val compileResult = FlutterDartCompiler.compile(context, flutterRoot, mode, progress = { message -> emit(message) })
            log.append(compileResult.log)
            if (!compileResult.success) {
                return FlutterBuildResult.failure(
                    log.append("Fallo la compilacion Dart\n").toString(),
                    System.currentTimeMillis() - startedAt,
                )
            }

            emit("Empaquetando APK con el pipeline Android...")
            val apkPath = runAndroidPipeline(context, scId, compileResult) { message -> emit(message) }

            if (apkPath == null) {
                FlutterBuildResult.failure(log.toString(), System.currentTimeMillis() - startedAt)
            } else {
                emit("APK listo: $apkPath")
                FlutterBuildResult.success(apkPath, log.toString(), System.currentTimeMillis() - startedAt)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Build Flutter fallido", e)
            log.append("[exception] ").append(e.javaClass.name).append(": ").append(e.message).append('\n')
            FlutterBuildResult.failure(log.toString(), System.currentTimeMillis() - startedAt)
        }
    }

    /**
     * Lanza el pipeline Android del fork de forma síncrona.
     *
     * @return ruta del APK final instalable, o `null` si algo falló.
     */
    private fun runAndroidPipeline(
        context: Context,
        scId: String,
        dartResult: FlutterDartCompileResult,
        progress: (String) -> Unit,
    ): String? {
        val projectMetadata = yq(context, scId)
        val fileManager = jC.b(scId)
        val dataManager = jC.a(scId)
        val libraryManager = jC.c(scId)

        projectMetadata.a(libraryManager, fileManager, dataManager)

        val receiver = BuildProgressReceiver { message, _ -> progress(message) }
        val builder = ProjectBuilder(receiver, context, projectMetadata)

        builder.buildBuiltInLibraryInformation()
        projectMetadata.b(fileManager, dataManager, libraryManager, builder.builtInLibraryManager)
        projectMetadata.f()
        projectMetadata.e()

        builder.maybeExtractAapt2()
        progress("Extracting built-in libraries...")
        BuiltInLibraries.extractCompileAssets(receiver)

        // Staging Flutter + Dart: nativas, flutter_assets, manifest, embedding.
        if (!FlutterCompilerBridge.compileFlutterCodeIfPossible(context, builder, dartResult)) {
            progress("El staging de Flutter fallo")
            return null
        }

        progress("AAPT2 is running...")
        builder.compileResources()
        progress("Generating view binding...")
        builder.generateViewBinding()
        progress("Java is compiling...")
        builder.compileJavaCode()
        progress(builder.dxRunningText)
        builder.createDexFilesFromClasses()
        progress("Merging DEX files...")
        builder.getDexFilesReady()
        progress("Building APK...")
        builder.buildApk()
        progress("Signing APK...")
        builder.signDebugApk()

        return projectMetadata.finalToInstallApkPath
    }
}
