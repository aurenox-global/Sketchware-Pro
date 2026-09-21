package pro.sketchware.ai;

import android.graphics.Color;

import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import a.a.a.eC;
import a.a.a.jC;
import mod.agus.jcoderz.beans.ViewBeans;

public final class AgentActionExecutor {

    public static final class Result {
        private final String reply;
        private final List<String> summaryLines;

        Result(String reply, List<String> summaryLines) {
            this.reply = reply == null ? "" : reply;
            this.summaryLines = summaryLines == null ? new ArrayList<>() : summaryLines;
        }

        public String getReply() {
            return reply;
        }

        public List<String> getSummaryLines() {
            return summaryLines;
        }
    }

    private final String scId;
    private final eC data;
    private final Set<String> usedIds = new HashSet<>();

    public AgentActionExecutor(String scId) {
        this.scId = scId;
        this.data = jC.a(scId);
        collectUsedIds();
    }

    public Result execute(JSONObject agentResponse) {
        ArrayList<String> summary = new ArrayList<>();
        String reply = agentResponse == null ? "" : agentResponse.optString("reply", "");
        JSONArray actions = agentResponse == null ? null : agentResponse.optJSONArray("actions");
        if (actions != null) {
            // Two passes: create views first, then events/code, so add_event and
            // inject_code always find the views they reference (small models often
            // emit actions out of order).
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject action = actions.optJSONObject(i);
                    if (action == null) {
                        continue;
                    }
                    boolean isViewAction = isViewAction(action);
                    if (pass == 0 && !isViewAction) {
                        continue;
                    }
                    if (pass == 1 && isViewAction) {
                        continue;
                    }
                    summary.addAll(executeAction(action));
                }
            }
        }
        data.k();
        return new Result(reply, summary);
    }

    private boolean isViewAction(JSONObject action) {
        String type = normalizeActionType(action.optString("type", ""));
        return "add_view".equals(type);
    }

    private List<String> executeAction(JSONObject action) {
        String type = normalizeActionType(action.optString("type", ""));
        switch (type) {
            case "inject_code":
            case "inject":
            case "add_block":
                return injectCode(action);
            case "add_event":
                return addEvent(action);
            case "add_view":
            case "create_view":
                return addView(action);
            default:
                ArrayList<String> unknown = new ArrayList<>();
                unknown.add("Accion desconocida ignorada: " + type);
                return unknown;
        }
    }

    /**
     * Small models often confuse the action type with the view type (e.g. they
     * emit {"type":"button", ...} meaning add_view with a button). Normalize the
     * action type: if it looks like a view type, treat it as an add_view action.
     */
    private static String normalizeActionType(String raw) {
        String type = raw == null ? "" : raw.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        switch (type) {
            case "inject_code":
            case "inject":
            case "add_block":
            case "add_code":
                return "inject_code";
            case "add_event":
            case "set_event":
            case "onclick":
                return "add_event";
            case "add_view":
            case "create_view":
            case "add_ui":
            case "create_ui":
            case "add_component":
                return "add_view";
            case "linear":
            case "linear_layout":
            case "vertical":
            case "horizontal":
            case "row":
            case "column":
            case "scroll":
            case "scrollview":
            case "vscroll":
            case "card":
            case "capsule":
            case "button":
            case "text":
            case "textview":
            case "label":
            case "input":
            case "edit":
            case "edittext":
            case "search":
            case "image":
            case "imageview":
            case "webview":
            case "web":
            case "progress":
            case "progressbar":
            case "list":
            case "listview":
            case "spinner":
            case "checkbox":
            case "switch":
            case "seekbar":
            case "seek_bar":
                return "add_view";
            default:
                return type;
        }
    }

    private List<String> injectCode(JSONObject action) {
        String target = normalizeFileName(action.optString("target", "main"));
        String javaName = ProjectFileBean.getJavaName(target);
        String xmlName = ProjectFileBean.getXmlName(target);
        String event = action.optString("event", "initializeLogic").trim();
        String code = action.optString("code", "").trim();
        ArrayList<String> lines = new ArrayList<>();

        if (code.isEmpty()) {
            lines.add("inject_code ignorado (sin codigo).");
            return lines;
        }

        String eventKey;
        if ("initializeLogic".equalsIgnoreCase(event) || "oncreate".equalsIgnoreCase(event)) {
            // The onCreate/initializeLogic activity event is always implicit in
            // Sketchware (it is rendered unconditionally by the Event tab), so it
            // must NOT be stored in the project data: doing so duplicates onCreate.
            eventKey = ProjectCodeInjector.INITIALIZE_LOGIC_EVENT;
        } else {
            String viewId = normalizeViewId(event);
            ViewBean view = data.c(xmlName, viewId);
            if (view == null) {
                lines.add("inject_code ignorado: la vista '" + viewId + "' no existe en " + xmlName + ".");
                return lines;
            }
            ensureViewEvent(view, javaName, xmlName);
            eventKey = viewId + "_onClick";
        }

        if (ProjectCodeInjector.injectIntoEvent(scId, javaName, eventKey, code)) {
            lines.add("Codigo inyectado en " + javaName + " (" + eventKey + ").");
        } else {
            lines.add("No se pudo inyectar codigo en " + javaName + " (" + eventKey + ").");
        }
        return lines;
    }

    private List<String> addEvent(JSONObject action) {
        String target = normalizeFileName(action.optString("target", "main"));
        String javaName = ProjectFileBean.getJavaName(target);
        String xmlName = ProjectFileBean.getXmlName(target);
        String viewId = normalizeViewId(action.optString("view_id", ""));
        String code = action.optString("code", "").trim();
        ArrayList<String> lines = new ArrayList<>();

        if (viewId.isEmpty()) {
            lines.add("add_event ignorado (falta view_id).");
            return lines;
        }

        ViewBean view = data.c(xmlName, viewId);
        if (view == null) {
            lines.add("add_event ignorado: la vista '" + viewId + "' no existe en " + xmlName + ".");
            return lines;
        }
        ensureViewEvent(view, javaName, xmlName);
        if (!code.isEmpty()) {
            ProjectCodeInjector.injectIntoEvent(scId, javaName, viewId + "_onClick", code);
        }
        lines.add("Evento " + viewId + "_onClick listo en " + javaName + ".");
        return lines;
    }

    private List<String> addView(JSONObject action) {
        String screen = normalizeFileName(action.optString("screen", "main"));
        String xmlName = ProjectFileBean.getXmlName(screen);
        String parentId = normalizeViewId(action.optString("parent", "root"));
        // Accept both "view_type" (preferred, unambiguous) and legacy "type".
        String type = action.optString("view_type", action.optString("type", "text"))
                .toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        ArrayList<String> lines = new ArrayList<>();

        int[] typeInfo = resolveViewType(type);
        if (typeInfo == null) {
            lines.add("add_view ignorado: tipo '" + type + "' no soportado.");
            return lines;
        }
        int viewType = typeInfo[0];
        String convert = typeInfo[1] == 1 ? "androidx.cardview.widget.CardView" : defaultConvert(viewType);

        ViewBean view = new ViewBean();
        view.type = viewType;
        view.convert = convert;
        view.id = nextId(action.optString("id", ""), viewType);
        view.name = view.id;
        view.parent = parentId;
        view.parentType = parentId.equals("root") ? ViewBean.VIEW_TYPE_LAYOUT_LINEAR : parentTypeOf(parentId, xmlName);
        view.index = nextIndex(parentId, xmlName);

        String width = action.optString("width", "match_parent").trim().toLowerCase(Locale.US);
        String height = action.optString("height", "wrap_content").trim().toLowerCase(Locale.US);
        view.layout.width = parseDimension(width, LayoutBean.LAYOUT_MATCH_PARENT);
        view.layout.height = parseDimension(height, LayoutBean.LAYOUT_WRAP_CONTENT);

        if (viewType == ViewBean.VIEW_TYPE_LAYOUT_LINEAR) {
            String orientation = action.optString("orientation", "vertical").toLowerCase(Locale.US);
            view.layout.orientation = orientation.contains("horizontal") || orientation.contains("row")
                    ? LayoutBean.ORIENTATION_HORIZONTAL
                    : LayoutBean.ORIENTATION_VERTICAL;
        }

        String text = action.optString("text", "");
        if (!text.isEmpty()) {
            view.text.text = text;
        }
        int textSize = action.optInt("text_size", action.optInt("textSize", 0));
        if (textSize > 0) {
            view.text.textSize = textSize;
        }
        String hint = action.optString("hint", "");
        if (!hint.isEmpty()) {
            view.text.hint = hint;
        }

        view.layout.marginLeft = action.optInt("margin_left", action.optInt("marginLeft", 0));
        view.layout.marginTop = action.optInt("margin_top", action.optInt("marginTop", 0));
        view.layout.marginRight = action.optInt("margin_right", action.optInt("marginRight", 0));
        view.layout.marginBottom = action.optInt("margin_bottom", action.optInt("marginBottom", 0));

        String backgroundColor = action.optString("background_color", action.optString("backgroundColor", ""));
        if (!backgroundColor.isEmpty()) {
            try {
                view.layout.backgroundColor = Color.parseColor(backgroundColor);
            } catch (IllegalArgumentException ignored) {
                android.util.Log.d("SketchwarePro", "AgentActionExecutor: IllegalArgumentException ignored", ignored);
            }
        }

        data.a(xmlName, view);
        usedIds.add(view.id);
        lines.add(viewName(view) + " '" + view.id + "' agregado en " + xmlName + ".");

        if (viewType == ViewBean.VIEW_TYPE_WIDGET_BUTTON) {
            String javaName = ProjectFileBean.getJavaName(screen);
            ensureViewEvent(view, javaName, xmlName);
        }
        return lines;
    }

    private void ensureViewEvent(ViewBean view, String javaName, String xmlName) {
        if (hasOnClickEvent(javaName, view.id)) {
            return;
        }
        ViewBean existing = data.c(xmlName, view.id);
        if (existing != null) {
            data.a(javaName, EventBean.EVENT_TYPE_VIEW, existing.type, existing.id, "onClick");
        } else {
            data.a(javaName, EventBean.EVENT_TYPE_VIEW, view.type, view.id, "onClick");
        }
    }

    private boolean hasOnClickEvent(String javaName, String viewId) {
        ArrayList<EventBean> events = data.g(javaName);
        if (events == null) {
            return false;
        }
        for (EventBean event : events) {
            if (event != null
                    && event.eventType == EventBean.EVENT_TYPE_VIEW
                    && "onClick".equals(event.eventName)
                    && viewId.equals(event.targetId)) {
                return true;
            }
        }
        return false;
    }

    private int parentTypeOf(String parentId, String xmlName) {
        if ("root".equals(parentId)) {
            return ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
        }
        ViewBean parent = data.c(xmlName, parentId);
        return parent == null ? ViewBean.VIEW_TYPE_LAYOUT_LINEAR : parent.type;
    }

    private int nextIndex(String parentId, String xmlName) {
        int max = -1;
        ArrayList<ViewBean> views = data.d(xmlName);
        if (views != null) {
            for (ViewBean view : views) {
                if (view != null && parentId.equals(view.parent) && view.index > max) {
                    max = view.index;
                }
            }
        }
        return max + 1;
    }

    private String nextId(String hint, int viewType) {
        String base;
        if (hint == null || hint.trim().isEmpty()) {
            base = defaultIdBase(viewType);
        } else {
            base = normalizeViewId(hint);
        }
        String candidate = base;
        int counter = 1;
        while (usedIds.contains(candidate)) {
            candidate = base + (counter++);
        }
        usedIds.add(candidate);
        return candidate;
    }

    private void collectUsedIds() {
        if (data.c == null) {
            return;
        }
        for (ArrayList<ViewBean> views : data.c.values()) {
            if (views == null) {
                continue;
            }
            for (ViewBean view : views) {
                if (view != null && view.id != null) {
                    usedIds.add(view.id);
                }
            }
        }
        usedIds.add("root");
    }

    private static int[] resolveViewType(String type) {
        if (type.contains("linear") || type.contains("column") || type.contains("vertical") || type.contains("horizontal") || type.contains("row")) {
            return new int[]{ViewBean.VIEW_TYPE_LAYOUT_LINEAR, 0};
        }
        if (type.contains("scroll")) {
            return new int[]{ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW, 0};
        }
        if (type.contains("card") || type.contains("capsule")) {
            return new int[]{ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW, 1};
        }
        if (type.contains("button")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_BUTTON, 0};
        }
        if (type.contains("input") || type.contains("edit") || type.contains("search")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_EDITTEXT, 0};
        }
        if (type.contains("image")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW, 0};
        }
        if (type.contains("webview") || type.contains("web")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_WEBVIEW, 0};
        }
        if (type.contains("progress")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR, 0};
        }
        if (type.contains("list")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_LISTVIEW, 0};
        }
        if (type.contains("spinner")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SPINNER, 0};
        }
        if (type.contains("checkbox")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_CHECKBOX, 0};
        }
        if (type.contains("switch")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SWITCH, 0};
        }
        if (type.contains("seekbar")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_SEEKBAR, 0};
        }
        if (type.contains("text") || type.contains("label")) {
            return new int[]{ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, 0};
        }
        return null;
    }

    private static String defaultConvert(int viewType) {
        switch (viewType) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "LinearLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "ScrollView";
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON:
                return "Button";
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW:
                return "TextView";
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT:
                return "EditText";
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW:
                return "ImageView";
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW:
                return "WebView";
            case ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR:
                return "ProgressBar";
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW:
                return "ListView";
            case ViewBean.VIEW_TYPE_WIDGET_SPINNER:
                return "Spinner";
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX:
                return "CheckBox";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "Switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "SeekBar";
            default:
                return null;
        }
    }

    private static String defaultIdBase(int viewType) {
        switch (viewType) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "linear";
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "scroll";
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON:
                return "button";
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW:
                return "text";
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT:
                return "input";
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW:
                return "image";
            case ViewBean.VIEW_TYPE_WIDGET_WEBVIEW:
                return "webview";
            case ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR:
                return "progress";
            case ViewBean.VIEW_TYPE_WIDGET_LISTVIEW:
                return "list";
            case ViewBean.VIEW_TYPE_WIDGET_SPINNER:
                return "spinner";
            case ViewBean.VIEW_TYPE_WIDGET_CHECKBOX:
                return "checkbox";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "seekbar";
            default:
                return "view";
        }
    }

    private static int parseDimension(String value, int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        if (value.contains("match")) {
            return LayoutBean.LAYOUT_MATCH_PARENT;
        }
        if (value.contains("wrap")) {
            return LayoutBean.LAYOUT_WRAP_CONTENT;
        }
        try {
            return (int) Double.parseDouble(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String viewName(ViewBean view) {
        String base = defaultConvert(view.type);
        return base == null ? "Vista" : base;
    }

    private static String normalizeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "main";
        }
        String normalized = value.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.toLowerCase(Locale.US).endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.toLowerCase(Locale.US).endsWith(".xml")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.isEmpty() ? "main" : normalized;
    }

    private static String normalizeViewId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_]", "").replaceAll("^_+|_+$", "");
    }
}
