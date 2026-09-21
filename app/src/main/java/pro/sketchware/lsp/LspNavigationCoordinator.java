package pro.sketchware.lsp;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class LspNavigationCoordinator {

    private static final long MIN_TIMEOUT_MS = 50L;

    private final LspNavigationProvider primaryProvider;
    private final LspNavigationProvider fallbackProvider;
    private final long timeoutMs;
    private final ExecutorService executor;

    public LspNavigationCoordinator(LspNavigationProvider primaryProvider,
                                    LspNavigationProvider fallbackProvider,
                                    long timeoutMs) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.timeoutMs = Math.max(MIN_TIMEOUT_MS, timeoutMs);
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "LspNavigationWorker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public LspNavigationResult requestDefinition(LspSessionConfig config, LspNavigationRequest request) {
        return request(request, config, NavigationKind.DEFINITION);
    }

    public LspNavigationResult requestReferences(LspSessionConfig config, LspNavigationRequest request) {
        return request(request, config, NavigationKind.REFERENCES);
    }

    public void dispose() {
        executor.shutdownNow();
    }

    private LspNavigationResult request(LspNavigationRequest request,
                                        LspSessionConfig config,
                                        NavigationKind kind) {
        long startedAt = System.currentTimeMillis();

        ProviderExecution primaryExecution = executeProvider(primaryProvider, config, request, kind, timeoutMs);
        if (primaryExecution.successful && !primaryExecution.locations.isEmpty()) {
            return LspNavigationResult.success(
                    primaryExecution.locations,
                    primaryExecution.providerId,
                    System.currentTimeMillis() - startedAt,
                    false
            );
        }

        if (fallbackProvider == null) {
            return toFailureResult(primaryExecution, System.currentTimeMillis() - startedAt, false);
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        long remainingTimeout = Math.max(MIN_TIMEOUT_MS, timeoutMs - elapsed);
        ProviderExecution fallbackExecution = executeProvider(fallbackProvider, config, request, kind, remainingTimeout);
        if (fallbackExecution.successful) {
            return LspNavigationResult.success(
                    fallbackExecution.locations,
                    fallbackExecution.providerId,
                    System.currentTimeMillis() - startedAt,
                    true
            );
        }

        return toFailureResult(fallbackExecution, System.currentTimeMillis() - startedAt, true);
    }

    private static LspNavigationResult toFailureResult(ProviderExecution execution,
                                                       long durationMs,
                                                       boolean fallbackUsed) {
        return LspNavigationResult.failure(
                execution.providerId,
                durationMs,
                fallbackUsed,
                execution.timedOut,
                execution.errorMessage
        );
    }

    private ProviderExecution executeProvider(LspNavigationProvider provider,
                                              LspSessionConfig config,
                                              LspNavigationRequest request,
                                              NavigationKind kind,
                                              long timeoutMs) {
        if (provider == null) {
            return ProviderExecution.failure("", false, "Provider unavailable");
        }

        Future<List<LspNavigationLocation>> future = executor.submit(new Callable<List<LspNavigationLocation>>() {
            @Override
            public List<LspNavigationLocation> call() throws Exception {
                if (kind == NavigationKind.DEFINITION) {
                    return provider.findDefinition(config, request);
                }
                return provider.findReferences(config, request);
            }
        });

        try {
            List<LspNavigationLocation> locations = future.get(Math.max(MIN_TIMEOUT_MS, timeoutMs), TimeUnit.MILLISECONDS);
            return ProviderExecution.success(provider.id(), locations == null ? Collections.emptyList() : locations);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ProviderExecution.failure(provider.id(), true, "Navigation timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ProviderExecution.failure(provider.id(), false,
                    cause == null ? e.getMessage() : cause.getMessage());
        } catch (Exception e) {
            return ProviderExecution.failure(provider.id(), false, e.getMessage());
        }
    }

    private enum NavigationKind {
        DEFINITION,
        REFERENCES
    }

    private static final class ProviderExecution {
        private final String providerId;
        private final List<LspNavigationLocation> locations;
        private final boolean successful;
        private final boolean timedOut;
        private final String errorMessage;

        private ProviderExecution(String providerId,
                                  List<LspNavigationLocation> locations,
                                  boolean successful,
                                  boolean timedOut,
                                  String errorMessage) {
            this.providerId = providerId == null ? "" : providerId;
            this.locations = locations == null ? Collections.emptyList() : locations;
            this.successful = successful;
            this.timedOut = timedOut;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        private static ProviderExecution success(String providerId, List<LspNavigationLocation> locations) {
            return new ProviderExecution(providerId, locations, true, false, "");
        }

        private static ProviderExecution failure(String providerId, boolean timedOut, String errorMessage) {
            return new ProviderExecution(providerId, Collections.emptyList(), false, timedOut, errorMessage);
        }
    }
}
