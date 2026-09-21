package pro.sketchware.ai;

import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Map;

import a.a.a.eC;
import a.a.a.jC;

public final class AgentProjectContext {

    private AgentProjectContext() {
    }

    public static String build(String scId) {
        if (scId == null || scId.trim().isEmpty()) {
            return "";
        }
        try {
            eC data = jC.a(scId);
            StringBuilder sb = new StringBuilder();
            sb.append("- Scope: ").append(scId).append('\n');
            appendScreens(sb, data);
            appendEvents(sb, data);
            appendVariables(sb, data);
            String files = ProjectCodeInjector.buildProjectContext(scId);
            if (!files.isEmpty()) {
                sb.append("- Archivos:\n").append(files);
            }
            return sb.toString();
        } catch (Throwable throwable) {
            return "";
        }
    }

    private static void appendScreens(StringBuilder sb, eC data) {
        sb.append("- Pantallas (xml):\n");
        Map<String, ArrayList<ViewBean>> screens = data.c;
        if (screens == null || screens.isEmpty()) {
            sb.append("  (sin layouts registrados)\n");
            return;
        }
        int totalViews = 0;
        for (Map.Entry<String, ArrayList<ViewBean>> entry : screens.entrySet()) {
            String xmlName = entry.getKey();
            ArrayList<ViewBean> views = entry.getValue();
            if (views == null || views.isEmpty()) {
                sb.append("  ").append(xmlName).append(" (vacía)\n");
                continue;
            }
            sb.append("  ").append(xmlName).append(":\n");
            int listed = 0;
            for (ViewBean view : views) {
                if (view == null || totalViews >= 40) {
                    continue;
                }
                sb.append("    - id=").append(view.id)
                        .append(" tipo=").append(typeName(view.type))
                        .append(" padre=").append(view.parent == null ? "?" : view.parent)
                        .append('\n');
                totalViews++;
                listed++;
            }
            if (listed == 0 && totalViews >= 40) {
                sb.append("    (más views omitidas por límite)\n");
            }
        }
        if (totalViews >= 40) {
            sb.append("  (límite de 40 views para mantener el prompt ligero)\n");
        }
    }

    private static void appendEvents(StringBuilder sb, eC data) {
        sb.append("- Eventos con bloques:\n");
        Map<String, ArrayList<EventBean>> events = data.i;
        boolean any = false;
        if (events != null) {
            for (Map.Entry<String, ArrayList<EventBean>> entry : events.entrySet()) {
                ArrayList<EventBean> list = entry.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }
                for (EventBean event : list) {
                    if (event == null) {
                        continue;
                    }
                    any = true;
                    sb.append("  ").append(entry.getKey()).append(" -> ")
                            .append(event.targetId).append('_').append(event.eventName).append('\n');
                }
            }
        }
        if (!any) {
            sb.append("  (sin eventos)\n");
        }
    }

    private static void appendVariables(StringBuilder sb, eC data) {
        int count = 0;
        if (data.e != null) {
            count += data.e.size();
        }
        if (data.f != null) {
            count += data.f.size();
        }
        if (data.g != null) {
            count += data.g.size();
        }
        sb.append("- Variables/listas registradas: ").append(count).append('\n');
    }

    private static String typeName(int type) {
        switch (type) {
            case ViewBean.VIEW_TYPE_LAYOUT_LINEAR:
                return "LinearLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_RELATIVE:
                return "RelativeLayout";
            case ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW:
                return "HScrollView";
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
            case ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW:
                return "VScrollView";
            case ViewBean.VIEW_TYPE_WIDGET_SWITCH:
                return "Switch";
            case ViewBean.VIEW_TYPE_WIDGET_SEEKBAR:
                return "SeekBar";
            case ViewBean.VIEW_TYPE_WIDGET_CALENDARVIEW:
                return "CalendarView";
            case ViewBean.VIEW_TYPE_WIDGET_FAB:
                return "Fab";
            case ViewBean.VIEW_TYPE_WIDGET_ADVIEW:
                return "AdView";
            case ViewBean.VIEW_TYPE_WIDGET_MAPVIEW:
                return "MapView";
            default:
                return "type" + type;
        }
    }
}
