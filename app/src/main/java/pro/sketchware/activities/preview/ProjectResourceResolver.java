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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9a-fA-F]{6,8})</color>");

    /** Entrada de un fichero dimens.xml del proyecto: nombre y valor ("16dp", "@dimen/x"...). */
    private static final Pattern DIMEN_ENTRY = Pattern.compile(
            "<dimen\\s+name=\"([^\"]+)\"\\s*>\\s*([^<\\s]+)\\s*</dimen>");

    /** Extensiones de imagen que sabemos leer, en orden de preferencia. */
    private static final String[] DRAWABLE_EXTENSIONS = {".xml", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"};

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
        OTHER("otros");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Cosas que la vista previa no ha podido resolver, agrupadas por causa. */
    private final Map<Kind, Set<String>> warningsByKind = new java.util.EnumMap<>(Kind.class);

    /** Drawables buscados solo en librerias/otras rutas (se indexa una vez por resolver). */
    private List<File> libraryDrawableDirs;
    /** Resumen de donde se ha buscado (para el detalle del aviso). */
    private String searchedLocations = "";

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

    private void ensureColorsLoaded() {
        if (colorsLoaded) {
            return;
        }
        colorsLoaded = true;
        File colorsFile = new File(new FilePathUtil().getPathResource(scId), "values/colors.xml");
        if (!colorsFile.isFile()) {
            return;
        }
        String content = FileUtil.readFile(colorsFile.getAbsolutePath());
        if (content == null) {
            return;
        }
        Matcher matcher = COLOR_ENTRY.matcher(content);
        while (matcher.find()) {
            try {
                colorCache.put(matcher.group(1), Color.parseColor(matcher.group(2)));
            } catch (IllegalArgumentException ignored) {
                android.util.Log.d("SketchwarePro", "ProjectResourceResolver: IllegalArgumentException ignored", ignored);
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
            warning(Kind.THEME, v + " (atributo del tema no resoluble en la vista previa)");
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
            warning(Kind.OTHER, v + " (no esta en files/resource/values/dimens.xml)");
            return fallback;
        }
        if (v.startsWith("@android:dimen/")) {
            int id = context.getResources().getIdentifier(
                    v.substring("@android:dimen/".length()), "dimen", "android");
            if (id != 0) {
                return context.getResources().getDimensionPixelSize(id);
            }
            warning(Kind.OTHER, v + " (medida del framework no encontrada)");
            return fallback;
        }
        if (v.startsWith("@") || v.startsWith("?")) {
            warning(Kind.OTHER, v + " (referencia de medida no resoluble en la vista previa)");
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
        // 1) Atributo del propio tema del IDE (Material3: colorPrimary, colorSurface, ...).
        int attrId = context.getResources().getIdentifier(attrName, "attr", pkg);
        if (attrId == 0 && !"android".equals(pkg)) {
            attrId = context.getResources().getIdentifier(attrName, "attr", "com.google.android.material");
        }
        if (attrId != 0) {
            try {
                return MaterialColors.getColor(view, attrId);
            } catch (Throwable ignored) {
                android.util.Log.d("SketchwarePro", "ProjectResourceResolver: Throwable ignored", ignored);
            }
        }
        // 2) Resolucion directa por tema (vale tambien para atributos de framework).
        if (attrId == 0 && !"android".equals(pkg)) {
            attrId = context.getResources().getIdentifier(attrName, "attr", "android");
        }
        if (attrId != 0) {
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attrId, typedValue, true)) {
                if (typedValue.type == TypedValue.TYPE_REFERENCE || typedValue.type == TypedValue.TYPE_STRING) {
                    try {
                        return context.getColor(typedValue.resourceId);
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
            parts.add(projectDirs + " carpetas drawable* del proyecto");
        }
        parts.add("assets del proyecto");
        int libraries = libraryDrawableDirs().size();
        if (libraries > 0) {
            parts.add(libraries + " recursos de librerias");
        }
        parts.add("recursos del IDE");
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
        return loadIdeDrawable(name);
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
                            if (width > 0 && height > 0) {
                                drawable.setSize(width, height);
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
