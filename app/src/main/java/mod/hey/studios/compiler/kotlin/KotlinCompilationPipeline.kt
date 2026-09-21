package mod.hey.studios.compiler.kotlin

import a.a.a.ProjectBuilder
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Extension point for Kotlin compilation pipeline customizations.
 */
interface KotlinCompilationExtension {
    fun configureArguments(
        rawArguments: MutableList<String>,
        compilerArguments: K2JVMCompilerArguments,
        builder: ProjectBuilder,
        filesToCompile: List<File>
    ) {
    }

    fun onCompilationFinished(
        success: Boolean,
        diagnostics: String,
        builder: ProjectBuilder,
        filesToCompile: List<File>
    ) {
    }
}

object KotlinCompilationPipeline {
    private val extensions = CopyOnWriteArrayList<KotlinCompilationExtension>()

    init {
        // Placeholder extension keeps the pipeline ready for KSP plugin wiring.
        register(KspReadyCompilationExtension())
    }

    @JvmStatic
    fun register(extension: KotlinCompilationExtension) {
        if (extensions.none { it.javaClass == extension.javaClass }) {
            extensions.add(extension)
        }
    }

    @JvmStatic
    fun unregister(extension: KotlinCompilationExtension) {
        extensions.removeIf { it.javaClass == extension.javaClass }
    }

    @JvmStatic
    fun snapshot(): List<KotlinCompilationExtension> {
        return extensions.toList()
    }
}

class KspReadyCompilationExtension : KotlinCompilationExtension {
    override fun configureArguments(
        rawArguments: MutableList<String>,
        compilerArguments: K2JVMCompilerArguments,
        builder: ProjectBuilder,
        filesToCompile: List<File>
    ) {
        // Intentionally no-op: reserved for future KSP compiler plugin wiring.
    }
}
