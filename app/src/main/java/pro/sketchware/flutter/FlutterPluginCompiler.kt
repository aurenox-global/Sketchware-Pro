package pro.sketchware.flutter

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Compilacion **de las fuentes Java/Kotlin de los plugins** de Flutter con los compiladores que el
 * fork ya lleva empaquetados (carril I, Fase 8; plan de 6 pasos del carril P §3.3).
 *
 * Sin Gradle y sin depender de `ProjectBuilder`: recibe listas de ficheros y un classpath, y deja
 * los `.class` en el directorio de clases compiladas del proyecto (el mismo que ECJ/kotlinc usan y
 * que D8 dexea). Asi los plugins entran al APK por el camino que ya existe.
 *
 * — Java: `org.eclipse.jdt.internal.compiler.batch.Main` (ECJ 3.26, el mismo que usa
 *   `ProjectBuilder.compileJavaCode`, linea ~639).
 * — Kotlin: `K2JVMCompiler` (kotlinc 2.1.21 portado a Android, el mismo que usa
 *   `mod.hey.studios.compiler.kotlin.KotlinCompiler`), con `noStdlib`/`noReflect`/`noJdk` y sin
 *   `kotlinHome` real, igual que el fork.
 *
 * Este fichero **no** importa nada de `android.*` ni de `a.a.a.*` a proposito: su logica se puede
 * type-checkear (y ejercitar) en el host con el compilador de Kotlin, que es la unica verificacion
 * posible mientras el build lo hace el padre (Gradle prohibido en los carriles).
 */
object FlutterPluginCompiler {

    /** Resultado de compilar la parte Android de los plugins del proyecto. */
    class PluginCompileResult(
        val javaSources: List<File>,
        val kotlinSources: List<File>,
        val flutterBuildSources: List<File>,
        val compiledJava: Boolean,
        val compiledKotlin: Boolean,
        /** Pasos que faltan y que el build no puede cubrir por si solo (sin red, sin AAR…). */
        val missingSteps: List<String>,
        val log: String,
    ) {
        val success: Boolean get() = compiledJava && compiledKotlin
    }

    /**
     * Fuentes que Flutter genera bajo `<flutterRoot>/.dart_tool/flutter_build` y que **tienen**
     * que entrar en la compilacion: `io/flutter/plugins/GeneratedPluginRegistrant.java` (lo busca
     * `FlutterActivity` por reflexion) y, si hubiera, cualquier `.java`/`.kt` generado.
     *
     * Es el punto 1 del plan del carril P (§7.1): antes el registrant se generaba y se quedaba en
     * disco sin llegar al APK.
     */
    @JvmStatic
    fun collectFlutterBuildSources(flutterRoot: File): List<File> {
        val buildDir = FlutterToolchainPaths.flutterBuildDir(flutterRoot)
        if (!buildDir.isDirectory) {
            return emptyList()
        }
        return buildDir.walkTopDown()
            .filter { it.isFile && (it.extension == "java" || it.extension == "kt") }
            .sortedBy { it.absolutePath }
            .toList()
    }

    /**
     * Compila los `.java` indicados con ECJ a [destination] y devuelve si no hubo errores.
     *
     * Mismos flags que el fork (`-17 -nowarn -d … -cp … -proc:none`): si compilara a otro nivel de
     * lenguaje, D8 se quejaria de bytecode no soportado (minSdk 26 = Java 8+ con desugaring).
     */
    fun compileJava(
        sources: List<File>,
        classpath: String,
        destination: File,
        log: StringBuilder,
    ): Boolean {
        val files = sources.filter { it.isFile }
        if (files.isEmpty()) {
            return true
        }
        destination.mkdirs()

        val args = ArrayList<String>()
        args.add("-17")
        args.add("-nowarn")
        args.add("-d")
        args.add(destination.absolutePath)
        args.add("-cp")
        args.add(classpath)
        args.add("-proc:none")
        for (file in files) {
            args.add(file.absolutePath)
        }

        val outBuffer = StringWriter()
        val errBuffer = StringWriter()
        var globalErrors = -1
        try {
            PrintWriter(outBuffer).use { out ->
                PrintWriter(errBuffer).use { err ->
                    val main = org.eclipse.jdt.internal.compiler.batch.Main(out, err, false, null, null)
                    log.append("$ ecj ").append(args.joinToString(" ")).append('\n')
                    main.compile(args.toTypedArray())
                    globalErrors = main.globalErrorsCount
                }
            }
        } catch (e: Throwable) {
            log.append("[error] ECJ fallo: ").append(e.javaClass.simpleName).append(": ")
                .append(e.message).append('\n')
            return false
        }
        log.append(outBuffer.toString())
        log.append(errBuffer.toString())
        if (globalErrors > 0) {
            log.append("[error] ECJ: ").append(globalErrors).append(" errores\n")
            return false
        }
        log.append("[ok] ").append(files.size).append(" fuentes Java de plugins compiladas (ECJ)\n")
        return true
    }

