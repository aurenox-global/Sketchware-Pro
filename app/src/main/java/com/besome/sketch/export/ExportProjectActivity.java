package com.besome.sketch.export;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import a.a.a.KB;
import a.a.a.MA;
import a.a.a.ProjectBuilder;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.oB;
import a.a.a.wq;
import a.a.a.xq;
import a.a.a.yB;
import a.a.a.yq;
import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.CustomKeySigner;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;
import mod.hey.studios.compiler.flutter.FlutterCompilerBridge;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.compiler.bundle.AppBundleCompiler;
import mod.jbk.export.GetKeyStoreCredentialsDialog;
import mod.jbk.util.TestkeySignBridge;
import pro.sketchware.R;
import pro.sketchware.keystore.CompilePreferences;
import pro.sketchware.keystore.KeystoreStore;
import pro.sketchware.metrics.BuildMetricsStore;
import com.android.apksig.ApkVerifier;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ExportProjectActivity extends BaseAppCompatActivity {

    /**
     * Set by the design editor's compile dialog: start the build for this format without showing
     * the dialog again (the choice, including how to sign, is already remembered).
     */
    public static final String EXTRA_COMPILE_FORMAT = "compile_format";
    public static final String EXTRA_AUTO_COMPILE = "auto_compile";

    private final oB file_utility = new oB();
    /**
     * /sketchware/signed_apk
     */
    private String signed_apk_postfix;
    /**
     * /sketchware/export_src
     */
    private String export_src_postfix;
    /**
     * /sdcard/sketchware/export_src
     */
    private String export_src_full_path;
    private String export_src_filename;
    private String sc_id;
    private HashMap<String, Object> sc_metadata = null;
    private yq project_metadata = null;

    private Button sign_apk_button;
    private Button export_aab_button;
    private Button export_source_button;
    private TextView sign_apk_output_path;
    private Button export_source_send_button;
    private LinearLayout sign_apk_output_stage;
    private TextView export_source_output_path;
    private LinearLayout export_source_output_stage;
    private com.airbnb.lottie.LottieAnimationView sign_apk_loading_anim;
    private com.airbnb.lottie.LottieAnimationView export_source_loading_anim;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.export_project);

        ImageView sign_apk_ic = findViewById(R.id.sign_apk_ic);
        ImageView export_aab_ic = findViewById(R.id.export_aab_ic);
        TextView sign_apk_title = findViewById(R.id.sign_apk_title);
        sign_apk_button = findViewById(R.id.sign_apk_button);
        ImageView export_source_ic = findViewById(R.id.export_source_ic);
        TextView export_aab_title = findViewById(R.id.export_aab_title);
        export_aab_button = findViewById(R.id.export_aab_button);
        TextView export_source_title = findViewById(R.id.export_source_title);
        sign_apk_output_path = findViewById(R.id.sign_apk_output_path);
        export_source_button = findViewById(R.id.export_source_button);
        sign_apk_output_stage = findViewById(R.id.sign_apk_output_stage);
        sign_apk_loading_anim = findViewById(R.id.sign_apk_loading_anim);
        export_source_output_path = findViewById(R.id.export_source_output_path);
        export_source_send_button = findViewById(R.id.export_source_send_button);
        export_source_output_stage = findViewById(R.id.export_source_output_stage);
        export_source_loading_anim = findViewById(R.id.export_source_loading_anim);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.layout_main_logo).setVisibility(View.GONE);
        getSupportActionBar().setTitle(Helper.getResString(R.string.myprojects_export_project_actionbar_title));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        sc_metadata = lC.b(sc_id);
        project_metadata = new yq(getApplicationContext(), wq.d(sc_id), sc_metadata);

        initializeOutputDirectories();
        initializeSignApkViews();
        initializeExportSrcViews();
        initializeAppBundleExportViews();

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_AUTO_COMPILE, false)) {
            startCompileFromRememberedChoice(getIntent().getStringExtra(EXTRA_COMPILE_FORMAT));
        }
    }

    /**
     * Called when the design editor already asked what to build and how to sign it. Reuses the
     * remembered choice; if it can't be resolved anymore (a deleted keystore, for instance), the
     * normal dialog is shown instead so the user can fix it.
     */
    private void startCompileFromRememberedChoice(String formatName) {
        GetKeyStoreCredentialsDialog.Format format = formatFromName(formatName);
        if (format == null) {
            return;
        }

        GetKeyStoreCredentialsDialog.Credentials credentials = credentialsFromRememberedChoice();
        if (credentials == null && !isRememberedChoiceNotSigning()) {
            showCompileDialog(format);
            return;
        }
        startBuild(new GetKeyStoreCredentialsDialog.CompileRequest(format, credentials));
    }

    private GetKeyStoreCredentialsDialog.Format formatFromName(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        for (GetKeyStoreCredentialsDialog.Format format : GetKeyStoreCredentialsDialog.Format.values()) {
            if (format.name().equals(name)) {
                return format;
            }
        }
        return null;
    }

    private GetKeyStoreCredentialsDialog.SigningMode rememberedSigningMode() {
        String name = CompilePreferences.getSigningMode(this);
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        for (GetKeyStoreCredentialsDialog.SigningMode mode : GetKeyStoreCredentialsDialog.SigningMode.values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return null;
    }

    private boolean isRememberedChoiceNotSigning() {
        return rememberedSigningMode() == GetKeyStoreCredentialsDialog.SigningMode.DONT_SIGN;
    }

    /**
     * @return the credentials to sign with, taken from the compile dialog's last choice.
     * {@code null} both when the user chose not to sign and when the choice can't be resolved
     * (use {@link #isRememberedChoiceNotSigning()} to tell the two apart).
     */
    private GetKeyStoreCredentialsDialog.Credentials credentialsFromRememberedChoice() {
        GetKeyStoreCredentialsDialog.SigningMode mode = rememberedSigningMode();
        if (mode == null) {
            return null;
        }

        switch (mode) {
            case TESTKEY -> {
                return new GetKeyStoreCredentialsDialog.Credentials("SHA256withRSA");
            }
            case SAVED_KEY_STORE -> {
                KeystoreStore.Entry entry = KeystoreStore.find(this, CompilePreferences.getKeystoreId(this));
                if (entry == null) {
                    return null;
                }
                return new GetKeyStoreCredentialsDialog.Credentials(
                        entry.file(this).getAbsolutePath(),
                        entry.getStorePassword(),
                        entry.getAlias(),
                        entry.getKeyPassword(),
                        entry.getAlgorithm());
            }
            case CUSTOM_KEY_STORE -> {
                String path = CompilePreferences.getCustomKeystorePath(this);
                if (TextUtils.isEmpty(path) || !new File(path).exists()) {
                    return null;
                }
                String algorithm = CompilePreferences.getCustomAlgorithm(this);
                return new GetKeyStoreCredentialsDialog.Credentials(
                        path,
                        CompilePreferences.getCustomStorePassword(this),
                        CompilePreferences.getCustomAlias(this),
                        CompilePreferences.getCustomKeyPassword(this),
                        TextUtils.isEmpty(algorithm) ? "SHA256withRSA" : algorithm);
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (export_source_loading_anim.isAnimating()) {
            export_source_loading_anim.cancelAnimation();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("sc_id", sc_id);
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Sets exported signed APK file path texts' content.
     */
    private void f(String filePath) {
        sign_apk_output_stage.setVisibility(View.VISIBLE);
        sign_apk_button.setVisibility(View.GONE);
        if (sign_apk_loading_anim.isAnimating()) {
            sign_apk_loading_anim.cancelAnimation();
        }
        sign_apk_loading_anim.setVisibility(View.GONE);
        sign_apk_output_path.setText(signed_apk_postfix + File.separator + filePath);
        SketchwareUtil.toast(Helper.getResString(R.string.sign_apk_title_export_apk_file));
    }

    private void exportSrc() {
        try {
            FileUtil.deleteFile(project_metadata.projectMyscPath);

            hC hCVar = new hC(sc_id);
            kC kCVar = new kC(sc_id);
            eC eCVar = new eC(sc_id);
            iC iCVar = new iC(sc_id);
            hCVar.i();
            kCVar.s();
            eCVar.g();
            eCVar.e();
            iCVar.i();

            /* Extract project type template */
            project_metadata.a(getApplicationContext(), wq.e(xq.a(sc_id) ? "600" : sc_id));

            /* Start generating project files */
            ProjectBuilder builder = new ProjectBuilder(this, project_metadata);
            project_metadata.a(iCVar, hCVar, eCVar, yq.ExportType.ANDROID_STUDIO);
            builder.buildBuiltInLibraryInformation();
            project_metadata.b(hCVar, eCVar, iCVar, builder.getBuiltInLibraryManager());
            if (yB.a(lC.b(sc_id), "custom_icon")) {
                project_metadata.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                if (yB.a(lC.b(sc_id), "isIconAdaptive", false)) {
                    project_metadata.createLauncherIconXml("""
                            <?xml version="1.0" encoding="utf-8"?>
                            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                            <background android:drawable="@mipmap/ic_launcher_background"/>
                            <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                            <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                            </adaptive-icon>""");
                }
            }
            project_metadata.a();
            kCVar.b(project_metadata.resDirectoryPath + File.separator + "drawable-xhdpi");
            kCVar.c(project_metadata.resDirectoryPath + File.separator + "raw");
            kCVar.a(project_metadata.assetsPath + File.separator + "fonts");
            project_metadata.f();

            /* It makes no sense that those methods aren't static */
            FilePathUtil util = new FilePathUtil();
            File pathJava = new File(util.getPathJava(sc_id));
            File pathResources = new File(util.getPathResource(sc_id));
            File pathAssets = new File(util.getPathAssets(sc_id));
            File pathNativeLibraries = new File(util.getPathNativelibs(sc_id));

            if (pathJava.exists()) {
                FileUtil.copyDirectory(pathJava, new File(project_metadata.javaFilesPath + File.separator + project_metadata.packageNameAsFolders));
            }
            if (pathResources.exists()) {
                FileUtil.copyDirectory(pathResources, new File(project_metadata.resDirectoryPath));
            }
            String pathProguard = util.getPathProguard(sc_id);
            if (FileUtil.isExistFile(pathProguard)) {
                FileUtil.copyFile(pathProguard, project_metadata.proguardFilePath);
            }
            if (pathAssets.exists()) {
                FileUtil.copyDirectory(pathAssets, new File(project_metadata.assetsPath));
            }
            if (pathNativeLibraries.exists()) {
                FileUtil.copyDirectory(pathNativeLibraries, new File(project_metadata.generatedFilesPath, "jniLibs"));
            }

            ArrayList<String> toCompress = new ArrayList<>();
            toCompress.add(project_metadata.projectMyscPath);
            String exportedFilename = yB.c(sc_metadata, "my_ws_name") + ".zip";

            String exportedSourcesZipPath = wq.s() + File.separator + "export_src" + File.separator + exportedFilename;
            if (file_utility.e(exportedSourcesZipPath)) {
                file_utility.c(exportedSourcesZipPath);
            }

            ArrayList<String> toExclude = new ArrayList<>();
            if (!new File(new FilePathUtil().getPathJava(sc_id) + File.separator + "SketchApplication.java").exists()) {
                toExclude.add("SketchApplication.java");
            }
            toExclude.add("DebugActivity.java");

            new KB().a(exportedSourcesZipPath, toCompress, toExclude);
            project_metadata.e();
            runOnUiThread(() -> initializeAfterExportedSourceViews(exportedFilename));
        } catch (Exception e) {
            runOnUiThread(() -> {
                Log.e("ProjectExporter", "While trying to export project's sources: "
                        + e.getMessage(), e);
                SketchwareUtil.showAnErrorOccurredDialog(this, Log.getStackTraceString(e));
                export_source_output_stage.setVisibility(View.GONE);
                export_source_loading_anim.setVisibility(View.GONE);
                export_source_button.setVisibility(View.VISIBLE);
            });
        }
    }

    private void initializeAppBundleExportViews() {
        export_aab_button.setOnClickListener(view -> showCompileDialog(GetKeyStoreCredentialsDialog.Format.AAB));
    }

    /**
     * Initialize Export to Android Studio views
     */
    private void initializeExportSrcViews() {
        export_source_loading_anim.setVisibility(View.GONE);
        export_source_output_stage.setVisibility(View.GONE);
        export_source_button.setOnClickListener(v -> {
            export_source_button.setVisibility(View.GONE);
            export_source_output_stage.setVisibility(View.GONE);
            export_source_loading_anim.setVisibility(View.VISIBLE);
            export_source_loading_anim.playAnimation();
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    exportSrc();
                }
            }.start();
        });
        export_source_send_button.setOnClickListener(v -> shareExportedSourceCode());
    }

    /**
     * Initialize APK Export views
     */
    private void initializeSignApkViews() {
        sign_apk_loading_anim.setVisibility(View.GONE);
        sign_apk_output_stage.setVisibility(View.GONE);

        sign_apk_button.setOnClickListener(view -> showCompileDialog(GetKeyStoreCredentialsDialog.Format.APK_RELEASE));
    }

    /**
     * The unified compile dialog: pick the format and how to sign it, in one place. Both entry
     * points on this screen ("Sign APK" and "Export AAB") open it, with the format they suggest
     * pre-selected, and the user can still switch between APK release and AAB inside it.
     */
    private void showCompileDialog(GetKeyStoreCredentialsDialog.Format defaultFormat) {
        GetKeyStoreCredentialsDialog credentialsDialog = new GetKeyStoreCredentialsDialog(this,
                R.drawable.ic_mtrl_key,
                "Compile project",
                "Pick what to build and how to sign it. A saved keystore needs no password. " +
                        "The choice is remembered.",
                defaultFormat,
                new GetKeyStoreCredentialsDialog.Format[]{
                        GetKeyStoreCredentialsDialog.Format.APK_RELEASE,
                        GetKeyStoreCredentialsDialog.Format.AAB
                });
        credentialsDialog.setCompileListener(this::startBuild);
        credentialsDialog.show();
    }

    /**
     * Starts the build described by {@code request}.
     */
    private void startBuild(GetKeyStoreCredentialsDialog.CompileRequest request) {
        setBuilding(true);

        BuildingAsyncTask task = new BuildingAsyncTask(this,
                request.isAppBundle() ? yq.ExportType.AAB : yq.ExportType.SIGN_APP);
        if (request.isAppBundle()) {
            task.enableAppBundleBuild();
        }

        GetKeyStoreCredentialsDialog.Credentials credentials = request.getCredentials();
        if (credentials != null) {
            if (credentials.isForSigningWithTestkey()) {
                task.setSignWithTestkey(true);
                task.setSigningSummary("testkey");
            } else {
                task.configureResultJarSigning(
                        credentials.getKeyStorePath(),
                        credentials.getKeyStorePassword().toCharArray(),
                        credentials.getKeyAlias(),
                        credentials.getKeyPassword().toCharArray(),
                        credentials.getSigningAlgorithm()
                );
                task.setSigningSummary(describeCredentials(credentials));
            }
        } else {
            task.disableResultJarSigning();
            task.setSigningSummary("not signed");
        }
        task.execute();
    }

    /**
     * @return a human-readable name of the keystore a build is about to be signed with, shown in the
     * result dialog (a build should never be signed silently with a key the user didn't choose).
     */
    private String describeCredentials(GetKeyStoreCredentialsDialog.Credentials credentials) {
        for (KeystoreStore.Entry entry : KeystoreStore.list(this)) {
            if (entry.file(this).getAbsolutePath().equals(credentials.getKeyStorePath())) {
                return entry.getName() + " (" + entry.getAlias() + ")";
            }
        }
        return new File(credentials.getKeyStorePath()).getName() + " (" + credentials.getKeyAlias() + ")";
    }

    private void setBuilding(boolean building) {
        if (building) {
            sign_apk_button.setVisibility(View.GONE);
            sign_apk_output_stage.setVisibility(View.GONE);
            sign_apk_loading_anim.setVisibility(View.VISIBLE);
            sign_apk_loading_anim.playAnimation();
        } else {
            sign_apk_button.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Tells the user what was produced, where it is, and with which key it was signed. For an APK,
     * it also offers to install it right away.
     */
    private void showCompileResultDialog(File result, String signingSummary) {
        boolean isApk = result.getName().toLowerCase().endsWith(".apk");
        // An unsigned APK cannot be installed, so don't offer it (the system would just refuse).
        boolean canBeInstalled = isApk && !"not signed".equals(signingSummary);
        new Thread(() -> {
            String certificate = isApk ? readApkCertificate(result) : readJarCertificate(result);
            String message = result.getAbsolutePath()
                    + "\n\nSigned with: " + signingSummary
                    + (certificate.isEmpty() ? "" : "\n" + certificate);
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.open_box_48)
                    .setTitle(isApk ? "APK built" : "AAB built")
                    .setMessage(message)
                    .setPositiveButton(canBeInstalled ? "Install" : "OK", (dialog, which) -> {
                        if (canBeInstalled) {
                            installApk(result);
                        }
                    })
                    .setNegativeButton(canBeInstalled ? "Close" : null, null)
                    .show());
        }).start();
    }

    private void installApk(File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(getApplicationContext(),
                getApplicationContext().getPackageName() + ".provider", apk);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    /**
     * Reads the signed APK's signing certificate back, so the user can see which key was used.
     */
    private String readApkCertificate(File apk) {
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apk).build().verify();
            StringBuilder builder = new StringBuilder();
            for (X509Certificate certificate : result.getSignerCertificates()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("Certificate: ").append(certificate.getSubjectX500Principal().getName());
                builder.append("\nSHA-256: ").append(KeystoreStore.formatSha256(
                        MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
            }
            return builder.toString();
        } catch (Exception e) {
            return "Certificate could not be read: " + e.getMessage();
        }
    }

    /**
     * Same as {@link #readApkCertificate(File)}, for the JAR-signed .aab file.
     */
    private String readJarCertificate(File archive) {
        try (JarFile jarFile = new JarFile(archive)) {
            JarEntry entry = jarFile.getJarEntry("META-INF/CERT.RSA");
            if (entry == null) {
                return "";
            }
            try (InputStream input = jarFile.getInputStream(entry)) {
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(input);
                return "Certificate: " + certificate.getSubjectX500Principal().getName()
                        + "\nSHA-256: " + KeystoreStore.formatSha256(
                        MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            }
        } catch (Exception e) {
            return "";
        }
    }

    private void initializeOutputDirectories() {
        signed_apk_postfix = File.separator + "sketchware" + File.separator + "signed_apk";
        export_src_postfix = File.separator + "sketchware" + File.separator + "export_src";
        /* /sdcard/sketchware/signed_apk */
        String signed_apk_full_path = wq.s() + File.separator + "signed_apk";
        export_src_full_path = wq.s() + File.separator + "export_src";

        /* Check if they exist, if not, create them */
        file_utility.f(signed_apk_full_path);
        file_utility.f(export_src_full_path);
    }

    private void shareExportedSourceCode() {
        if (!export_src_filename.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("plain/text");
            intent.putExtra(Intent.EXTRA_SUBJECT, Helper.getResString(R.string.myprojects_export_src_title_email_subject, export_src_filename));
            intent.putExtra(Intent.EXTRA_TEXT, Helper.getResString(R.string.myprojects_export_src_title_email_body, export_src_filename));
            String filePath = export_src_full_path + File.separator + export_src_filename;
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", new File(filePath)));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(Intent.createChooser(intent, Helper.getResString(R.string.myprojects_export_src_chooser_title_email)));
        }
    }

    /**
     * Set content of exported source views
     */
    private void initializeAfterExportedSourceViews(String exportedSrcFilename) {
        export_src_filename = exportedSrcFilename;
        export_source_loading_anim.cancelAnimation();
        export_source_loading_anim.setVisibility(View.GONE);
        export_source_output_stage.setVisibility(View.VISIBLE);
        export_source_output_path.setText(export_src_postfix + File.separator + export_src_filename);
    }

    private static class BuildingAsyncTask extends MA implements DialogInterface.OnCancelListener, BuildProgressReceiver {
        private final WeakReference<ExportProjectActivity> activity;
        private final yq project_metadata;
        private final WeakReference<LottieAnimationView> loading_sign_apk;
        private final yq.ExportType exportType;

        private ProjectBuilder builder;
        private boolean canceled = false;
        private boolean buildingAppBundle = false;
        private String signingKeystorePath = null;
        private char[] signingKeystorePassword = null;
        private String signingAliasName = null;
        private char[] signingAliasPassword = null;
        private String signingAlgorithm = null;
        private boolean signWithTestkey = false;
        private String signingSummary = "testkey";
        private long buildStartedAtMs;
        private boolean metricsRecorded;
        private boolean failedWithError;
        private final ArrayList<BuildMetricsStore.StageDuration> stageDurations = new ArrayList<>();
        private String currentStageName;
        private long currentStageStartedAtMs;

        public BuildingAsyncTask(ExportProjectActivity exportProjectActivity, yq.ExportType exportType) {
            super(exportProjectActivity);
            this.exportType = exportType;
            activity = new WeakReference<>(exportProjectActivity);
            project_metadata = exportProjectActivity.project_metadata;
            loading_sign_apk = new WeakReference<>(exportProjectActivity.sign_apk_loading_anim);
            // Register as AsyncTask with dialog to Activity
            activity.get().addTask(this);
            // Make a simple ProgressDialog show and set its OnCancelListener
            activity.get().a((DialogInterface.OnCancelListener) this);
            // Allow user to use back button
            activity.get().progressDialog.setCancelable(false);
        }

        /**
         * a.a.a.MA's doInBackground()
         */
        @Override // a.a.a.MA
        public void b() {
            if (canceled) {
                cancel(true);
                return;
            }

            String sc_id = activity.get().sc_id;

            try {
                publishProgress("Deleting temporary files...");
                FileUtil.deleteFile(project_metadata.projectMyscPath);

                publishProgress(Helper.getResString(R.string.design_run_title_ready_to_build));
                oB oBVar = new oB();
                /* Check if /Internal storage/sketchware/signed_apk/ exists */
                if (!oBVar.e(wq.o())) {
                    /* Doesn't exist yet, let's create it */
                    oBVar.f(wq.o());
                }
                hC hCVar = new hC(sc_id);
                kC kCVar = new kC(sc_id);
                eC eCVar = new eC(sc_id);
                iC iCVar = new iC(sc_id);
                hCVar.i();
                kCVar.s();
                eCVar.g();
                eCVar.e();
                iCVar.i();
                if (canceled) {
                    cancel(true);
                    return;
                }
                File outputFile = new File(getCorrectResultFilename(project_metadata.releaseApkPath));
                if (outputFile.exists()) {
                    if (!outputFile.delete()) {
                        throw new IllegalStateException("Couldn't delete file " + outputFile.getAbsolutePath());
                    }
                }
                project_metadata.c(a);
                if (canceled) {
                    cancel(true);
                    return;
                }
                project_metadata.a(a, wq.e("600"));
                if (canceled) {
                    cancel(true);
                    return;
                }
                if (yB.a(lC.b(sc_id), "custom_icon")) {
                    project_metadata.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                    if (yB.a(lC.b(sc_id), "isIconAdaptive", false)) {
                        project_metadata.createLauncherIconXml("""
                                <?xml version="1.0" encoding="utf-8"?>
                                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                                </adaptive-icon>""");
                    } else {
                        project_metadata.a(wq.e() + File.separator + sc_id + File.separator + "icon.png");
                    }
                }
                project_metadata.a();
                kCVar.b(project_metadata.resDirectoryPath + File.separator + "drawable-xhdpi");
                kCVar.c(project_metadata.resDirectoryPath + File.separator + "raw");
                kCVar.a(project_metadata.assetsPath + File.separator + "fonts");

                builder = new ProjectBuilder(this, a, project_metadata);
                builder.setBuildAppBundle(buildingAppBundle);
                if (builder.isKmpProjectDetected()) {
                    publishProgress(builder.getKmpBuildPipelineStatusMessage());
                }

                project_metadata.a(iCVar, hCVar, eCVar, exportType);
                builder.buildBuiltInLibraryInformation();
                project_metadata.b(hCVar, eCVar, iCVar, builder.getBuiltInLibraryManager());
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Check AAPT/AAPT2 */
                publishProgress("Extracting AAPT/AAPT2 binaries...");
                builder.maybeExtractAapt2();
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Check built-in libraries */
                publishProgress("Extracting built-in libraries...");
                BuiltInLibraries.extractCompileAssets(this);
                if (canceled) {
                    cancel(true);
                    return;
                }

                builder.buildBuiltInLibraryInformation();

                /* Flutter (carril C, Fase 7): compila el Dart on-device y deja flutter_assets
                 * en yq.assetsPath antes de que AAPT2 los enlace con -A. */
                FlutterCompilerBridge.compileFlutterCodeIfPossible(activity.get(), builder);
                if (canceled) {
                    cancel(true);
                    return;
                }

                publishProgress("AAPT2 is running...");
                builder.compileResources();
                if (canceled) {
                    cancel(true);
                    return;
                }

                KotlinCompilerBridge.compileKotlinCodeIfPossible(this, builder);
                if (canceled) {
                    cancel(true);
                    return;
                }

                publishProgress("Java is compiling...");
                builder.compileJavaCode();
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Encrypt Strings in classes if enabled */
                StringfogHandler stringfogHandler = new StringfogHandler(project_metadata.sc_id);
                stringfogHandler.start(this, builder);
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Obfuscate classes if enabled */
                ProguardHandler proguardHandler = new ProguardHandler(project_metadata.sc_id);
                proguardHandler.start(this, builder);
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Create DEX file(s) */
                publishProgress(builder.getDxRunningText());
                builder.createDexFilesFromClasses();
                if (canceled) {
                    cancel(true);
                    return;
                }

                /* Merge DEX file(s) with libraries' dexes */
                publishProgress("Merging libraries' DEX files...");
                builder.getDexFilesReady();
                if (canceled) {
                    cancel(true);
                    return;
                }

                if (buildingAppBundle) {
                    AppBundleCompiler compiler = new AppBundleCompiler(builder);
                    publishProgress("Creating app module...");
                    compiler.createModuleMainArchive();
                    publishProgress("Building app bundle...");
                    compiler.buildBundle();

                    /* Sign the generated .aab file */
                    publishProgress("Signing app bundle...");

                    String createdBundlePath = AppBundleCompiler.getDefaultAppBundleOutputFile(project_metadata).getAbsolutePath();
                    String signedAppBundleDirectoryPath = FileUtil.getExternalStorageDir()
                            + File.separator + "sketchware"
                            + File.separator + "signed_aab";
                    FileUtil.makeDir(signedAppBundleDirectoryPath);
                    String outputPath = signedAppBundleDirectoryPath + File.separator +
                            Uri.fromFile(new File(createdBundlePath)).getLastPathSegment();

                    if (signWithTestkey) {
                        ZipSigner signer = new ZipSigner();
                        signer.setKeymode(ZipSigner.KEY_TESTKEY);
                        signer.signZip(createdBundlePath, outputPath);
                    } else if (isResultJarSigningEnabled()) {
                        Security.addProvider(new BouncyCastleProvider());
                        CustomKeySigner.signZip(
                                new ZipSigner(),
                                signingKeystorePath,
                                signingKeystorePassword,
                                signingAliasName,
                                signingAliasPassword,
                                signingAlgorithm,
                                createdBundlePath,
                                outputPath
                        );
                    } else {
                        FileUtil.copyFile(createdBundlePath, getCorrectResultFilename(outputPath));
                    }
                } else {
                    publishProgress("Building APK...");
                    builder.buildApk();
                    if (canceled) {
                        cancel(true);
                        return;
                    }

                    publishProgress("Aligning APK...");
                    builder.runZipalign(builder.yq.unsignedUnalignedApkPath, builder.yq.unsignedAlignedApkPath);
                    if (canceled) {
                        cancel(true);
                        return;
                    }

                    publishProgress("Signing APK...");
                    String outputLocation = getCorrectResultFilename(builder.yq.releaseApkPath);
                    if (signWithTestkey) {
                        TestkeySignBridge.signWithTestkey(builder.yq.unsignedAlignedApkPath, outputLocation);
                    } else if (isResultJarSigningEnabled()) {
                        /* Sign with apksig (v1 + v2 + v3). kellinwood's ZipSigner only produces a
                           v1 (JAR) signature, and an APK targeting Android 11+ (API 30+) signed
                           only with v1 is rejected by the platform and by apksigner:
                           "Target SDK version 34 requires a minimum of signature scheme v2". */
                        new mod.alucard.tn.apksigner.ApkSigner().signWithKeyStore(
                                builder.yq.unsignedAlignedApkPath,
                                outputLocation,
                                signingKeystorePath,
                                new String(signingKeystorePassword),
                                signingAliasName,
                                new String(signingAliasPassword),
                                null
                        );
                    } else {
                        FileUtil.copyFile(builder.yq.unsignedAlignedApkPath, outputLocation);
                    }
                }
            } catch (Throwable throwable) {
                failedWithError = true;
                if (throwable instanceof LoadKeystoreException &&
                        "Incorrect password, or integrity check failed.".equals(throwable.getMessage())) {
                    activity.get().runOnUiThread(() -> SketchwareUtil.showAnErrorOccurredDialog(activity.get(),
                            "Either an incorrect password was entered, or your key store is corrupt."));
                } else {
                    Log.e("AppExporter", throwable.getMessage(), throwable);
                    activity.get().runOnUiThread(() -> SketchwareUtil.showAnErrorOccurredDialog(activity.get(),
                            Log.getStackTraceString(throwable)));
                }

                cancel(true);
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            if (!activity.get().progressDialog.isCancelable()) {
                activity.get().progressDialog.setCancelable(true);
                activity.get().a((DialogInterface.OnCancelListener) this);
                publishProgress("Canceling process...");
                canceled = true;
            }
        }

        @Override
        public void onCancelled() {
            super.onCancelled();
            ExportProjectActivity activityRef = activity.get();
            if (activityRef != null) {
                recordMetricsIfNeeded(activityRef, false, !failedWithError);
            }
            builder = null;
            activity.get().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Dismiss the ProgressDialog
            activity.get().i();
            activity.get().sign_apk_output_stage.setVisibility(View.GONE);
            LottieAnimationView loading_sign_apk = this.loading_sign_apk.get();
            if (loading_sign_apk.isAnimating()) {
                loading_sign_apk.cancelAnimation();
            }
            loading_sign_apk.setVisibility(View.GONE);
            activity.get().sign_apk_button.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPreExecute() {
            super.onPreExecute();
            buildStartedAtMs = System.currentTimeMillis();
            metricsRecorded = false;
            failedWithError = false;
            stageDurations.clear();
            currentStageName = null;
            currentStageStartedAtMs = buildStartedAtMs;
            activity.get().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        @Override // android.os.AsyncTask
        public void onProgressUpdate(String... strArr) {
            super.onProgressUpdate(strArr);
            if (strArr.length > 0) {
                captureStageTransition(strArr[0]);
            }
            // Update the ProgressDialog's text
            activity.get().a(strArr[0]);
        }

        /**
         * a.a.a.MA's onPostExecute()
         */
        @Override // a.a.a.MA
        public void a() {
            ExportProjectActivity activityRef = activity.get();
            if (activityRef != null) {
                recordMetricsIfNeeded(activityRef, true, false);
            }
            activity.get().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Dismiss the ProgressDialog
            activity.get().i();

            File built = getBuiltResultFile();
            if (built == null) {
                return;
            }

            if (!buildingAppBundle) {
                activity.get().f(built.getName());
            }
            activity.get().showCompileResultDialog(built, signingSummary);
        }

        /**
         * Called by a.a.a.MA if doInBackground() (a.a.a.MA#b()) returned a non-empty String,
         * ergo, an error occurred.
         */
        @Override // a.a.a.MA
        public void a(String str) {
            ExportProjectActivity activityRef = activity.get();
            if (activityRef != null) {
                recordMetricsIfNeeded(activityRef, false, false);
            }
            activity.get().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Dismiss the ProgressDialog
            activity.get().i();
            SketchwareUtil.showAnErrorOccurredDialog(activity.get(), str);
            activity.get().sign_apk_output_stage.setVisibility(View.GONE);
            LottieAnimationView loading_sign_apk = this.loading_sign_apk.get();
            if (loading_sign_apk.isAnimating()) {
                loading_sign_apk.cancelAnimation();
            }
            loading_sign_apk.setVisibility(View.GONE);
            activity.get().sign_apk_button.setVisibility(View.VISIBLE);
        }

        public void enableAppBundleBuild() {
            buildingAppBundle = true;
        }

        /**
         * Named in the result dialog, so the user can see which key the build was signed with.
         */
        public void setSigningSummary(String signingSummary) {
            this.signingSummary = signingSummary;
        }

        /**
         * @return the absolute path of the file that was just built, or {@code null} if none of the
         * two expected outputs exists.
         */
        private File getBuiltResultFile() {
            if (buildingAppBundle) {
                File aab = new File(Environment.getExternalStorageDirectory(),
                        "sketchware" + File.separator + "signed_aab" + File.separator
                                + getCorrectResultFilename(project_metadata.projectName + ".aab"));
                return aab.exists() ? aab : null;
            }
            File apk = new File(getCorrectResultFilename(project_metadata.releaseApkPath));
            return apk.exists() ? apk : null;
        }

        /**
         * Configures parameters for JAR signing the result.
         * <p></p>
         * If {@link #signWithTestkey} is <code>true</code> though, the result will be signed
         * regardless of {@link #configureResultJarSigning(String, char[], String, char[], String)} and {@link #disableResultJarSigning()} calls.
         */
        public void configureResultJarSigning(String keystorePath, char[] keystorePassword, String aliasName, char[] aliasPassword, String signatureAlgorithm) {
            signingKeystorePath = keystorePath;
            signingKeystorePassword = keystorePassword;
            signingAliasName = aliasName;
            signingAliasPassword = aliasPassword;
            signingAlgorithm = signatureAlgorithm;
        }

        /**
         * Whether to sign the result with testkey or not.
         * Note that this value will always be prioritized over values set with {@link #configureResultJarSigning(String, char[], String, char[], String)}.
         */
        public void setSignWithTestkey(boolean signWithTestkey) {
            this.signWithTestkey = signWithTestkey;
        }

        /**
         * Disables JAR signing of the result. Equivalent to calling {@link #configureResultJarSigning(String, char[], String, char[], String)}
         * with <code>null</code> parameters.
         * <p></p>
         * If {@link #signWithTestkey} is <code>true</code> though, the result will be signed
         * regardless of {@link #configureResultJarSigning(String, char[], String, char[], String)} and {@link #disableResultJarSigning()} calls.
         */
        public void disableResultJarSigning() {
            signingKeystorePath = null;
            signingKeystorePassword = null;
            signingAliasName = null;
            signingAliasPassword = null;
            signingAlgorithm = null;
        }

        public boolean isResultJarSigningEnabled() {
            return signingKeystorePath != null && signingKeystorePassword != null &&
                    signingAliasName != null && signingAliasPassword != null && signingAlgorithm != null;
        }

        private String getCorrectResultFilename(String oldFormatFilename) {
            if (!isResultJarSigningEnabled() && !signWithTestkey) {
                if (buildingAppBundle) {
                    return oldFormatFilename.replace(".aab", ".unsigned.aab");
                } else {
                    return oldFormatFilename.replace("_release", "_release.unsigned");
                }
            } else {
                return oldFormatFilename;
            }
        }

        @Override
        public void onProgress(String progress, int step) {
            publishProgress(progress);
        }

        private void recordMetricsIfNeeded(ExportProjectActivity activity, boolean success, boolean canceled) {
            if (metricsRecorded) {
                return;
            }

            finalizeCurrentStage();
            long durationMs = Math.max(System.currentTimeMillis() - buildStartedAtMs, 0L);
            String buildType = buildingAppBundle ? "export_aab" : "export_apk";
            BuildMetricsStore.recordBuild(activity.getApplicationContext(), buildType, durationMs, success, canceled,
                    new ArrayList<>(stageDurations));
            metricsRecorded = true;
        }

        private void captureStageTransition(String stageName) {
            long now = System.currentTimeMillis();

            if (currentStageName == null) {
                currentStageName = stageName;
                currentStageStartedAtMs = now;
                return;
            }

            if (currentStageName.equals(stageName)) {
                return;
            }

            stageDurations.add(new BuildMetricsStore.StageDuration(currentStageName,
                    Math.max(now - currentStageStartedAtMs, 0L)));
            currentStageName = stageName;
            currentStageStartedAtMs = now;
        }

        private void finalizeCurrentStage() {
            if (currentStageName == null) {
                return;
            }

            long now = System.currentTimeMillis();
            stageDurations.add(new BuildMetricsStore.StageDuration(currentStageName,
                    Math.max(now - currentStageStartedAtMs, 0L)));
            currentStageName = null;
        }
    }
}