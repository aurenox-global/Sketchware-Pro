package pro.sketchware.plugins.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginManifestValidationResult {

    public final PluginManifest manifest;
    public final long validatedAtMs;
    public final List<PluginManifestIssue> issues;

    public PluginManifestValidationResult(PluginManifest manifest,
                                          long validatedAtMs,
                                          List<PluginManifestIssue> issues) {
        this.manifest = manifest;
        this.validatedAtMs = Math.max(validatedAtMs, 0L);
        this.issues = Collections.unmodifiableList(new ArrayList<>(
                issues == null ? Collections.emptyList() : issues
        ));
    }

    public boolean hasErrors() {
        for (PluginManifestIssue issue : issues) {
            if (issue != null && issue.severity.isError()) {
                return true;
            }
        }
        return manifest == null;
    }
}
