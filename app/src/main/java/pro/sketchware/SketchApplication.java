package pro.sketchware;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.besome.sketch.tools.CollectErrorActivity;

import dev.aldi.sayuti.block.MyBlockDefaultsInstaller;
import pro.sketchware.ai.rag.LocalAiRagRegistry;
import pro.sketchware.featureflags.FeatureFlags;
import pro.sketchware.metrics.StartupPerformanceTracker;
import pro.sketchware.utility.theme.ThemeManager;

public class SketchApplication extends Application {
    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        Thread.UncaughtExceptionHandler existingHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("error", Log.getStackTraceString(throwable));
                startActivity(intent);
                if (existingHandler != null) {
                    existingHandler.uncaughtException(thread, throwable);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(1);
                }
            }
        });
        super.onCreate();
        FeatureFlags.applyDefaultsIfMissing(this);
        MyBlockDefaultsInstaller.installIfNeeded(this);
        ThemeManager.applyTheme(this, ThemeManager.getCurrentTheme(this));
        StartupPerformanceTracker.markApplicationCreated(this);
        LocalAiRagRegistry.getInstance().initialize(this);
    }
}
