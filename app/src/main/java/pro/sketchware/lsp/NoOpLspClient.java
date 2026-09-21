package pro.sketchware.lsp;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import pro.sketchware.metrics.EditorPerformanceMetricsStore;

public final class NoOpLspClient implements LspClient {

    private static final long DEFAULT_COMPLETION_TIMEOUT_MS = 120L;
    private static final long DEFAULT_NAVIGATION_TIMEOUT_MS = 140L;
    private final Context appContext;

    public NoOpLspClient(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public LspDocumentSession createSession(LspSessionConfig config) {
        return new NoOpLspSession(config, appContext);
    }

    private static final class NoOpLspSession implements LspDocumentSession {
        private final LspSessionConfig config;
        private final Context appContext;
        private final LspDiagnosticsStream diagnosticsStream = new LspDiagnosticsStream();
        private final LspCompletionCoordinator completionCoordinator = new LspCompletionCoordinator(
                new NoOpCompletionProvider(),
                new KeywordCompletionProvider(),
                DEFAULT_COMPLETION_TIMEOUT_MS
        );
        private final LspNavigationCoordinator navigationCoordinator = new LspNavigationCoordinator(
            new NoOpNavigationProvider(),
            new LocalSymbolNavigationProvider(),
            DEFAULT_NAVIGATION_TIMEOUT_MS
        );
        private String documentText = "";

        private NoOpLspSession(LspSessionConfig config, Context appContext) {
            this.config = config;
            this.appContext = appContext;
        }

        @Override
        public LspDiagnosticsStream diagnostics() {
            return diagnosticsStream;
        }

        @Override
        public LspCompletionResult requestCompletions(LspCompletionRequest request) {
            LspCompletionRequest normalizedRequest;
            if (request == null) {
                normalizedRequest = new LspCompletionRequest(documentText, "", 0, 0);
            } else {
                normalizedRequest = request.ensureDocumentText(documentText);
            }
            LspCompletionResult result = completionCoordinator.requestCompletions(config, normalizedRequest);
            recordMetric(EditorPerformanceMetricsStore.OP_COMPLETION, result.durationMs);
            return result;
        }

        @Override
        public LspNavigationResult requestDefinition(LspNavigationRequest request) {
            LspNavigationRequest normalizedRequest;
            if (request == null) {
                normalizedRequest = new LspNavigationRequest(documentText, "", 0, 0);
            } else {
                normalizedRequest = request.ensureDocumentText(documentText);
            }
            LspNavigationResult result = navigationCoordinator.requestDefinition(config, normalizedRequest);
            recordMetric(EditorPerformanceMetricsStore.OP_DEFINITION, result.durationMs);
            return result;
        }

        @Override
        public LspNavigationResult requestReferences(LspNavigationRequest request) {
            LspNavigationRequest normalizedRequest;
            if (request == null) {
                normalizedRequest = new LspNavigationRequest(documentText, "", 0, 0);
            } else {
                normalizedRequest = request.ensureDocumentText(documentText);
            }
            LspNavigationResult result = navigationCoordinator.requestReferences(config, normalizedRequest);
            recordMetric(EditorPerformanceMetricsStore.OP_REFERENCES, result.durationMs);
            return result;
        }

        @Override
        public void openDocument(String documentText) {
            this.documentText = documentText == null ? "" : documentText;
            publishDiagnosticsWithTiming();
        }

        @Override
        public void updateDocument(String documentText) {
            this.documentText = documentText == null ? "" : documentText;
            publishDiagnosticsWithTiming();
        }

        @Override
        public void closeDocument() {
            documentText = "";
            diagnosticsStream.publish(Collections.emptyList());
        }

        @Override
        public void dispose() {
            completionCoordinator.dispose();
            navigationCoordinator.dispose();
            closeDocument();
        }

        private void publishDiagnosticsWithTiming() {
            long startedAt = System.currentTimeMillis();
            List<LspDiagnostic> diagnostics = LocalDiagnosticsAnalyzer.analyze(config.language, this.documentText);
            long durationMs = Math.max(0L, System.currentTimeMillis() - startedAt);
            recordMetric(EditorPerformanceMetricsStore.OP_DIAGNOSTICS_ANALYZE, durationMs);
            diagnosticsStream.publish(diagnostics);
        }

        private void recordMetric(String operation, long durationMs) {
            if (appContext == null) {
                return;
            }
            EditorPerformanceMetricsStore.recordSample(appContext, operation, durationMs);
        }
    }
}
