package pro.sketchware.activities.preview;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Xml;
import android.view.View;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.material.color.MaterialColors;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class ProjectResourceResolver {

    private static final Pattern COLOR_ENTRY = Pattern.compile(
            "<color\\s+name=\"([^\"]+)\"\\s*>\\s*(#[0-9a-fA-F]{6,8})\\s*</color>");

    private final Context context;
    private final String scId;
    private final Map<String, Integer> colorCache = new HashMap<>();
    private final Map<String, Drawable> drawableCache = new HashMap<>();
    private boolean colorsLoaded;

    public ProjectResourceResolver(Context context, String scId) {
        this.context = context;
        this.scId = scId;
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
                return fallback;
            }
        }
        if (v.startsWith("@color/")) {
            ensureColorsLoaded();
            Integer cached = colorCache.get(v.substring("@color/".length()));
            if (cached != null) {
                return cached;
            }
            return fallback;
        }
        if (v.startsWith("?attr/") || v.startsWith("?android:attr/")) {
            return resolveThemeColor(view, v, fallback);
        }
        return fallback;
    }

    private int resolveThemeColor(View view, String attrExpression, int fallback) {
        String attrName = attrExpression.substring(attrExpression.indexOf('/') + 1);
        String defPackage = attrExpression.startsWith("?android:attr/") ? "android" : context.getPackageName();
        int attrId = context.getResources().getIdentifier(attrName, "attr", defPackage);
        if (attrId == 0 && !"android".equals(defPackage)) {
            attrId = context.getResources().getIdentifier(attrName, "attr", "com.google.android.material");
        }
        if (attrId == 0) {
            return fallback;
        }
        try {
            return MaterialColors.getColor(view, attrId);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @Nullable
    public Drawable resolveDrawable(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (!v.startsWith("@drawable/")) {
            return null;
        }
        String name = v.substring("@drawable/".length());
        Drawable cached = drawableCache.get(name);
        if (cached != null) {
            return cached;
        }
        File drawableDir = new File(new FilePathUtil().getPathResource(scId), "drawable");
        File xmlFile = new File(drawableDir, name + ".xml");
        if (xmlFile.isFile()) {
            Drawable parsed = parseShapeDrawable(FileUtil.readFile(xmlFile.getAbsolutePath()));
            if (parsed != null) {
                drawableCache.put(name, parsed);
            }
            return parsed;
        }
        File pngFile = new File(drawableDir, name + ".png");
        if (pngFile.isFile()) {
            Drawable parsed = new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(pngFile.getAbsolutePath()));
            drawableCache.put(name, parsed);
            return parsed;
        }
        File jpgFile = new File(drawableDir, name + ".jpg");
        if (jpgFile.isFile()) {
            Drawable parsed = new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(jpgFile.getAbsolutePath()));
            drawableCache.put(name, parsed);
            return parsed;
        }
        return null;
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

    private int parseDimen(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String v = value.trim();
        float density = context.getResources().getDisplayMetrics().density;
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
        try {
            return (int) Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
