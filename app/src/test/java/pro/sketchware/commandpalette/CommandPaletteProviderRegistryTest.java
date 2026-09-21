package pro.sketchware.commandpalette;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandPaletteProviderRegistryTest {

    @Test
    public void registerProvider_replacesDuplicateIdAndSortsIds() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();

        registry.registerProvider(provider("zeta", action("zeta.action", "Zeta action", 0)));
        registry.registerProvider(provider("alpha", action("alpha.action", "Alpha action", 0)));
        registry.registerProvider(provider("alpha", action("alpha.action.v2", "Alpha action v2", 0)));

        assertEquals(Arrays.asList("alpha", "zeta"), registry.providerIds());

        CommandPaletteSearchResult result = registry.search(new CommandPaletteQuery("v2", 10));
        assertEquals(1, result.actions.size());
        assertEquals("alpha.action.v2", result.actions.get(0).id);
    }

    @Test
    public void search_filtersByQueryAndAppliesPriority() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();
        registry.registerProvider(provider("core",
                action("project.open", "Open Project", 5),
                action("project.close", "Close Project", 0),
                action("editor.open_palette", "Open Command Palette", 50, "Search actions", "Editor", Arrays.asList("command", "palette"))
        ));

        CommandPaletteSearchResult result = registry.search(new CommandPaletteQuery("open", 10));
        assertEquals(2, result.actions.size());
        assertEquals("editor.open_palette", result.actions.get(0).id);
        assertEquals("project.open", result.actions.get(1).id);
    }

    @Test
    public void search_deduplicatesByActionIdKeepingHigherScore() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();

        registry.registerProvider(provider("a",
                action("project.run", "Run Project", 1)
        ));
        registry.registerProvider(provider("b",
                action("project.run", "Run Project", 99)
        ));

        CommandPaletteSearchResult result = registry.search(new CommandPaletteQuery("run", 10));
        assertEquals(1, result.actions.size());
        assertEquals(99, result.actions.get(0).priority);
    }

    @Test
    public void search_continuesWhenProviderThrows() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();
        registry.registerProvider(new CommandPaletteProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public List<CommandPaletteAction> getActions(CommandPaletteQuery query) {
                throw new IllegalStateException("boom");
            }
        });
        registry.registerProvider(provider("safe", action("safe.open", "Open Safe", 0)));

        CommandPaletteSearchResult result = registry.search(new CommandPaletteQuery("open", 10));

        assertEquals(1, result.actions.size());
        assertEquals("safe.open", result.actions.get(0).id);
        assertEquals(2, result.providerReports.size());
        assertFalse(result.providerReports.get(0).success);
        assertTrue(result.providerReports.get(0).errorMessage.contains("boom"));
    }

    @Test
    public void search_appliesMaxResultsLimit() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();
        registry.registerProvider(provider("core",
                action("a", "Action A", 1),
                action("b", "Action B", 2),
                action("c", "Action C", 3)
        ));

        CommandPaletteSearchResult result = registry.search(new CommandPaletteQuery("action", 2));
        assertEquals(2, result.actions.size());
    }

    private static CommandPaletteProvider provider(String id, CommandPaletteAction... actions) {
        return new CommandPaletteProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<CommandPaletteAction> getActions(CommandPaletteQuery query) {
                return Arrays.asList(actions);
            }
        };
    }

    private static CommandPaletteAction action(String id, String title, int priority) {
        return action(id, title, priority, "", "General", Collections.emptyList());
    }

    private static CommandPaletteAction action(String id,
                                               String title,
                                               int priority,
                                               String subtitle,
                                               String category,
                                               List<String> keywords) {
        return new CommandPaletteAction(id, title, subtitle, category, keywords, priority);
    }
}
