package mod.hey.studios.build;

import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";
    public static final String SETTING_ENABLE_GRADLE_TOOLING_BRIDGE = "enable_gradle_tooling_bridge";
    public static final String SETTING_GRADLE_TOOLING_TIMEOUT_MS = "gradle_tooling_timeout_ms";
    public static final String SETTING_ENABLE_KMP_GRADLE_BRIDGE = "enable_kmp_gradle_bridge";
    public static final String SETTING_KMP_GRADLE_TIMEOUT_MS = "kmp_gradle_timeout_ms";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_1_9 = "1.9";
    public static final String SETTING_JAVA_VERSION_10 = "10";
    public static final String SETTING_JAVA_VERSION_11 = "11";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    @Override
    public String getPath() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
    }
}
