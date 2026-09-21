package pro.sketchware.commandpalette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandPaletteAction {

    public final String id;
    public final String title;
    public final String subtitle;
    public final String category;
    public final List<String> keywords;
    public final int priority;

    public CommandPaletteAction(String id,
                                String title,
                                String subtitle,
                                String category,
                                List<String> keywords,
                                int priority) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
        this.subtitle = subtitle == null ? "" : subtitle.trim();
        this.category = category == null ? "" : category.trim();
        this.keywords = Collections.unmodifiableList(new ArrayList<>(keywords == null
                ? Collections.emptyList()
                : keywords));
        this.priority = priority;
    }

    public boolean isValid() {
        return !id.isEmpty() && !title.isEmpty();
    }
}
