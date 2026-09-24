package pro.sketchware.utility;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compatibilidad de {@code android:scaleType} entre lo que guarda el editor, lo que se escribe en el
 * XML del layout y lo que cada widget admite de verdad.
 *
 * <p>Resuelve dos incoherencias reales:
 *
 * <ul>
 *   <li><b>Dos nombres para el mismo valor.</b> El editor guarda el nombre del enum de Android
 *       ({@code CENTER}, {@code CENTER_CROP}, {@code FIT_CENTER}...) mientras el XML usa el nombre
 *       camelCase ({@code center}, {@code centerCrop}, {@code fitCenter}). Quien aplicaba un valor
 *       sin traducirlo fallaba o caia en el valor por defecto.</li>
 *   <li><b>{@code de.hdodenhof.circleimageview.CircleImageView} solo admite {@code centerCrop}.</b>
 *       El bytecode de la libreria (3.1.0) compara el valor contra un unico campo estatico
 *       ({@code ScaleType.CENTER_CROP}) y lanza
 *       {@code IllegalArgumentException: ScaleType X not supported} con cualquier otro, incluidos
 *       {@code CENTER_INSIDE}, {@code CENTER} y {@code FIT_CENTER}. Como el editor trae {@code center}
 *       por defecto, el widget reventaba: en la vista previa (con aviso, desde la ronda 6) y, si el
 *       valor llegaba al XML, tambien en la <b>app compilada</b> del usuario.</li>
 * </ul>
 *
 * <p>Todos los metodos son puros (solo cadenas y {@link Locale}), sin dependencias de Android, para
 * poder probarlos en la JVM sin emulador.
 */
public final class ScaleTypeCompat {

    /** Valor XML por defecto: {@code ImageView.ScaleType.CENTER}. */
    public static final String XML_CENTER = "center";
    public static final String XML_CENTER_CROP = "centerCrop";
    public static final String XML_CENTER_INSIDE = "centerInside";

    /** Valores que admite CircleImageView (nombre del enum, como los guarda el editor). */
    public static final String ENUM_CENTER_CROP = "CENTER_CROP";
    public static final String ENUM_CENTER_INSIDE = "CENTER_INSIDE";

    /**
     * Valores que se pueden ofrecer/guardar para un {@code CircleImageView}: la libreria solo admite
     * CENTER_CROP (cualquier otro hace que el widget lance {@code IllegalArgumentException}).
     */
    public static final String[] CIRCLE_IMAGE_VIEW_ENUM_VALUES = {ENUM_CENTER_CROP};

    /** Valor XML con el que se sustituye un scaleType no admitido por CircleImageView. */
    public static final String CIRCLE_IMAGE_VIEW_FALLBACK_XML = XML_CENTER_CROP;

    /** Nombre simple de la clase del widget; si coincide, es un CircleImageView. */
    private static final String CIRCLE_IMAGE_VIEW_CLASS = "CircleImageView";

    private static final Pattern INJECT_SCALE_TYPE =
            Pattern.compile("android:scaleType\\s*=\\s*\"([^\"]*)\"");

    private ScaleTypeCompat() {
    }

    /**
     * Tabla: clave normalizada (minusculas y sin separadores) -> [nombre del enum, valor XML].
     */
    private static final String[][] VALUES = {
            {"center", "CENTER", XML_CENTER},
            {"centercrop", ENUM_CENTER_CROP, XML_CENTER_CROP},
            {"centerinside", ENUM_CENTER_INSIDE, XML_CENTER_INSIDE},
            {"fitxy", "FIT_XY", "fitXY"},
            {"fitstart", "FIT_START", "fitStart"},
            {"fitend", "FIT_END", "fitEnd"},
            {"fitcenter", "FIT_CENTER", "fitCenter"},
            {"matrix", "MATRIX", "matrix"},
    };

    private static String normalizeKey(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toLowerCase(Locale.US).replace("_", "").replace("-", "").replace(" ", "");
        return key.isEmpty() ? null : key;
    }

    private static int indexOfValue(@Nullable String value) {
        String key = normalizeKey(value);
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i][0].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** ¿Es {@code value} un scaleType conocido? Acepta tanto {@code CENTER_CROP} como {@code centerCrop}. */
    public static boolean isKnown(@Nullable String value) {
        return indexOfValue(value) >= 0;
    }