    /**
     * Compila los `.kt` indicados con `K2JVMCompiler` a [destination].
     *
     * `kotlinHome` se usa solo como directorio de trabajo del compilador (el fork lo apunta a
     * `<bin>/kotlin_home`); no se necesita un home real porque van `noStdlib`/`noReflect`/`noJdk`.
     */
    fun compileKotlin(
        sources: List<File>,
        classpath: String,
        destinationDir: File,
        kotlinHomeDir: File,
        log: StringBuilder,
    ): Boolean {
        val files = sources.filter { it.isFile }
        if (files.isEmpty()) {
            return true
        }
        destinationDir.mkdirs()
        kotlinHomeDir.mkdirs()

        val arguments = ArrayList<String>()
        arguments.add("-cp")
        arguments.add(classpath)
        for (file in files) {
            arguments.add(file.absolutePath)
        }

        val exitCode = try {
            val compiler = org.jetbrains.kotlin.cli.jvm.K2JVMCompiler()
            val collector: org.jetbrains.kotlin.cli.common.messages.MessageCollector =
                org.jetbrains.kotlin.cli.common.messages.MessageCollector.NONE
            val args = org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments().apply {
                compileJava = false
                includeRuntime = false
                noJdk = true
                noReflect = true
                noStdlib = true
                kotlinHome = kotlinHomeDir.absolutePath
                destination = destinationDir.absolutePath
                pluginClasspaths = emptyArray()
            }
            log.append("$ kotlinc ").append(arguments.joinToString(" ")).append('\n')
            compiler.parseArguments(arguments.toTypedArray(), args)
            compiler.exec(collector, org.jetbrains.kotlin.config.Services.EMPTY, args)
        } catch (e: Throwable) {
            log.append("[error] kotlinc fallo: ").append(e.javaClass.simpleName).append(": ")
                .append(e.message).append('\n')
            return false
        }

        // kotlinc deja `.kotlin_module` que hacen fallar a D8: el fork los borra y aqui se replica.
        deleteQuietly(File(destinationDir, "META-INF"))

        if (exitCode.code != 0) {
            log.append("[error] kotlinc salio con ").append(exitCode.name).append('\n')
            return false
        }
        log.append("[ok] ").append(files.size).append(" fuentes Kotlin de plugins compiladas (kotlinc)\n")
        return true
    }

    /**
     * Compila **todo** lo que aporta un plugin (Java + Kotlin) y las fuentes generadas por Flutter
     * (`flutter_build`, recursivo, incluido el registrant). El classpath lo calcula el llamante
     * (`ProjectBuilder.getClasspath()`), que incluye `android.jar`, el embedding de Flutter y las
     * dependencias resueltas (AAR de los plugins).
     */
    @JvmStatic
    fun compilePluginSources(
        javaSources: List<File>,
        kotlinSources: List<File>,
        flutterBuildSources: List<File>,
        classpath: String,
        destination: File,
        kotlinHome: File,
    ): PluginCompileResult {
        val log = StringBuilder()
        val allJava = ArrayList<File>()
        allJava.addAll(flutterBuildSources.filter { it.extension == "java" })
        allJava.addAll(javaSources.filter { it.extension == "java" })
        val allKotlin = ArrayList<File>()
        allKotlin.addAll(flutterBuildSources.filter { it.extension == "kt" })
        allKotlin.addAll(kotlinSources.filter { it.extension == "kt" })

        val missing = ArrayList<String>()
        if (allKotlin.isNotEmpty() && !hasKotlinCompiler()) {
            missing.add(
                "kotlinc no esta en el classpath de la app (el fork lo empaqueta como " +
                    "`libs.bundles.kotlin.compiler`); sin el, los plugins Kotlin no se compilan."
            )
        }
        if (allJava.isNotEmpty() && !hasEclipseCompiler()) {
            missing.add("ECJ no esta en el classpath de la app (`libs.ecj`).")
        }

        val javaOk = if (hasEclipseCompiler()) {
            compileJava(allJava, classpath, destination, log)
        } else {
            false
        }
        val kotlinOk = if (allKotlin.isNotEmpty()) {
            if (hasKotlinCompiler()) {
                compileKotlin(allKotlin, classpath, destination, kotlinHome, log)
            } else {
                false
            }
        } else {
            true
        }

        return PluginCompileResult(
            javaSources = allJava,
            kotlinSources = allKotlin,
            flutterBuildSources = flutterBuildSources,
            compiledJava = javaOk,
            compiledKotlin = kotlinOk,
            missingSteps = missing,
            log = log.toString(),
        )
    }

    /** `true` si ECJ esta disponible en tiempo de ejecucion (lo esta: `libs.ecj`). */
    @JvmStatic
    fun hasEclipseCompiler(): Boolean = try {
        Class.forName("org.eclipse.jdt.internal.compiler.batch.Main")
        true
    } catch (_: Throwable) {
        false
    }

    /** `true` si `K2JVMCompiler` esta disponible en tiempo de ejecucion. */
    @JvmStatic
    fun hasKotlinCompiler(): Boolean = try {
        Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        true
    } catch (_: Throwable) {
        false
    }

    private fun deleteQuietly(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteQuietly(it) }
        }
        file.delete()
    }
}
