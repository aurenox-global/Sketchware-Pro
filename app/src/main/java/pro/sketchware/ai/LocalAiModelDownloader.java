package pro.sketchware.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import pro.sketchware.utility.FileUtil;

public class LocalAiModelDownloader {
    public interface Callback {
        void onProgress(long downloadedBytes, long totalBytes);

        void onSuccess(File downloadedFile);

        void onError(Throwable throwable);
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final AtomicReference<Call> activeCall = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void download(String url, File targetFile, Callback callback) {
        File modelsDirectory = LocalAiConfig.getModelsDirectory();
        if (!modelsDirectory.exists() && !modelsDirectory.mkdirs()) {
            callback.onError(new IOException("Couldn't create " + modelsDirectory.getAbsolutePath()));
            return;
        }

        String targetName = targetFile.getName();
        File temporaryTarget = new File(modelsDirectory, targetName + ".part");
        File finalTarget = new File(modelsDirectory, targetName);

        cancelled.set(false);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "SketchwarePro-LocalAI/7.0.4")
                .build();

        Call call = httpClient.newCall(request);
        activeCall.set(call);

        new Thread(() -> {
            long downloadedBytes = 0L;
            try (Response response = call.execute()) {
                if (cancelled.get()) {
                    FileUtil.deleteFile(temporaryTarget.getAbsolutePath());
                    callback.onError(new IOException("Download cancelled."));
                    return;
                }
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Download failed with status " + response.code()));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    callback.onError(new IOException("Empty download response."));
                    return;
                }

                long totalBytes = body.contentLength();
                try (InputStream inputStream = body.byteStream();
                     FileOutputStream outputStream = new FileOutputStream(temporaryTarget, false)) {
                    byte[] buffer = new byte[1024 * 256];
                    int read;
                    while ((read = inputStream.read(buffer)) > 0) {
                        if (cancelled.get()) {
                            outputStream.close();
                            FileUtil.deleteFile(temporaryTarget.getAbsolutePath());
                            callback.onError(new IOException("Download cancelled."));
                            return;
                        }
                        outputStream.write(buffer, 0, read);
                        downloadedBytes += read;
                        callback.onProgress(downloadedBytes, totalBytes);
                    }
                }

                if (!temporaryTarget.renameTo(finalTarget)) {
                    FileUtil.deleteFile(temporaryTarget.getAbsolutePath());
                    callback.onError(new IOException("Couldn't move downloaded file to " + finalTarget.getAbsolutePath()));
                    return;
                }
                callback.onSuccess(finalTarget);
            } catch (Throwable throwable) {
                FileUtil.deleteFile(temporaryTarget.getAbsolutePath());
                if (cancelled.get()) {
                    callback.onError(new IOException("Download cancelled."));
                } else {
                    callback.onError(throwable);
                }
            } finally {
                activeCall.set(null);
            }
        }, "local-ai-model-download").start();
    }

    public void cancel() {
        cancelled.set(true);
        Call call = activeCall.get();
        if (call != null) {
            call.cancel();
        }
    }

    public boolean isDownloading() {
        return activeCall.get() != null;
    }
}
