package pro.sketchware.activities.preview;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.util.Xml;
import android.view.View;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.android.material.color.MaterialColors;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Resuelve recursos del proyecto (colores y drawables) para la vista previa de disenos.
 *
 * La vista previa no forma parte del APK del proyecto, asi que no puede usar R.color/R.drawable del
 * proyecto: tiene que leer los ficheros del proyecto a mano. Aqui se centraliza esa resolucion y,
 * cuando algo NO se puede resolver, se ANOTA (ver {@link #getWarnings()}) para que la vista previa
 * pueda avisar en pantalla en vez de dejar un hueco mudo o pintar un color equivocado.
 */
public class ProjectResourceResolver {

    private static final String TAG = "LayoutPreview";

    private static final Pattern COLOR_ENTRY = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*([^<]+?)\\s*</color>");

    /** Nombres con los que el IDE guarda los colores del proyecto (values/colors.xml, color.xml). */
    private static final String[] COLOR_FILE_NAMES = {"colors.xml", "color.xml"};

    /** Entrada de un fichero dimens.xml del proyecto: nombre y valor ("16dp", "@dimen/x"...). */
    private static final Pattern DIMEN_ENTRY = Pattern.compile(
            "<dimen\\s+name=\"([^\"]+)\"\\s*>\\s*([^<\\s]+)\\s*</dimen>");

    /** Nombre de un <style name="...">. */
    private static final Pattern STYLE_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");

    /** Padre de un <style parent="...">. */
    private static final Pattern STYLE_PARENT = Pattern.compile("parent\\s*=\\s*\"([^\"]+)\"");

    /**
     * Entrada de un <style> del proyecto: <item name="...">valor</item>.
     */
    private static final Pattern STYLE_ITEM = Pattern.compile(
            "<item\\s+name=\"([^\"]+)\"\\s*>\\s*([^<]*?)\\s*</item>");

    /** <item name="..."/> vacio (sin valor). */
    private static final Pattern STYLE_ITEM_EMPTY = Pattern.compile(
            "<item\\s+name=\"([^\"]+)\"\\s*/>");

    /**
     * Bloque completo de un <style>: con cuerpo ({@code <style ...>...</style>}) o AUTOCIERRE
     * ({@code <style ... />}).
     *
     * <p>El autociérre es lo que escribe el IDE cuando genera un estilo sin items
     * ({@code addStyle("AppTheme.AppBarOverlay", "ThemeOverlay.AppCompat.Dark.ActionBar")} produce
     * {@code <style name="AppTheme.AppBarOverlay" parent="..." />}). El patron anterior
     * ({@code <style\s+([^>]*)>(.*?)</style>}) NO lo reconocia: se saltaba ese estilo Y se tragaba el
     * cuerpo del SIGUIENTE hasta su {@code </style>}, con lo que p.ej. {@code AppTheme.PopupOverlay}
     * desaparecia del mapa y salia como "estilo no encontrado". Por eso el grupo 2 distingue
     * {@code />} de {@code >cuerpo</style>}.
     */
    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style\\s+([^>]*?)(/>|>(.*?)</style>)", Pattern.DOTALL);

    /** Extensiones de imagen que sabemos leer, en orden de preferencia. */
    private static final String[] DRAWABLE_EXTENSIONS = {".xml", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"};

    /**
     * Estilos del set de iconos Material que trae el propio IDE ({@code assets/icons/icon_pack.zip}):
     * {@code svg/<nombre>/<estilo>.svg}. Son exactamente los cinco que ofrece el selector de iconos.
     */
    private static final String[] ICON_PACK_STYLES = {"baseline", "outline", "round", "sharp", "twotone"};

    /** Entrada del zip del set de iconos del IDE, dentro del APK. */
    private static final String ICON_PACK_ASSET = "icons/icon_pack.zip";

    private final Context context;
    private final String scId;
    private final Map<String, Integer> colorCache = new HashMap<>();
    private final Map<String, Integer> dimenCache = new HashMap<>();
    private final Map<String, Drawable> drawableCache = new HashMap<>();

    /**
     * Motivo por el que un recurso no se ha podido resolver. La vista previa agrupa el aviso por
     * esta causa: antes se volcaba una lista plana de nombres ("... no disponibles: a, b, c, ...")
     * que el usuario leia como "N vistas no disponibles" mezclando VISTAS con RECURSOS.
     */
    public enum Kind {
        COLOR("colores"),
        THEME("atributos de tema"),
        DRAWABLE("drawables/imagenes"),
        FONT("fuentes"),
        STYLE("estilos y medidas"),
        OTHER("otros");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Cosas que la vista previa no ha podido resolver, agrupadas por causa. */
    private final Map<Kind, Set<String>> warningsByKind = new java.util.EnumMap<>(Kind.class);

    /**
     * Referencias que NO se han podido aplicar pero que TAMPOCO impiden dibujar la vista (estilos y
     * medidas: un {@code android:theme} sin equivalente, un {@code @dimen} que falta, un
     * {@code tabTextAppearance} de plataforma sin traducir...). Se guardan aparte de
     * {@link #warningsByKind} a proposito: la regla del proyecto es que el ROJO se reserva a lo que
     * de verdad no se puede dibujar (una imagen que no existe, una clase sin reserva). Estas van al
     * aviso AMBAR "no aplicado / ajustado", con su motivo.
     */
    private final Map<Kind, Set<String>> informativeByKind = new java.util.EnumMap<>(Kind.class);

    /** Estilos del proyecto leidos de files/resource/values/styles.xml (nombre -> estilo). */
    private final Map<String, ProjectStyle> styleCache = new LinkedHashMap<>();
    /** Resoluciones de @style/... ya calculadas (referencia tal cual -> resolucion). */
    private final Map<String, StyleResolution> styleResolutionCache = new LinkedHashMap<>();
    /** Referencias de estilo ya avisadas en ambar, para no repetir el mismo aviso por vista. */
    private final Set<String> styleNotes = new LinkedHashSet<>();

    /** Drawables buscados solo en librerias/otras rutas (se indexa una vez por resolver). */
    private List<File> libraryDrawableDirs;
    /** Resumen de donde se ha buscado (para el detalle del aviso). */
    private String searchedLocations = "";

    /**
     * Ficheros {@code styles.xml} del proyecto ya localizados, en orden de lectura:
     * {@code files/resource/value*} (lo que edita el gestor de recursos) y el {@code res} GENERADO
     * del build ({@code .sketchware/mysc/<sc_id>/app/src/main/res/value*}), que es lo que compila
     * aapt2. Aqui es donde viven, por ejemplo, {@code AppTheme.AppBarOverlay} y
     * {@code AppTheme.PopupOverlay} (el IDE los genera y los inyecta en Toolbar/AppBarLayout).
     */
    private final List<File> styleFiles = new ArrayList<>();
    /** Estilos que el propio IDE genera para el proyecto (mismos que compila aapt2). Ultimo recurso. */
    private boolean ideGeneratedStylesLoaded;
    /** Nombres de icono del set del IDE que ya se ha comprobado que NO estan (para no releer el zip). */
    private final Set<String> iconPackMisses = new LinkedHashSet<>();

    /**
     * Nombres heredados que NO existen con ese nombre pero que se han podido mapear a un drawable
     * real del IDE (p.ej. {@code @drawable/ic_tune_white} -> {@code ic_tune_24}).
     *
     * Se guardan aparte de {@link #warningsByKind} a proposito: un nombre heredado resuelto NO es un
     * fallo, pero la vista previa SI debe decirlo ("nada de sustituir en silencio"): el detalle
     * distingue "resuelto por nombre heredado -> nombre real" de "no encontrado".
     */
    private final List<LegacyResolution> legacyResolutions = new ArrayList<>();

    /** Un nombre heredado (Sketchware antiguo) mapeado a un drawable actual del IDE. */
    public static final class LegacyResolution {
        /** Como lo pedia el diseno, tal cual aparece en el XML (p.ej. "@drawable/ic_tune_white"). */
        public final String original;
        /** El drawable real del IDE que se ha dibujado (p.ej. "ic_tune_24"). */
        public final String resolved;
        /** La regla/variante que ha funcionado (p.ej. "ic_<base>_24", base "tune"). */
        public final String rule;

        LegacyResolution(String original, String resolved, String rule) {
            this.original = original;
            this.resolved = resolved;
            this.rule = rule;
        }
    }

    private boolean colorsLoaded;
    private boolean dimensLoaded;
    private boolean stylesLoaded;

    /** Un <style> del proyecto: nombre, padre (tal cual) y sus <item>. */
    public static final class ProjectStyle {
        public final String name;
        public final String parent;
        public final Map<String, String> items = new LinkedHashMap<>();

        ProjectStyle(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    /**
     * Resultado de traducir una referencia {@code @style/...} a algo que la vista previa pueda
     * aplicar de verdad.
     */
    public static final class StyleResolution {
        /** Referencia tal cual venia en el XML. */
        public final String requested;
        /** Nombre del estilo dentro de la cadena que SI existe en tiempo de ejecucion (null si no). */
        public final String resolvedName;
        /** Id de recurso de estilo disponible en el APK del editor (0 = no hay equivalente). */
        public final int styleId;
        /** Items del estilo del proyecto fusionados con los de sus padres (vacio si no es del proyecto). */
        public final Map<String, String> items = new LinkedHashMap<>();
        /** Motivo cuando no se ha podido traducir (para el aviso ambar). */
        public final String reason;
        /**
         * Base real del framework/Material encontrada en la cadena de padres de un estilo del
         * proyecto que no tiene items propios ni resId (p.ej. {@code AppTheme.AppBarOverlay} ->
         * {@code ThemeOverlay.MaterialComponents.Dark.ActionBar}). 0 si no hay ninguna.
         *
         * <p>Sin esto, un estilo del IDE escrito en AUTOCIERRE ({@code <style name="..." parent="..." />},
         * que es justo como lo genera el IDE en {@code getXMLStyle()}) se resolvia pero se consideraba
         * "no usable" y no se aplicaba NADA: ni aviso ni tema. Con la base se puede construir la vista
         * dentro de un {@link android.view.ContextThemeWrapper} igual que en la app.
         */
        public int baseStyleId;

        StyleResolution(String requested, String resolvedName, int styleId, String reason) {
            this.requested = requested;
            this.resolvedName = resolvedName;
            this.styleId = styleId;
            this.reason = reason;
        }

        public boolean isUsable() {
            return styleId != 0 || !items.isEmpty() || baseStyleId != 0;
        }
    }

    public ProjectResourceResolver(Context context, String scId) {
        this.context = context;
        this.scId = scId;
    }

    /** Avisos acumulados (recursos no resueltos), en texto plano para el log. */
    public List<String> getWarnings() {
        List<String> flat = new ArrayList<>();
        for (Set<String> values : warningsByKind.values()) {
            flat.addAll(values);
        }
        return flat;
    }

    /**
     * Referencias de estilo/medida que no se han podido aplicar y que NO impiden dibujar la vista.
     * La vista previa las presenta en AMBAR (grupo "no aplicado / ajustado"), nunca en rojo.
     */
    public List<String> getInformativeWarnings() {
        List<String> flat = new ArrayList<>();
        for (Set<String> values : informativeByKind.values()) {
            flat.addAll(values);
        }
        return flat;
    }

    /** Como {@link #getInformativeWarnings()} pero agrupado por causa. */
    public Map<Kind, Set<String>> getInformativeByKind() {
        Map<Kind, Set<String>> copy = new java.util.EnumMap<>(Kind.class);
        for (Map.Entry<Kind, Set<String>> entry : informativeByKind.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    /** Avisos agrupados por causa (para el resumen y el detalle en pantalla). */
    public Map<Kind, Set<String>> getWarningsByKind() {
        Map<Kind, Set<String>> copy = new java.util.EnumMap<>(Kind.class);
        for (Map.Entry<Kind, Set<String>> entry : warningsByKind.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    /** De donde ha buscado los drawables (proyecto, assets, librerias, IDE). */
    public String getSearchedLocations() {
        return searchedLocations;
    }

    /** Nombres heredados resueltos a un drawable actual (para explicarlo en el aviso). */
    public List<LegacyResolution> getLegacyResolutions() {
        return new ArrayList<>(legacyResolutions);
    }

    public void clearWarnings() {
        warningsByKind.clear();
        informativeByKind.clear();
        legacyResolutions.clear();
    }

    private void warning(Kind kind, String message) {
        warningsByKind.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(message);
        android.util.Log.w("LayoutPreview", "warning: recurso no resuelto [" + kind.label + "]: " + message);
    }

    /**
     * Anota un recurso que no se ha podido resolver desde FUERA del resolvedor (la vista previa
     * resuelve las fuentes por su cuenta). Asi el aviso agrupado incluye tambien las fuentes.
     */
    public void addExternalWarning(Kind kind, String message) {
        warning(kind, message);
    }

    /**
     * Anota una referencia que no se ha podido APLICAR pero que no impide dibujar la vista (estilo o
     * medida). Va al aviso ambar, no al rojo, y siempre con el motivo.
     */
    public void warningInformative(Kind kind, String message) {
        informativeByKind.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(message);
        android.util.Log.w("LayoutPreview", "warning: no aplicado [" + kind.label + "]: " + message);
    }

    private void ensureColorsLoaded() {
        if (colorsLoaded) {
            return;
        }
        colorsLoaded = true;
        List<File> files = new ArrayList<>();
        // 1) La ruta canonica del IDE (lo que ya se leia antes) va PRIMERO: tiene prioridad.
        File primary = new File(new FilePathUtil().getPathResource(scId), "values/colors.xml");
        if (primary.isFile()) {
            files.add(primary);
        }
        // 2) El resto de rutas donde el IDE deja los recursos: value* (values, value, values-v21,
        //    values-night...). Antes solo se miraba values/colors.xml: un color definido en value/ (el
        //    nombre heredado del IDE) o en values-night se daba por NO encontrado y la vista salia con
        //    el color por defecto, igual que le pasaba a styles.xml antes de la ronda 9a.
        collectColorFiles(new File(new FilePathUtil().getPathResource(scId)), files);
        // 3) El res GENERADO del build (.sketchware/mysc/<sc_id>/app/src/main/res/value*), que es
        //    literalmente lo que compila aapt2. Es la ruta que faltaba.
        try {
            collectColorFiles(new File(a.a.a.wq.d(scId), "app/src/main/res"), files);
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "warning: no se pudo localizar el res generado del build: " + throwable);
        }
        for (File file : files) {
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            Matcher matcher = COLOR_ENTRY.matcher(content);
            while (matcher.find()) {
                String name = matcher.group(1);
                try {
                    // putIfAbsent: la primera ruta que define el color gana (values/ por delante de
                    // value/ y del res generado), asi el comportamiento actual no cambia.
                    colorCache.putIfAbsent(name, Color.parseColor(matcher.group(2).trim()));
                } catch (IllegalArgumentException ignored) {
                    android.util.Log.d(TAG, "warning: color del proyecto no parseable: " + name
                            + " = " + matcher.group(2).trim());
                }
            }
        }
        android.util.Log.i(TAG, "info: colors.xml del proyecto leidos: " + files
                + " -> " + colorCache.size() + " colores");
    }

    /** Anyade los colors.xml de los directorios {@code value*} del directorio dado (orden estable). */
    private void collectColorFiles(File resourceRoot, List<File> target) {
        File[] children = resourceRoot == null ? null : resourceRoot.listFiles();
        if (children == null) {
            return;
        }
        List<File> valueDirs = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && child.getName().toLowerCase(Locale.US).startsWith("value")) {
                valueDirs.add(child);
            }
        }
        Collections.sort(valueDirs, (left, right) -> left.getName().compareTo(right.getName()));
        for (File dir : valueDirs) {
            for (String fileName : COLOR_FILE_NAMES) {
                File file = new File(dir, fileName);
                if (file.isFile() && !target.contains(file)) {
                    target.add(file);
                }
            }
        }
    }

    /**
     * Resuelve un color escrito como en el XML de Android.
     *
     * Acepta: "#RGB/#RRGGBB/#AARRGGBB", "@color/nombre" (colors.xml del proyecto) y CUALQUIER
     * expresion de atributo de tema que empiece por "?":
     * "?attr/colorPrimary", "?colorPrimary", "?android:attr/colorPrimary", "?android:colorPrimary".
     *
     * Este ultimo caso es el que rompia los colores elegidos en la PALETA MATERIAL del editor: el
     * selector de color guarda el valor como "?colorPrimary" (ver PropertyColorItem/ColorPickerDialog)
     * y el generador de XML (a.a.a.Ox) escribe ese texto tal cual en android:textColor /
     * android:background. La version anterior de este resolvedor solo entendia "?attr/...", asi que
     * devolvia el fallback: en un texto el fallback es el centinela 0xffffff (color sin definir) y el
     * texto se quedaba con el color por defecto; en un fondo el fallback era "sin color" y entonces se
     * pintaba el marcador blanco opaco 0xFFFFFFFF que deja ViewBeanFactory, con lo que el layout
     * parecia "no pintado".
     *
     * @return el color resuelto, o el fallback si no se ha podido resolver.
     */
    public int resolveColor(View view, String value, int fallback) {
        return resolveColor(view, value, fallback, Kind.COLOR);
    }

    public int resolveColor(View view, String value, int fallback, Kind kind) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return fallback;
        }
        if (v.startsWith("#")) {
            try {
                return Color.parseColor(v);
            } catch (IllegalArgumentException e) {
                warning(kind, "color invalido: " + v);
                return fallback;
            }
        }
        if (v.startsWith("@color/")) {
            ensureColorsLoaded();
            Integer cached = colorCache.get(v.substring("@color/".length()));
            if (cached != null) {
                return cached;
            }
            warning(kind, v + " (no esta en files/resource/values/colors.xml)");
            return fallback;
        }
        if (v.startsWith("@android:color/")) {
            int id = context.getResources().getIdentifier(v.substring("@android:color/".length()), "color", "android");
            if (id != 0) {
                return context.getColor(id);
            }
            warning(kind, v);
            return fallback;
        }
        if (v.startsWith("?")) {
            int color = resolveThemeColor(view, v);
            if (color != 0) {
                return color;
            }
            // AMBAR, no rojo: un atributo de tema que no se puede resolver NO impide dibujar la
            // vista; se dibuja con el color que ya tuviera. (Regla del proyecto: el rojo se reserva
            // a lo que de verdad no se puede dibujar.)
            warningInformative(Kind.THEME, v + " (atributo del tema no aplicable en la vista previa)");
            return fallback;
        }
        return fallback;
    }

    /**
     * Resuelve una MEDIDA escrita como en el XML de Android.
     *
     * <p>Acepta "8dp"/"8dip"/"8px"/"8sp" y las referencias del proyecto o del framework:
     * "@dimen/x" (dimens.xml del proyecto) y "@android:dimen/x".
     *
     * <p>Por que hacia falta (ronda 8): los radios, grosores de borde, altos de indicador y
     * elevaciones configurados con una medida del proyecto ("@dimen/mi_radio") se descartaban: el
     * parser de la vista previa solo entendia numeros con unidad, asi que el atributo no se aplicaba
     * y el widget se veia con su valor por defecto ("no se ven los tamanos"). El editor de diseno
     * usa su propio resolver; la vista previa necesita este.
     *
     * @return la medida en px, o {@code fallback} si no se ha podido resolver.
     */
    public int resolveDimen(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return fallback;
        }
        if (v.startsWith("@dimen/") || v.startsWith("@dimen:")) {
            String name = v.substring("@dimen".length() + 1);
            ensureDimensLoaded();
            Integer cached = dimenCache.get(name);
            if (cached != null) {
                return cached;
            }
            warningInformative(Kind.STYLE, v + " (no esta en files/resource/values/dimens.xml; la vista se dibuja con su valor por defecto)");
            return fallback;
        }
        if (v.startsWith("@android:dimen/")) {
            int id = context.getResources().getIdentifier(
                    v.substring("@android:dimen/".length()), "dimen", "android");
            if (id != 0) {
                return context.getResources().getDimensionPixelSize(id);
            }
            warningInformative(Kind.STYLE, v + " (medida del framework no encontrada; la vista se dibuja con su valor por defecto)");
            return fallback;
        }
        if (v.startsWith("@style/") || v.startsWith("@android:style/") || v.startsWith("@style:")) {
            // Un estilo NO es una medida: si alguien lo manda aqui (p.ej. "@style/x" en un atributo
            // que el editor espera numerico) no se puede convertir a px. Antes esto se avisaba como
            // "referencia de medida no resoluble" y ademas contaba como RECURSO NO ENCONTRADO (rojo),
            // cuando la vista se dibuja perfectamente. Ahora es un aviso AMBAR con el motivo real.
            warningInformative(Kind.STYLE, v
                    + " (es un estilo, no una medida: no se puede convertir a px; la vista se dibuja igual)");
            return fallback;
        }
        if (v.startsWith("@") || v.startsWith("?")) {
            warningInformative(Kind.STYLE, v
                    + " (valor no aplicable en la vista previa; la vista se dibuja con su valor por defecto)");
            return fallback;
        }
        int parsed = parseDimen(v);
        return parsed == 0 && !v.startsWith("0") ? fallback : parsed;
    }

    /** Carga (una vez) las medidas del proyecto desde files/resource/values/dimens.xml. */
    private void ensureDimensLoaded() {
        if (dimensLoaded) {
            return;
        }
        dimensLoaded = true;
        Map<String, String> raw = new HashMap<>();
        String[] candidates = {"values/dimens.xml", "value/dimens.xml"};
        for (String candidate : candidates) {
            File file = new File(new FilePathUtil().getPathResource(scId), candidate);
            if (!file.isFile()) {
                continue;
            }
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content == null) {
                continue;
            }
            Matcher matcher = DIMEN_ENTRY.matcher(content);
            while (matcher.find()) {
                raw.putIfAbsent(matcher.group(1), matcher.group(2));
            }
        }
        // Varias pasadas: una medida puede referenciar a otra definida mas abajo en el fichero
        // ("@dimen/otra"), y el fichero se lee de arriba a abajo.
        for (int pass = 0; pass < 5; pass++) {
            boolean progress = false;
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                if (dimenCache.containsKey(entry.getKey())) {
                    continue;
                }
                String value = entry.getValue().trim();
                if (value.startsWith("@dimen/")) {
                    Integer referenced = dimenCache.get(value.substring("@dimen/".length()));
                    if (referenced != null) {
                        dimenCache.put(entry.getKey(), referenced);
                        progress = true;
                    }
                    continue;
                }
                int resolved = resolveDimensionReference(value);
                if (resolved != 0) {
                    dimenCache.put(entry.getKey(), resolved);
                    progress = true;
                }
            }
            if (!progress) {
                break;
            }
        }
    }

    // ------------------------------------------------------------------ estilos del proyecto

    /**
     * Carga (una vez) los estilos del proyecto desde files/resource/values/styles.xml.
     *
     * <p>Por que hacia falta (ronda 9a): un XML que referencia {@code @style/AppTheme.PopupOverlay}
     * (estilo del PROYECTO, no del APK del editor) no tenia forma de resolverse, asi que la vista
     * previa lo daba por no encontrado y ademas lo pintaba en ROJO. Los estilos del proyecto SI se
     * pueden leer del disco, igual que los colores o las medidas.
     */
    private void ensureStylesLoaded() {
        if (stylesLoaded) {
            return;
        }
        stylesLoaded = true;
        projectStyleFiles();
        for (File file : styleFiles) {
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            parseStyles(content);
        }
        android.util.Log.i(TAG, "info: styles.xml del proyecto leidos: " + styleFiles
                + " -> " + styleCache.size() + " estilos");
    }

    /**
     * Localiza los {@code styles.xml} del proyecto (una vez), en las dos rutas donde el IDE los deja:
     *
     * <ol>
     *   <li>{@code files/resource/value*}: lo que el usuario ve/edita en el gestor de recursos
     *       ({@code values}, {@code value}, {@code values-v21}, {@code values-night}...). Antes solo
     *       se miraba {@code values/styles.xml} exacto.</li>
     *   <li>{@code .sketchware/mysc/<sc_id>/app/src/main/res/value*}: el {@code res} GENERADO del
     *       build, que es literalmente lo que compila aapt2 para la app. Es la ruta que faltaba y la
     *       causa de que estilos que la app SI tiene (p.ej. {@code AppTheme.AppBarOverlay}, que el IDE
     *       genera en {@code getXMLStyle()} e inyecta en los Toolbar/AppBarLayout) salieran como "no
     *       presentes" en la vista previa.</li>
     * </ol>
     */
    private void projectStyleFiles() {
        if (!styleFiles.isEmpty()) {
            return;
        }
        collectStyleFiles(new File(new FilePathUtil().getPathResource(scId)));
        try {
            collectStyleFiles(new File(a.a.a.wq.d(scId), "app/src/main/res"));
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "warning: no se pudo localizar el res generado del build: " + throwable);
        }
    }

    /** Anyade a {@link #styleFiles} los styles.xml de los directorios {@code value*} del directorio dado. */
    private void collectStyleFiles(File resourceRoot) {
        File[] children = resourceRoot == null ? null : resourceRoot.listFiles();
        if (children == null) {
            return;
        }
        List<File> valueDirs = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && child.getName().toLowerCase(Locale.US).startsWith("value")) {
                valueDirs.add(child);
            }
        }
        // Orden estable (values antes que values-v21/values-night) para que la lectura sea determinista.
        Collections.sort(valueDirs, (left, right) -> left.getName().compareTo(right.getName()));
        for (File dir : valueDirs) {
            for (String fileName : new String[]{"styles.xml", "style.xml"}) {
                File file = new File(dir, fileName);
                if (file.isFile() && !styleFiles.contains(file)) {
                    styleFiles.add(file);
                }
            }
        }
    }

    /**
     * Lee (una vez, solo si hace falta) los estilos que el propio IDE GENERA para el proyecto.
     *
     * <p>Es la misma fuente que usa el compilador ({@code yq.getXMLStyle()}), asi que refleja el tema
     * real del proyecto (Material3 / AppCompat / Material) con sus estilos inyectados
     * ({@code AppTheme.AppBarOverlay}, {@code AppTheme.PopupOverlay}...). Se consulta SOLO como ultimo
     * recurso, cuando el estilo no estaba en ninguno de los ficheros del disco, y nunca sustituye a
     * lo que el proyecto tiene escrito: si el IDE tampoco lo genera, el aviso ambar sigue saliendo.
     */
    private void ensureIdeGeneratedStylesLoaded() {
        if (ideGeneratedStylesLoaded) {
            return;
        }
        ideGeneratedStylesLoaded = true;
        try {
            a.a.a.yq generator = new a.a.a.yq(context, scId);
            // Inicializa el proyecto igual que el editor de recursos: asi el generador sabe si el
            // proyecto usa AppCompat/Material3 y produce el styles.xml correcto.
            generator.a(a.a.a.jC.c(scId), a.a.a.jC.b(scId), a.a.a.jC.a(scId));
            String content = generator.getXMLStyle();
            if (content != null && !content.trim().isEmpty()) {
                parseStyles(content);
                android.util.Log.i(TAG, "info: estilos generados por el IDE leidos ("
                        + content.length() + " chars) -> " + styleCache.size() + " estilos en total");
            }
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "warning: no se pudieron leer los estilos generados por el IDE: " + throwable);
        }
    }

    /**
     * Lee los bloques {@code <style>} de un styles.xml del proyecto. Si un estilo no declara
     * {@code parent} pero su nombre lleva puntos ({@code AppTheme.PopupOverlay}), se toma como padre
     * la parte anterior al ultimo punto (convencion de Android), para poder encadenar herencia.
     */
    private void parseStyles(String content) {
        Matcher block = STYLE_BLOCK.matcher(content);
        while (block.find()) {
            String attributes = block.group(1) == null ? "" : block.group(1);
            // group(2) es "/>" en un <style .../> (sin items) y ">cuerpo</style>" en el resto; el
            // cuerpo esta en group(3) y solo existe en el segundo caso.
            String body = block.group(3) == null ? "" : block.group(3);
            Matcher nameMatcher = STYLE_NAME.matcher(attributes);
            if (!nameMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1).trim();
            String parent = null;
            Matcher parentMatcher = STYLE_PARENT.matcher(attributes);
            if (parentMatcher.find()) {
                parent = parentMatcher.group(1).trim();
            } else {
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    parent = name.substring(0, dot);
                }
            }
            ProjectStyle style = styleCache.get(name);
            if (style == null) {
                style = new ProjectStyle(name, parent);
                styleCache.put(name, style);
            }
            Matcher item = STYLE_ITEM.matcher(body);
            while (item.find()) {
                style.items.putIfAbsent(item.group(1).trim(), item.group(2).trim());
            }
            Matcher emptyItem = STYLE_ITEM_EMPTY.matcher(body);
            while (emptyItem.find()) {
                style.items.putIfAbsent(emptyItem.group(1).trim(), "");
            }
        }
    }

    /**
     * Traduce una referencia {@code @style/...} (o {@code @android:style/...}) a algo utilizable.
     *
     * <p>Busca primero en los estilos del proyecto (con sus padres, hasta 6 niveles) y, si no esta,
     * en el APK del editor (app/material/android). No lanza nunca: si no se puede, devuelve una
     * resolucion con {@link StyleResolution#reason} para que el aviso sea AMBAR (nunca rojo: una
     * referencia de estilo no impide dibujar la vista).
     */
    public StyleResolution resolveStyle(String reference, String usage) {
        if (reference == null) {
            return null;
        }
        String v = reference.trim();
        if (!isStyleReference(v)) {
            return null;
        }
        StyleResolution cached = styleResolutionCache.get(v);
        if (cached != null) {
            return cached;
        }
        ensureStylesLoaded();
        String name = styleName(v);
        String reason = null;
        int styleId = 0;
        String resolvedName = null;
        Map<String, String> items = new LinkedHashMap<>();
        if (v.startsWith("@android:style/")) {
            styleId = context.getResources().getIdentifier(name, "style", "android");
            if (styleId != 0) {
                resolvedName = name;
            }
        } else if (styleCache.containsKey(name)) {
            resolvedName = name;
            collectStyleItems(name, items, new LinkedHashSet<>(), 0);
        } else {
            // No esta en los styles.xml escritos en el disco: puede ser un estilo que el IDE GENERA
            // para el proyecto (AppTheme.AppBarOverlay / AppTheme.PopupOverlay / AppTheme.FullScreen...)
            // sin dejarlo en files/resource. Se le pregunta al propio generador del IDE (la misma
            // fuente que usa el compilador) y, si tampoco lo tiene, al APK del editor.
            ensureIdeGeneratedStylesLoaded();
            if (styleCache.containsKey(name)) {
                resolvedName = name;
                collectStyleItems(name, items, new LinkedHashSet<>(), 0);
                android.util.Log.i(TAG, "info: estilo del proyecto resuelto desde los estilos generados"
                        + " por el IDE: " + v);
            }
        }
        if (resolvedName == null) {
            // No esta en el styles.xml del proyecto (ni escrito ni generado): puede ser un estilo del
            // editor (Material3, AppCompat) o simplemente no existir. Se prueba el APK del editor.
            String pkg = context.getPackageName();
            styleId = context.getResources().getIdentifier(name, "style", pkg);
            if (styleId == 0) {
                styleId = context.getResources().getIdentifier(name, "style", "com.google.android.material");
            }
            if (styleId == 0) {
                styleId = context.getResources().getIdentifier(name, "style", "androidx.appcompat");
            }
            if (styleId != 0) {
                resolvedName = name;
            }
        }
        if (resolvedName == null) {
            reason = "no esta en los styles.xml del proyecto (files/resource/values* ni el res generado"
                    + " del build) ni en los recursos del editor";
        }
        StyleResolution resolution = new StyleResolution(v, resolvedName, styleId, reason);
        resolution.items.putAll(items);
        if (resolvedName != null && styleId == 0 && items.isEmpty()) {
            // Estilo del proyecto/IDE sin items propios (autocierre): si su cadena de padres lleva a un
            // estilo real del framework/Material, se guarda como BASE para poder aplicarlo igual que
            // la app (ContextThemeWrapper), en vez de resolverlo y no hacer nada con el.
            resolution.baseStyleId = resolveBaseStyleId(v);
        }
        styleResolutionCache.put(v, resolution);
        if (reason != null && styleNotes.add(v)) {
            // AMBAR (no aplicado): la vista se dibuja igual, solo no se le aplica el estilo.
            warningInformative(Kind.STYLE, v + " (" + reason
                    + "; la vista se dibuja sin ese estilo"
                    + (usage == null || usage.isEmpty() ? "" : " [" + usage + "]") + ")");
        }
        return resolution;
    }

    /**
     * Id de recurso de estilo del APK del editor/framework utilizable como BASE de un tema.
     *
     * <p>Un estilo del PROYECTO no esta compilado en el APK del editor, asi que no tiene resId. Pero
     * sus padres suelen ser estilos del framework/Material (p.ej. {@code ThemeOverlay.AppCompat.Dark.
     * ActionBar}); este metodo recorre el propio estilo y sus padres del proyecto y devuelve el
     * primer resId que exista en el editor, para poder envolver el contexto en un
     * {@code ContextThemeWrapper} con una base real. Devuelve 0 si no hay ninguno.
     */
    public int resolveBaseStyleId(String reference) {
        if (reference == null) {
            return 0;
        }
        String v = reference.trim();
        if (!isStyleReference(v)) {
            return 0;
        }
        ensureStylesLoaded();
        if (v.startsWith("@android:style/")) {
            return context.getResources().getIdentifier(styleName(v), "style", "android");
        }
        Set<String> visited = new LinkedHashSet<>();
        String name = styleName(v);
        for (int depth = 0; name != null && depth <= 6 && visited.add(name); depth++) {
            int id = editorStyleId(name);
            if (id != 0) {
                return id;
            }
            ProjectStyle style = styleCache.get(name);
            name = style == null || style.parent == null ? null : styleName(style.parent);
        }
        return 0;
    }

    /** resId de un estilo en el APK del editor (app/material/androidx) o en android. 0 si no existe. */
    private int editorStyleId(String name) {
        int id = context.getResources().getIdentifier(name, "style", context.getPackageName());
        if (id == 0) {
            id = context.getResources().getIdentifier(name, "style", "com.google.android.material");
        }
        if (id == 0) {
            id = context.getResources().getIdentifier(name, "style", "androidx.appcompat");
        }
        if (id == 0) {
            id = context.getResources().getIdentifier(name, "style", "android");
        }
        return id;
    }

    /** ¿La cadena es una referencia de estilo ({@code @style/...} o {@code @android:style/...})? */
    public static boolean isStyleReference(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return v.startsWith("@style/") || v.startsWith("@android:style/")
                || v.startsWith("@style:") || v.startsWith("@android:style:");
    }

    /** Nombre del estilo dentro de una referencia ({@code @style/AppTheme.PopupOverlay} -> nombre). */
    private static String styleName(String reference) {
        String v = reference.trim();
        int slash = v.indexOf('/');
        if (slash >= 0 && slash + 1 < v.length()) {
            return v.substring(slash + 1);
        }
        int colon = v.lastIndexOf(':');
        return colon >= 0 && colon + 1 < v.length() ? v.substring(colon + 1) : v;
    }

    /**
     * Acumula los items de un estilo del proyecto y los de sus padres. El hijo gana sobre el padre
     * (misma idea que Android: los items del hijo sobrescriben los heredados).
     */
    private void collectStyleItems(String name, Map<String, String> into, Set<String> visited, int depth) {
        if (name == null || depth > 6 || !visited.add(name)) {
            return;
        }
        ProjectStyle style = styleCache.get(name);
        if (style == null) {
            return;
        }
        for (Map.Entry<String, String> entry : style.items.entrySet()) {
            into.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (style.parent != null) {
            collectStyleItems(styleName(style.parent), into, visited, depth + 1);
        }
    }

    /** Valor de un <dimen> que puede ser a su vez una referencia a una medida del framework. */
    private int resolveDimensionReference(String value) {
        String v = value == null ? "" : value.trim();
        if (v.startsWith("@android:dimen/")) {
            int id = context.getResources().getIdentifier(
                    v.substring("@android:dimen/".length()), "dimen", "android");
            return id == 0 ? 0 : context.getResources().getDimensionPixelSize(id);
        }
        return v.startsWith("@") || v.startsWith("?") ? 0 : parseDimen(v);
    }

    /**
     * Resuelve un atributo de tema ("?colorPrimary", "?attr/colorPrimary", "?android:attr/...").
     * Se prueba primero contra los atributos de la app del IDE (que es un tema Material3, igual que
     * los que ofrece el selector de color) y despues contra los atributos de android. Si no existe
     * en ninguno, devuelve 0 para que el que llama pueda distinguir "no resuelto" de un color negro.
     */
    private int resolveThemeColor(View view, String attrExpression) {
        String expression = attrExpression.substring(1); // quitamos '?'
        // El contexto de la VISTA (no el de la Activity): un AppBarLayout con android:theme="@style/..."
        // se crea con un ContextThemeWrapper, y "?attr/colorPrimary" dentro de el DEBE resolverse con
        // SU tema, no con el del IDE. Si la vista no lleva tema propio, view.getContext() es el mismo
        // contexto de antes (sin cambio de comportamiento).
        Context viewContext = view != null && view.getContext() != null ? view.getContext() : context;
        Resources viewResources = viewContext.getResources();
        String pkg = context.getPackageName();
        String attrName = expression;
        if (attrName.startsWith("android:attr/")) {
            pkg = "android";
            attrName = attrName.substring("android:attr/".length());
        } else if (attrName.startsWith("android:")) {
            pkg = "android";
            attrName = attrName.substring("android:".length());
        } else if (attrName.startsWith("attr/")) {
            attrName = attrName.substring("attr/".length());
        }
        if (attrName.isEmpty()) {
            return 0;
        }
        // 1) Atributo del propio tema de la VISTA (Material3: colorPrimary, colorSurface, ...).
        int attrId = viewResources.getIdentifier(attrName, "attr", pkg);
        if (attrId == 0 && !"android".equals(pkg)) {
            attrId = viewResources.getIdentifier(attrName, "attr", "com.google.android.material");
        }
        if (attrId != 0) {
            try {
                return MaterialColors.getColor(view, attrId);
            } catch (Throwable ignored) {
                android.util.Log.d("SketchwarePro", "ProjectResourceResolver: Throwable ignored", ignored);
            }
        }
        // 2) Resolucion directa por el tema de la vista (vale tambien para atributos de framework).
        if (attrId == 0 && !"android".equals(pkg)) {
            attrId = viewResources.getIdentifier(attrName, "attr", "android");
        }
        if (attrId != 0) {
            TypedValue typedValue = new TypedValue();
            if (viewContext.getTheme().resolveAttribute(attrId, typedValue, true)) {
                if (typedValue.type == TypedValue.TYPE_REFERENCE || typedValue.type == TypedValue.TYPE_STRING) {
                    try {
                        return viewContext.getColor(typedValue.resourceId);
                    } catch (Throwable ignored) {
                        android.util.Log.d("SketchwarePro", "ProjectResourceResolver: Throwable ignored", ignored);
                    }
                } else if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return typedValue.data;
                }
            }
        }
        return 0;
    }

    /**
     * Resuelve un drawable escrito como en el XML ("@drawable/nombre") o una ruta de fichero
     * ("file:///android_asset/x.png", "assets/x.png", "/ruta/absoluta.png").
     *
     * Busca, en este orden: los directorios drawable* del proyecto (incluidos drawable-hdpi, ... y
     * subcarpetas), la carpeta de assets del proyecto y, como ultimo recurso, los drawables que ya
     * trae el propio IDE (asi "@drawable/default_image" se ve, igual que en el editor de disenos).
     *
     * Antes solo miraba files/resource/drawable y solo .xml/.png/.jpg: una imagen en webp/jpeg, en
     * drawable-xhdpi, en una subcarpeta o en assets se veia como un hueco vacio.
     */
    @Nullable
    public Drawable resolveDrawable(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (isStyleReference(v)) {
            // Un estilo NO es un drawable: si llega aqui (p.ej. android:background="@style/x") la
            // vista se dibuja igual, asi que es un aviso AMBAR (no aplicado), NUNCA un marcador rojo.
            resolveStyle(v, "background");
            return null;
        }
        Drawable cached = drawableCache.get(v);
        if (cached != null) {
            return cached;
        }

        Drawable resolved = null;
        if (v.startsWith("file://") || v.startsWith("/")) {
            resolved = loadFile(new File(v.replace("file://", "")));
        } else if (v.startsWith("@android:drawable/")) {
            resolved = loadFrameworkDrawable(v.substring("@android:drawable/".length()));
        } else {
            String name = v;
            if (name.startsWith("@drawable/") || name.startsWith("@mipmap/") || name.startsWith("@raw/")) {
                name = name.substring(name.indexOf('/') + 1);
            } else if (name.startsWith("assets/")) {
                name = name.substring("assets/".length());
            } else if (name.startsWith("@asset/")) {
                name = name.substring("@asset/".length());
            }
            resolved = loadProjectOrIdeDrawable(name);
            if (resolved == null) {
                // Ultimo recurso: nombre heredado de una version vieja de Sketchware (p.ej.
                // "ic_tune_white"). Solo con una regla clara y una coincidencia del IDE; si no,
                // se mantiene el rojo honesto con el motivo.
                resolved = resolveLegacyIdeDrawable(name, v);
            }
        }

        if (resolved != null) {
            drawableCache.put(v, resolved);
        } else {
            warning(Kind.DRAWABLE, value + searchedLocationsSuffix());
        }
        return resolved;
    }

    /** Sufijo con los sitios donde se ha buscado un drawable (para el detalle del aviso). */
    private String searchedLocationsSuffix() {
        String summary = librarySearchSummary();
        return summary.isEmpty() ? "" : " (buscado en: " + summary + ")";
    }

    /** Resumen de los sitios de busqueda de drawables, para poder explicar el fallo. */
    private String librarySearchSummary() {
        if (!searchedLocations.isEmpty()) {
            return searchedLocations;
        }
        List<String> parts = new ArrayList<>();
        int projectDirs = projectDrawableDirs().size();
        if (projectDirs > 0) {
            parts.add(projectDirs + (projectDirs == 1 ? " carpeta drawable* del proyecto"
                    : " carpetas drawable* del proyecto"));
        }
        if (projectImageStoreDir() != null) {
            parts.add("imagenes del proyecto (.sketchware/resources/images/" + scId + ")");
        }
        parts.add("assets del proyecto");
        if (generatedBuildResDir() != null) {
            parts.add("res generado del build");
        }
        int libraries = libraryDrawableDirs().size();
        if (libraries > 0) {
            parts.add(libraries + " recursos de librerias");
        }
        parts.add("recursos del IDE");
        if (iconPackAvailable()) {
            parts.add("set de iconos del IDE (icon_pack.zip)");
        }
        searchedLocations = android.text.TextUtils.join(", ", parts);
        return searchedLocations;
    }

    /** Busca "@drawable/lo_que_sea" en el proyecto y, si no esta, entre los drawables del IDE. */
    @Nullable
    private Drawable loadProjectOrIdeDrawable(String name) {
        if (name.isEmpty()) {
            return null;
        }
        // 1) Directorios de recursos del proyecto: drawable, drawable-hdpi, drawable-xhdpi, ... y subcarpetas.
        for (File dir : projectDrawableDirs()) {
            Drawable found = findDrawableIn(dir, name);
            if (found != null) {
                return found;
            }
        }
        // 1b) ALMACEN DE IMAGENES DEL PROYECTO (.sketchware/resources/images/<sc_id>).
        //     Aqui guarda el selector de iconos del IDE el SVG convertido a vector:
        //     icon_<nombre>_<estilo>.xml (ver ImportIconActivity + kC.l()). No esta en
        //     files/resource/drawable*, y esa era la causa de que "los iconos no se vean".
        File imageStore = projectImageStoreDir();
        if (imageStore != null) {
            Drawable inStore = findDrawableIn(imageStore, name);
            if (inStore != null) {
                return inStore;
            }
        }
        // 2) Assets del proyecto (mucha gente mete ahi las imagenes y las referencia como @drawable).
        File assets = new File(new FilePathUtil().getPathAssets(scId));
        Drawable found = findDrawableIn(assets, name);
        if (found != null) {
            return found;
        }
        // 3) Resto de carpetas del proyecto (files/), por si la imagen no esta en drawable ni assets.
        File filesDir = new File(new FilePathUtil().getPathAssets(scId)).getParentFile();
        Drawable inFiles = findDrawableIn(filesDir, name);
        if (inFiles != null) {
            return inFiles;
        }
        // 3b) RES GENERADO DEL BUILD (.sketchware/mysc/<sc_id>/app/src/main/res): es lo que compila
        //     aapt2 para la app, asi que si un drawable solo esta ahi (o solo ahi con ese nombre),
        //     la vista previa debe verlo igual.
        File generatedRes = generatedBuildResDir();
        if (generatedRes != null) {
            Drawable inGenerated = findDrawableIn(generatedRes, name);
            if (inGenerated != null) {
                return inGenerated;
            }
        }
        // 4) Recursos de las LIBRERIAS del proyecto (AAR locales descargados por DependencyResolver
        //    y librerias integradas del IDE ya extraidas). El compilador las enlaza con aapt2
        //    (ResourceCompiler: "-R"), asi que en la app compilada SI existen: la vista previa debe
        //    resolverlas igual, y antes no las miraba en absoluto.
        for (File dir : libraryDrawableDirs()) {
            Drawable inLibrary = findDrawableIn(dir, name);
            if (inLibrary != null) {
                return inLibrary;
            }
        }
        // 5) Drawables propios del IDE: default_image, iconos mtrl, etc.
        // 6) Set de iconos Material que el propio IDE trae (assets/icons/icon_pack.zip): los
        //    "icon_<nombre>_<estilo>" del selector de iconos salen de ahi. Se intenta ANTES del
        //    mapeo de nombres heredados porque aqui el nombre es exacto (no una variante adivinada).
        Drawable fromIde = loadIdeDrawable(name);
        return fromIde != null ? fromIde : loadIconPackDrawable(name);
    }

    /** Almacen de imagenes del proyecto ({@code .sketchware/resources/images/<sc_id>}). */
    @Nullable
    private File projectImageStoreDir() {
        try {
            return new File(a.a.a.wq.g(), scId);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Raiz de recursos GENERADA del build del proyecto
     * ({@code .sketchware/mysc/<sc_id>/app/src/main/res}).
     */
    @Nullable
    private File generatedBuildResDir() {
        try {
            return new File(a.a.a.wq.d(scId), "app/src/main/res");
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Directorios de recursos (drawable*) de las librerias que usa el proyecto:
     *
     * <ul>
     *   <li>Librerias locales del proyecto (JSON {@code files/local_library}: {@code resPath} /
     *       {@code assetsPath}), que es donde {@code DependencyResolver} descomprime los AAR en
     *       {@code .sketchware/libs/local_libs/&lt;nombre&gt;/}.</li>
     *   <li>Ruta heredada {@code &lt;proyecto&gt;/files/library/res}.</li>
     *   <li>Librerias integradas del IDE ya extraidas en {@code filesDir/libs/libs/&lt;lib&gt;/res}
     *       (es la ruta que usa {@code BuiltInLibraries.getLibraryResourcesPath}).</li>
     * </ul>
     */
    private List<File> libraryDrawableDirs() {
        if (libraryDrawableDirs != null) {
            return libraryDrawableDirs;
        }
        List<File> dirs = new ArrayList<>();
        // 1) Librerias locales declaradas por el proyecto (lo hace ManageLocalLibrary/DependencyResolver).
        File localLibraryFile = new File(new FilePathUtil().getPathLocalLibrary(scId));
        if (localLibraryFile.isFile()) {
            String content = FileUtil.readFile(localLibraryFile.getAbsolutePath());
            if (content != null && !content.trim().isEmpty()) {
                try {
                    org.json.JSONArray libraries = new org.json.JSONArray(content);
                    for (int i = 0; i < libraries.length(); i++) {
                        org.json.JSONObject library = libraries.optJSONObject(i);
                        if (library == null) {
                            continue;
                        }
                        addIfDirectory(dirs, library.optString("resPath", null));
                        addIfDirectory(dirs, library.optString("assetsPath", null));
                        String name = library.optString("name", null);
                        if (name != null && !name.isEmpty()) {
                            addIfDirectory(dirs, new File(localLibsRoot(), name + "/res").getAbsolutePath());
                            addIfDirectory(dirs, new File(localLibsRoot(), name + "/assets").getAbsolutePath());
                        }
                    }
                } catch (Throwable throwable) {
                    android.util.Log.w("LayoutPreview", "warning: local_library ilegible: " + throwable);
                }
            }
        }
        // 2) Ruta heredada por proyecto.
        addIfDirectory(dirs, new FilePathUtil().getResPathLocalLibraryUser(scId));
        addIfDirectory(dirs, new File(new FilePathUtil().getPathLocalLibrary(scId)).getParentFile() == null
                ? null
                : new File(new File(new FilePathUtil().getPathLocalLibrary(scId)).getParentFile(), "library/assets").getAbsolutePath());
        // 3) Librerias integradas del IDE ya extraidas (Material, Firebase, ...).
        File extractedBuiltInLibraries = new File(context.getFilesDir(), "libs/libs");
        File[] builtIn = extractedBuiltInLibraries.listFiles();
        if (builtIn != null) {
            for (File library : builtIn) {
                if (library.isDirectory()) {
                    addIfDirectory(dirs, new File(library, "res").getAbsolutePath());
                    addIfDirectory(dirs, new File(library, "assets").getAbsolutePath());
                }
            }
        }
        libraryDrawableDirs = dirs;
        android.util.Log.i(TAG, "info: recursos de librerias indexados: " + dirs.size());
        return dirs;
    }

    private static File localLibsRoot() {
        return new File(android.os.Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs");
    }

    private static void addIfDirectory(List<File> dirs, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File dir = new File(path);
        if (dir.isDirectory() && !dirs.contains(dir)) {
            dirs.add(dir);
        }
    }

    /** Directorios del proyecto que pueden contener drawables (drawable, drawable-xhdpi, ...). */
    private List<File> projectDrawableDirs() {
        List<File> dirs = new ArrayList<>();
        File resourceDir = new File(new FilePathUtil().getPathResource(scId));
        File[] children = resourceDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().toLowerCase(Locale.US).startsWith("drawable")) {
                    dirs.add(child);
                }
            }
        }
        return dirs;
    }

    /** Busca el fichero del drawable por nombre (con o sin extension), en esa carpeta y subcarpetas. */
    @Nullable
    private Drawable findDrawableIn(File dir, String name) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        // En una raiz de recursos de libreria solo interesan las carpetas drawable*/mipmap*.
        if ("res".equals(dir.getName())) {
            File[] resChildren = dir.listFiles();
            if (resChildren != null) {
                for (File child : resChildren) {
                    if (!child.isDirectory()) {
                        continue;
                    }
                    String folder = child.getName().toLowerCase(Locale.US);
                    if (folder.startsWith("drawable") || folder.startsWith("mipmap")) {
                        Drawable found = findDrawableIn(child, name);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
            return null;
        }
        String lowerName = name.toLowerCase(Locale.US);
        // Nombre con extension explicita (p.ej. "foto.png" o una ruta relativa "sub/foto.png").
        if (lowerName.contains(".")) {
            File direct = new File(dir, name);
            if (direct.isFile()) {
                return loadFile(direct);
            }
        }
        for (String extension : DRAWABLE_EXTENSIONS) {
            File file = findFileIgnoreCase(dir, name + extension);
            if (file != null) {
                return loadFile(file);
            }
        }
        // Subcarpetas (una sola pasada, los proyectos no anidan mas).
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    Drawable found = findDrawableIn(child, name);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /** Busca "fichero" dentro de "dir" sin importar mayusculas/minusculas (y en subcarpetas). */
    @Nullable
    private File findFileIgnoreCase(File dir, String fileName) {
        File exact = new File(dir, fileName);
        if (exact.isFile()) {
            return exact;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isFile() && child.getName().equalsIgnoreCase(fileName)) {
                return child;
            }
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findFileIgnoreCase(child, fileName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Drawable ya incluido en el IDE (p.ej. default_image), por nombre. */
    @Nullable
    private Drawable loadIdeDrawable(String name) {
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id != 0) {
                Drawable drawable = context.getDrawable(id);
                if (drawable != null) {
                    return drawable;
                }
                // Algunos drawables del IDE son tintables/mutables: nos aseguramos de tener una copia.
                return new ColorDrawable(Color.TRANSPARENT);
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "ProjectResourceResolver: Throwable ignored", ignored);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Set de iconos del IDE: "icon_<nombre>_<estilo>" -> svg/<nombre>/<estilo>.svg
    // ---------------------------------------------------------------------------------------------

    /**
     * Resuelve los iconos del propio set del IDE.
     *
     * <p>El selector de iconos ({@code ImportIconActivity}) lista {@code svg/<nombre>/} y guarda el
     * elegido como {@code icon_<nombre>_<estilo>} (p.ej. {@code icon_miscellaneous_services_round}).
     * Ese set va DENTRO del APK del editor en {@code assets/icons/icon_pack.zip} (2.191 nombres x 5
     * estilos), asi que un diseno que referencia ese nombre tiene que poder verse: antes no se miraba
     * en ningun sitio (no es un {@code res/drawable} del APK, asi que {@code getIdentifier()} no lo ve).
     *
     * <p>El nombre es exacto (nombre + estilo), no una variante adivinada: se dibuja el mismo SVG que
     * el selector habria convertido a vector. Se anota en {@link #legacyResolutions} para que no sea
     * una sustitucion en silencio (el aviso dice de donde ha salido).
     */
    @Nullable
    private Drawable loadIconPackDrawable(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (!lower.startsWith("icon_")) {
            return null;
        }
        int separator = lower.lastIndexOf('_');
        if (separator <= "icon_".length() - 1) {
            return null;
        }
        String style = lower.substring(separator + 1);
        if (!isIconPackStyle(style)) {
            return null;
        }
        String iconName = lower.substring("icon_".length(), separator);
        if (iconName.isEmpty()) {
            return null;
        }
        String entry = "svg/" + iconName + "/" + style + ".svg";
        if (iconPackMisses.contains(entry)) {
            return null;
        }
        String svg = readIconPackSvg(entry);
        if (svg == null) {
            iconPackMisses.add(entry);
            return null;
        }
        String vectorXml = svgToVectorXml(svg);
        Drawable drawable = vectorXml == null ? null : VectorPathDrawable.parse(context, vectorXml);
        if (drawable == null) {
            iconPackMisses.add(entry);
            return null;
        }
        legacyResolutions.add(new LegacyResolution("@drawable/" + name,
                "svg/" + iconName + "/" + style + ".svg",
                "icono del set Material del IDE (assets/icons/icon_pack.zip), convertido a vector"));
        android.util.Log.i(TAG, "info: icono del set del IDE resuelto: @drawable/" + name
                + " -> " + entry);
        return drawable;
    }

    private static boolean isIconPackStyle(String style) {
        for (String candidate : ICON_PACK_STYLES) {
            if (candidate.equals(style)) {
                return true;
            }
        }
        return false;
    }

    /** Hay set de iconos del IDE disponible (extraido del APK o dentro de assets)? */
    private boolean iconPackAvailable() {
        if (extractedIconPackDir() != null && new File(extractedIconPackDir(), "svg").isDirectory()) {
            return true;
        }
        try (InputStream stream = context.getAssets().open(ICON_PACK_ASSET)) {
            return stream != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Carpeta donde el IDE descomprime el set de iconos la primera vez que se abre el selector
     * ({@code wq.getExtractedIconPackStoreLocation()}). Puede no existir todavia.
     */
    @Nullable
    private File extractedIconPackDir() {
        try {
            return new File(a.a.a.wq.getExtractedIconPackStoreLocation());
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Lee un SVG del set de iconos del IDE: de la copia ya descomprimida si esta, y si no, del zip
     * que va dentro del APK ({@code assets/icons/icon_pack.zip}).
     */
    @Nullable
    private String readIconPackSvg(String entry) {
        File extracted = extractedIconPackDir();
        if (extracted != null) {
            File file = new File(extracted, entry);
            if (file.isFile()) {
                String content = FileUtil.readFile(file.getAbsolutePath());
                if (content != null && !content.trim().isEmpty()) {
                    return content;
                }
            }
        }
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open(ICON_PACK_ASSET))) {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                if (!entry.equals(zipEntry.getName())) {
                    continue;
                }
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = zip.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }
                zip.closeEntry();
                return buffer.toString("UTF-8");
            }
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "warning: no se pudo leer el set de iconos del IDE (" + entry + "): " + throwable);
        }
        return null;
    }

    /**
     * Convierte un SVG del set de iconos (Material: uno o varios {@code <path d="..."/>}) en el XML
     * de un vector drawable, que es lo que sabe dibujar {@link VectorPathDrawable}. No se anaden
     * dependencias nuevas: los iconos del set son deliberadamente simples.
     */
    @Nullable
    private static String svgToVectorXml(String svg) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(svg));
            StringBuilder paths = new StringBuilder();
            String viewportWidth = "24";
            String viewportHeight = "24";
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("svg".equals(tag)) {
                        String viewBox = parser.getAttributeValue(null, "viewBox");
                        if (viewBox != null) {
                            String[] parts = viewBox.trim().split("[\\s,]+");
                            if (parts.length >= 4) {
                                viewportWidth = parts[2];
                                viewportHeight = parts[3];
                            }
                        }
                        String width = parser.getAttributeValue(null, "width");
                        String height = parser.getAttributeValue(null, "height");
                        if (width != null && !width.isEmpty() && height != null && !height.isEmpty()
                                && viewportWidth.isEmpty()) {
                            // Sin viewBox se usan las dimensiones del propio SVG.
                            viewportWidth = width.replaceAll("[^0-9.]", "");
                            viewportHeight = height.replaceAll("[^0-9.]", "");
                        }
                    } else if ("path".equals(tag)) {
                        String data = parser.getAttributeValue(null, "d");
                        if (data == null || data.trim().isEmpty()) {
                            continue;
                        }
                        String fill = parser.getAttributeValue(null, "fill");
                        if (fill == null || fill.isEmpty() || "none".equalsIgnoreCase(fill)) {
                            fill = "#FF000000";
                        }
                        paths.append("<path android:pathData=\"")
                                .append(data.replace("\"", ""))
                                .append("\" android:fillColor=\"")
                                .append(fill)
                                .append("\"/>");
                    }
                }
                eventType = parser.next();
            }
            if (paths.length() == 0) {
                return null;
            }
            return "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\""
                    + " android:width=\"24dp\" android:height=\"24dp\""
                    + " android:viewportWidth=\"" + viewportWidth + "\""
                    + " android:viewportHeight=\"" + viewportHeight + "\">"
                    + paths
                    + "</vector>";
        } catch (Throwable throwable) {
            android.util.Log.d("SketchwarePro", "ProjectResourceResolver: SVG no convertible", throwable);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Nombres heredados (Sketchware antiguo): "@drawable/ic_tune_white" -> "ic_tune_24"
    // ---------------------------------------------------------------------------------------------

    /**
     * Sufijos que la Sketchware antigua pegaba al nombre del icono (color y tamano). Se quitan del
     * FINAL, repetidamente y sin importar el orden relativo (p.ej. "add_circle_white_24dp" ->
     * "add_circle"). Solo se usan para BUSCAR un equivalente; nunca renombran lo que pide el XML.
     */
    private static final String[] LEGACY_SUFFIX_TOKENS = {
            "_white", "_black", "_dark", "_light", "_primary", "_accent",
            "_grey600", "_grey", "_gray", "_holo_light", "_holo_dark",
            "_24dp", "_36dp", "_48dp", "_96dp", "_64dp", "_40dp", "_32dp", "_18dp", "_16dp", "_12dp", "_8dp",
            "_24", "_36", "_48", "_96", "_64", "_40", "_32", "_18", "_16", "_12", "_8",
            "_dp", "_px", "_alpha"
    };

    /** Prefijos de la nomenclatura antigua ("ic_tune_white" -> "tune_white"). */
    private static final String[] LEGACY_PREFIXES = {"ic_", "img_", "icon_"};

    /**
     * Convierte un nombre heredado en su "base" de concepto: sin prefijo ({@code ic_}/{@code img_})
     * ni sufijos de color/tamano. Devuelve null si no queda una base utilizable (nombre vacio o
     * demasiado corto), para no mapear cualquier cosa a un icono cualquiera.
     */
    @Nullable
    static String legacyBaseName(String name) {
        if (name == null) {
            return null;
        }
        String base = name.trim().toLowerCase(Locale.US);
        for (String prefix : LEGACY_PREFIXES) {
            if (base.startsWith(prefix) && base.length() > prefix.length()) {
                base = base.substring(prefix.length());
                break;
            }
        }
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String suffix : LEGACY_SUFFIX_TOKENS) {
                if (base.length() > suffix.length() && base.endsWith(suffix)) {
                    base = base.substring(0, base.length() - suffix.length());
                    stripped = true;
                    break;
                }
            }
        }
        base = trimSeparators(base);
        // Una base de 1 caracter (o vacia) mapearia demasiadas cosas: no arriesgamos.
        if (base.length() < 2 || !base.matches("[a-z0-9_]+")) {
            return null;
        }
        return base;
    }

    private static String trimSeparators(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * Variantes que se prueban contra los recursos del IDE, EN ORDEN de prioridad. El primer
     * candidato que exista gana; si el nombre heredado es genuinamente dudoso (no encaja con
     * ninguna) o demasiado generico, no hay candidato y se mantiene el aviso.
     *
     * El orden es fijo a proposito: hace la resolucion DETERMINISTA (nada de adivinar entre dos
     * iconos parecidos por puntuacion difusa).
     */
    private static List<String> legacyCandidateNames(String base) {
        List<String> candidates = new ArrayList<>();
        // 1) El nombre tal cual lo usa hoy el IDE para sus propios iconos.
        candidates.add("ic_" + base);
        // 2) Iconos con tamano de 24 (la convencion de esta era; p.ej. ic_tune_24).
        candidates.add("ic_" + base + "_24");
        // 3) La familia Material del IDE (p.ej. ic_mtrl_tune).
        candidates.add("ic_mtrl_" + base);
        // 4) Otras densidades/formatos que el IDE tambien usa.
        candidates.add("ic_" + base + "_24dp");
        candidates.add("ic_" + base + "_48dp");
        candidates.add("ic_" + base + "_48");
        candidates.add("ic_" + base + "_36");
        candidates.add("ic_" + base + "_18");
        // 5) El nombre sin prefijo (p.ej. "arrow_back_white_48dp").
        candidates.add(base);
        return candidates;
    }

    /**
     * Ultimo recurso de {@link #resolveDrawable}: mapea un nombre heredado a un drawable actual del
     * IDE. Devuelve null (y el que llama deja el aviso rojo) si no hay base utilizable o ningun
     * candidato existe.
     *
     * Dos pasos, ambos deterministas:
     * <ol>
     *   <li><b>Variantes estructurales</b> ({@code ic_<base>}, {@code ic_<base>_24},
     *       {@code ic_mtrl_<base>}, ...): gana la primera que exista, en orden fijo.</li>
     *   <li><b>Variantes de color/tamano</b> ({@code ic_<base>_black_24}, {@code ic_<base>_white_24dp},
     *       ...): solo se usan si existe EXACTAMENTE UNA. Si existen dos o mas candidatos de este
     *       tipo no hay forma honesta de elegir -> no se resuelve ("dudas" -> se mantiene el rojo).</li>
     * </ol>
     */
    @Nullable
    private Drawable resolveLegacyIdeDrawable(String name, String original) {
        String base = legacyBaseName(name);
        if (base == null) {
            return null;
        }
        for (String candidate : legacyCandidateNames(base)) {
            if (isIdeDrawable(candidate)) {
                return recordLegacyResolution(original, candidate, base, "variante estructural");
            }
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : legacyColorVariantNames(base)) {
            if (isIdeDrawable(candidate)) {
                matches.add(candidate);
            }
        }
        if (matches.size() == 1) {
            return recordLegacyResolution(original, matches.get(0), base, "unica variante de color/tamano");
        }
        if (matches.size() > 1) {
            // Dudoso a proposito: dos iconos distintos podrian valer y elegir seria adivinar.
            android.util.Log.i(TAG, "info: nombre heredado ambiguo, no se resuelve: " + original
                    + " (candidatos: " + matches + ")");
        }
        return null;
    }

    private Drawable recordLegacyResolution(String original, String candidate, String base, String reason) {
        Drawable drawable = loadIdeDrawable(candidate);
        if (drawable == null) {
            return null;
        }
        legacyResolutions.add(new LegacyResolution(original, candidate, reason + " (base: " + base + ")"));
        android.util.Log.i(TAG, "info: nombre heredado resuelto: " + original
                + " -> @drawable/" + candidate + " [base: " + base + ", " + reason + "]");
        return drawable;
    }

    /**
     * Variantes con el color/tamano que usa el IDE para esa familia (p.ej. {@code ic_arrow_back_*}).
     * Solo valen si hay UNA: con varias no se puede saber cual queria el diseno antiguo.
     */
    private static List<String> legacyColorVariantNames(String base) {
        List<String> candidates = new ArrayList<>();
        candidates.add("ic_" + base + "_black_24");
        candidates.add("ic_" + base + "_white_24dp");
        candidates.add("ic_" + base + "_black_24dp");
        candidates.add("ic_" + base + "_white_24");
        candidates.add("ic_" + base + "_black");
        candidates.add("ic_" + base + "_white");
        candidates.add("ic_" + base + "_grey600_24dp");
        candidates.add("ic_" + base + "_gray_48dp");
        candidates.add("ic_" + base + "_grey_48dp");
        return candidates;
    }

    /** Existe ese drawable en los recursos del propio IDE? (id != 0). */
    private boolean isIdeDrawable(String name) {
        try {
            return context.getResources().getIdentifier(name, "drawable", context.getPackageName()) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private Drawable loadFrameworkDrawable(String name) {
        int id = context.getResources().getIdentifier(name, "drawable", "android");
        return id != 0 ? context.getDrawable(id) : null;
    }

    /** Lee un fichero de imagen (png/jpg/webp/...) o un drawable XML (shape/vector). */
    @Nullable
    private Drawable loadFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".xml")) {
            return loadXmlDrawable(file);
        }
        try {
            android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                return null;
            }
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            // El bitmap no trae densidad: fijamos la del dispositivo para que el tamano sea el real.
            bitmap.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
            drawable.setTargetDensity(context.getResources().getDisplayMetrics());
            return drawable;
        } catch (Throwable throwable) {
            android.util.Log.w("LayoutPreview", "warning: no se pudo leer la imagen " + file, throwable);
            return null;
        }
    }

    /** Drawable XML: primero vector, luego <shape> (lo que dibuja el editor de recursos). */
    @Nullable
    private Drawable loadXmlDrawable(File file) {
        String xml = FileUtil.readFile(file.getAbsolutePath());
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("vector".equals(tag)) {
                        // Los <vector> del proyecto no se pueden inflar desde una cadena: el
                        // inflador de plataforma exige un XmlBlock.Parser y lanza ClassCastException
                        // si el XML viene de un fichero/texto (y con el tampoco se podria pedir el
                        // color de un tema). Por eso los dibujamos nosotros con PathParser.
                        try {
                            Drawable vector = VectorPathDrawable.parse(context, xml);
                            if (vector != null) {
                                return vector;
                            }
                        } catch (Throwable ignored) {
                            android.util.Log.d("SketchwarePro", "ProjectResourceResolver: vector no soportado", ignored);
                        }
                        return null;
                    }
                    if ("shape".equals(tag) || "selector".equals(tag) || "layer-list".equals(tag)
                            || "inset".equals(tag) || "clip".equals(tag) || "ripple".equals(tag)
                            || "animated-vector".equals(tag)) {
                        break;
                    }
                }
                eventType = parser.next();
            }
        } catch (Throwable ignored) {
            android.util.Log.d("SketchwarePro", "ProjectResourceResolver: Throwable ignored", ignored);
        }
        return parseShapeDrawable(xml);
    }

    @Nullable
    private Drawable parseShapeDrawable(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));

            GradientDrawable drawable = new GradientDrawable();
            boolean foundShape = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    switch (name) {
                        case "shape" -> {
                            foundShape = true;
                            String shape = parser.getAttributeValue(null, "android:shape");
                            if ("oval".equals(shape)) {
                                drawable.setShape(GradientDrawable.OVAL);
                            } else if ("ring".equals(shape)) {
                                drawable.setShape(GradientDrawable.RING);
                            } else if ("line".equals(shape)) {
                                drawable.setShape(GradientDrawable.LINE);
                            } else {
                                drawable.setShape(GradientDrawable.RECTANGLE);
                            }
                        }
                        case "corners" -> {
                            String radius = parser.getAttributeValue(null, "android:radius");
                            if (radius != null) {
                                drawable.setCornerRadius(parseDimen(radius));
                            } else {
                                drawable.setCornerRadii(new float[]{
                                        parseDimen(parser.getAttributeValue(null, "android:topLeftRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:topLeftRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:topRightRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:topRightRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:bottomRightRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:bottomRightRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:bottomLeftRadius")),
                                        parseDimen(parser.getAttributeValue(null, "android:bottomLeftRadius"))
                                });
                            }
                        }
                        case "solid" -> {
                            String color = parser.getAttributeValue(null, "android:color");
                            if (color != null) {
                                drawable.setColor(resolveColor(null, color, Color.TRANSPARENT));
                            }
                        }
                        case "stroke" -> {
                            String width = parser.getAttributeValue(null, "android:width");
                            String color = parser.getAttributeValue(null, "android:color");
                            drawable.setStroke(parseDimen(width), resolveColor(null, color, Color.TRANSPARENT));
                        }
                        case "gradient" -> {
                            String start = parser.getAttributeValue(null, "android:startColor");
                            String end = parser.getAttributeValue(null, "android:endColor");
                            int startColor = resolveColor(null, start, 0);
                            int endColor = resolveColor(null, end, 0);
                            if (startColor != 0 && endColor != 0) {
                                drawable.setColors(new int[]{startColor, endColor});
                            }
                        }
                        case "size" -> {
                            int width = parseDimen(parser.getAttributeValue(null, "android:width"));
                            int height = parseDimen(parser.getAttributeValue(null, "android:height"));
                            // Un <size> puede traer SOLO el alto (tipico en un separador) o solo el
                            // ancho: antes se exigian los dos, asi que un divider con solo
                            // android:height="4dp" quedaba con altura intrinseca 0 y no se veia.
                            if (width > 0 || height > 0) {
                                drawable.setSize(width > 0 ? width : -1, height > 0 ? height : -1);
                            }
                        }
                        default -> {
                        }
                    }
                }
                eventType = parser.next();
            }
            return foundShape ? drawable : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Convierte un color de tema a una lista de estados (para backgroundTint). Se evita depender de
     * MaterialColors cuando el atributo no existe.
     */
    @Nullable
    public ColorStateList resolveColorStateList(String value) {
        int color = resolveColor(null, value, 0);
        return color == 0 ? null : ColorStateList.valueOf(color);
    }

    private int parseDimen(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String v = value.trim();
        float density = context.getResources().getDisplayMetrics().density;
        try {
            if (v.toLowerCase(Locale.US).endsWith("dp")) {
                return (int) (Float.parseFloat(v.substring(0, v.length() - 2)) * density);
            }
            if (v.toLowerCase(Locale.US).endsWith("dip")) {
                return (int) (Float.parseFloat(v.substring(0, v.length() - 3)) * density);
            }
            if (v.toLowerCase(Locale.US).endsWith("px")) {
                return (int) Float.parseFloat(v.substring(0, v.length() - 2));
            }
            if (v.toLowerCase(Locale.US).endsWith("sp")) {
                return (int) (Float.parseFloat(v.substring(0, v.length() - 2)) * density);
            }
            return (int) Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Renderizador minimo de un <vector> del proyecto.
     *
     * No se puede usar VectorDrawableCompat con un XML leido de fichero/cadena (exige un
     * XmlBlock.Parser), asi que se construye el dibujo a mano con PathParser: tamafto, viewport y
     * los <path> con su pathData, fillColor y stroke. Cubre los vectores que genera el gestor de
     * recursos al importar un SVG, que es el caso habitual en los proyectos.
     */
    private static final class VectorPathDrawable extends Drawable {

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<float[]> paths = new ArrayList<>();
        private final List<Path> geometry = new ArrayList<>();
        private final List<Integer> fills = new ArrayList<>();
        private final List<Integer> strokes = new ArrayList<>();
        private final List<Float> strokeWidths = new ArrayList<>();
        private float viewportWidth = 24f;
        private float viewportHeight = 24f;
        private int intrinsicWidth;
        private int intrinsicHeight;

        @Nullable
        static Drawable parse(Context context, String xml) {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(new StringReader(xml));
                VectorPathDrawable drawable = new VectorPathDrawable();
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("vector".equals(tag)) {
                            drawable.viewportWidth = parseFloat(parser.getAttributeValue(null, "android:viewportWidth"), 24f);
                            drawable.viewportHeight = parseFloat(parser.getAttributeValue(null, "android:viewportHeight"), 24f);
                            drawable.intrinsicWidth = parseDimen(context, parser.getAttributeValue(null, "android:width"));
                            drawable.intrinsicHeight = parseDimen(context, parser.getAttributeValue(null, "android:height"));
                        } else if ("path".equals(tag)) {
                            String pathData = parser.getAttributeValue(null, "android:pathData");
                            if (pathData == null || pathData.trim().isEmpty()) {
                                continue;
                            }
                            Path path = androidx.core.graphics.PathParser.createPathFromPathData(pathData);
                            if (path == null) {
                                continue;
                            }
                            String fill = parser.getAttributeValue(null, "android:fillColor");
                            String stroke = parser.getAttributeValue(null, "android:strokeColor");
                            float strokeWidth = parseFloat(parser.getAttributeValue(null, "android:strokeWidth"), 0f);
                            drawable.geometry.add(path);
                            drawable.fills.add(fill == null ? 0xFF000000 : resolveVectorColor(context, fill));
                            drawable.strokes.add(stroke == null ? 0 : resolveVectorColor(context, stroke));
                            drawable.strokeWidths.add(strokeWidth);
                        }
                    }
                    eventType = parser.next();
                }
                return drawable.geometry.isEmpty() ? null : drawable;
            } catch (Throwable throwable) {
                android.util.Log.d("SketchwarePro", "ProjectResourceResolver: vector no soportado", throwable);
                return null;
            }
        }

        private static int resolveVectorColor(Context context, String value) {
            try {
                return Color.parseColor(value);
            } catch (Throwable ignored) {
                try {
                    if (value.startsWith("@color/")) {
                        int id = context.getResources().getIdentifier(value.substring("@color/".length()), "color", context.getPackageName());
                        if (id != 0) {
                            return context.getColor(id);
                        }
                    }
                } catch (Throwable ignored2) {
                    return Color.TRANSPARENT;
                }
                return Color.TRANSPARENT;
            }
        }

        private static float parseFloat(String value, float fallback) {
            if (value == null) {
                return fallback;
            }
            try {
                return Float.parseFloat(value.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static int parseDimen(Context context, String value) {
            if (value == null) {
                return 0;
            }
            String v = value.trim();
            float density = context.getResources().getDisplayMetrics().density;
            try {
                if (v.endsWith("dp") || v.endsWith("dip")) {
                    return (int) (Float.parseFloat(v.replace("dip", "").replace("dp", "")) * density);
                }
                if (v.endsWith("px")) {
                    return (int) Float.parseFloat(v.substring(0, v.length() - 2));
                }
                return (int) (Float.parseFloat(v) * density);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public void draw(@androidx.annotation.NonNull Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            int save = canvas.save();
            canvas.scale(bounds.width() / viewportWidth, bounds.height() / viewportHeight);
            for (int i = 0; i < geometry.size(); i++) {
                int fill = fills.get(i);
                if (Color.alpha(fill) != 0) {
                    fillPaint.setColor(fill);
                    fillPaint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(geometry.get(i), fillPaint);
                }
                int stroke = strokes.get(i);
                if (Color.alpha(stroke) != 0 && strokeWidths.get(i) > 0) {
                    strokePaint.setColor(stroke);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(strokeWidths.get(i));
                    canvas.drawPath(geometry.get(i), strokePaint);
                }
            }
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicWidth > 0 ? intrinsicWidth : (int) (viewportWidth * 3);
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicHeight > 0 ? intrinsicHeight : (int) (viewportHeight * 3);
        }
    }
}
