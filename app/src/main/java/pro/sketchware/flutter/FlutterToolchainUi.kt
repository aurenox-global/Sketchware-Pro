package pro.sketchware.flutter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pro.sketchware.R
import pro.sketchware.activities.settings.FeatureFlagsActivity
import pro.sketchware.featureflags.FeatureFlags
import java.io.File
import java.io.Serializable

/**
 * Estado y dialogos del toolchain de Dart/Flutter, reutilizables desde **cualquier** pantalla
 * (ajustes/creacion del proyecto, pantalla de diseno, editor de logica).
 *
 * Antes este flujo solo existia enterrado en el menu del editor de logica
 * (`LogicEditorActivity`), detras de `FLUTTER_EXPERIMENTAL_ENABLE`. Esta clase expone lo mismo
 * (estado real, consentimiento, descarga, borrado) para que se pueda ofrecer en sitios visibles
 * sin duplicar la logica: todo sale de [FlutterToolchainManager].
 *
 * Reglas de uso:
 *  - [statusLine] y [collectState] son **bloqueantes** (ejecutan binarios y miden disco): llamar
 *    siempre desde un hilo de fondo.
 *  - [showStatusDialog] y [showDisabledDialog] se llaman desde el hilo de UI.
 */
object FlutterToolchainUi {

    private const val TAG = "FlutterToolchainUi"

    /** Estado real del toolchain, ya calculado (inmutable desde el punto de vista de la UI). */
    class State {
        @JvmField var mode: FlutterBuildMode = FlutterProjectDefaults.DEFAULT_MODE
        @JvmField var installed: Boolean = false
        @JvmField var dartVersion: String? = null
        @JvmField var notReadyReason: String? = null
        @JvmField var directory: String = ""
        @JvmField var summary: String = ""
        @JvmField var downloadBytes: Long = 0L
        @JvmField var fullDownloadBytes: Long = 0L
        @JvmField var installedBytes: Long = 0L
        @JvmField var components: List<FlutterToolchainManager.ToolchainComponent> = emptyList()
    }

    /** `true` si la funcion Flutter esta activada en Ajustes > Feature flags. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        FeatureFlags.isEnabled(context, FeatureFlags.Key.FLUTTER_EXPERIMENTAL_ENABLE)

    /**
     * Ejecuta la comprobacion del `FLUTTER_EXPERIMENTAL_ENABLE` **por defecto** si la clave no
     * existe todavia en preferencias. Asi una instalacion ya existente (que quiza no ha abierto
     * nunca la pantalla de Feature flags) tambien recibe el nuevo valor por defecto, sin pisar a
     * quien ya lo haya cambiado.
     */
    @JvmStatic
    fun applyDefaultFlagIfMissing(context: Context) {
        try {
            FeatureFlags.applyDefaultsIfMissing(context.applicationContext)
        } catch (e: Exception) {
            Log.d(TAG, "No se pudieron aplicar los valores por defecto de los feature flags", e)
        }
    }

