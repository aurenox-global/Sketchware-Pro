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
import java.util.Locale;
import java.util.Map;
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

    private static final String TAG = "LayoutPreview";

    private void debug(String message) {
        binding.debugStatus.setVisibility(android.view.View.VISIBLE);
        binding.debugStatus.setBackgroundColor(0xB3000000);
        binding.debugStatus.setText(message);
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
            if (renderWarnings.isEmpty() && resourceResolver.getWarnings().isEmpty()) {
                debug("Preview OK · vistas: " + viewsById.size() + " · WebViews: " + webViewCount + sample);
            } else {
                // Degradacion visible (requisito: nunca un lienzo mudo sin motivo): se listan tanto las
                // vistas que no se han podido crear como los recursos (colores/fuentes/imagenes) que no
                // se han podido resolver.
                java.util.List<String> pendientes = new ArrayList<>(renderWarnings);
                pendientes.addAll(resourceResolver.getWarnings());
                debugWarning("Preview PARCIAL · vistas: " + viewsById.size() + " · no disponibles: "
                        + android.text.TextUtils.join(", ", pendientes));
            }
            return true;
        } catch (Throwable throwable) {
            pane.removeAllViews();
            debugWarning("Preview FAIL: " + throwable);
            return false;
        }
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
        View view = pro.sketchware.utility.InvokeUtil.createView(this, className);
        if (view == null) {
            // La clase no se puede instanciar en el editor (normalmente una libreria/widat que el
            // IDE no incluye, o una vista propia del proyecto). Antes se saltaba en silencio, asi que
            // el layout aparecia "vacio" sin explicacion: ahora pintamos un marcador visible y lo
            // anotamos para avisar en la barra de estado.
            renderWarnings.add(className);
            android.util.Log.w(TAG, "warning: no se pudo crear la vista " + className + " (id=" + bean.id + ")");
            return createMissingViewPlaceholder(className);
        }
        view.setId(android.view.View.generateViewId());
        applyBeanAppearance(view, bean);
        return view;
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
     * Marcador visible que sustituye a una vista que el editor no sabe inflar. Mantiene el hueco en
     * la jerarquia (para no romper el layout del resto) y explica que falta.
     */
    private View createMissingViewPlaceholder(String className) {
        android.widget.TextView placeholder = new android.widget.TextView(this);
        String shortName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        placeholder.setText("⚠ " + shortName + ": no disponible en el editor");
        placeholder.setTextSize(11f);
        placeholder.setTextColor(0xFFB00020);
        placeholder.setPadding(dp(6), dp(6), dp(6), dp(6));
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(0x14B00020);
        border.setStroke(dp(1), 0xFFB00020);
        placeholder.setBackground(border);
        return placeholder;
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
            applyCardViewInject(cardView, bean.inject);
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
                        // Hueco mudo no: marcador visible con el motivo (ver createMissingViewPlaceholder).
                        imageView.setImageDrawable(createMissingDrawable(image.resName));
                    }
                }
                if (image.scaleType != null && !image.scaleType.isEmpty()) {
                    imageView.setScaleType(parseScaleType(image.scaleType));
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
            renderWarnings.add(value);
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

    private void applyInjectAttributes(View view, ViewBean bean) {
        try {
            var injectHandler = new pro.sketchware.utility.InjectAttributeHandler(bean);
            for (android.util.Pair<String, String> pair : injectHandler.getAttributes()) {
                switch (localAttrName(pair.first)) {
                    case "background": {
                        String value = pair.second;
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
                        break;
                    }
                    case "backgroundTint": {
                        int color = resourceResolver.resolveColor(view, pair.second, 0);
                        if (!isColorNotSet(color)) {
                            view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                        }
                        break;
                    }
                    case "textColor": {
                        int color = resourceResolver.resolveColor(view, pair.second, 0);
                        if (!isColorNotSet(color) && view instanceof android.widget.TextView textView) {
                            textView.setTextColor(color);
                        }
                        break;
                    }
                    case "gravity": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setGravity(parseGravity(pair.second));
                        } else {
                            view.setForegroundGravity(parseGravity(pair.second));
                        }
                        break;
                    }
                    case "orientation": {
                        if (view instanceof android.widget.LinearLayout linearLayout) {
                            linearLayout.setOrientation("horizontal".equalsIgnoreCase(pair.second)
                                    ? android.widget.LinearLayout.HORIZONTAL
                                    : android.widget.LinearLayout.VERTICAL);
                        }
                        break;
                    }
                    case "elevation": {
                        view.setElevation(parseDimen(pair.second));
                        break;
                    }
                    case "alpha": {
                        try {
                            view.setAlpha(Float.parseFloat(pair.second));
                        } catch (NumberFormatException ignored) {
                            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: NumberFormatException ignored", ignored);
                        }
                        break;
                    }
                    case "visibility": {
                        if ("gone".equalsIgnoreCase(pair.second)) {
                            view.setVisibility(android.view.View.GONE);
                        } else if ("invisible".equalsIgnoreCase(pair.second)) {
                            view.setVisibility(android.view.View.INVISIBLE);
                        }
                        break;
                    }
                    case "padding": {
                        int pad = parseDimen(pair.second);
                        view.setPadding(pad, pad, pad, pad);
                        break;
                    }
                    case "paddingLeft": {
                        view.setPadding(parseDimen(pair.second), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
                        break;
                    }
                    case "paddingTop": {
                        view.setPadding(view.getPaddingLeft(), parseDimen(pair.second), view.getPaddingRight(), view.getPaddingBottom());
                        break;
                    }
                    case "paddingRight": {
                        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), parseDimen(pair.second), view.getPaddingBottom());
                        break;
                    }
                    case "paddingBottom": {
                        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), parseDimen(pair.second));
                        break;
                    }
                    case "textSize": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setTextSize(parseSp(pair.second));
                        }
                        break;
                    }
                    case "hint": {
                        if (view instanceof android.widget.EditText editText) {
                            editText.setHint(pair.second);
                        }
                        break;
                    }
                    // android:src y app:srcCompat: el mismo tratamiento que en el resto de la
                    // preview (drawable del proyecto, de assets o del propio IDE). Si no se puede
                    // resolver, marcador visible en vez de un hueco vacio.
                    case "src":
                    case "srcCompat": {
                        if (view instanceof android.widget.ImageView imageView) {
                            android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(pair.second);
                            imageView.setImageDrawable(drawable != null
                                    ? drawable
                                    : createMissingDrawable(pair.second));
                        }
                        break;
                    }
                    case "scaleType": {
                        if (view instanceof android.widget.ImageView imageView) {
                            imageView.setScaleType(parseScaleType(pair.second));
                        }
                        break;
                    }
                    case "singleLine": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setSingleLine("true".equalsIgnoreCase(pair.second));
                        }
                        break;
                    }
                    case "maxLines": {
                        if (view instanceof android.widget.TextView textView) {
                            try {
                                textView.setMaxLines(Integer.parseInt(pair.second));
                            } catch (NumberFormatException ignored) {
                                android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: NumberFormatException ignored", ignored);
                            }
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: Throwable ignored", ignored);
        }
        // Fuente y estilo del texto: se aplican aqui, al final, para cubrir tanto los atributos del
        // bean como los "extra" (android:fontFamily) y cualquier TextView, no solo los reconocidos.
        if (view instanceof android.widget.TextView textView) {
            applyTextTypeface(textView, bean);
        }
    }

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

    private android.widget.ImageView.ScaleType parseScaleType(String value) {
        if (value == null) {
            return android.widget.ImageView.ScaleType.FIT_CENTER;
        }
        switch (value) {
            case "centerCrop":
                return android.widget.ImageView.ScaleType.CENTER_CROP;
            case "centerInside":
                return android.widget.ImageView.ScaleType.CENTER_INSIDE;
            case "fitXY":
                return android.widget.ImageView.ScaleType.FIT_XY;
            case "fitStart":
                return android.widget.ImageView.ScaleType.FIT_START;
            case "fitEnd":
                return android.widget.ImageView.ScaleType.FIT_END;
            case "center":
                return android.widget.ImageView.ScaleType.CENTER;
            default:
                return android.widget.ImageView.ScaleType.FIT_CENTER;
        }
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

    private void applyCardViewInject(androidx.cardview.widget.CardView cardView, String inject) {
        if (inject == null || inject.isEmpty()) {
            return;
        }
        Matcher radiusMatcher = Pattern.compile("cardCornerRadius=\"(\\d+)(?:dp|px)?\"").matcher(inject);
        if (radiusMatcher.find()) {
            try {
                cardView.setRadius(dp(Integer.parseInt(radiusMatcher.group(1))));
            } catch (NumberFormatException ignored) {
                android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: NumberFormatException ignored", ignored);
            }
        }
        Matcher elevationMatcher = Pattern.compile("cardElevation=\"(\\d+)(?:dp|px)?\"").matcher(inject);
        if (elevationMatcher.find()) {
            try {
                cardView.setCardElevation(dp(Integer.parseInt(elevationMatcher.group(1))));
            } catch (NumberFormatException ignored) {
                android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: NumberFormatException ignored", ignored);
            }
        }
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
