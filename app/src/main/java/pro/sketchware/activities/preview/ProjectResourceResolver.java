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

    private static final Pattern COLOR_ENTRY = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9a-fA-F]{6,8})</color>");

    /** Extensiones de imagen que sabemos leer, en orden de preferencia. */
    private static final String[] DRAWABLE_EXTENSIONS = {".xml", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"};

    private final Context context;
    private final String scId;
    private final Map<String, Integer> colorCache = new HashMap<>();
    private final Map<String, Drawable> drawableCache = new HashMap<>();

    /** Cosas que la vista previa no ha podido resolver (para avisar en la barra de estado). */
    private final Set<String> warnings = new LinkedHashSet<>();

    private boolean colorsLoaded;

    public ProjectResourceResolver(Context context, String scId) {
        this.context = context;
        this.scId = scId;
    }

    /** Avisos acumulados (recursos no resueltos). El que llama decide como mostrarlos. */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public void clearWarnings() {
        warnings.clear();
    }

    private void warning(String message) {
        warnings.add(message);
        android.util.Log.w("LayoutPreview", "warning: recurso no resuelto: " + message);
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
                warning("color invalido: " + v);
                return fallback;
            }
        }
        if (v.startsWith("@color/")) {
            ensureColorsLoaded();
            Integer cached = colorCache.get(v.substring("@color/".length()));
            if (cached != null) {
                return cached;
            }
            warning(v + " (no esta en files/resource/values/colors.xml)");
            return fallback;
        }
        if (v.startsWith("@android:color/")) {
            int id = context.getResources().getIdentifier(v.substring("@android:color/".length()), "color", "android");
            if (id != 0) {
                return context.getColor(id);
            }
            warning(v);
            return fallback;
        }
        if (v.startsWith("?")) {
            int color = resolveThemeColor(view, v);
            if (color != 0) {
                return color;
            }
            warning(v + " (atributo del tema no resoluble en la vista previa)");
            return fallback;
        }
        return fallback;
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
        }

        if (resolved != null) {
            drawableCache.put(v, resolved);
        } else {
            warning(value);
        }
        return resolved;
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
        // 4) Drawables propios del IDE: default_image, iconos mtrl, etc.
        return loadIdeDrawable(name);
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
