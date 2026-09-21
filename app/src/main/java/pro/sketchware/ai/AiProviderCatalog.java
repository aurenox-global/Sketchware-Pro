package pro.sketchware.ai;

import java.util.ArrayList;
import java.util.List;

public final class AiProviderCatalog {
    private AiProviderCatalog() {
    }

    public static String[] getCloudProviderIds() {
        return LocalAiConfig.getCloudProviderIds();
    }

    public static String[] getCloudProviderLabels() {
        String[] ids = getCloudProviderIds();
        String[] labels = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            labels[i] = LocalAiConfig.getProviderDisplayName(ids[i]);
        }
        return labels;
    }

    public static String getProviderLabel(String providerId) {
        return LocalAiConfig.getProviderDisplayName(providerId);
    }

    public static String getProviderIdFromLabel(String label) {
        String normalizedLabel = label == null ? "" : label.trim();
        for (String providerId : getCloudProviderIds()) {
            String display = LocalAiConfig.getProviderDisplayName(providerId);
            if (display.equalsIgnoreCase(normalizedLabel)) {
                return providerId;
            }
        }
        return LocalAiConfig.PROVIDER_DEEPSEEK;
    }

    public static List<String> getCloudProviderLabelList() {
        List<String> labels = new ArrayList<>();
        for (String id : getCloudProviderIds()) {
            labels.add(LocalAiConfig.getProviderDisplayName(id));
        }
        return labels;
    }
}
