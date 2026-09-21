package com.besome.sketch.lib.base;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.ui.LoadingDialog;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;

import a.a.a.MA;
import a.a.a.lC;
import dev.chrisbanes.insetter.Insetter;
import pro.sketchware.ui.layout.AdaptiveLayoutPolicy;
import pro.sketchware.ui.layout.AdaptiveLayoutSnapshot;
import pro.sketchware.dialogs.ProgressDialog;

public abstract class BaseAppCompatActivity extends AppCompatActivity {

    public FirebaseAnalytics mAnalytics;

    @Deprecated
    public Context e;
    public Activity parent;
    protected ProgressDialog progressDialog;
    private LoadingDialog lottieDialog;
    private ArrayList<MA> taskList;
    private AdaptiveLayoutSnapshot adaptiveLayoutSnapshot;

    public void a(MA var1) {
        taskList.add(var1);
    }

    public void addTask(MA task) {
        taskList.add(task);
    }

    public void a(OnCancelListener cancelListener) {
        if (progressDialog != null && !progressDialog.isShowing()) {
            progressDialog.setOnCancelListener(cancelListener);
            progressDialog.show();
        }
    }

    public void a(String var1) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(var1);
        }
    }

    public void g() {
        for (MA task : taskList) {
            if (task.getStatus() != Status.FINISHED && !task.isCancelled()) {
                task.cancel(true);
            }
        }
        taskList.clear();
    }

    public void h() {
        try {
            if (lottieDialog != null && lottieDialog.isShowing()) {
                lottieDialog.dismiss();
            }
        } catch (Exception var2) {
            lottieDialog = null;
            lottieDialog = new LoadingDialog(this);
        }
    }

    public void i() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (Exception var2) {
            progressDialog = null;
            progressDialog = new ProgressDialog(this);
        }

    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 0 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == 0;
    }

    public boolean j() {
        return isStoragePermissionGranted();
    }

    public void k() {
        if (lottieDialog != null && !lottieDialog.isShowing() && !isFinishing()) {
            lottieDialog.show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        e = getApplicationContext();
        taskList = new ArrayList<>();
        lottieDialog = new LoadingDialog(this);
        lC.a(getApplicationContext(), false);
        progressDialog = new ProgressDialog(this);
        mAnalytics = FirebaseAnalytics.getInstance(this);
        refreshAdaptiveLayoutPolicy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshAdaptiveLayoutPolicy();
    }

    @Override
    public void onDestroy() {
        g();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.cancelAnimation();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.pauseAnimation();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (lottieDialog != null && lottieDialog.isShowing()) {
            lottieDialog.resumeAnimation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (parent != null) {
            return parent.onCreateOptionsMenu(menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (parent != null) {
            return parent.onOptionsItemSelected(item);
        }
        return false;
    }

    public void handleInsetts(View root) {
        Insetter.builder()
                .padding(WindowInsetsCompat.Type.navigationBars())
                .applyToView(root);
    }

    protected void enableEdgeToEdgeNoContrast() {
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ may force edge-to-edge for targetSdk 35; keep legacy insets behavior.
            WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
            }
            return;
        }

        SystemBarStyle systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(this, systemBarStyle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    protected final void refreshAdaptiveLayoutPolicy() {
        adaptiveLayoutSnapshot = AdaptiveLayoutPolicy.fromContext(this);
        onAdaptiveLayoutChanged(adaptiveLayoutSnapshot);
    }

    protected final AdaptiveLayoutSnapshot getAdaptiveLayoutSnapshot() {
        if (adaptiveLayoutSnapshot == null) {
            adaptiveLayoutSnapshot = AdaptiveLayoutPolicy.fromContext(this);
        }
        return adaptiveLayoutSnapshot;
    }

    protected void onAdaptiveLayoutChanged(@NonNull AdaptiveLayoutSnapshot snapshot) {
        // Subclasses can override to adapt spacing, panes, and content width per size class.
    }
}