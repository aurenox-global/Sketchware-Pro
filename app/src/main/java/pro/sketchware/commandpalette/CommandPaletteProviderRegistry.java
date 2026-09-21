package pro.sketchware.commandpalette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CommandPaletteProviderRegistry {

    private final CopyOnWriteArrayList<CommandPaletteProvider> providers = new CopyOnWriteArrayList<>();

    public static CommandPaletteProviderRegistry createDefault() {
        CommandPaletteProviderRegistry registry = new CommandPaletteProviderRegistry();
        registry.registerProvider(new NoOpCommandPaletteProvider());
        return registry;
    }

    public void registerProvider(CommandPaletteProvider provider) {
        if (provider == null || provider.id() == null || provider.id().trim().isEmpty()) {
            return;
        }

        String providerId = provider.id().trim();
        providers.removeIf(existing -> providerId.equals(existing.id()));
        providers.add(provider);
        providers.sort(Comparator.comparing(CommandPaletteProvider::id));
    }

    public boolean removeProvider(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) {
            return false;
        }
        String normalized = providerId.trim();
        return providers.removeIf(existing -> normalized.equals(existing.id()));
    }

    public void clearProviders() {
        providers.clear();
    }

    public List<String> providerIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (CommandPaletteProvider provider : providers) {
            ids.add(provider.id());
        }
        return Collections.unmodifiableList(ids);
    }

    public CommandPaletteSearchResult search(CommandPaletteQuery query) {
        long startedAt = System.currentTimeMillis();
        CommandPaletteQuery safeQuery = query == null ? CommandPaletteQuery.defaultQuery() : query;

        Map<String, ScoredAction> selectedById = new LinkedHashMap<>();
        ArrayList<CommandPaletteProviderReport> reports = new ArrayList<>();

        for (CommandPaletteProvider provider : providers) {
            long providerStart = System.currentTimeMillis();
            List<CommandPaletteAction> actions;
            try {
                actions = provider.getActions(safeQuery);
            } catch (Throwable throwable) {
                reports.add(CommandPaletteProviderReport.failure(
                        provider.id(),
                        System.currentTimeMillis() - providerStart,
                        throwable.getMessage() == null ? "Provider execution failed" : throwable.getMessage()
                ));
                continue;
            }

            List<CommandPaletteAction> safeActions = actions == null ? Collections.emptyList() : actions;
            reports.add(CommandPaletteProviderReport.success(
                    provider.id(),
                    System.currentTimeMillis() - providerStart,
                    safeActions.size()
            ));

            for (CommandPaletteAction action : safeActions) {
                if (action == null || !action.isValid()) {
                    continue;
                }

                int textScore = scoreActionForQuery(action, safeQuery);
                if (safeQuery.hasText() && textScore <= 0) {
                    continue;
                }

                int totalScore = action.priority + textScore;
                ScoredAction candidate = new ScoredAction(action, provider.id(), totalScore);
                ScoredAction existing = selectedById.get(action.id);
                if (existing == null || candidate.compareTo(existing) > 0) {
                    selectedById.put(action.id, candidate);
                }
            }
        }

        ArrayList<ScoredAction> sorted = new ArrayList<>(selectedById.values());
        sorted.sort(Comparator.reverseOrder());

        int limit = Math.max(1, safeQuery.maxResults);
        ArrayList<CommandPaletteAction> limited = new ArrayList<>();
        for (ScoredAction scoredAction : sorted) {
            if (limited.size() >= limit) {
                break;
            }
            limited.add(scoredAction.action);
        }

        return new CommandPaletteSearchResult(
                limited,
                reports,
                System.currentTimeMillis() - startedAt
        );
    }

    private static int scoreActionForQuery(CommandPaletteAction action, CommandPaletteQuery query) {
        if (!query.hasText()) {
            return 1;
        }

        String needle = query.text.toLowerCase(Locale.ROOT);
        int score = 0;

        if (contains(action.id, needle)) {
            score += 25;
        }
        if (contains(action.title, needle)) {
            score += 40;
        }
        if (contains(action.subtitle, needle)) {
            score += 15;
        }
        if (contains(action.category, needle)) {
            score += 10;
        }

        for (String keyword : action.keywords) {
            if (contains(keyword, needle)) {
                score += 20;
                break;
            }
        }
        return score;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static final class ScoredAction implements Comparable<ScoredAction> {

        private final CommandPaletteAction action;
        private final String providerId;
        private final int score;

        private ScoredAction(CommandPaletteAction action, String providerId, int score) {
            this.action = action;
            this.providerId = providerId == null ? "" : providerId;
            this.score = score;
        }

        @Override
        public int compareTo(ScoredAction other) {
            if (other == null) {
                return 1;
            }
            int byScore = Integer.compare(score, other.score);
            if (byScore != 0) {
                return byScore;
            }

            int byPriority = Integer.compare(action.priority, other.action.priority);
            if (byPriority != 0) {
                return byPriority;
            }

            int byTitle = other.action.title.compareToIgnoreCase(action.title);
            if (byTitle != 0) {
                return byTitle;
            }

            int byProvider = other.providerId.compareToIgnoreCase(providerId);
            if (byProvider != 0) {
                return byProvider;
            }

            return other.action.id.compareToIgnoreCase(action.id);
        }
    }
}