    /**
     * Modo de compilacion que tiene sentido para un proyecto: el guardado en su `project.json` de
     * Flutter, o el modo por defecto si el proyecto no tiene Flutter todavia.
     */
    @JvmStatic
    fun preferredMode(filesDirectory: File?): FlutterBuildMode {
        try {
            if (filesDirectory != null) {
                val project = FlutterProjectStore.load(filesDirectory)
                val mode = project?.mode
                if (mode != null) {
                    return mode
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo leer el modo Flutter del proyecto; se usa el modo por defecto", e)
        }
        return FlutterProjectDefaults.DEFAULT_MODE
    }

    /** Calcula el estado real del toolchain. Bloqueante: llamar desde un hilo de fondo. */
    @JvmStatic
    fun collectState(context: Context, mode: FlutterBuildMode): State {
        val applicationContext = context.applicationContext
        val state = State()
        state.mode = mode
        state.installed = FlutterToolchainManager.isReady(applicationContext, mode)
        state.dartVersion = FlutterToolchainManager.installedDartVersion(applicationContext)
        state.notReadyReason = if (state.installed) {
            null
        } else {
            FlutterToolchainManager.describeNotReady(applicationContext, mode)
        }
        state.directory = FlutterToolchainManager.toolchainDir(applicationContext).absolutePath
        state.summary = FlutterToolchainManager.toolchainStatusSummary(applicationContext, mode)
        state.fullDownloadBytes = FlutterToolchainManager.totalDownloadBytes(applicationContext, mode)
        state.installedBytes = FlutterToolchainManager.installedBytes(applicationContext)
        state.components = try {
            FlutterToolchainManager.componentInventory(applicationContext, mode)
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo inventariar el toolchain; se usa solo la estimacion", e)
            emptyList()
        }
        state.downloadBytes = if (state.components.isEmpty()) {
            FlutterToolchainManager.estimatedDownloadBytes(applicationContext, mode)
        } else {
            state.components.filter { !it.installed }.sumOf { it.downloadBytes }
        }
        return state
    }

    /**
     * Linea corta y legible para filas de ajustes, p. ej.
     * `Toolchain Flutter: instalado (Dart 3.13.4) · 307.4 MB` o `… no instalado (~307.2 MB)`.
     *
     * Bloqueante: llamar desde un hilo de fondo.
     */
    @JvmStatic
    fun statusLine(context: Context, mode: FlutterBuildMode): String =
        statusLine(context, collectState(context.applicationContext, mode))

    /** Igual que [statusLine] pero reutilizando un estado ya calculado. */
    @JvmStatic
    fun statusLine(context: Context, state: State): String {
        return when {
            state.installed -> context.getString(
                R.string.flutter_discoverability_row_installed,
                state.dartVersion ?: "-",
                FlutterToolchainManager.formatBytes(state.installedBytes)
            )
            // Estado intermedio: los datos del SDK Dart estan extraidos, pero aun no se puede
            // compilar (p. ej. los ejecutables de esta ABI no arrancan). No es "no instalado".
            state.dartVersion != null -> context.getString(
                R.string.flutter_discoverability_row_partial,
                state.dartVersion,
                FlutterToolchainManager.formatBytes(state.installedBytes)
            )
            else -> context.getString(
                R.string.flutter_discoverability_row_missing,
                FlutterToolchainManager.formatBytes(state.downloadBytes)
            )
        }
    }

    /** Texto del dialogo de consentimiento con estado, tamanos, red, ubicacion y modo offline. */
    @JvmStatic
    fun consentMessage(context: Context, state: State, forBuild: Boolean): String {
        val message = StringBuilder()
        val stateLabel = context.getString(
            if (state.installed) {
                R.string.flutter_toolchain_status_installed
            } else {
                R.string.flutter_toolchain_status_missing
            }
        )
        message.append(context.getString(R.string.flutter_toolchain_consent_state, stateLabel))
        message.append("\n\n").append(state.summary)

        val missing = state.components.filter { !it.installed }.map { component ->
            context.getString(
                R.string.flutter_toolchain_consent_component_size,
                component.label,
                FlutterToolchainManager.formatBytes(component.downloadBytes)
            )
        }
        if (missing.isNotEmpty()) {
            message.append("\n\n").append(context.getString(R.string.flutter_toolchain_consent_missing_header))
            for (item in missing) {
                message.append("\n - ").append(item)
            }
        }

        if (!state.installed) {
            val size = FlutterToolchainManager.formatBytes(state.downloadBytes)
            message.append("\n\n").append(context.getString(R.string.flutter_toolchain_consent_download_size, size))
            message.append("\n").append(context.getString(R.string.flutter_toolchain_consent_network, size))
            message.append("\n").append(context.getString(R.string.flutter_toolchain_consent_disk_note))
        } else if (state.installedBytes > 0L) {
            message.append("\n\n").append(
                context.getString(
                    R.string.flutter_toolchain_info_disk_usage,
                    FlutterToolchainManager.formatBytes(state.installedBytes)
                )
            )
        }

        message.append("\n").append(context.getString(R.string.flutter_toolchain_consent_location, state.directory))
        message.append("\n").append(context.getString(R.string.flutter_toolchain_consent_offline))

        if (forBuild) {
            message.append("\n\n").append(FlutterToolchainManager.MESSAGE_TOOLCHAIN_REQUIRES_CONSENT).append('.')
        }

        val reason = state.notReadyReason
        if (!state.installed && !reason.isNullOrEmpty()) {
            message.append("\n\n").append(reason)
        }
        return message.toString()
    }

    /**
     * Muestra el dialogo de estado del toolchain (con descarga consentida y borrado).
     *
     * Si la funcion esta desactivada, muestra el aviso de como activarla en vez de desaparecer.
     */
    @JvmStatic
    fun showStatusDialog(activity: Activity) {
        showStatusDialog(activity, FlutterProjectDefaults.DEFAULT_MODE)
    }

    @JvmStatic
    fun showStatusDialog(activity: Activity, mode: FlutterBuildMode) {
        if (!isEnabled(activity)) {
            showDisabledDialog(activity)
            return
        }

        val progressDialog: AlertDialog
        try {
            progressDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.flutter_menu_toolchain_status)
                .setMessage(R.string.flutter_toolchain_calculating)
                .setCancelable(false)
                .create()
            progressDialog.show()
        } catch (e: Exception) {
            toastError(activity, e)
            return
        }

        Thread({
            val state: State
            try {
                state = collectState(activity, mode)
            } catch (throwable: Throwable) {
                activity.runOnUiThread {
                    dismissQuietly(progressDialog)
                    toastError(activity, throwable)
                }
                return@Thread
            }
            activity.runOnUiThread {
                dismissQuietly(progressDialog)
                showConsentDialog(activity, state)
            }
        }, "flutter-toolchain-status").start()
    }

    /** Aviso (visible, no silencioso) de que la funcion Flutter esta apagada y como activarla. */
    @JvmStatic
    fun showDisabledDialog(activity: Activity) {
        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.flutter_discoverability_disabled_title)
                .setMessage(R.string.flutter_discoverability_disabled_message)
                .setPositiveButton(R.string.flutter_discoverability_open_flags) { _, _ ->
                    try {
                        activity.startActivity(Intent(activity, FeatureFlagsActivity::class.java))
                    } catch (e: Exception) {
                        Log.d(TAG, "No se pudo abrir la pantalla de Feature flags", e)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (e: Exception) {
            toastError(activity, e)
        }
    }

    private fun showConsentDialog(activity: Activity, state: State) {
        try {
            val builder = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.flutter_toolchain_consent_title)
                .setView(createScrollableLog(activity, consentMessage(activity, state, false)))

            if (state.installed) {
                builder.setPositiveButton(android.R.string.ok, null)
            } else {
                builder.setPositiveButton(R.string.flutter_toolchain_download_now) { _, _ ->
                    startInstall(activity, state.mode)
                }
            }

            if (state.components.isEmpty() || state.installedBytes > 0L) {
                builder.setNeutralButton(
                    activity.getString(
                        R.string.flutter_toolchain_delete,
                        FlutterToolchainManager.formatBytes(state.installedBytes)
                    )
                ) { _, _ -> confirmDelete(activity, state) }
            }

            builder.setNegativeButton(android.R.string.cancel, null).show()
        } catch (e: Exception) {
            toastError(activity, e)
        }
    }

    private fun confirmDelete(activity: Activity, state: State) {
        val bytes = state.installedBytes
        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.flutter_toolchain_delete_title)
                .setMessage(
                    activity.getString(
                        R.string.flutter_toolchain_delete_message,
                        FlutterToolchainManager.formatBytes(bytes),
                        state.directory
                    )
                )
                .setPositiveButton(R.string.flutter_toolchain_delete_confirm) { _, _ ->
                    Thread({
                        FlutterToolchainManager.reset(activity.applicationContext)
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                activity.getString(
                                    R.string.flutter_toolchain_delete_done,
                                    FlutterToolchainManager.formatBytes(bytes)
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            showStatusDialog(activity, state.mode)
                        }
                    }, "flutter-toolchain-delete").start()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (e: Exception) {
            toastError(activity, e)
        }
    }

    private fun startInstall(activity: Activity, mode: FlutterBuildMode) {
        val logView = TextView(activity)
        logView.textSize = 12f
        logView.typeface = android.graphics.Typeface.MONOSPACE
        logView.setTextIsSelectable(true)
        logView.setPadding(24, 24, 24, 24)
        logView.text = activity.getString(R.string.flutter_build_starting)

        val scrollView = ScrollView(activity)
        scrollView.addView(logView)

        val progressDialog: AlertDialog
        try {
            progressDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.flutter_toolchain_install)
                .setView(scrollView)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            progressDialog.show()
        } catch (e: Exception) {
            toastError(activity, e)
            return
        }

        val applicationContext = activity.applicationContext

        Thread({
            val logBuffer = StringBuilder()
            var installed: Boolean
            try {
                // Aqui SI se descarga: el usuario acaba de autorizarlo en el dialogo de consentimiento.
                installed = FlutterToolchainManager.ensureInstalled(applicationContext, mode, true) { message ->
                    logBuffer.append(message).append('\n')
                    appendProgressLog(activity, logView, scrollView, logBuffer.toString())
                    Unit
                }
            } catch (throwable: Throwable) {
                logBuffer.append(activity.getString(R.string.flutter_error_prefix, safeMessage(throwable))).append('\n')
                installed = false
            }

            val finalInstalled = installed
            val finalLog = logBuffer.toString()
            activity.runOnUiThread {
                dismissQuietly(progressDialog)
                try {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(
                            if (finalInstalled) {
                                R.string.flutter_toolchain_ready
                            } else {
                                R.string.flutter_toolchain_not_ready
                            }
                        )
                        .setView(createScrollableLog(activity, finalLog))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } catch (e: Exception) {
                    toastError(activity, e)
                }
            }
        }, "flutter-toolchain").start()
    }

