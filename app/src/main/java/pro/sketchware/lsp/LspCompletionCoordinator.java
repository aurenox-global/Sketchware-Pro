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

public final class LspCompletionCoordinator {

    private static final long MIN_TIMEOUT_MS = 50L;

    private final LspCompletionProvider primaryProvider;
    private final LspCompletionProvider fallbackProvider;
    private final long timeoutMs;
    private final ExecutorService executor;

    public LspCompletionCoordinator(LspCompletionProvider primaryProvider,
                                    LspCompletionProvider fallbackProvider,
                                    long timeoutMs) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.timeoutMs = Math.max(MIN_TIMEOUT_MS, timeoutMs);
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "LspCompletionWorker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public LspCompletionResult requestCompletions(LspSessionConfig config, LspCompletionRequest request) {
        long startedAt = System.currentTimeMillis();

        ProviderExecution primaryExecution = executeProvider(primaryProvider, config, request, timeoutMs);
        if (primaryExecution.successful && !primaryExecution.items.isEmpty()) {
            return LspCompletionResult.success(
                    primaryExecution.items,
                    primaryExecution.providerId,
                    System.currentTimeMillis() - startedAt,
                    false
            );
        }

        boolean canFallback = fallbackProvider != null;
        if (!canFallback) {
            return toFailureResult(primaryExecution, System.currentTimeMillis() - startedAt, false);
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        long remainingTimeout = Math.max(MIN_TIMEOUT_MS, timeoutMs - elapsed);
        ProviderExecution fallbackExecution = executeProvider(fallbackProvider, config, request, remainingTimeout);
        if (fallbackExecution.successful) {
            return LspCompletionResult.success(
                    fallbackExecution.items,
                    fallbackExecution.providerId,
                    System.currentTimeMillis() - startedAt,
                    true
            );
        }

        return toFailureResult(fallbackExecution, System.currentTimeMillis() - startedAt, true);
    }

    public void dispose() {
        executor.shutdownNow();
    }

    private static LspCompletionResult toFailureResult(ProviderExecution execution,
                                                       long durationMs,
                                                       boolean fallbackUsed) {
        return LspCompletionResult.failure(
                execution.providerId,
                durationMs,
                fallbackUsed,
                execution.timedOut,
                execution.errorMessage
        );
    }

    private ProviderExecution executeProvider(LspCompletionProvider provider,
                                              LspSessionConfig config,
                                              LspCompletionRequest request,
                                              long timeoutMs) {
        if (provider == null) {
            return ProviderExecution.failure("", false, "Provider unavailable");
        }

        Future<List<LspCompletionItem>> future = executor.submit(new Callable<List<LspCompletionItem>>() {
            @Override
            public List<LspCompletionItem> call() throws Exception {
                return provider.getCompletions(config, request);
            }
        });

        try {
            List<LspCompletionItem> items = future.get(Math.max(MIN_TIMEOUT_MS, timeoutMs), TimeUnit.MILLISECONDS);
            return ProviderExecution.success(provider.id(), items == null ? Collections.emptyList() : items);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ProviderExecution.failure(provider.id(), true, "Completion timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return ProviderExecution.failure(provider.id(), false,
                    cause == null ? e.getMessage() : cause.getMessage());
        } catch (Exception e) {
            return ProviderExecution.failure(provider.id(), false, e.getMessage());
        }
    }

    private static final class ProviderExecution {
        private final String providerId;
        private final List<LspCompletionItem> items;
        private final boolean successful;
        private final boolean timedOut;
        private final String errorMessage;

        private ProviderExecution(String providerId,
                                  List<LspCompletionItem> items,
                                  boolean successful,
                                  boolean timedOut,
                                  String errorMessage) {
            this.providerId = providerId == null ? "" : providerId;
            this.items = items == null ? Collections.emptyList() : items;
            this.successful = successful;
            this.timedOut = timedOut;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        private static ProviderExecution success(String providerId, List<LspCompletionItem> items) {
            return new ProviderExecution(providerId, items, true, false, "");
        }

        private static ProviderExecution failure(String providerId, boolean timedOut, String errorMessage) {
            return new ProviderExecution(providerId, Collections.emptyList(), false, timedOut, errorMessage);
        }
    }
}
