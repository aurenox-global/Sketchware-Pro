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

    private void debug(String message) {
        binding.debugStatus.setVisibility(android.view.View.VISIBLE);
        binding.debugStatus.setText(message);
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
        pane.initialize(scId, true);
        pane.setVerticalScrollBarEnabled(true);
        pane.setResourceManager(jC.d(scId));
        UI.addSystemWindowInsetToPadding(binding.pane, false, false, false, true);

        layoutHistory.push(title);
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
                String xml = new yq(getApplicationContext(), scId)
                        .getFileSrc(layoutName, jC.b(scId), jC.a(scId), jC.c(scId));
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

    private void renderLayout(String layoutName, String xml) {
        // Layouts with a WebView use the real-views builder so HTML/https content works.
        // Layouts without one use the design editor's native renderer for pixel-perfect looks.
        if (xmlContainsWebView(xml)) {
            if (!tryInflateRealLayout(layoutName, xml)) {
                renderWithViewPane(layoutName, xml);
            }
        } else {
            renderWithViewPane(layoutName, xml);
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
            for (ViewBean bean : beans) {
                View view = createRealView(bean);
                if (view != null) {
                    viewsById.put(bean.id, view);
                    viewIdNames.put(view, bean.id);
                }
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
            pane.addView(rootView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
            debug("Preview OK · vistas: " + viewsById.size() + " · WebViews: " + webViewCount + sample);
            return true;
        } catch (Throwable throwable) {
            pane.removeAllViews();
            debug("Preview FAIL: " + throwable.getMessage());
            return false;
        }
    }

    private View createRealView(ViewBean bean) {
        String className = bean.convert == null ? "" : bean.convert.trim();
        if (className.isEmpty()) {
            className = android.widget.LinearLayout.class.getName();
        }
        View view = pro.sketchware.utility.InvokeUtil.createView(this, className);
        if (view == null) {
            return null;
        }
        view.setId(android.view.View.generateViewId());
        applyBeanAppearance(view, bean);
        return view;
    }

    private void applyBeanAppearance(View view, ViewBean bean) {
        com.besome.sketch.beans.LayoutBean layout = bean.layout;
        if (layout == null) {
            return;
        }
        view.setPadding(dp(layout.paddingLeft), dp(layout.paddingTop), dp(layout.paddingRight), dp(layout.paddingBottom));
        if (layout.backgroundColor != 0) {
            view.setBackgroundColor(layout.backgroundColor);
        }
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
                if (textColor != 0) {
                    textView.setTextColor(textColor);
                }
                if (text.textType == 1) {
                    textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                }
                textView.setGravity(layout.gravity);
                if (view instanceof android.widget.EditText editText && text.hint != null && !text.hint.isEmpty()) {
                    editText.setHint(text.hint);
                    int hintColor = resourceResolver.resolveColor(editText, text.resHintColor, text.hintColor);
                    if (hintColor != 0) {
                        editText.setHintTextColor(hintColor);
                    }
                }
            }
        }
        applyInjectAttributes(view, bean);
    }

    private void applyInjectAttributes(View view, ViewBean bean) {
        try {
            var injectHandler = new pro.sketchware.utility.InjectAttributeHandler(bean);
            for (android.util.Pair<String, String> pair : injectHandler.getAttributes()) {
                switch (pair.first) {
                    case "android:background": {
                        String value = pair.second;
                        if (value.startsWith("#") || value.startsWith("@color/") || value.startsWith("?attr/")) {
                            int color = resourceResolver.resolveColor(view, value, 0);
                            if (color != 0) {
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
                    case "android:backgroundTint": {
                        int color = resourceResolver.resolveColor(view, pair.second, 0);
                        if (color != 0) {
                            view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                        }
                        break;
                    }
                    case "android:textColor": {
                        int color = resourceResolver.resolveColor(view, pair.second, 0);
                        if (color != 0 && view instanceof android.widget.TextView textView) {
                            textView.setTextColor(color);
                        }
                        break;
                    }
                    case "android:gravity": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setGravity(parseGravity(pair.second));
                        } else {
                            view.setForegroundGravity(parseGravity(pair.second));
                        }
                        break;
                    }
                    case "android:orientation": {
                        if (view instanceof android.widget.LinearLayout linearLayout) {
                            linearLayout.setOrientation("horizontal".equalsIgnoreCase(pair.second)
                                    ? android.widget.LinearLayout.HORIZONTAL
                                    : android.widget.LinearLayout.VERTICAL);
                        }
                        break;
                    }
                    case "android:elevation": {
                        view.setElevation(parseDimen(pair.second));
                        break;
                    }
                    case "android:alpha": {
                        try {
                            view.setAlpha(Float.parseFloat(pair.second));
                        } catch (NumberFormatException ignored) {
                            android.util.Log.d("SketchwarePro", "LayoutPreviewActivity: NumberFormatException ignored", ignored);
                        }
                        break;
                    }
                    case "android:visibility": {
                        if ("gone".equalsIgnoreCase(pair.second)) {
                            view.setVisibility(android.view.View.GONE);
                        } else if ("invisible".equalsIgnoreCase(pair.second)) {
                            view.setVisibility(android.view.View.INVISIBLE);
                        }
                        break;
                    }
                    case "android:padding": {
                        int pad = parseDimen(pair.second);
                        view.setPadding(pad, pad, pad, pad);
                        break;
                    }
                    case "android:paddingLeft": {
                        view.setPadding(parseDimen(pair.second), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
                        break;
                    }
                    case "android:paddingTop": {
                        view.setPadding(view.getPaddingLeft(), parseDimen(pair.second), view.getPaddingRight(), view.getPaddingBottom());
                        break;
                    }
                    case "android:paddingRight": {
                        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), parseDimen(pair.second), view.getPaddingBottom());
                        break;
                    }
                    case "android:paddingBottom": {
                        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), parseDimen(pair.second));
                        break;
                    }
                    case "android:textSize": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setTextSize(parseSp(pair.second));
                        }
                        break;
                    }
                    case "android:textStyle": {
                        if (view instanceof android.widget.TextView textView) {
                            boolean bold = pair.second.contains("bold");
                            boolean italic = pair.second.contains("italic");
                            textView.setTypeface(android.graphics.Typeface.DEFAULT,
                                    (bold ? android.graphics.Typeface.BOLD : 0) | (italic ? android.graphics.Typeface.ITALIC : 0));
                        }
                        break;
                    }
                    case "android:hint": {
                        if (view instanceof android.widget.EditText editText) {
                            editText.setHint(pair.second);
                        }
                        break;
                    }
                    case "android:src": {
                        android.graphics.drawable.Drawable drawable = resourceResolver.resolveDrawable(pair.second);
                        if (drawable != null && view instanceof android.widget.ImageView imageView) {
                            imageView.setImageDrawable(drawable);
                        }
                        break;
                    }
                    case "android:scaleType": {
                        if (view instanceof android.widget.ImageView imageView) {
                            imageView.setScaleType(parseScaleType(pair.second));
                        }
                        break;
                    }
                    case "android:singleLine": {
                        if (view instanceof android.widget.TextView textView) {
                            textView.setSingleLine("true".equalsIgnoreCase(pair.second));
                        }
                        break;
                    }
                    case "android:maxLines": {
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

    private void renderWithViewPane(String layoutName, String xml) {
        try {
            pane.removeAllViews();
            pane.updateRootLayout(scId, layoutName);
            var parser = new ViewBeanParser(xml);
            ArrayList<ViewBean> beans = parser.parse();
            loadViews(beans);
            wirePaneNavigation(layoutName);
            debug("Preview nativa OK · vistas: " + beans.size());
        } catch (Exception e) {
            debug("Render error: " + e.getMessage());
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