    private fun appendProgressLog(activity: Activity, logView: TextView, scrollView: ScrollView, log: String) {
        activity.runOnUiThread {
            try {
                logView.text = log
                scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
            } catch (ignored: Exception) {
                // El dialogo ya puede estar cerrado; se ignora.
            }
        }
    }

    private fun createScrollableLog(activity: Activity, log: String?): ScrollView {
        val logView = TextView(activity)
        logView.textSize = 12f
        logView.typeface = android.graphics.Typeface.MONOSPACE
        logView.setTextIsSelectable(true)
        logView.setPadding(24, 24, 24, 24)
        logView.text = log ?: ""
        val scrollView = ScrollView(activity)
        scrollView.addView(logView)
        return scrollView
    }

    private fun dismissQuietly(dialog: AlertDialog?) {
        try {
            dialog?.dismiss()
        } catch (ignored: Exception) {
            // Nada que hacer.
        }
    }

    private fun safeMessage(throwable: Throwable?): String =
        throwable?.message ?: "error desconocido"

    private fun toastError(activity: Activity, throwable: Throwable?) {
        try {
            Toast.makeText(
                activity,
                activity.getString(R.string.flutter_error_prefix, safeMessage(throwable)),
                Toast.LENGTH_LONG
            ).show()
        } catch (ignored: Exception) {
            // Nada que hacer.
        }
    }
}