    /** Nombre del enum ({@code CENTER_CROP}, {@code FIT_CENTER}...) o {@code null} si no se reconoce. */
    @Nullable
    public static String toEnumName(@Nullable String value) {
        int index = indexOfValue(value);
        return index < 0 ? null : VALUES[index][1];
    }

    /** Valor del XML ({@code centerCrop}, {@code fitCenter}...) o {@code null} si no se reconoce. */
    @Nullable
    public static String toXmlValue(@Nullable String value) {
        int index = indexOfValue(value);
        return index < 0 ? null : VALUES[index][2];
    }

    /**
     * ¿El nombre de clase ({@code convert} del bean, p.ej. {@code de.hdodenhof.circleimageview.CircleImageView})
     * corresponde a un CircleImageView? Se compara el nombre simple: una clase ajena llamada
     * {@code MyCircleImageView} no cuenta.
     */
    public static boolean isCircleImageViewName(@Nullable String convert) {
        if (convert == null) {
            return false;
        }
        String name = convert.trim();
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            name = name.substring(lastDot + 1);
        }
        return CIRCLE_IMAGE_VIEW_CLASS.equalsIgnoreCase(name);
    }

    /**
     * {@code CircleImageView} solo admite {@code centerCrop} (verificado en el bytecode de la
     * libreria: {@code setScaleType} compara contra {@code ScaleType.CENTER_CROP} y lanza
     * {@code IllegalArgumentException} con cualquier otro valor).
     */
    public static boolean isSupportedByCircleImageView(@Nullable String value) {
        int index = indexOfValue(value);
        return index >= 0 && VALUES[index][2].equals(XML_CENTER_CROP);
    }

    /**
     * Valor XML valido para un CircleImageView: conserva {@code centerCrop} y ajusta cualquier otro
     * (incluidos {@code CENTER_INSIDE}, {@code null} o un valor desconocido) al respaldo
     * {@link #CIRCLE_IMAGE_VIEW_FALLBACK_XML}. Nunca devuelve {@code null}.
     */
    public static String adjustForCircleImageView(@Nullable String value) {
        return isSupportedByCircleImageView(value) ? toXmlValue(value) : CIRCLE_IMAGE_VIEW_FALLBACK_XML;
    }

    /**
     * Igual que {@link #adjustForCircleImageView(String)} pero devolviendo el nombre del enum, que es
     * la forma en la que el editor guarda el valor en el bean.
     */
    public static String adjustEnumForCircleImageView(@Nullable String value) {
        return toEnumName(adjustForCircleImageView(value));
    }

    /**
     * Valor que hay que escribir en el XML para este bean. Para CircleImageView ajusta el valor a uno
     * admitido; para el resto de ImageViews devuelve la traduccion del valor (o {@code null} si no se
     * reconoce, que es el comportamiento historico: no escribir el atributo).
     */
    @Nullable
    public static String xmlValueFor(boolean circleImageView, @Nullable String value) {
        String xmlValue = toXmlValue(value);
        if (!circleImageView) {
            return xmlValue;
        }
        return adjustForCircleImageView(xmlValue);
    }

    /** Texto legible para avisos: {@code CENTER_CROP} en mayusculas, o {@code (ninguno)}. */
    public static String describe(@Nullable String value) {
        String enumName = toEnumName(value);
        if (enumName != null) {
            return enumName;
        }
        if (value == null || value.trim().isEmpty()) {
            return "(ninguno)";
        }
        return value.trim().toUpperCase(Locale.US);
    }

    /** Valor de {@code android:scaleType} dentro del texto de {@code inject}, o {@code null}. */
    @Nullable
    public static String injectScaleTypeValue(@Nullable String inject) {
        if (inject == null) {
            return null;
        }
        Matcher matcher = INJECT_SCALE_TYPE.matcher(inject);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Corrige, dentro del texto de {@code inject}, un {@code android:scaleType} no admitido por
     * CircleImageView. Idempotente: si ya es valido (o no hay atributo) devuelve el texto tal cual.
     */
    public static String sanitizeInjectScaleTypeForCircleImageView(@Nullable String inject) {
        if (inject == null) {
            return "";
        }
        Matcher matcher = INJECT_SCALE_TYPE.matcher(inject);
        if (!matcher.find()) {
            return inject;
        }
        String current = matcher.group(1);
        String adjusted = adjustForCircleImageView(current);
        if (adjusted.equals(toXmlValue(current))) {
            return inject;
        }
        return matcher.replaceFirst("android:scaleType=\"" + adjusted + "\"");
    }
}
