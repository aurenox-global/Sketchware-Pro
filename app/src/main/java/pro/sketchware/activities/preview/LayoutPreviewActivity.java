package pro.sketchware.activities.preview;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ViewPane;
import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.hC;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.wq;
import a.a.a.yq;
import pro.sketchware.databinding.ActivityLayoutPreviewBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.ScaleTypeCompat;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class LayoutPreviewActivity extends BaseAppCompatActivity {

    private static final Pattern NAV_PATTERN = Pattern.compile(
            "(?:(?:findViewById\\(R\\.id\\.|binding\\.)([A-Za-z0-9_]+)\\s*\\)?|([a-z][a-z0-9_]*))\\s*\\.setOnClickListener[\\s\\S]{0,900}?Intent\\([^)]*?([A-Za-z0-9_]+Activity)\\.class",
            Pattern.DOTALL);

    private static final Pattern LOAD_URL_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*)\\s*\\.loadUrl\\(\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private static final Pattern LOAD_BASE_URL_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*)\\s*\\.loadDataWithBaseURL\\(\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    private ViewPane pane;
    private ActivityLayoutPreviewBinding binding;
    private ProjectResourceResolver resourceResolver;

    private final Deque<String> layoutHistory = new ArrayDeque<>();
    private final Map<View, String> viewIdNames = new HashMap<>();
    private String scId;
    private String currentLayout;
    private boolean firstResume = true;
    private boolean rendering;

    /**
     * XML que nos pasa quien nos abre (el editor de XML de vistas envia el contenido que se esta
     * editando). Si viene, se previsualiza ESE xml en lugar del guardado en el proyecto: permite ver
     * cambios sin guardar y es tambien la via para reproducir/comprobar la vista previa con un XML
     * exacto.
     */
    private String pendingXml;

    /**
     * Vistas que no se han podido crear en esta previsualizacion (p.ej. clases de librerias que el
     * editor no tiene en su classpath). Se usa para avisar de forma visible en vez de dejar huecos
     * mudos en el lienzo.
     */
    private final java.util.List<String> renderWarnings = new ArrayList<>();

    /**
     * Motivo por clase no instanciable (clase\u2192explicacion): "no esta en el APK del editor",
     * "sin constructor usable..." etc. Lo pone {@link pro.sketchware.utility.InvokeUtil}. Sirve para
     * que el aviso diga POR QUE, no solo QUE.
     */
    private final Map<String, String> renderWarningReasons = new HashMap<>();

    /**
     * Vistas que SI se han dibujado pero con algun atributo no aplicable (p.ej. CircleImageView solo
     * admite CENTER_CROP y el XML pide FIT_CENTER). No son vistas perdidas: se anotan
     * para no ensenar una preview silenciosamente distinta a la del editor.
     */
    private final java.util.List<String> appearanceWarnings = new ArrayList<>();

    private static final String TAG = "LayoutPreview";

    /** Nombre corto de una clase (lo ultimo tras el punto). */
    private static String shortClassName(String className) {
        if (className == null) {
            return "";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private void debug(String message) {
        binding.debugStatus.setVisibility(android.view.View.VISIBLE);
        binding.debugStatus.setBackgroundColor(0xB3000000);
        binding.debugStatus.setText(message);
        binding.debugStatus.setOnClickListener(null);
        binding.debugStatus.setClickable(false);
        android.util.Log.i(TAG, "info: " + message);
    }

    /**
     * Igual que {@link #debug} pero pintando la barra en rojo: aviso de que la previsualizacion es
     * incompleta (nunca debe quedar una pantalla vacia sin explicacion).
     */
    private void debugWarning(String message) {
        binding.debugStatus.setVisibility(android.view.View.VISIBLE);
        binding.debugStatus.setBackgroundColor(0xB3B00020);
        binding.debugStatus.setText("⚠ " + message);
        android.util.Log.w(TAG, "warning: " + message);
    }

    /**
     * Causa raiz del bug de los disenos "en blanco o en negro".
     *
     * Los ViewBean del IDE usan 0xffffff como CENTINELA de "este color no lo ha definido el
     * usuario": es el valor por defecto de {@link com.besome.sketch.beans.TextBean#textColor},
     * {@link com.besome.sketch.beans.TextBean#hintColor} y
     * {@link com.besome.sketch.beans.LayoutBean#backgroundColor}, y el generador de XML
     * (a.a.a.Ox) solo escribe los atributos android:textColor/android:background cuando el valor
     * es DISTINTO de 0xffffff. Es decir: un layout sin colores propios genera un XML sin esos
     * atributos, y el parser devuelve el centinela 0xffffff.
     *
     * Al aplicar ese centinela "tal cual" sobre la vista real:
     * - setTextColor(0xffffff)  -> 0x00FFFFFF, es decir ALFA 0: texto transparente.
     * - setBackgroundColor(0xffffff) -> fondo transparente, que ademas BORRA el fondo que el tema
     *   habria dado al widget (un Button pintaba su colorPrimary; ahora pinta nada).
     * Resultado: un LinearLayout + Button se ve como un rectangulo vacio (blanco sobre el lienzo
     * blanco, o negro/oscuro sobre el lienzo oscuro segun el tema), mientras que los widgets que
     * se pintan solos (SeekBar, ProgressBar...) y los HTML/WebView (pintan su propio contenido)
     * seguian viendose. Exactamente el sintoma reportado.
     *
     * Por eso, igual que hace Ox, tratamos 0xffffff (y cualquier color con alfa 0) como "sin
     * definir" y dejamos que decida el tema del widget.
     */
    private static boolean isColorNotSet(int color) {
        return color == 0 || color == 0xffffff || (color & 0xFF000000) == 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityLayoutPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        var toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Live Preview");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        scId = getIntent().getStringExtra("sc_id");
        String title = getIntent().getStringExtra("title");
        if (scId == null || title == null) {
            SketchwareUtil.toastError("Missing preview data.");
            finish();
            return;
        }
        resourceResolver = new ProjectResourceResolver(this, scId);

        pane = binding.pane;
        // ViewPane.initialize() construye el editor de colores del proyecto, y ese lee el campo estatico
        // DesignActivity.sc_id (el proyecto abierto en el editor de diseno). Si la vista previa se abre
        // sin pasar por el editor de diseno (proceso en frio, por ejemplo al recuperar la Activity de
        // recientes) ese campo es null: jC.c(null) lanzaba NullPointerException y la Activity se
        // cerraba sin ningun mensaje (pantalla muerta). Fijamos el proyecto en curso y, si aun asi
        // fallara la preparacion del lienzo, lo mostramos en lugar de caernos.
        com.besome.sketch.design.DesignActivity.sc_id = scId;
        try {
            pane.initialize(scId, true);
        } catch (Throwable throwable) {
            android.util.Log.e(TAG, "error: fallo al inicializar el lienzo de la vista previa", throwable);
            SketchwareUtil.showAnErrorOccurredDialog(this, "No se pudo preparar la vista previa: " + throwable);
        }
        pane.setVerticalScrollBarEnabled(true);
        pane.setResourceManager(jC.d(scId));
        UI.addSystemWindowInsetToPadding(binding.pane, false, false, false, true);
        // La barra de aviso va pegada al borde inferior: sin el inset de la barra de navegacion el
        // resumen quedaba cortado (era parte del problema de "aviso ilegible").
        UI.addSystemWindowInsetToPadding(binding.debugStatus, false, false, false, true);

        layoutHistory.push(title);
        // Si quien abre la vista previa nos da el XML (editor de XML de vistas), lo usamos tal cual.
        pendingXml = getIntent().getStringExtra("xml");
        renderCurrentLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!firstResume && !rendering) {
            refreshFromProject();
        }
        firstResume = false;
    }

    @Override
    public void onBackPressed() {
        if (layoutHistory.size() > 1) {
            layoutHistory.pop();
            currentLayout = layoutHistory.peek();
            renderCurrentLayout();
            return;
        }
        super.onBackPressed();
    }

    private void renderCurrentLayout() {
        final String layoutName = currentLayout != null ? currentLayout : layoutHistory.peek();
        if (layoutName == null || rendering) {
            return;
        }
        rendering = true;
        binding.toolbar.setSubtitle(layoutName);

        new Thread(() -> {
            try {
                // Primero el XML que nos haya pasado quien nos abre (cambios sin guardar); si no,
                // se genera desde los datos del proyecto.
                String xml = pendingXml;
                pendingXml = null;
                if (xml == null || xml.trim().isEmpty()) {
                    xml = new yq(getApplicationContext(), scId)
                            .getFileSrc(layoutName, jC.b(scId), jC.a(scId), jC.c(scId));
                }
                if (xml == null || xml.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        rendering = false;
                        SketchwareUtil.toastError("Couldn't generate the layout preview.");
                    });
                    return;
                }
                final String finalXml = xml;
                runOnUiThread(() -> {
                    renderLayout(layoutName, finalXml);
                    rendering = false;
                });
            } catch (Throwable throwable) {
                runOnUiThread(() -> {
                    rendering = false;
                    SketchwareUtil.toastError("Preview failed: " + throwable.getMessage());
                });
            }
        }, "live-preview-render").start();
    }

    private void refreshFromProject() {
        final String layoutName = layoutHistory.peek();
        if (layoutName == null) {
            return;
        }
        new Thread(() -> {
            try {
                String xml = new yq(getApplicationContext(), scId)
                        .getFileSrc(layoutName, jC.b(scId), jC.a(scId), jC.c(scId));
                if (xml == null || xml.trim().isEmpty()) {
                    return;
                }
                final String finalXml = xml;
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    renderLayout(layoutName, finalXml);
                });
            } catch (Exception ignored) {
                android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Exception ignored", ignored);
            }
        }, "live-preview-refresh").start();
    }

    /**
     * Diagnostico: vuelca el XML que se va a previsualizar al log (troceado, logcat corta los
     * mensajes largos). Sirve para reproducir un diseno exactamente con
     * `am start ... --es xml "<layout...>"` y para ver que XML genera el editor de diseno.
     */
    private void debugXml(String layoutName, String xml) {
        if (xml == null) {
            android.util.Log.i(TAG, "xml " + layoutName + ": (null)");
            return;
        }
        final int chunk = 700;
        // Tope de seguridad para no inundar el log con disenos muy grandes.
        final int max = 16 * 1024;
        android.util.Log.i(TAG, "xml " + layoutName + " len=" + xml.length());
        for (int i = 0; i < Math.min(xml.length(), max); i += chunk) {
            android.util.Log.i(TAG, "xml[%d] %s".formatted(i, xml.substring(i, Math.min(xml.length(), i + chunk))));
        }
    }

    private void renderLayout(String layoutName, String xml) {
        debugXml(layoutName, xml);
        // Construimos siempre con el constructor de vistas reales: trabaja SOLO con el XML (no depende de
        // los datos internos del proyecto) y ya soportaba WebView/HTML. Antes, los layouts sin WebView iban
        // por el renderizador del editor de diseno, que si no encuentra el nombre del layout en los datos
        // del proyecto pinta una raiz vacia: de ahi las vistas previas en blanco.
        renderWarnings.clear();
        renderWarningReasons.clear();
        appearanceWarnings.clear();
        resourceResolver.clearWarnings();
        if (!tryInflateRealLayout(layoutName, xml) && !renderWithViewPane(layoutName, xml)) {
            // Nada de pantallas mudas: motivo visible en la barra de estado y toast.
            String motivo = binding.debugStatus.getText() == null ? "" : binding.debugStatus.getText().toString();
            debugWarning("No se pudo previsualizar " + layoutName + (
                    motivo.isEmpty() ? "" : " · " + motivo));
            SketchwareUtil.toastError("No se pudo generar la vista previa de este layout");
        }
    }

    private boolean xmlContainsWebView(String xml) {
        return xml != null && xml.contains("WebView");
    }

    private boolean tryInflateRealLayout(String layoutName, String xml) {
        try {
            ArrayList<ViewBean> beans = new ViewBeanParser(xml).parse();
            if (beans.isEmpty()) {
                debug("Preview FAIL: layout vacio");
                return false;
            }

            final Map<String, View> viewsById = new HashMap<>();
            viewIdNames.clear();
            renderWarnings.clear();
            renderWarningReasons.clear();
            appearanceWarnings.clear();
            for (ViewBean bean : beans) {
                View view = createRealView(bean);
                if (view != null) {
                    viewsById.put(bean.id, view);
                    viewIdNames.put(view, bean.id);
                }
            }
            if (viewsById.isEmpty()) {
                debugWarning("Preview FAIL: no se pudo crear ninguna vista del layout");
                return false;
            }

            View rootView = null;
            for (ViewBean bean : beans) {
                View view = viewsById.get(bean.id);
                if (view == null) {
                    continue;
                }
                if (bean.parent == null || bean.parent.isEmpty() || "root".equals(bean.parent)) {
                    rootView = view;
                    continue;
                }
                View parentView = viewsById.get(bean.parent);
                if (parentView instanceof ViewGroup parentGroup) {
                    applyLayoutParams(view, bean, parentGroup);
                    parentGroup.addView(view);
                }
            }
            if (rootView == null) {
                debug("Preview FAIL: no se encontro la vista raiz");
                return false;
            }

            pane.removeAllViews();
            // Respetamos las dimensiones declaradas de la raiz (los layouts suelen ser
            // match_parent x match_parent). Antes se forzaba WRAP_CONTENT en alto: en un layout raiz
            // match_parent con hijos con peso/match_parent, el contenido colapsaba a 0 px y parecia
            // "vacio" aunque las vistas existieran.
            pane.addView(rootView, rootLayoutParams(beans));

            wireNavigation(rootView, layoutName);
            int webViewCount = setupWebViews(rootView, layoutName);
            String sample = "";
            int shown = 0;
            for (ViewBean bean : beans) {
                if (shown >= 2) {
                    break;
                }
                String beanText = bean.text != null ? bean.text.text : null;
                sample += " [" + bean.id + "/" + bean.convert + "/" + (beanText == null ? "-" : beanText) + "]";
                shown++;
            }
            if (renderWarnings.isEmpty() && appearanceWarnings.isEmpty()
                    && resourceResolver.getWarnings().isEmpty()
                    && resourceResolver.getLegacyResolutions().isEmpty()) {
                hidePreviewWarning();
                debug("Preview OK · vistas: " + viewsById.size() + " · WebViews: " + webViewCount + sample);
            } else {
                // Nada de muros de texto: resumen corto y agrupado en la barra + detalle a un toque.
                showPreviewWarningSummary(viewsById.size(), webViewCount);
            }
            return true;
        } catch (Throwable throwable) {
            pane.removeAllViews();
            debugWarning("Preview FAIL: " + throwable);
            return false;
        }
    }

    /**
     * Aviso corto y agrupado de vista previa parcial, con detalle bajo demanda.
     *
     * Antes se imprimia una sola linea asi:
     *
     * <pre>Preview PARCIAL · vistas: 32 · no disponibles: @drawable/ic_tune_white</pre>
     *
     * que el usuario leia como "32 vistas no disponibles" (cuando "32" eran las vistas SI dibujadas)
     * y el resto era un muro de nombres de recursos, sin decir que tipo de problema era cada uno.
     *
     * Ahora: la barra dice CUANTOS recursos no se encontraron, CUANTAS vistas se han dibujado de
     * forma aproximada y CUANTOS atributos no se han podido aplicar (categorias distintas), y el
     * detalle agrupado por causa sale en un dialogo (y completo en el log, tag LayoutPreview).
     */
    private void showPreviewWarningSummary(int views, int webViews) {
        Map<ProjectResourceResolver.Kind, java.util.Set<String>> byKind = resourceResolver.getWarningsByKind();
        int resourceCount = resourceResolver.getWarnings().size();
        int viewCount = renderWarnings.size();
        int appearanceCount = appearanceWarnings.size();
        List<ProjectResourceResolver.LegacyResolution> legacy = resourceResolver.getLegacyResolutions();

        List<String> parts = new ArrayList<>();
        if (resourceCount > 0) {
            parts.add(resourceCount + (resourceCount == 1 ? " recurso no encontrado" : " recursos no encontrados"));
        }
        if (viewCount > 0) {
            // "aproximada", no "no instanciable": ahora el lienzo SI conserva su hueco, su aspecto y
            // sus hijos (con marca discreta), asi que decir "no instanciable" y pintar un hueco mudo
            // era pesimista y, ademas, enganoso (parecia que se perdia el contenido).
            parts.add(viewCount + (viewCount == 1 ? " vista aproximada" : " vistas aproximadas"));
        }
        if (appearanceCount > 0) {
            parts.add(appearanceCount + (appearanceCount == 1
                    ? " atributo no aplicado" : " atributos no aplicados"));
        }
        if (!legacy.isEmpty()) {
            parts.add(legacy.size() + (legacy.size() == 1 ? " nombre heredado resuelto" : " nombres heredados resueltos"));
        }
        // "PARCIAL" y rojo SOLO cuando algo ha fallado de verdad (recurso que no existe en ninguna
        // fuente). Una vista aproximada o un atributo no aplicable no son un fallo del diseno: se
        // avisan en ambar. Un nombre heredado mapeado es informativo.
        boolean partial = resourceCount > 0 || viewCount > 0 || appearanceCount > 0;
        boolean error = resourceCount > 0;
        String summary = (partial ? "Preview PARCIAL: " : "Preview: ") + android.text.TextUtils.join(" · ", parts);
        binding.debugStatus.setVisibility(android.view.View.VISIBLE);
        binding.debugStatus.setBackgroundColor(error ? 0xB3B00020 : 0xB3B26A00);
        binding.debugStatus.setText((error ? "⚠ " : "ℹ ") + summary);
        binding.debugStatus.setOnClickListener(v -> showPreviewWarningDetail());
        binding.debugStatus.setClickable(true);
        if (partial) {
            android.util.Log.w(TAG, "warning: " + summary);
        } else {
            android.util.Log.i(TAG, "info: " + summary);
        }

        // Detalle completo en el log, agrupado por causa (no depende del dialogo).
        android.util.Log.w(TAG, "warning: vistas dibujadas: " + views + " · WebViews: " + webViews);
        for (Map.Entry<ProjectResourceResolver.Kind, java.util.Set<String>> entry : byKind.entrySet()) {
            for (String value : entry.getValue()) {
                android.util.Log.w(TAG, "warning: [" + entry.getKey().label + "] " + value);
            }
        }
        for (ProjectResourceResolver.LegacyResolution resolution : legacy) {
            android.util.Log.i(TAG, "info: [nombre heredado] " + resolution.original
                    + " -> @drawable/" + resolution.resolved + " (" + resolution.rule + ")");
        }
        for (String className : renderWarnings) {
            String reason = renderWarningReasons.get(className);
            android.util.Log.w(TAG, "warning: [vista aproximada] " + className
                    + (reason == null || reason.isEmpty() ? "" : " · " + reason));
        }
        for (String warning : appearanceWarnings) {
            android.util.Log.w(TAG, "warning: [atributo no aplicado] " + warning);
        }
    }

    /** Quita el aviso de la barra y su listener (previsualizacion correcta). */
    private void hidePreviewWarning() {
        binding.debugStatus.setOnClickListener(null);
        binding.debugStatus.setClickable(false);
    }

    /**
     * Detalle agrupado del aviso, en un dialogo: una linea de cabecera por causa con su recuento y
     * la lista de elementos (limitada, para que un diseno con 200 recursos rotos siga siendo legible).
     */
    private void showPreviewWarningDetail() {
        Map<ProjectResourceResolver.Kind, java.util.Set<String>> byKind = resourceResolver.getWarningsByKind();
        StringBuilder text = new StringBuilder();
        Map<String, List<String>> groups = new LinkedHashMap<>();

        if (!renderWarnings.isEmpty()) {
            // Con el motivo de cada clase: "clase no encontrada", "sin constructor usable..." etc. Es
            // la diferencia entre entender el aviso y tener que adivinar.
            List<String> lines = new ArrayList<>();
            for (String className : renderWarnings) {
                String reason = renderWarningReasons.get(className);
                lines.add(reason == null || reason.isEmpty() ? className : className + "  ·  " + reason);
            }
            groups.put("Vistas aproximadas (clase no disponible en el editor; se dibujan sus hijos)", lines);
        }
        if (!appearanceWarnings.isEmpty()) {
            groups.put("Atributos no aplicados o ajustados a un valor soportado (la vista SI se ha dibujado)",
                    new ArrayList<>(appearanceWarnings));
        }
        for (Map.Entry<ProjectResourceResolver.Kind, java.util.Set<String>> entry : byKind.entrySet()) {
            groups.put("Recursos no encontrados · " + entry.getKey().label, new ArrayList<>(entry.getValue()));
        }
        // Distinto de "no encontrado": aqui SI se ha dibujado algo, con el nombre heredado mapeado
        // a un icono actual. Se dice cual, para que no sea una sustitucion silenciosa.
        List<ProjectResourceResolver.LegacyResolution> legacy = resourceResolver.getLegacyResolutions();
        if (!legacy.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (ProjectResourceResolver.LegacyResolution resolution : legacy) {
                lines.add(resolution.original + "  ->  @drawable/" + resolution.resolved);
            }
            groups.put("Nombres heredados resueltos -> icono actual", lines);
        }

        final int maxPerGroup = 12;
        boolean first = true;
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            if (!first) {
                text.append('\n');
            }
            first = false;
            List<String> values = group.getValue();
            text.append("• ").append(group.getKey()).append(": ").append(values.size()).append('\n');
            int shown = Math.min(values.size(), maxPerGroup);
            for (int i = 0; i < shown; i++) {
                text.append("    ").append(values.get(i)).append('\n');
            }
            if (values.size() > shown) {
                text.append("    …y ").append(values.size() - shown).append(" mas (ver log LayoutPreview)\n");
            }
        }
        String searched = resourceResolver.getSearchedLocations();
        if (!searched.isEmpty()) {
            text.append("\nDrawables buscados en: ").append(searched);
        }
        boolean partial = !renderWarnings.isEmpty() || !appearanceWarnings.isEmpty()
                || !resourceResolver.getWarnings().isEmpty();
        if (partial) {
            text.append("\n\nEl resto del diseno SI se ha dibujado; solo falta lo listado arriba.");
            if (!renderWarnings.isEmpty()) {
                text.append(" Las vistas aproximadas conservan fondo, padding y tamano, y sus hijos se")
                        .append(" dibujan dentro (se marcan con un borde ambar y una pastilla en el lienzo).");
            }
            if (!resourceResolver.getWarnings().isEmpty()) {
                text.append(" Los recursos no resueltos se marcan en rojo en el lienzo.");
            }
        }
        if (!legacy.isEmpty()) {
            text.append("\n\nLos nombres heredados de arriba SI se han dibujado, usando el icono actual").append(" del editor (la version vieja de Sketchware los llamaba de otra forma).");
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(partial ? "Vista previa parcial" : "Vista previa")
                .setMessage(text.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Dimensiones con las que colgamos la raiz inflada del lienzo: usamos las declaradas en el XML
     * del proyecto (MATCH_PARENT/WRAP_CONTENT o dp) para que el resultado se parezca a la app.
     */
    private ViewGroup.LayoutParams rootLayoutParams(ArrayList<ViewBean> beans) {
        com.besome.sketch.beans.LayoutBean layout = beans.get(0).layout;
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (layout != null) {
            if (layout.width == com.besome.sketch.beans.LayoutBean.LAYOUT_WRAP_CONTENT) {
                width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else if (layout.width != com.besome.sketch.beans.LayoutBean.LAYOUT_MATCH_PARENT) {
                width = dp(layout.width);
            }
            if (layout.height == com.besome.sketch.beans.LayoutBean.LAYOUT_WRAP_CONTENT) {
                height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else if (layout.height != com.besome.sketch.beans.LayoutBean.LAYOUT_MATCH_PARENT) {
                height = dp(layout.height);
            }
        }
        return new ViewGroup.LayoutParams(width, height);
    }

    private View createRealView(ViewBean bean) {
        String className = bean.convert == null ? "" : bean.convert.trim();
        if (className.isEmpty()) {
            className = android.widget.LinearLayout.class.getName();
        }
        pro.sketchware.utility.InvokeUtil.CreateResult result =
                pro.sketchware.utility.InvokeUtil.createViewDetailed(this, className);
        View view = result.view;
        if (view instanceof android.widget.ProgressBar && !(view instanceof android.widget.SeekBar)) {
            // Un ProgressBar creado por reflexion trae el estilo del tema del IDE (circulo
            // INDETERMINADO) y ademas ProgressBar no deja pasar a determinado si el estilo es
            // "solo indeterminado": por eso el progreso y el tinte configurados no se veian
            // ("no se ven los colores"). Cuando el XML pide la barra horizontal (lo que escribe el
            // editor: style="?android:progressBarStyleHorizontal") se construye con ESE estilo del
            // framework, que es exactamente el widget que tendra la app compilada.
            view = createHorizontalProgressBarIfNeeded(bean, view);
        }
        if (view == null) {
            // La clase no se puede instanciar en el editor (libreria/widat que el IDE no incluye, una
            // vista propia del proyecto, o -caso de la release minificada- un constructor que R8
            // elimino por usarse solo desde esta reflexion). Antes se saltaba en silencio y el layout
            // parecia "vacio"; luego se pinto un hueco rojo mudo que ADEMAS se comia a los hijos.
            // Ahora: contenedor generico que conserva fondo/padding/tamano y DIBUJA LOS HIJOS, con una
            // marca discreta de "aproximado" y el motivo en el detalle.
            String reason = result.failureReason == null || result.failureReason.isEmpty()
                    ? "clase no disponible en el editor" : result.failureReason;
            renderWarnings.add(className);
            renderWarningReasons.put(className, reason);
            android.util.Log.w(TAG, "warning: no se pudo crear la vista " + className
                    + " (id=" + bean.id + ") · " + reason);
            return createApproximateView(className, bean);
        }
        view.setId(android.view.View.generateViewId());
        try {
            applyBeanAppearance(view, bean);
        } catch (Throwable throwable) {
            // Una vista que SI se ha creado pero con un atributo no aplicable no debe tumbar el resto
            // del diseno (los hijos de un contenedor se quedarian sin dibujar). Se anota y se sigue.
            appearanceWarnings.add(shortClassName(className) + ": " + conciseMessage(throwable));
            android.util.Log.w(TAG, "warning: atributos no aplicados en " + className
                    + " (id=" + bean.id + ")", throwable);
        }
        return view;
    }

    /**
     * Devuelve un ProgressBar HORIZONTAL cuando el bean lo pide (style del XML o progressStyle del
     * bean). Si no, devuelve el mismo que le han pasado (circulo indeterminado), sin tocarlo.
     */
    private View createHorizontalProgressBarIfNeeded(ViewBean bean, View progressBar) {
        var handler = new pro.sketchware.utility.InjectAttributeHandler(bean);
        String style = handler.getAttributeValueOf("style");
        boolean horizontal = style.toLowerCase(Locale.US).contains("horizontal")
                || com.besome.sketch.beans.ViewBean.PROGRESSBAR_STYLE_HORIZONTAL.equals(bean.progressStyle)
                || handler.contains("progressTint");
        if (!horizontal) {
            return progressBar;
        }
        try {
            return new android.widget.ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "warning: no se pudo crear el ProgressBar horizontal", throwable);
            return progressBar;
        }
    }

    /** Motivo corto y legible de un fallo (para el dialogo): sin paquetes ni stacktrace. */
    private static String conciseMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        return message == null || message.isEmpty()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
    }

    /**
     * Marcador visible para un recurso que la vista previa no puede resolver (imagen que no esta en
     * el APK del IDE, nombre mal escrito, drawable que solo existe en la app compilada...).
     * Requisito: nunca un hueco mudo; siempre explicar el motivo.
     */
    private android.graphics.drawable.Drawable createMissingDrawable(String resourceName) {
        android.graphics.drawable.GradientDrawable marker = new android.graphics.drawable.GradientDrawable();
        marker.setColor(0x22B00020);
        marker.setStroke(dp(2), 0xFFB00020);
        marker.setSize(dp(96), dp(96));
        return marker;
    }

    /**
     * Contenedor "aproximado" que sustituye a una vista que el editor no sabe inflar.
     *
     * <p>Requisitos que cumple (los tres importan):
     * <ul>
     *   <li>Sigue siendo un {@link android.view.ViewGroup}, asi que los HIJOS del XML se cuelgan
     *       dentro y se dibujan: un MaterialToolbar no dibujable ya no oculta el TextView que lleva
     *       dentro (antes el marcador era un TextView, no un contenedor, asi que los hijos se perdian
     *       o acababan colgados de la raiz, deformando el diseno).</li>
     *   <li>Conserva fondo, padding y tamano declarados, aplicando el mismo aspecto del bean.</li>
     *   <li>Se marca como APROXIMADO con un borde ambar fino y una pastilla con el nombre de la
     *       clase: nada de rojo de error (la app compilada SI tendra ese widget) y nada de huecos
     *       mudos. El motivo exacto va al detalle (y al log).</li>
     * </ul>
     */
    private View createApproximateView(String className, ViewBean bean) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        try {
            applyBeanAppearance(container, bean);
        } catch (Throwable throwable) {
            appearanceWarnings.add(shortClassName(className) + ": " + conciseMessage(throwable));
        }
        // Borde ambar discreto (foreground: se pinta encima del fondo pero no tapa a los hijos).
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(0x00000000);
        border.setStroke(dp(1), 0x66FF9800);
        container.setForeground(border);
        container.setForegroundGravity(android.view.Gravity.FILL);

        android.widget.TextView badge = new android.widget.TextView(this);
        // El boton de Google (SignInButton) no tiene constructor usable en el editor, pero SI tiene
        // atributos configurables (buttonSize/colorScheme). En vez de un hueco mudo, se dibuja un
        // boton equivalente con esos valores aplicados (igual que hace el editor de diseno con su
        // ItemSignInButton), marcado con la pastilla ambar de "aproximado".
        addSignInButtonStubIfNeeded(container, className, bean);
        badge.setText("≈ " + shortClassName(className));
        badge.setTextSize(9f);
        badge.setTextColor(0xCC8A5000);
        badge.setPadding(dp(4), dp(1), dp(4), dp(1));
        android.graphics.drawable.GradientDrawable chip = new android.graphics.drawable.GradientDrawable();
        chip.setColor(0x1FFFB300);
        chip.setStroke(dp(1), 0x66FF9800);
        chip.setCornerRadius(dp(3));
        badge.setBackground(chip);
        badge.setClickable(false);
        badge.setFocusable(false);
        android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        container.addView(badge, badgeParams);
        return container;
    }

    /**
     * Boton de respaldo para {@code com.google.android.gms.common.SignInButton} (no instanciable en
     * el editor): aplica {@code app:buttonSize} y {@code app:colorScheme} a un boton equivalente para
     * que el usuario vea el efecto de esos atributos en la vista previa.
     */
    private void addSignInButtonStubIfNeeded(android.widget.FrameLayout container, String className, ViewBean bean) {
        if (className == null || !className.endsWith("SignInButton")) {
            return;
        }
        var handler = new pro.sketchware.utility.InjectAttributeHandler(bean);
        String size = handler.getAttributeValueOf("buttonSize");
        String scheme = handler.getAttributeValueOf("colorScheme");
        boolean wide = "wide".equals(size);
        boolean iconOnly = "icon_only".equals(size);
        boolean dark = "dark".equals(scheme);
        android.widget.TextView stub = new android.widget.TextView(this);
        stub.setText(iconOnly ? "G" : "Iniciar sesion con Google");
        stub.setTextSize(14f);
        stub.setGravity(android.view.Gravity.CENTER);
        stub.setTextColor(dark ? 0xFFF1F1F1 : 0xFF3C4043);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(dark ? 0xFF4285F4 : 0xFFFFFFFF);
        background.setStroke(dp(1), 0xFF747775);
        background.setCornerRadius(dp(4));
        stub.setBackground(background);
        int minWidth = wide ? dp(200) : dp(48);
        stub.setMinWidth(minWidth);
        stub.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
        container.addView(stub, params);
    }

    /**
     * Fondo de una vista del layout.
     *
     * Ojo con el orden y con los centinelas:
     * - Cuando el XML pide un fondo por RECURSO (`android:background="@color/..."` o `"?attr/..."`),
     *   ViewBeanFactory.applyBackground() guarda el nombre en layout.backgroundResColor y marca
     *   layout.backgroundColor = 0xFFFFFFFF (blanco opaco) como "pendiente de resolver". La vista
     *   previa pintaba ese blanco y NUNCA miraba backgroundResColor: un fondo de color del proyecto
     *   se veia blanco. Ahora se resuelve primero el recurso (colors.xml del proyecto, o ?attr/ del
     *   tema) y luego el drawable.
     * - 0xffffff (y cualquier color con alfa 0) es el centinela de "sin fondo definido"; aplicarlo
     *   borraba el fondo que el tema da al widget y lo dejaba invisible (ver isColorNotSet()).
     */
    private void applyBeanBackground(View view, com.besome.sketch.beans.LayoutBean layout) {
        String resColor = layout.backgroundResColor;
        String resDrawable = layout.backgroundResource;
        boolean hasResourceColor = resColor != null && !resColor.isEmpty();
        boolean hasResourceDrawable = resDrawable != null && !resDrawable.isEmpty()
                && !"NONE".equalsIgnoreCase(resDrawable);
        if (hasResourceColor) {
            int color = resourceResolver.resolveColor(view,
                    resColor.startsWith("#") || resColor.startsWith("@") || resColor.startsWith("?")
                            ? resColor : "@color/" + resColor, 0);
            if (!isColorNotSet(color)) {
                view.setBackgroundColor(color);
                return;
            }
            // El color venia de un recurso que la vista previa NO puede resolver (p.ej. un
            // "?atributoDelTema" que no existe en el tema del IDE). Ojo: en ese caso
            // ViewBeanFactory deja layout.backgroundColor = 0xFFFFFFFF como MARCADOR de "pendiente
            // de resolver", y pintarlo dejaba el layout en BLANCO opaco (parecia no pintado, y
            // tapaba el fondo del tema). Por eso aqui NO caemos al color literal si venia de
            // recurso: dejamos el fondo del tema y avisamos en la barra de estado.
        }
        if (hasResourceDrawable) {
            android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(
                    resDrawable.startsWith("@") ? resDrawable : "@drawable/" + resDrawable);
            if (drawable != null) {
                view.setBackground(drawable);
                return;
            }
        }
        if (!isColorNotSet(layout.backgroundColor)) {
            view.setBackgroundColor(layout.backgroundColor);
        }
    }

    private void applyBeanAppearance(View view, ViewBean bean) {
        com.besome.sketch.beans.LayoutBean layout = bean.layout;
        if (layout == null) {
            return;
        }
        view.setPadding(dp(layout.paddingLeft), dp(layout.paddingTop), dp(layout.paddingRight), dp(layout.paddingBottom));
        applyBeanBackground(view, layout);
        if (view instanceof android.widget.LinearLayout linearLayout) {
            linearLayout.setOrientation(layout.orientation == com.besome.sketch.beans.LayoutBean.ORIENTATION_HORIZONTAL
                    ? android.widget.LinearLayout.HORIZONTAL
                    : android.widget.LinearLayout.VERTICAL);
            linearLayout.setGravity(layout.gravity);
        }
        if (view instanceof androidx.cardview.widget.CardView cardView) {
            applyCardViewInject(cardView, bean);
        }
        if (view instanceof android.widget.ImageView imageView) {
            com.besome.sketch.beans.ImageBean image = bean.image;
            if (image != null) {
                if (image.resName != null && !image.resName.isEmpty()) {
                    android.graphics.drawable.Drawable drawable =
                            resourceResolver.resolveDrawable("@drawable/" + image.resName);
                    if (drawable != null) {
                        imageView.setImageDrawable(drawable);
                    } else {
                        // Hueco mudo no: marcador visible con el motivo (ver createApproximateView).
                        imageView.setImageDrawable(createMissingDrawable(image.resName));
                    }
                }
                if (image.scaleType != null && !image.scaleType.isEmpty()) {
                    applyScaleType(imageView, image.scaleType);
                }
                if (image.rotate != 0) {
                    imageView.setRotation(image.rotate);
                }
            }
        }
        if (view instanceof android.widget.TextView textView) {
            com.besome.sketch.beans.TextBean text = bean.text;
            if (text != null) {
                if (text.text != null && !text.text.isEmpty() && !isResourceReference(text.text)) {
                    textView.setText(text.text);
                }
                textView.setTextSize(text.textSize);
                int textColor = resourceResolver.resolveColor(textView, text.resTextColor, text.textColor);
                if (!isColorNotSet(textColor)) {
                    textView.setTextColor(textColor);
                }
                textView.setGravity(layout.gravity);
                if (view instanceof android.widget.EditText editText && text.hint != null && !text.hint.isEmpty()) {
                    editText.setHint(text.hint);
                    int hintColor = resourceResolver.resolveColor(editText, text.resHintColor, text.hintColor);
                    if (!isColorNotSet(hintColor)) {
                        editText.setHintTextColor(hintColor);
                    }
                }
            }
        }
        applyInjectAttributes(view, bean);
    }

    /**
     * Nombre "local" de un atributo: quita el prefijo de espacio de nombres.
     *
     * Importante: {@link pro.sketchware.utility.InjectAttributeHandler} parsea el XML con los
     * espacios de nombres ACTIVADOS, asi que devuelve nombres locales ("textColor", "fontFamily",
     * "src", ...). Este metodo normaliza tambien la forma con prefijo ("android:textColor") para que
     * funcione con las dos.
     */
    private static String localAttrName(String attribute) {
        if (attribute == null) {
            return "";
        }
        int index = attribute.indexOf(':');
        return index >= 0 ? attribute.substring(index + 1) : attribute;
    }

    /**
     * Aplica fuente y estilo al texto de un TextView, como hace el editor de disenos
     * ({@code ViewPane.updateTextView}).
     *
     * Antes solo se contemplaba textType == 1 (negrita): cursiva (2) y negrita+cursiva (3) se
     * ignoraban, y android:fontFamily no se aplicaba en absoluto (ni las fuentes del proyecto
     * "@font/miFuente", ni las familias del sistema como serif/monospace). Resultado: los estilos de
     * texto elegidos en el editor no se veian en la vista previa.
     */
    private void applyTextTypeface(android.widget.TextView textView, ViewBean bean) {
        com.besome.sketch.beans.TextBean text = bean.text;
        int style = text == null ? android.graphics.Typeface.NORMAL : text.textType;
        String family = null;
        for (android.util.Pair<String, String> pair : new pro.sketchware.utility.InjectAttributeHandler(bean).getAttributes()) {
            String name = localAttrName(pair.first);
            if ("fontFamily".equals(name)) {
                family = pair.second;
            } else if ("textStyle".equals(name)) {
                style = parseTextStyle(pair.second);
            }
        }
        if (family == null && text != null && text.textFont != null
                && !com.besome.sketch.beans.TextBean.TEXT_FONT.equals(text.textFont)) {
            // La propiedad "fuente" del bean (los XML generados por el IDE la escriben como
            // android:fontFamily, pero por si viene sin pasar por ahi).
            family = text.textFont;
        }
        android.graphics.Typeface typeface = resolveTypeface(family);
        if (typeface != null) {
            textView.setTypeface(typeface, style);
        } else if (style != android.graphics.Typeface.NORMAL) {
            textView.setTypeface(null, style);
        }
    }

    private int parseTextStyle(String value) {
        if (value == null) {
            return android.graphics.Typeface.NORMAL;
        }
        int style = android.graphics.Typeface.NORMAL;
        if (value.contains("bold")) {
            style |= android.graphics.Typeface.BOLD;
        }
        if (value.contains("italic")) {
            style |= android.graphics.Typeface.ITALIC;
        }
        return style;
    }

    /**
     * Fuente del texto: "@font/nombre" del proyecto (files/resource/font/...) o una familia del
     * sistema (serif, monospace, sans-serif, cursive, ...). Devuelve null si no aplica o no existe.
     */
    private android.graphics.Typeface resolveTypeface(String family) {
        if (family == null) {
            return null;
        }
        String value = family.trim();
        if (value.isEmpty() || "NONE".equalsIgnoreCase(value)
                || com.besome.sketch.beans.TextBean.TEXT_FONT.equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("@font/")) {
            String name = value.substring("@font/".length());
            File fontFile = findProjectFontFile(name);
            if (fontFile != null) {
                try {
                    return android.graphics.Typeface.createFromFile(fontFile);
                } catch (Throwable throwable) {
                    android.util.Log.w(TAG, "warning: fuente ilegible " + fontFile, throwable);
                }
            }
            // Una fuente que no existe es un RECURSO no resuelto, no una vista no disponible: va al
            // grupo de recursos para que el aviso lo cuente como recurso (no como vista).
            resourceResolver.addExternalWarning(
                    ProjectResourceResolver.Kind.FONT, value + " (no esta en files/resource/font)");
            return null;
        }
        if (value.startsWith("@") || value.startsWith("?")) {
            return null;
        }
        try {
            return android.graphics.Typeface.create(value, android.graphics.Typeface.NORMAL);
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: familia no valida " + value, ignored);
            return null;
        }
    }

    /** Fichero de fuente del proyecto: files/resource/font/<nombre>.(ttf|otf|ttc), sin distinguir mayusculas. */
    private File findProjectFontFile(String name) {
        File fontDir = new File(new FilePathUtil().getPathResource(scId), "font");
        if (!fontDir.isDirectory()) {
            return null;
        }
        File[] children = fontDir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (!child.isFile()) {
                continue;
            }
            String fileName = child.getName();
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
            if (baseName.equalsIgnoreCase(name) || fileName.equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Atributos "extra" del widget ({@code ViewBean.inject}: todo lo que el XML trae como
     * {@code app:x} / {@code android:x} y no cabe en los beans fondo/texto/imagen).
     *
     * <p>Antes esto era una lista blanca de ~19 nombres dentro de un switch y TODO lo demas se
     * descartaba en silencio: por eso "los colores no se ven" en las familias AndroidX/Library/Google
     * (tabIndicatorColor, cardCornerRadius, civ_border_color, cornerRadius, progressTint...). Ahora:
     * <ol>
     *   <li>Se aplican primero las familias que ya sabia pintar el editor de diseno
     *       ({@link pro.sketchware.utility.WidgetInjectApplier}: TabLayout, CircleImageView,
     *       MaterialButton, CardView).</li>
     *   <li>El resto pasa por un aplicador GENERICO por tipo de vista (TextView, ImageView,
     *       Progress/Seek/Rating, CompoundButton, ListView/GridView/Spinner, BottomNavigationView,
     *       TextInputLayout, CalendarView/DatePicker/TimePicker, SearchView...).</li>
     *   <li>Lo que sigue sin aplicarse intenta un setter por REFLEXION (atributos de librerias:
     *       {@code app:loQueSea} -> {@code setLoQueSea}), resolviendo colores/medidas/booleanos
     *       segun el tipo del parametro.</li>
     *   <li>Si nada de lo anterior funciona, el atributo se ANOTA en el aviso ambar con el motivo
     *       (nada de descartes silenciosos).</li>
     * </ol>
     * Los {@code @color}/{@code @dimen}/{@code @drawable} del proyecto se resuelven en el camino
     * (ver {@link #sharedResolver}).
     */
    private void applyInjectAttributes(View view, ViewBean bean) {
        java.util.List<String> notApplied = new ArrayList<>();
        java.util.Set<String> handled = new java.util.HashSet<>();
        try {
            var injectHandler = new pro.sketchware.utility.InjectAttributeHandler(bean);
            if (view instanceof android.widget.TextView) {
                // La fuente y el estilo de texto NO se aplican en el bucle generico: los aplica
                // applyTextTypeface (al final, con el catalogo de fuentes del proyecto). Se marcan
                // como atendidos para no avisar de un "setter" que no existe.
                handled.add("fontFamily");
                handled.add("textStyle");
                handled.add("typeface");
            }
            applyFamilyAttributes(view, bean, injectHandler, handled);
            for (android.util.Pair<String, String> pair : injectHandler.getAttributes()) {
                String name = localAttrName(pair.first);
                String value = pair.second;
                if (name.isEmpty() || handled.contains(name) || isLayoutOnlyAttribute(name)) {
                    continue;
                }
                String reason = applyGenericAttribute(view, name, value);
                if (reason == null) {
                    continue;
                }
                // Segundo intento: setter generico por reflexion (atributos de librerias).
                String reflectionReason = applyViaReflection(view, name, value);
                if (reflectionReason == null) {
                    continue;
                }
                notApplied.add(name + "=\"" + value + "\" · " + reflectionReason);
            }
        } catch (Throwable throwable) {
            // Un atributo raro no debe tumbar el resto del diseno (los hijos incluidos).
            appearanceWarnings.add(shortClassName(view.getClass().getName())
                    + ": atributos extra no procesados (" + conciseMessage(throwable) + ")");
            android.util.Log.w(TAG, "warning: fallo procesando los atributos extra de "
                    + view.getClass().getName(), throwable);
        }
        for (String warning : notApplied) {
            appearanceWarnings.add(shortClassName(view.getClass().getName()) + ": " + warning);
            android.util.Log.w(TAG, "warning: [atributo no aplicado] "
                    + shortClassName(view.getClass().getName()) + ": " + warning);
        }
        // Fuente y estilo del texto: se aplican aqui, al final, para cubrir tanto los atributos del
        // bean como los "extra" (android:fontFamily) y cualquier TextView, no solo los reconocidos.
        if (view instanceof android.widget.TextView textView) {
            applyTextTypeface(textView, bean);
        }
    }

    /** Resolucion de colores/medidas de la vista previa para los appliers compartidos. */
    private pro.sketchware.utility.WidgetInjectApplier.ValueResolver sharedResolver(View view) {
        return new pro.sketchware.utility.WidgetInjectApplier.ValueResolver() {
            @Override
            public int color(String value, int fallback) {
                if (value == null || value.trim().isEmpty()) {
                    return fallback;
                }
                int color = resourceResolver.resolveColor(view, value.trim(), 0);
                return isColorNotSet(color) ? fallback : color;
            }

            @Override
            public int dimension(String value, int fallback) {
                if (value == null || value.trim().isEmpty()) {
                    return fallback;
                }
                String v = value.trim();
                int resolved = resourceResolver.resolveDimen(v, -1);
                if (resolved >= 0) {
                    return resolved;
                }
                int parsed = parseDimen(v);
                return parsed == 0 && !v.startsWith("0") ? fallback : parsed;
            }
        };
    }

    /**
     * Aplica las familias con semantica propia usando los MISMOS appliers que el editor de diseno
     * (ver {@link pro.sketchware.utility.WidgetInjectApplier}). Los nombres aplicados se anotan en
     * {@code handled} para que el aplicador generico no los vuelva a procesar (ni los avise).
     */
    private void applyFamilyAttributes(View view, ViewBean bean,
                                       pro.sketchware.utility.InjectAttributeHandler handler,
                                       java.util.Set<String> handled) {
        var resolver = sharedResolver(view);
        if (view instanceof com.google.android.material.tabs.TabLayout tabLayout) {
            // El editor de diseno muestra 3 pestanas de ejemplo (ItemTabLayout); sin pestanas no hay
            // NADA que pintar, asi que los colores del TabLayout eran invisibles en la vista previa.
            // Se replican las mismas pestanas de ejemplo para que el color configurado se vea.
            ensureTabLayoutHasSampleTabs(tabLayout);
            pro.sketchware.utility.WidgetInjectApplier.applyTabLayout(tabLayout, handler, resolver);
            handled.addAll(TAB_LAYOUT_ATTRIBUTES);
        }
        if (view instanceof de.hdodenhof.circleimageview.CircleImageView circleImageView) {
            pro.sketchware.utility.WidgetInjectApplier.applyCircleImageView(circleImageView, handler, resolver);
            handled.addAll(CIRCLE_IMAGE_VIEW_ATTRIBUTES);
        }
        if (view instanceof com.google.android.material.button.MaterialButton materialButton) {
            pro.sketchware.utility.WidgetInjectApplier.applyMaterialButton(materialButton, handler, resolver);
            handled.addAll(MATERIAL_BUTTON_ATTRIBUTES);
        }
        if (view instanceof androidx.cardview.widget.CardView cardView) {
            pro.sketchware.utility.WidgetInjectApplier.applyCardView(cardView, handler, resolver, 0);
            handled.addAll(CARD_VIEW_ATTRIBUTES);
        }
        if (view instanceof android.widget.ProgressBar progressBar && !(view instanceof android.widget.SeekBar)) {
            applyProgressBarAttributes(progressBar, bean, handler, resolver, handled);
        }
        if (view instanceof android.widget.SeekBar seekBar) {
            applySeekBarAttributes(seekBar, bean, handler, resolver, handled);
        }
        if (view instanceof android.widget.RatingBar ratingBar) {
            applyRatingBarAttributes(ratingBar, handler, resolver, handled);
        }
        if (view instanceof android.widget.CompoundButton compoundButton) {
            applyCompoundButtonAttributes(compoundButton, bean, handler, resolver, handled);
        }
        if (view instanceof android.widget.CalendarView calendarView) {
            // firstDayOfWeek tiene valor por defecto 1 en el bean, asi que casi nunca llega por
            // inject: hay que leerlo del bean (igual que hace el editor de diseno).
            calendarView.setFirstDayOfWeek(bean.firstDayOfWeek);
        }
        if (view instanceof com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigationView) {
            applyBottomNavigationAttributes(bottomNavigationView, handler, resolver, handled);
        }
        if (view instanceof com.google.android.material.textfield.TextInputLayout textInputLayout) {
            applyTextInputLayoutAttributes(textInputLayout, handler, resolver, handled);
        }
        if (view instanceof com.google.android.gms.common.SignInButton signInButton) {
            applySignInButtonAttributes(signInButton, handler, handled);
        }
    }

    /**
     * Pestanas de ejemplo para un TabLayout sin pestanas: es lo que hace el editor de diseno
     * (ItemTabLayout anade "Tab 1..3"). Sin esto, los colores configurados del TabLayout
     * (indicador, texto, texto seleccionado) no se podrian ver en la vista previa.
     */
    private void ensureTabLayoutHasSampleTabs(com.google.android.material.tabs.TabLayout tabLayout) {
        if (tabLayout.getTabCount() > 0) {
            return;
        }
        for (int i = 1; i <= 3; i++) {
            tabLayout.addTab(tabLayout.newTab().setText("Tab " + i), i == 1);
        }
    }

    private void applyProgressBarAttributes(android.widget.ProgressBar progressBar, ViewBean bean,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        // El estilo horizontal (style="?android:attr/progressBarStyleHorizontal") decide si la barra
        // es una barra o un circulo indeterminado: sin esto, el progreso y el tinte no se veian.
        boolean horizontal = ViewBean.PROGRESSBAR_STYLE_HORIZONTAL.equals(bean.progressStyle)
                || handler.contains("progressTint") || handler.contains("progress");
        if (horizontal) {
            progressBar.setIndeterminate(false);
            try {
                progressBar.setProgressDrawable(getResources().getDrawable(
                        android.R.drawable.progress_horizontal, getTheme()));
            } catch (Throwable throwable) {
                android.util.Log.d(TAG, "no se pudo aplicar la barra horizontal", throwable);
            }
        }
        int progress = bean.progress;
        String progressValue = handler.getAttributeValueOf("progress");
        android.util.Log.i(TAG, "info: ProgressBar " + bean.id + " clase=" + progressBar.getClass().getName()
                + " horizontal=" + horizontal + " progressStyle=" + bean.progressStyle);
        if (!progressValue.isEmpty()) {
            try {
                progress = Integer.parseInt(progressValue);
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "progress no numerico", ignored);
            }
        }
        progressBar.setProgress(progress);
        handled.add("progress");
        int max = bean.max > 0 ? bean.max : 100;
        String maxValue = handler.getAttributeValueOf("max");
        if (!maxValue.isEmpty()) {
            try {
                max = Integer.parseInt(maxValue);
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "max no numerico", ignored);
            }
        }
        progressBar.setMax(max);
        handled.add("max");
        if (handler.contains("indeterminate")) {
            progressBar.setIndeterminate(Boolean.parseBoolean(handler.getAttributeValueOf("indeterminate")));
            handled.add("indeterminate");
        }
        applyTint(progressBar::setProgressTintList, handler, resolver, "progressTint", handled);
        applyTint(progressBar::setSecondaryProgressTintList, handler, resolver, "secondaryProgressTint", handled);
        applyTint(progressBar::setProgressBackgroundTintList, handler, resolver, "progressBackgroundTint", handled);
        applyTint(progressBar::setIndeterminateTintList, handler, resolver, "indeterminateTint", handled);
        handled.add("progressStyle");
        handled.add("style");
    }

    private void applySeekBarAttributes(android.widget.SeekBar seekBar, ViewBean bean,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        int progress = bean.progress;
        String progressValue = handler.getAttributeValueOf("progress");
        if (!progressValue.isEmpty()) {
            try {
                progress = Integer.parseInt(progressValue);
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "progress no numerico", ignored);
            }
        }
        seekBar.setProgress(progress);
        handled.add("progress");
        int max = bean.max > 0 ? bean.max : 100;
        String maxValue = handler.getAttributeValueOf("max");
        if (!maxValue.isEmpty()) {
            try {
                max = Integer.parseInt(maxValue);
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "max no numerico", ignored);
            }
        }
        seekBar.setMax(max);
        handled.add("max");
        if (handler.contains("splitTrack")) {
            seekBar.setSplitTrack(Boolean.parseBoolean(handler.getAttributeValueOf("splitTrack")));
            handled.add("splitTrack");
        }
        applyTint(seekBar::setProgressTintList, handler, resolver, "progressTint", handled);
        applyTint(seekBar::setProgressBackgroundTintList, handler, resolver, "progressBackgroundTint", handled);
        applyTint(seekBar::setThumbTintList, handler, resolver, "thumbTint", handled);
    }

    private void applyRatingBarAttributes(android.widget.RatingBar ratingBar,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        if (handler.contains("rating")) {
            try {
                ratingBar.setRating(Float.parseFloat(handler.getAttributeValueOf("rating")));
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "rating no numerico", ignored);
            }
            handled.add("rating");
        }
        if (handler.contains("numStars")) {
            try {
                ratingBar.setNumStars(Integer.parseInt(handler.getAttributeValueOf("numStars")));
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "numStars no numerico", ignored);
            }
            handled.add("numStars");
        }
        if (handler.contains("stepSize")) {
            try {
                ratingBar.setStepSize(Float.parseFloat(handler.getAttributeValueOf("stepSize")));
            } catch (NumberFormatException ignored) {
                android.util.Log.d(TAG, "stepSize no numerico", ignored);
            }
            handled.add("stepSize");
        }
        if (handler.contains("isIndicator")) {
            ratingBar.setIsIndicator(Boolean.parseBoolean(handler.getAttributeValueOf("isIndicator")));
            handled.add("isIndicator");
        }
        applyTint(ratingBar::setProgressTintList, handler, resolver, "progressTint", handled);
        applyTint(ratingBar::setSecondaryProgressTintList, handler, resolver, "secondaryProgressTint", handled);
    }

    private void applyCompoundButtonAttributes(android.widget.CompoundButton button, ViewBean bean,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        boolean checked = bean.checked != 0;
        if (handler.contains("checked")) {
            checked = Boolean.parseBoolean(handler.getAttributeValueOf("checked"));
            handled.add("checked");
        }
        button.setChecked(checked);
        applyTint(button::setButtonTintList, handler, resolver, "buttonTint", handled);
        if (button instanceof android.widget.Switch switchView) {
            applyTint(switchView::setThumbTintList, handler, resolver, "thumbTint", handled);
            applyTint(switchView::setTrackTintList, handler, resolver, "trackTint", handled);
            if (handler.contains("splitTrack")) {
                switchView.setSplitTrack(Boolean.parseBoolean(handler.getAttributeValueOf("splitTrack")));
                handled.add("splitTrack");
            }
        }
    }

    private void applyBottomNavigationAttributes(
            com.google.android.material.bottomnavigation.BottomNavigationView view,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        applyTint(view::setItemIconTintList, handler, resolver, "itemIconTint", handled);
        applyTint(view::setItemTextColor, handler, resolver, "itemTextColor", handled);
        applyTint(view::setItemRippleColor, handler, resolver, "itemRippleColor", handled);
        String itemBackground = handler.getAttributeValueOf("itemBackground");
        if (!itemBackground.isEmpty()) {
            android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(itemBackground);
            if (drawable != null) {
                view.setItemBackground(drawable);
            }
            handled.add("itemBackground");
        }
        String labelVisibility = handler.getAttributeValueOf("labelVisibilityMode");
        if (!labelVisibility.isEmpty()) {
            switch (labelVisibility) {
                case "labeled" -> view.setLabelVisibilityMode(
                        com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_LABELED);
                case "unlabeled" -> view.setLabelVisibilityMode(
                        com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_UNLABELED);
                case "selected" -> view.setLabelVisibilityMode(
                        com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_SELECTED);
                default -> view.setLabelVisibilityMode(
                        com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_AUTO);
            }
            handled.add("labelVisibilityMode");
        }
    }

    private void applyTextInputLayoutAttributes(
            com.google.android.material.textfield.TextInputLayout view,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            java.util.Set<String> handled) {
        applyTint(view::setBoxStrokeColorStateList, handler, resolver, "boxStrokeColor", handled);
        applyTint(view::setHintTextColor, handler, resolver, "hintTextColor", handled);
        applyTint(view::setBoxBackgroundColorStateList, handler, resolver, "boxBackgroundColor", handled);
        applyTint(view::setBoxStrokeErrorColor, handler, resolver, "boxStrokeErrorColor", handled);
        for (String corner : new String[]{"boxCornerRadiusTopStart", "boxCornerRadiusTopEnd",
                "boxCornerRadiusBottomStart", "boxCornerRadiusBottomEnd"}) {
            if (!handler.contains(corner)) {
                continue;
            }
            int size = resolver.dimension(handler.getAttributeValueOf(corner), 0);
            switch (corner) {
                case "boxCornerRadiusTopStart" -> view.setBoxCornerRadii(size, view.getBoxCornerRadiusTopEnd(),
                        view.getBoxCornerRadiusBottomEnd(), view.getBoxCornerRadiusBottomStart());
                case "boxCornerRadiusTopEnd" -> view.setBoxCornerRadii(view.getBoxCornerRadiusTopStart(), size,
                        view.getBoxCornerRadiusBottomEnd(), view.getBoxCornerRadiusBottomStart());
                case "boxCornerRadiusBottomEnd" -> view.setBoxCornerRadii(view.getBoxCornerRadiusTopStart(),
                        view.getBoxCornerRadiusTopEnd(), size, view.getBoxCornerRadiusBottomStart());
                default -> view.setBoxCornerRadii(view.getBoxCornerRadiusTopStart(),
                        view.getBoxCornerRadiusTopEnd(), view.getBoxCornerRadiusBottomEnd(), size);
            }
            handled.add(corner);
        }
        String placeholder = handler.getAttributeValueOf("placeholderText");
        if (!placeholder.isEmpty()) {
            view.setPlaceholderText(placeholder);
            handled.add("placeholderText");
        }
    }

    private void applySignInButtonAttributes(com.google.android.gms.common.SignInButton button,
            pro.sketchware.utility.InjectAttributeHandler handler, java.util.Set<String> handled) {
        String size = handler.getAttributeValueOf("buttonSize");
        if (!size.isEmpty()) {
            switch (size) {
                case "wide" -> button.setSize(com.google.android.gms.common.SignInButton.SIZE_WIDE);
                case "icon_only" -> button.setSize(com.google.android.gms.common.SignInButton.SIZE_ICON_ONLY);
                default -> button.setSize(com.google.android.gms.common.SignInButton.SIZE_STANDARD);
            }
            handled.add("buttonSize");
        }
        String scheme = handler.getAttributeValueOf("colorScheme");
        if (!scheme.isEmpty()) {
            switch (scheme) {
                case "light" -> button.setColorScheme(com.google.android.gms.common.SignInButton.COLOR_LIGHT);
                case "auto" -> button.setColorScheme(com.google.android.gms.common.SignInButton.COLOR_AUTO);
                default -> button.setColorScheme(com.google.android.gms.common.SignInButton.COLOR_DARK);
            }
            handled.add("colorScheme");
        }
    }

    /** Aplica un tinte (ColorStateList) resuelto de un atributo, si esta presente. */
    private void applyTint(java.util.function.Consumer<android.content.res.ColorStateList> setter,
            pro.sketchware.utility.InjectAttributeHandler handler,
            pro.sketchware.utility.WidgetInjectApplier.ValueResolver resolver,
            String name, java.util.Set<String> handled) {
        if (!handler.contains(name)) {
            return;
        }
        int color = resolver.color(handler.getAttributeValueOf(name), 0);
        if (color != 0) {
            setter.accept(android.content.res.ColorStateList.valueOf(color));
        }
        handled.add(name);
    }

    /**
     * Atributos de tipos de vista concretos del framework. Devuelve {@code null} si se ha aplicado
     * y, si no, el motivo por el que no (para el aviso ambar).
     */
    private String applyGenericAttribute(View view, String name, String value) {
        try {
            switch (name) {
                // ------------------------------------------------ comunes a cualquier vista
                case "background": {
                    if (value.startsWith("#") || value.startsWith("@color/")
                            || value.startsWith("@android:color/") || value.startsWith("?")) {
                        int color = resourceResolver.resolveColor(view, value, 0);
                        if (!isColorNotSet(color)) {
                            view.setBackgroundColor(color);
                        }
                    } else {
                        android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(value);
                        if (drawable != null) {
                            view.setBackground(drawable);
                        }
                    }
                    return null;
                }
                case "backgroundTint": {
                    int color = resourceResolver.resolveColor(view, value, 0);
                    if (isColorNotSet(color)) {
                        return "color no resoluble (" + value + ")";
                    }
                    view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    return null;
                }
                case "foregroundTint": {
                    int color = resourceResolver.resolveColor(view, value, 0);
                    if (isColorNotSet(color)) {
                        return "color no resoluble (" + value + ")";
                    }
                    view.getForeground().setTint(color);
                    return null;
                }
                case "elevation":
                    view.setElevation(sharedResolver(view).dimension(value, 0));
                    return null;
                case "translationZ":
                    view.setTranslationZ(sharedResolver(view).dimension(value, 0));
                    return null;
                case "alpha": {
                    try {
                        view.setAlpha(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        return "alpha no numerico (" + value + ")";
                    }
                    return null;
                }
                case "visibility": {
                    if ("gone".equalsIgnoreCase(value)) {
                        view.setVisibility(android.view.View.GONE);
                    } else if ("invisible".equalsIgnoreCase(value)) {
                        view.setVisibility(android.view.View.INVISIBLE);
                    } else {
                        view.setVisibility(android.view.View.VISIBLE);
                    }
                    return null;
                }
                case "padding": {
                    int pad = sharedResolver(view).dimension(value, 0);
                    view.setPadding(pad, pad, pad, pad);
                    return null;
                }
                case "paddingLeft":
                    view.setPadding(sharedResolver(view).dimension(value, 0), view.getPaddingTop(),
                            view.getPaddingRight(), view.getPaddingBottom());
                    return null;
                case "paddingTop":
                    view.setPadding(view.getPaddingLeft(), sharedResolver(view).dimension(value, 0),
                            view.getPaddingRight(), view.getPaddingBottom());
                    return null;
                case "paddingRight":
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                            sharedResolver(view).dimension(value, 0), view.getPaddingBottom());
                    return null;
                case "paddingBottom":
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                            sharedResolver(view).dimension(value, 0));
                    return null;
                case "paddingStart":
                    if (view instanceof android.view.View && android.os.Build.VERSION.SDK_INT >= 17) {
                        view.setPaddingRelative(sharedResolver(view).dimension(value, 0), view.getPaddingTop(),
                                view.getPaddingEnd(), view.getPaddingBottom());
                        return null;
                    }
                    return "paddingStart requiere API 17+";
                case "paddingEnd":
                    if (view instanceof android.view.View && android.os.Build.VERSION.SDK_INT >= 17) {
                        view.setPaddingRelative(view.getPaddingStart(), view.getPaddingTop(),
                                sharedResolver(view).dimension(value, 0), view.getPaddingBottom());
                        return null;
                    }
                    return "paddingEnd requiere API 17+";
                case "rotation":
                    view.setRotation(parseFloatOr(value, 0f));
                    return null;
                case "rotationX":
                    view.setRotationX(parseFloatOr(value, 0f));
                    return null;
                case "rotationY":
                    view.setRotationY(parseFloatOr(value, 0f));
                    return null;
                case "scaleX":
                    view.setScaleX(parseFloatOr(value, 1f));
                    return null;
                case "scaleY":
                    view.setScaleY(parseFloatOr(value, 1f));
                    return null;
                case "translationX":
                    view.setTranslationX(sharedResolver(view).dimension(value, 0));
                    return null;
                case "translationY":
                    view.setTranslationY(sharedResolver(view).dimension(value, 0));
                    return null;
                case "minWidth":
                    view.setMinimumWidth(sharedResolver(view).dimension(value, 0));
                    return null;
                case "minHeight":
                    view.setMinimumHeight(sharedResolver(view).dimension(value, 0));
                    return null;
                case "clipToPadding":
                    if (view instanceof android.view.ViewGroup group) {
                        group.setClipToPadding(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "clipToPadding solo aplica a contenedores";
                case "clickable":
                    view.setClickable(Boolean.parseBoolean(value));
                    return null;
                case "enabled":
                    view.setEnabled(Boolean.parseBoolean(value));
                    return null;
                case "focusable":
                    view.setFocusable(Boolean.parseBoolean(value));
                    return null;
                case "selected":
                    view.setSelected(Boolean.parseBoolean(value));
                    return null;
                case "keepScreenOn":
                    view.setKeepScreenOn(Boolean.parseBoolean(value));
                    return null;

                // ------------------------------------------------ TextView y derivados
                case "text":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setText(value);
                        return null;
                    }
                    return "text solo aplica a vistas de texto";
                case "textColor": {
                    int color = resourceResolver.resolveColor(view, value, 0);
                    if (isColorNotSet(color)) {
                        return "color no resoluble (" + value + ")";
                    }
                    if (view instanceof android.widget.TextView textView) {
                        textView.setTextColor(color);
                        return null;
                    }
                    if (view instanceof android.widget.ImageView imageView) {
                        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(color));
                        return null;
                    }
                    return "textColor no aplicable a " + shortClassName(view.getClass().getName());
                }
                case "textSize":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setTextSize(parseSp(value));
                        return null;
                    }
                    return "textSize solo aplica a vistas de texto";
                case "textColorHint":
                case "hintTextColor": {
                    int color = resourceResolver.resolveColor(view, value, 0);
                    if (isColorNotSet(color)) {
                        return "color no resoluble (" + value + ")";
                    }
                    if (view instanceof android.widget.TextView textView) {
                        textView.setHintTextColor(color);
                        return null;
                    }
                    return "color de hint no aplicable a " + shortClassName(view.getClass().getName());
                }
                case "gravity": {
                    int gravity = parseGravity(value);
                    if (view instanceof android.widget.TextView textView) {
                        textView.setGravity(gravity);
                        return null;
                    }
                    view.setForegroundGravity(gravity);
                    return null;
                }
                case "textAlignment": {
                    if (view instanceof android.widget.TextView textView) {
                        textView.setTextAlignment(switch (value) {
                            case "center" -> android.view.View.TEXT_ALIGNMENT_CENTER;
                            case "viewStart" -> android.view.View.TEXT_ALIGNMENT_VIEW_START;
                            case "viewEnd" -> android.view.View.TEXT_ALIGNMENT_VIEW_END;
                            default -> android.view.View.TEXT_ALIGNMENT_GRAVITY;
                        });
                        return null;
                    }
                    return "textAlignment solo aplica a vistas de texto";
                }
                case "singleLine":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setSingleLine("true".equalsIgnoreCase(value));
                        return null;
                    }
                    return "singleLine solo aplica a vistas de texto";
                case "maxLines":
                    if (view instanceof android.widget.TextView textView) {
                        try {
                            textView.setMaxLines(Integer.parseInt(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "maxLines no numerico (" + value + ")";
                        }
                    }
                    return "maxLines solo aplica a vistas de texto";
                case "lines": {
                    if (view instanceof android.widget.TextView textView) {
                        try {
                            int lines = Integer.parseInt(value);
                            textView.setLines(lines);
                            textView.setMinLines(lines);
                            textView.setMaxLines(lines);
                            return null;
                        } catch (NumberFormatException e) {
                            return "lines no numerico (" + value + ")";
                        }
                    }
                    return "lines solo aplica a vistas de texto";
                }
                case "ellipsize":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setEllipsize(switch (value) {
                            case "start" -> android.text.TextUtils.TruncateAt.START;
                            case "middle" -> android.text.TextUtils.TruncateAt.MIDDLE;
                            case "end" -> android.text.TextUtils.TruncateAt.END;
                            case "marquee" -> android.text.TextUtils.TruncateAt.MARQUEE;
                            default -> null;
                        });
                        return null;
                    }
                    return "ellipsize solo aplica a vistas de texto";
                case "letterSpacing":
                    if (view instanceof android.widget.TextView textView) {
                        if (android.os.Build.VERSION.SDK_INT >= 21) {
                            textView.setLetterSpacing(parseFloatOr(value, 0f));
                            return null;
                        }
                        return "letterSpacing requiere API 21+";
                    }
                    return "letterSpacing solo aplica a vistas de texto";
                case "lineSpacingExtra":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setLineSpacing(sharedResolver(view).dimension(value, 0), textView.getLineSpacingMultiplier());
                        return null;
                    }
                    return "lineSpacingExtra solo aplica a vistas de texto";
                case "textAllCaps":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setAllCaps(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "textAllCaps solo aplica a vistas de texto";
                case "maxLength":
                    if (view instanceof android.widget.TextView textView) {
                        try {
                            int max = Integer.parseInt(value);
                            textView.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
                            return null;
                        } catch (NumberFormatException e) {
                            return "maxLength no numerico (" + value + ")";
                        }
                    }
                    return "maxLength solo aplica a campos de texto";
                case "inputType":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setInputType(parseInputType(value));
                        return null;
                    }
                    return "inputType solo aplica a campos de texto";
                case "imeOptions":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setImeOptions(parseImeOptions(value));
                        return null;
                    }
                    return "imeOptions solo aplica a campos de texto";
                case "hint":
                    if (view instanceof android.widget.EditText editText) {
                        editText.setHint(value);
                        return null;
                    }
                    if (view instanceof com.google.android.material.textfield.TextInputLayout layout) {
                        layout.setHint(value);
                        return null;
                    }
                    return "hint solo aplica a campos de texto";
                case "textIsSelectable":
                    if (view instanceof android.widget.TextView textView) {
                        textView.setTextIsSelectable(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "textIsSelectable solo aplica a vistas de texto";

                // ------------------------------------------------ ImageView
                case "src":
                case "srcCompat":
                    if (view instanceof android.widget.ImageView imageView) {
                        android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(value);
                        imageView.setImageDrawable(drawable != null ? drawable : createMissingDrawable(value));
                        return null;
                    }
                    return "src solo aplica a imagenes";
                case "scaleType":
                    if (view instanceof android.widget.ImageView imageView) {
                        applyScaleType(imageView, value);
                        return null;
                    }
                    return "scaleType solo aplica a imagenes";
                case "adjustViewBounds":
                    if (view instanceof android.widget.ImageView imageView) {
                        imageView.setAdjustViewBounds(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "adjustViewBounds solo aplica a imagenes";
                case "tint":
                    if (view instanceof android.widget.ImageView imageView) {
                        int color = resourceResolver.resolveColor(view, value, 0);
                        if (isColorNotSet(color)) {
                            return "color no resoluble (" + value + ")";
                        }
                        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(color));
                        return null;
                    }
                    return "tint solo aplica a imagenes";

                // ------------------------------------------------ listas / contenedores
                case "divider": {
                    // El valor puede ser un COMO color ("#FFFF0000") o un drawable ("@drawable/x"):
                    // antes solo se intentaba como drawable y un color de separador se perdia.
                    android.graphics.drawable.Drawable drawable;
                    if (value.startsWith("#") || value.startsWith("@color/")
                            || value.startsWith("@android:color/") || value.startsWith("?")) {
                        int color = resourceResolver.resolveColor(view, value, 0);
                        drawable = isColorNotSet(color) ? null
                                : new android.graphics.drawable.ColorDrawable(color);
                    } else {
                        drawable = resourceResolver.resolveDrawable(value);
                    }
                    if (view instanceof android.widget.ListView listView) {
                        listView.setDivider(drawable);
                        return null;
                    }
                    if (view instanceof android.widget.LinearLayout linearLayout) {
                        linearLayout.setDividerDrawable(drawable);
                        return null;
                    }
                    return "divider no aplicable a " + shortClassName(view.getClass().getName());
                }
                case "dividerHeight":
                    if (view instanceof android.widget.ListView listView) {
                        listView.setDividerHeight(sharedResolver(view).dimension(value, 0));
                        return null;
                    }
                    return "dividerHeight solo aplica a ListView";
                case "dividerPadding":
                    if (view instanceof android.widget.LinearLayout linearLayout) {
                        linearLayout.setDividerPadding(sharedResolver(view).dimension(value, 0));
                        return null;
                    }
                    return "dividerPadding solo aplica a contenedores lineales";
                case "showDividers": {
                    int flags = 0;
                    if (value.contains("beginning")) flags |= android.widget.LinearLayout.SHOW_DIVIDER_BEGINNING;
                    if (value.contains("middle")) flags |= android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE;
                    if (value.contains("end")) flags |= android.widget.LinearLayout.SHOW_DIVIDER_END;
                    if (view instanceof android.widget.LinearLayout linearLayout) {
                        linearLayout.setShowDividers(flags);
                        return null;
                    }
                    return "showDividers solo aplica a contenedores lineales";
                }
                case "orientation":
                    if (view instanceof android.widget.LinearLayout linearLayout) {
                        linearLayout.setOrientation("horizontal".equalsIgnoreCase(value)
                                ? android.widget.LinearLayout.HORIZONTAL
                                : android.widget.LinearLayout.VERTICAL);
                        return null;
                    }
                    return "orientation solo aplica a contenedores lineales";
                case "numColumns":
                    if (view instanceof android.widget.GridView gridView) {
                        try {
                            gridView.setNumColumns(Integer.parseInt(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "numColumns no numerico (" + value + ")";
                        }
                    }
                    return "numColumns solo aplica a GridView";
                case "horizontalSpacing":
                    if (view instanceof android.widget.GridView gridView) {
                        gridView.setHorizontalSpacing(sharedResolver(view).dimension(value, 0));
                        return null;
                    }
                    return "horizontalSpacing solo aplica a GridView";
                case "verticalSpacing":
                    if (view instanceof android.widget.GridView gridView) {
                        gridView.setVerticalSpacing(sharedResolver(view).dimension(value, 0));
                        return null;
                    }
                    return "verticalSpacing solo aplica a GridView";
                case "stretchMode":
                    if (view instanceof android.widget.GridView gridView) {
                        gridView.setStretchMode(switch (value) {
                            case "columnWidth" -> android.widget.GridView.STRETCH_COLUMN_WIDTH;
                            case "spacingWidth" -> android.widget.GridView.STRETCH_SPACING;
                            case "spacingWidthUniform" -> android.widget.GridView.STRETCH_SPACING_UNIFORM;
                            default -> android.widget.GridView.STRETCH_COLUMN_WIDTH;
                        });
                        return null;
                    }
                    return "stretchMode solo aplica a GridView";
                case "choiceMode":
                    if (view instanceof android.widget.ListView listView) {
                        listView.setChoiceMode("multiple".equalsIgnoreCase(value)
                                ? android.widget.ListView.CHOICE_MODE_MULTIPLE
                                : android.widget.ListView.CHOICE_MODE_SINGLE);
                        return null;
                    }
                    return "choiceMode solo aplica a ListView";
                case "cacheColorHint":
                    if (view instanceof android.widget.AbsListView listView) {
                        listView.setCacheColorHint(resourceResolver.resolveColor(view, value, 0));
                        return null;
                    }
                    return "cacheColorHint solo aplica a listas";
                case "spinnerMode":
                    if (view instanceof android.widget.Spinner spinner) {
                        spinner.setPrompt(value);
                        return null;
                    }
                    return "spinnerMode solo aplica a Spinner";
                case "popupBackground":
                    if (view instanceof android.widget.Spinner spinner) {
                        android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(value);
                        if (drawable != null) {
                            spinner.setPopupBackgroundDrawable(drawable);
                        }
                        return null;
                    }
                    return "popupBackground solo aplica a Spinner";
                case "weightSum":
                    if (view instanceof android.widget.LinearLayout linearLayout) {
                        try {
                            linearLayout.setWeightSum(Float.parseFloat(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "weightSum no numerico (" + value + ")";
                        }
                    }
                    return "weightSum solo aplica a contenedores lineales";

                // ------------------------------------------------ fecha y hora
                case "firstDayOfWeek":
                    if (view instanceof android.widget.CalendarView calendarView) {
                        try {
                            calendarView.setFirstDayOfWeek(Integer.parseInt(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "firstDayOfWeek no numerico (" + value + ")";
                        }
                    }
                    if (view instanceof android.widget.DatePicker datePicker) {
                        try {
                            datePicker.setFirstDayOfWeek(Integer.parseInt(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "firstDayOfWeek no numerico (" + value + ")";
                        }
                    }
                    return "firstDayOfWeek solo aplica a CalendarView/DatePicker";
                case "shownWeekCount":
                    if (view instanceof android.widget.CalendarView calendarView) {
                        try {
                            calendarView.setShownWeekCount(Integer.parseInt(value));
                            return null;
                        } catch (NumberFormatException e) {
                            return "shownWeekCount no numerico (" + value + ")";
                        }
                    }
                    return "shownWeekCount solo aplica a CalendarView";
                case "dayOfWeekBackground":
                case "selectedWeekBackgroundColor":
                case "focusedMonthDateColor":
                case "weekNumberColor": {
                    int color = resourceResolver.resolveColor(view, value, 0);
                    if (isColorNotSet(color)) {
                        return "color no resoluble (" + value + ")";
                    }
                    if (view instanceof android.widget.CalendarView calendarView) {
                        switch (name) {
                            case "selectedWeekBackgroundColor" -> calendarView.setSelectedWeekBackgroundColor(color);
                            case "focusedMonthDateColor" -> calendarView.setFocusedMonthDateColor(color);
                            case "weekNumberColor" -> calendarView.setWeekNumberColor(color);
                            default -> calendarView.setUnfocusedMonthDateColor(color);
                        }
                        return null;
                    }
                    return name + " solo aplica a CalendarView";
                }
                case "datePickerMode":
                case "timePickerMode":
                    return "el modo (calendar/spinner) se elige por codigo en la app compilada";
                case "calendarViewShown":
                    if (view instanceof android.widget.DatePicker datePicker) {
                        datePicker.setCalendarViewShown(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "calendarViewShown solo aplica a DatePicker";
                case "spinnersShown":
                    if (view instanceof android.widget.DatePicker datePicker) {
                        datePicker.setSpinnersShown(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "spinnersShown solo aplica a DatePicker";
                case "iconifiedByDefault":
                    if (view instanceof android.widget.SearchView searchView) {
                        searchView.setIconifiedByDefault(Boolean.parseBoolean(value));
                        return null;
                    }
                    return "iconifiedByDefault solo aplica a SearchView";
                case "queryHint":
                    if (view instanceof android.widget.SearchView searchView) {
                        searchView.setQueryHint(value);
                        return null;
                    }
                    return "queryHint solo aplica a SearchView";

                // ------------------------------------------------ piezas que la vista previa no
                // puede resolver porque se configuran por CODIGO en la app compilada
                case "menu":
                case "itemCount":
                case "listitem":
                    return "se configura por codigo en la app (no hay XML equivalente)";
                case "colorScheme":
                case "colorSchemeColors":
                    return "SwipeRefreshLayout define sus colores en codigo (setColorSchemeColors)";

                default:
                    return "atributo no reconocido por la lista de la vista previa";
            }
        } catch (Throwable throwable) {
            return conciseMessage(throwable);
        }
    }

    // ------------------------------------------------------------------ reflexion

    /**
     * Ultimo recurso: aplicar el atributo llamando a un setter del PROPIO widget.
     *
     * <p>Con esto, un atributo de libreria ({@code app:loQueSea}) se aplica si la clase destino
     * tiene un setter convencional, sin necesidad de conocerlo de antemano: se prueban variantes del
     * nombre de mas especifica a mas generica ({@code civ_border_color} -> {@code setCivBorderColor},
     * {@code setBorderColor}) y el valor se convierte segun el tipo del parametro (int de color,
     * int de medida, boolean, float, CharSequence, ColorStateList).
     *
     * @return {@code null} si se ha aplicado; si no, el motivo para el aviso ambar.
     */
    private String applyViaReflection(View view, String name, String value) {
        String[] tokens = name.split("[_.]");
        if (tokens.length == 0) {
            return "nombre de atributo vacio";
        }
        java.util.List<String> candidates = new ArrayList<>();
        for (int start = 0; start < tokens.length; start++) {
            StringBuilder candidate = new StringBuilder();
            for (int i = start; i < tokens.length; i++) {
                if (tokens[i].isEmpty()) {
                    continue;
                }
                candidate.append(Character.toUpperCase(tokens[i].charAt(0)))
                        .append(tokens[i].substring(1));
            }
            if (candidate.length() > 0 && !candidates.contains(candidate.toString())) {
                candidates.add(candidate.toString());
            }
        }
        for (String candidate : candidates) {
            for (Class<?> clazz = view.getClass(); clazz != null && clazz != Object.class;
                 clazz = clazz.getSuperclass()) {
                java.lang.reflect.Method method = findSingleArgSetter(clazz, "set" + candidate);
                if (method == null) {
                    continue;
                }
                Object argument = convertForSetter(method.getParameterTypes()[0], view, name, value);
                if (argument == null) {
                    continue;
                }
                try {
                    method.invoke(view, argument);
                    android.util.Log.i(TAG, "info: atributo " + name + " aplicado con " + clazz.getSimpleName()
                            + "." + method.getName());
                    return null;
                } catch (Throwable throwable) {
                    return "el setter " + method.getName() + " rechazo el valor (" + conciseMessage(throwable) + ")";
                }
            }
        }
        return "no se ha encontrado un setter equivalente en "
                + shortClassName(view.getClass().getName());
    }

    private static java.lang.reflect.Method findSingleArgSetter(Class<?> clazz, String methodName) {
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == 1
                    && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return method;
            }
        }
        return null;
    }

    /** Convierte el valor del XML al tipo del parametro del setter; null = no sabemos convertirlo. */
    private Object convertForSetter(Class<?> type, View view, String name, String value) {
        String lowerName = name.toLowerCase(Locale.US);
        boolean colorishName = lowerName.contains("color") || lowerName.contains("tint")
                || lowerName.contains("colour");
        boolean colorishValue = value.startsWith("#") || value.startsWith("@color/")
                || value.startsWith("@android:color/") || value.startsWith("?");
        if (type == int.class || type == Integer.class) {
            if (colorishValue || colorishName) {
                int color = resourceResolver.resolveColor(view, value, 0);
                return isColorNotSet(color) ? null : color;
            }
            return sharedResolver(view).dimension(value, 0);
        }
        if (type == float.class || type == Float.class) {
            if (colorishValue) {
                return null;
            }
            return (float) sharedResolver(view).dimension(value, 0);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type == android.content.res.ColorStateList.class) {
            int color = resourceResolver.resolveColor(view, value, 0);
            return isColorNotSet(color) ? null : android.content.res.ColorStateList.valueOf(color);
        }
        if (CharSequence.class.isAssignableFrom(type) || type == String.class) {
            return value;
        }
        if (type == android.graphics.drawable.Drawable.class) {
            return isResourceReference(value) ? resourceResolver.resolveDrawable(value) : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Atributos que NO son "aspecto" sino posicion/tamano en el padre: los aplica el parser (bean de
     * layout) y {@link #applyLayoutParams}. Se ignoran a proposito para no ensuciar el aviso ambar.
     */
    private static boolean isLayoutOnlyAttribute(String name) {
        return LAYOUT_ONLY_ATTRIBUTES.contains(name) || name.startsWith("layout_") || name.startsWith("tools:");
    }

    private static final java.util.Set<String> LAYOUT_ONLY_ATTRIBUTES = java.util.Set.of(
            "id", "layout", "weightSum", "listitem", "orientation_");

    private float parseFloatOr(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseInputType(String value) {
        int type = android.text.InputType.TYPE_CLASS_TEXT;
        if (value == null) {
            return type;
        }
        if (value.contains("number")) {
            type = value.contains("decimal")
                    ? android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    : android.text.InputType.TYPE_CLASS_NUMBER;
        }
        if (value.contains("phone")) {
            type = android.text.InputType.TYPE_CLASS_PHONE;
        }
        if (value.contains("textEmailAddress")) {
            type = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        }
        if (value.contains("textPassword")) {
            type = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
        }
        if (value.contains("textMultiLine")) {
            type = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        if (value.contains("textPersonName")) {
            type = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
        }
        return type;
    }

    private int parseImeOptions(String value) {
        if (value == null) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
        }
        if (value.contains("actionDone")) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
        }
        if (value.contains("actionSearch")) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH;
        }
        if (value.contains("actionNext")) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_NEXT;
        }
        if (value.contains("actionGo")) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_GO;
        }
        if (value.contains("actionSend")) {
            return android.view.inputmethod.EditorInfo.IME_ACTION_SEND;
        }
        return android.view.inputmethod.EditorInfo.IME_ACTION_NONE;
    }

    // ---------------------------------------------- atributos de las familias compartidas

    private static final java.util.Set<String> TAB_LAYOUT_ATTRIBUTES = java.util.Set.of(
            "tabGravity", "tabMode", "tabIndicatorHeight", "tabIndicatorColor", "tabTextColor",
            "tabSelectedTextColor");

    private static final java.util.Set<String> CIRCLE_IMAGE_VIEW_ATTRIBUTES = java.util.Set.of(
            "civ_border_color", "civ_circle_background_color", "civ_border_width", "civ_border_overlay",
            "civ_fill_color");

    private static final java.util.Set<String> MATERIAL_BUTTON_ATTRIBUTES = java.util.Set.of(
            "cornerRadius", "strokeWidth", "strokeColor", "iconTint", "iconSize", "insetTop", "insetBottom");

    private static final java.util.Set<String> CARD_VIEW_ATTRIBUTES = java.util.Set.of(
            "cardBackgroundColor", "cardElevation", "cardCornerRadius", "cardUseCompatPadding",
            "cardMaxElevation", "cardPreventCornerOverlap", "strokeColor", "strokeWidth");

    private int parseGravity(String value) {
        int gravity = android.view.Gravity.NO_GRAVITY;
        if (value == null) {
            return gravity;
        }
        if (value.contains("top")) gravity |= android.view.Gravity.TOP;
        if (value.contains("bottom")) gravity |= android.view.Gravity.BOTTOM;
        if (value.contains("left")) gravity |= android.view.Gravity.LEFT;
        if (value.contains("right")) gravity |= android.view.Gravity.RIGHT;
        if (value.contains("center_horizontal")) gravity |= android.view.Gravity.CENTER_HORIZONTAL;
        if (value.contains("center_vertical")) gravity |= android.view.Gravity.CENTER_VERTICAL;
        if (value.contains("center")) gravity |= android.view.Gravity.CENTER;
        if (value.contains("end")) gravity |= android.view.Gravity.END;
        if (value.contains("start")) gravity |= android.view.Gravity.START;
        return gravity;
    }

    /**
     * Aplica scaleType sin que un widget que no lo admita tumbe la vista previa.
     *
     * <p>{@code de.hdodenhof.circleimageview.CircleImageView} solo acepta CENTER_CROP y
     * lanza {@code IllegalArgumentException: ScaleType X not supported} con cualquier otro (el bean
     * trae CENTER por defecto y el XML del editor no siempre llevaba atributo). Antes esa excepcion
     * escapaba de tryInflateRealLayout y abortaba TODA la preview nativa: una sola vista con un
     * atributo no admitido ocultaba el resto del diseno (hijos incluidos). Desde la ronda 6 se anota
     * como aviso y se sigue dibujando; desde la ronda 7, ademas, el valor se <b>ajusta</b> a uno
     * soportado y el aviso dice a cual ("scaleType CENTER -> ajustado a CENTER_CROP").
     */
    private void applyScaleType(android.widget.ImageView imageView, String value) {
        boolean circleImageView = isCircleImageViewInstance(imageView);
        // Un CircleImageView no puede recibir otro valor: se ajusta antes de tocar la vista.
        String xmlValue = circleImageView
                ? ScaleTypeCompat.adjustForCircleImageView(value)
                : ScaleTypeCompat.toXmlValue(value);
        if (circleImageView && !ScaleTypeCompat.isSupportedByCircleImageView(value)) {
            appearanceWarnings.add(shortClassName(imageView.getClass().getName())
                    + ": scaleType " + ScaleTypeCompat.describe(value) + " -> ajustado a "
                    + ScaleTypeCompat.describe(xmlValue) + " (el widget solo admite CENTER_CROP)");
            android.util.Log.w(TAG, "warning: CircleImageView: scaleType " + ScaleTypeCompat.describe(value)
                    + " -> ajustado a " + ScaleTypeCompat.describe(xmlValue));
        }
        android.widget.ImageView.ScaleType scaleType = parseScaleType(xmlValue);
        try {
            imageView.setScaleType(scaleType);
        } catch (Throwable throwable) {
            // Red de seguridad para subclases que rechacen tambien el valor ajustado.
            String fallback = circleImageView ? ScaleTypeCompat.describe(ScaleTypeCompat.CIRCLE_IMAGE_VIEW_FALLBACK_XML)
                    : "valor por defecto";
            if (circleImageView) {
                try {
                    imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                } catch (Throwable ignored) {
                    android.util.Log.d(TAG, "LayoutPreviewActivity: respaldo CENTER_CROP ignorado", ignored);
                }
            }
            appearanceWarnings.add(shortClassName(imageView.getClass().getName())
                    + ": scaleType " + ScaleTypeCompat.describe(value) + " no admitido -> ajustado a "
                    + fallback + " (" + conciseMessage(throwable) + ")");
            android.util.Log.w(TAG, "warning: scaleType " + ScaleTypeCompat.describe(value) + " no admitido por "
                    + imageView.getClass().getName(), throwable);
        }
    }

    /**
     * ¿La vista es (o hereda de) {@code de.hdodenhof.circleimageview.CircleImageView}? Se compara el
     * nombre de la clase y de sus superclases, asi tambien cuenta la variante del editor
     * ({@code ItemCircleImageView}).
     */
    private static boolean isCircleImageViewInstance(android.view.View view) {
        for (Class<?> clazz = view.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            if (ScaleTypeCompat.isCircleImageViewName(clazz.getName())) {
                return true;
            }
        }
        return false;
    }

    private android.widget.ImageView.ScaleType parseScaleType(String value) {
        // Acepta las dos formas del valor: CENTER_CROP (lo que guarda el editor) y centerCrop (XML).
        String enumName = ScaleTypeCompat.toEnumName(value);
        if (enumName == null) {
            return android.widget.ImageView.ScaleType.FIT_CENTER;
        }
        return android.widget.ImageView.ScaleType.valueOf(enumName);
    }

    private float parseSp(String value) {
        if (value == null || value.isEmpty()) {
            return 14f;
        }
        String v = value.trim();
        if (v.toLowerCase(Locale.US).endsWith("sp")) {
            v = v.substring(0, v.length() - 2);
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 14f;
        }
    }

    private int parseDimen(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String v = value.trim();
        float density = getResources().getDisplayMetrics().density;
        if (v.toLowerCase(Locale.US).endsWith("dp") || v.toLowerCase(Locale.US).endsWith("dip")) {
            v = v.substring(0, v.length() - (v.endsWith("dip") ? 3 : 2));
        } else if (v.toLowerCase(Locale.US).endsWith("px")) {
            v = v.substring(0, v.length() - 2);
            try {
                return (int) Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return (int) (Float.parseFloat(v) * density);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isResourceReference(String value) {
        return value.startsWith("@") || value.startsWith("?");
    }

    /**
     * CardView: radio, elevacion y borde. Se delega en el applier compartido con el editor de
     * diseno ({@link pro.sketchware.utility.WidgetInjectApplier#applyCardView}) para que las dos
     * vistas previas no se contradigan. El fondo de la tarjeta sale del bean de layout (que es donde
     * el parser guarda {@code app:cardBackgroundColor} y {@code android:background}).
     */
    private void applyCardViewInject(androidx.cardview.widget.CardView cardView, ViewBean bean) {
        if (bean == null) {
            return;
        }
        var handler = new pro.sketchware.utility.InjectAttributeHandler(bean);
        int fallbackBackground = 0;
        if (handler.getAttributeValueOf("cardBackgroundColor").isEmpty() && bean.layout != null) {
            String resColor = bean.layout.backgroundResColor;
            if (resColor != null && !resColor.isEmpty()) {
                int resolved = resourceResolver.resolveColor(cardView, resColor, 0);
                if (!isColorNotSet(resolved)) {
                    fallbackBackground = resolved;
                }
            } else if (!isColorNotSet(bean.layout.backgroundColor)) {
                fallbackBackground = bean.layout.backgroundColor;
            }
        }
        pro.sketchware.utility.WidgetInjectApplier.applyCardView(
                cardView, handler, sharedResolver(cardView), fallbackBackground);
    }

    private void applyLayoutParams(View view, ViewBean bean, ViewGroup parent) {
        com.besome.sketch.beans.LayoutBean layout = bean.layout;
        if (layout == null) {
            return;
        }
        int width = layout.width == com.besome.sketch.beans.LayoutBean.LAYOUT_MATCH_PARENT
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : layout.width == com.besome.sketch.beans.LayoutBean.LAYOUT_WRAP_CONTENT
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : dp(layout.width);
        int height = layout.height == com.besome.sketch.beans.LayoutBean.LAYOUT_MATCH_PARENT
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : layout.height == com.besome.sketch.beans.LayoutBean.LAYOUT_WRAP_CONTENT
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : dp(layout.height);

        if (parent instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout.LayoutParams params =
                    new android.widget.LinearLayout.LayoutParams(width, height, layout.weight);
            params.setMargins(dp(layout.marginLeft), dp(layout.marginTop), dp(layout.marginRight), dp(layout.marginBottom));
            params.gravity = layout.layoutGravity;
            view.setLayoutParams(params);
            return;
        }
        if (parent instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout.LayoutParams params =
                    new android.widget.FrameLayout.LayoutParams(width, height, layout.layoutGravity);
            params.setMargins(dp(layout.marginLeft), dp(layout.marginTop), dp(layout.marginRight), dp(layout.marginBottom));
            view.setLayoutParams(params);
            return;
        }
        if (parent instanceof android.widget.RelativeLayout) {
            android.widget.RelativeLayout.LayoutParams params =
                    new android.widget.RelativeLayout.LayoutParams(width, height);
            params.setMargins(dp(layout.marginLeft), dp(layout.marginTop), dp(layout.marginRight), dp(layout.marginBottom));
            view.setLayoutParams(params);
            return;
        }
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(width, height);
        view.setLayoutParams(params);
    }

    private boolean renderWithViewPane(String layoutName, String xml) {
        try {
            pane.removeAllViews();
            pane.updateRootLayout(scId, layoutName);
            var parser = new ViewBeanParser(xml);
            ArrayList<ViewBean> beans = parser.parse();
            loadViews(beans);
            wirePaneNavigation(layoutName);
            debug("Preview nativa OK · vistas: " + beans.size());
            return true;
        } catch (Exception e) {
            debug("Render error: " + e.getMessage());
            return false;
        }
    }

    private void wirePaneNavigation(String layoutName) {
        String javaName = ProjectFileBean.getJavaName(layoutName);
        Map<String, String> navigationMap = buildNavigationMap(javaName);
        if (navigationMap.isEmpty()) {
            return;
        }
        walkPaneAndWire(pane, navigationMap);
    }

    private void walkPaneAndWire(View view, Map<String, String> navigationMap) {
        if (view instanceof ItemView itemView) {
            ViewBean bean = itemView.getBean();
            if (bean != null && bean.id != null) {
                String target = navigationMap.get(bean.id);
                if (target != null) {
                    final String targetLayout = target;
                    view.setOnClickListener(v -> navigateTo(targetLayout));
                }
            }
        }
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                walkPaneAndWire(viewGroup.getChildAt(i), navigationMap);
            }
        }
    }

    private void wireNavigation(View root, String layoutName) {        String javaName = ProjectFileBean.getJavaName(layoutName);
        Map<String, String> navigationMap = buildNavigationMap(javaName);
        if (navigationMap.isEmpty()) {
            return;
        }
        walkAndWire(root, navigationMap);
    }

    private void walkAndWire(View view, Map<String, String> navigationMap) {
        String idName = resolveViewIdName(view);
        if (idName != null && navigationMap.containsKey(idName)) {
            final String targetLayout = navigationMap.get(idName);
            view.setOnClickListener(v -> navigateTo(targetLayout));
        }
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                walkAndWire(viewGroup.getChildAt(i), navigationMap);
            }
        }
    }

    private String resolveViewIdName(View view) {
        String recorded = viewIdNames.get(view);
        if (recorded != null && !recorded.isEmpty()) {
            return recorded;
        }
        int id = view.getId();
        if (id == View.NO_ID) {
            return null;
        }
        try {
            return getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    private void navigateTo(String targetLayoutName) {
        if (targetLayoutName == null || targetLayoutName.equals(layoutHistory.peek())) {
            return;
        }
        layoutHistory.push(targetLayoutName);
        currentLayout = targetLayoutName;
        renderCurrentLayout();
    }

    private int setupWebViews(View root, String layoutName) {
        String assetsDir = new FilePathUtil().getPathAssets(scId);
        String javaSource = collectJavaSource(layoutName);
        int[] count = {0};
        walkWebViews(root, javaSource, assetsDir, count);
        return count[0];
    }

    private String collectJavaSource(String layoutName) {
        StringBuilder combined = new StringBuilder();
        try {
            String javaName = ProjectFileBean.getJavaName(layoutName);
            String generated = new yq(getApplicationContext(), scId)
                    .getFileSrc(javaName, jC.b(scId), jC.a(scId), jC.c(scId));
            if (generated != null) {
                combined.append(generated).append('\n');
            }
            // Also scan the on-disk java file (the user may have edited the code editor).
            String packageName = "";
            java.util.HashMap<String, Object> metadata = a.a.a.lC.b(scId);
            if (metadata != null && metadata.get("my_sc_pkg_name") != null) {
                packageName = String.valueOf(metadata.get("my_sc_pkg_name"));
            }
            if (!packageName.isEmpty()) {
                File javaFile = new File(new FilePathUtil().getPathJava(scId),
                        packageName.replace('.', '/') + "/" + javaName + ".java");
                if (javaFile.isFile()) {
                    String diskContent = FileUtil.readFile(javaFile.getAbsolutePath());
                    if (diskContent != null) {
                        combined.append(diskContent);
                    }
                }
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Throwable ignored", ignored);
        }
        return combined.toString();
    }

    private void walkWebViews(View view, String javaSource, String assetsDir, int[] count) {
        if (view instanceof WebView webView) {
            ensureWebViewVisible(webView);
            configureAndLoad(webView, javaSource, assetsDir);
            count[0]++;
        }
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                walkWebViews(viewGroup.getChildAt(i), javaSource, assetsDir, count);
            }
        }
    }

    private void ensureWebViewVisible(WebView webView) {
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        if (params != null && params.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            webView.setMinimumHeight(dp(240));
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void configureAndLoad(WebView webView, String javaSource, String assetsDir) {
        try {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setAllowFileAccess(true);
            webView.getSettings().setAllowFileAccessFromFileURLs(true);
            webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Throwable ignored", ignored);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                view.loadUrl(rewriteUrl(url, assetsDir));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                debug("WebView error: " + error.getErrorCode() + " " + (request == null || request.getUrl() == null ? "" : request.getUrl()));
            }
        });

        String viewIdName = resolveViewIdName(webView);
        String url = findWebViewUrl(javaSource, viewIdName);
        if (url != null && !url.trim().isEmpty() && !isScriptUrl(url)) {
            String finalUrl = rewriteUrl(url, assetsDir);
            debug("WebView " + viewIdName + " -> " + finalUrl);
            webView.loadUrl(finalUrl);
            return;
        }
        String baseUrl = findWebViewBaseUrl(javaSource, viewIdName);
        if (baseUrl != null) {
            debug("WebView " + viewIdName + " base -> " + rewriteUrl(baseUrl, assetsDir));
            webView.loadDataWithBaseURL(rewriteUrl(baseUrl, assetsDir), "", "text/html", "utf-8", null);
            return;
        }
        File htmlFile = findFirstHtmlFile(
                new File(assetsDir),
                new File(wq.b(scId), "files/html"),
                new File(wq.b(scId), "files"));
        if (htmlFile != null) {
            debug("WebView " + viewIdName + " fallback html -> " + htmlFile.getAbsolutePath());
            webView.loadUrl("file://" + htmlFile.getAbsolutePath());
        } else {
            debug("WebView " + viewIdName + ": no URL en el codigo ni HTML en assets.");
        }
    }

    private boolean isScriptUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".json");
    }

    private String findWebViewUrl(String javaSource, String viewIdName) {
        if (javaSource == null || javaSource.isEmpty()) {
            return null;
        }
        java.util.List<String> knownIds = new ArrayList<>(viewIdNames.values());
        Matcher matcher = LOAD_URL_PATTERN.matcher(javaSource);
        String fallback = null;
        while (matcher.find()) {
            String receiver = matcher.group(1);
            String target = matcher.group(2);
            if (isScriptUrl(target)) {
                // hooks.js and similar init scripts are not pages; keep scanning for the real URL.
                continue;
            }
            if (receiver.equals(viewIdName)) {
                return target;
            }
            if (knownIds.contains(receiver)) {
                return target;
            }
            if (fallback == null) {
                fallback = target;
            }
        }
        return fallback;
    }

    private String findWebViewBaseUrl(String javaSource, String viewIdName) {
        if (javaSource == null || javaSource.isEmpty()) {
            return null;
        }
        java.util.List<String> knownIds = new ArrayList<>(viewIdNames.values());
        Matcher matcher = LOAD_BASE_URL_PATTERN.matcher(javaSource);
        String fallback = null;
        while (matcher.find()) {
            String receiver = matcher.group(1);
            String target = matcher.group(2);
            if (receiver.equals(viewIdName)) {
                return target;
            }
            if (knownIds.contains(receiver)) {
                return target;
            }
            if (fallback == null) {
                fallback = target;
            }
        }
        return fallback;
    }

    private String rewriteUrl(String url, String assetsDir) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return url;
        }
        if (lower.startsWith("file:///android_asset")) {
            String relative = url.substring("file:///android_asset".length());
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return "file://" + assetsDir + "/" + relative;
        }
        if (lower.startsWith("file://")) {
            return url;
        }
        String relative = url;
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (lower.startsWith("assets/")) {
            relative = relative.substring("assets/".length());
        }
        return "file://" + assetsDir + "/" + relative;
    }

    private File findFirstHtmlFile(File... dirs) {
        for (File dir : dirs) {
            File found = findHtmlInDirectory(dir);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private File findHtmlInDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase("index.html")) {
                return child;
            }
        }
        for (File child : children) {
            if (child.isFile() && child.getName().toLowerCase(Locale.US).endsWith(".html")) {
                return child;
            }
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findHtmlInDirectory(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Map<String, String> buildNavigationMap(String javaName) {
        Map<String, String> navigationMap = new HashMap<>();
        try {
            String javaSource = new yq(getApplicationContext(), scId)
                    .getFileSrc(javaName, jC.b(scId), jC.a(scId), jC.c(scId));
            if (javaSource == null || javaSource.isEmpty()) {
                return navigationMap;
            }

            Matcher matcher = NAV_PATTERN.matcher(javaSource);
            while (matcher.find()) {
                String viewId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                String targetClass = matcher.group(3);
                String targetLayout = findLayoutForJavaName(targetClass);
                if (viewId != null && targetLayout != null) {
                    navigationMap.put(viewId, targetLayout);
                }
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Throwable ignored", ignored);
        }
        return navigationMap;
    }

    private String findLayoutForJavaName(String javaName) {
        try {
            hC projectFileManager = jC.b(scId);
            ArrayList<ProjectFileBean> files = new ArrayList<>(projectFileManager.b());
            files.addAll(new ArrayList<>(projectFileManager.c()));
            for (ProjectFileBean file : files) {
                if (javaName.equals(file.getJavaName())) {
                    return file.getXmlName();
                }
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Throwable ignored", ignored);
        }
        return null;
    }

    private ItemView loadView(ViewBean view) {
        var itemView = pane.createItemView(view);
        pane.addViewAndUpdateIndex(itemView);
        if (itemView instanceof ItemView sy) {
            sy.setFixed(true);
            return sy;
        }
        return null;
    }

    private ItemView loadViews(ArrayList<ViewBean> views) {
        ItemView itemView = null;
        for (ViewBean view : views) {
            if (views.indexOf(view) == 0) {
                view.parent = "root";
                view.parentType = 0;
                view.preParent = null;
                view.preParentType = -1;
                itemView = loadView(view);
            } else {
                loadView(view);
            }
        }
        return itemView;
    }
}
