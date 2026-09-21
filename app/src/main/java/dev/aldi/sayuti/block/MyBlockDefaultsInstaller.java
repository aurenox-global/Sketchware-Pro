package dev.aldi.sayuti.block;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import pro.sketchware.utility.FileUtil;

public final class MyBlockDefaultsInstaller {
    private static final String TAG = "MyBlockDefaultsInstaller";
    private static final String ASSET_BASE_PATH = "default-my-block";
    private static final String ASSET_BASE_PATH_V2 = "default-my-block-2";
    private static final File MENU_FILE = new File(
            Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/My Block/menu.json"
    );

    private MyBlockDefaultsInstaller() {
    }

    public static void installIfNeeded(Context context) {
        if (context == null) {
            return;
        }

        installInternal(context, false);
    }

    public static void forceInstallDefaults(Context context) {
        if (context == null) {
            return;
        }

        installInternal(context, true);
    }

    private static void installInternal(Context context, boolean force) {
        String mergedBlocks = mergeJsonArrays(
                readAssetText(context, ASSET_BASE_PATH + "/block.json"),
                readAssetText(context, ASSET_BASE_PATH_V2 + "/block.json")
        );
        String mergedPalettes = mergeJsonArrays(
                readAssetText(context, ASSET_BASE_PATH + "/palette.json"),
                readAssetText(context, ASSET_BASE_PATH_V2 + "/palette.json")
        );
        String menu = normalizeJsonContent(readAssetText(context, ASSET_BASE_PATH + "/menu.json"), "[]");

        installSingleDefault(ExtraBlockFile.EXTRA_BLOCKS_DATA_FILE, mergedBlocks, force);
        installSingleDefault(ExtraBlockFile.EXTRA_BLOCKS_PALETTE_FILE, mergedPalettes, force);
        installSingleDefault(MENU_FILE, menu, force);
    }

    private static void installSingleDefault(File targetFile, String content, boolean force) {
        try {
            if (!force && isUsableJsonFile(targetFile)) {
                return;
            }

            String normalized = normalizeJsonContent(content, "[]");

            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileUtil.writeFile(targetFile.getAbsolutePath(), normalized);
        } catch (Exception e) {
            Log.w(TAG, "Failed to install default My Block file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static boolean isUsableJsonFile(File file) {
        return file != null && file.exists() && file.length() > 2L;
    }

    private static String readAssetText(Context context, String assetPath) {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(assetPath);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String mergeJsonArrays(String firstRaw, String secondRaw) {
        String first = normalizeJsonArray(firstRaw);
        String second = normalizeJsonArray(secondRaw);

        String firstInner = first.substring(1, first.length() - 1).trim();
        String secondInner = second.substring(1, second.length() - 1).trim();

        if (firstInner.isEmpty()) {
            return second;
        }
        if (secondInner.isEmpty()) {
            return first;
        }
        return "[" + firstInner + "," + secondInner + "]";
    }

    private static String normalizeJsonArray(String raw) {
        String normalized = normalizeJsonContent(raw, "[]").trim();
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            return "[]";
        }
        return normalized;
    }

    private static String normalizeJsonContent(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}