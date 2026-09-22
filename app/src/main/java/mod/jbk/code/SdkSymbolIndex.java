package mod.jbk.code;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import a.a.a.Jp;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Indice de los nombres de clase disponibles para el autocompletado, aparte de los del propio
 * proyecto: los del SDK de Android (android.jar) y los de las librerias que usa el proyecto.
 *
 * <p>Solo se indexan <b>nombres de clase</b> (no sus miembros: serian cientos de miles de entradas y
 * no caben en memoria de forma razonable en un movil). Se lee el jar una vez y el resultado se
 * cachea en memoria y en disco, invalidandose cuando el jar cambia.
 */
public final class SdkSymbolIndex {

    private static final int MAX_SDK_SYMBOLS = 20000;
    private static final int MAX_LIBRARY_SYMBOLS = 12000;

    private static final Map<String, List<String>> memoryCache = new HashMap<>();

    private SdkSymbolIndex() {
    }

    /** Nombres de clase del SDK de Android (android.jar), cacheados. */
    public static List<String> getSdkClasses(Context context) {
        File androidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        return cached(context, "sdk", Collections.singletonList(androidJar), MAX_SDK_SYMBOLS);
    }

    /** Nombres de clase de las librerias que usa el proyecto mas las locales que anada el usuario. */
    public static List<String> getLibraryClasses(Context context, String scId) {
        if (scId == null || scId.isEmpty()) {
            return Collections.emptyList();
        }
        List<File> jars = new ArrayList<>();

        try {
            for (Jp library : new BuiltInLibraryManager(scId).getLibraries()) {
                if (library == null || library.getName() == null) {
                    continue;
                }
                File jar = new File(BuiltInLibraries.getLibraryClassesJarPathString(library.getName()));
                if (jar.isFile()) {
                    jars.add(jar);
                }
            }
        } catch (Throwable ignored) {
            // Sin librerias integradas: seguimos con las locales.
        }

        try {
            File localLibs = new File(new FilePathUtil().getJarPathLocalLibraryUser(scId));
            File[] files = localLibs.isDirectory() ? localLibs.listFiles() : null;
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        jars.add(file);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Idem.
        }

        if (jars.isEmpty()) {
            return Collections.emptyList();
        }
        return cached(context, "libs-" + scId, jars, MAX_LIBRARY_SYMBOLS);
    }

    /** Lee (o reutiliza) el indice de los jars indicados. */
    private static synchronized List<String> cached(Context context, String key, List<File> sources, int limit) {
        if (context == null) {
            return Collections.emptyList();
        }

        long stamp = 0L;
        for (File source : sources) {
            stamp = Math.max(stamp, source.lastModified());
        }

        String cacheKey = key + "@" + stamp;
        List<String> inMemory = memoryCache.get(cacheKey);
        if (inMemory != null) {
            return inMemory;
        }

        File cacheFile = new File(new File(context.getCacheDir(), "ide-symbols"), key + ".txt");
        List<String> names = readCache(cacheFile, stamp);
        if (names == null) {
            names = new ArrayList<>();
            for (File source : sources) {
                if (names.size() >= limit) {
                    break;
                }
                readJar(source, names, limit);
            }
            writeCache(cacheFile, stamp, names);
        }

        memoryCache.put(cacheKey, names);
        return names;
    }

    private static void readJar(File jar, List<String> out, int limit) {
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && out.size() < limit) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.contains("$")) {
                    continue;
                }
                out.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
            }
        } catch (Throwable ignored) {
            // Jar ilegible: se ignora.
        }
    }

    private static List<String> readCache(File cacheFile, long stamp) {
        try {
            if (!cacheFile.isFile() || cacheFile.lastModified() < stamp) {
                return null;
            }
            String content = FileUtil.readFile(cacheFile.getAbsolutePath());
            if (content == null || content.isEmpty()) {
                return null;
            }
            String[] lines = content.split("\n");
            List<String> names = new ArrayList<>(lines.length);
            for (String line : lines) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    names.add(value);
                }
            }
            return names.isEmpty() ? null : names;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeCache(File cacheFile, long stamp, List<String> names) {
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            StringBuilder builder = new StringBuilder(names.size() * 24);
            builder.append("# ").append(stamp).append('\n');
            for (String name : names) {
                builder.append(name).append('\n');
            }
            FileUtil.writeFile(cacheFile.getAbsolutePath(), builder.toString());
        } catch (Throwable ignored) {
            // Sin cache en disco: se volvera a leer el jar la proxima vez.
        }
    }
}
