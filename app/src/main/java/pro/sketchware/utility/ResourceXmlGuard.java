package pro.sketchware.utility;

import java.io.File;
import java.util.List;

import mod.jbk.util.LogUtil;

/**
 * Guards AAPT2 against resource XML files that have no root element.
 * <p>
 * A resource file containing only the XML declaration
 * ({@code <?xml version="1.0" encoding="utf-8"?>}) has no root element.
 * Expat/AAPT2 then aborts the whole build with the cryptic message
 * {@code error: no element found} / {@code error: file failed to compile},
 * which does not tell the user which file is at fault.
 * <p>
 * Before invoking AAPT2, {@link #skipXmlResourcesWithoutRootElement(File)} moves such files
 * to a sibling quarantine directory ({@value #QUARANTINE_DIR_NAME}) and logs a clear warning,
 * so a single stray file cannot break the entire build.
 */
public final class ResourceXmlGuard {

    private static final String TAG = "ResourceXmlGuard";

    /**
     * Name of the directory the offending files are moved to. It is a sibling of the scanned
     * resource directory (and therefore not seen by AAPT2).
     */
    public static final String QUARANTINE_DIR_NAME = ".invalid-xml-skipped";

    private ResourceXmlGuard() {
    }

    /**
     * @return Whether {@code content} contains an XML root element (start tag).
     * Skipped over: UTF-8 BOM, XML declarations, comments and doctype declarations.
     */
    public static boolean hasRootElement(String content) {
        if (content == null) {
            return false;
        }

        String s = content;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }

        while (true) {
            s = s.trim();
            if (s.startsWith("<?")) {
                int end = s.indexOf("?>");
                if (end < 0) {
                    return false;
                }
                s = s.substring(end + 2);
            } else if (s.startsWith("<!--")) {
                int end = s.indexOf("-->");
                if (end < 0) {
                    return false;
                }
                s = s.substring(end + 3);
            } else if (s.startsWith("<!")) {
                int end = s.indexOf('>');
                if (end < 0) {
                    return false;
                }
                s = s.substring(end + 1);
            } else {
                break;
            }
        }

        if (s.length() < 2 || s.charAt(0) != '<') {
            return false;
        }
        char c = s.charAt(1);
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    /**
     * Scans {@code resourcesDirectory} recursively; every {@code .xml} file without a root element
     * is moved to {@code <parent of resourcesDirectory>/{@value #QUARANTINE_DIR_NAME}/<relative path>}
     * and a warning is logged.
     *
     * @return The amount of skipped (quarantined) files.
     */
    public static int skipXmlResourcesWithoutRootElement(File resourcesDirectory) {
        if (resourcesDirectory == null || !resourcesDirectory.isDirectory()) {
            return 0;
        }

        int skipped = 0;
        List<File> xmlFiles = FileUtil.listFilesRecursively(resourcesDirectory, ".xml");
        for (File xmlFile : xmlFiles) {
            String content = FileUtil.readFileIfExist(xmlFile.getAbsolutePath());
            if (hasRootElement(content)) {
                continue;
            }

            File quarantined = quarantine(resourcesDirectory, xmlFile);
            if (quarantined != null) {
                LogUtil.w(TAG, "aviso: se omite " + quarantined.getAbsolutePath()
                        + " (XML sin elemento raiz); borralo o ponle contenido valido");
                skipped++;
            } else {
                LogUtil.e(TAG, "No se pudo aislar " + xmlFile.getAbsolutePath()
                        + " (XML sin elemento raiz); puede que AAPT2 falle");
            }
        }
        return skipped;
    }

    private static File quarantine(File resourcesDirectory, File xmlFile) {
        File parent = resourcesDirectory.getParentFile();
        if (parent == null) {
            return null;
        }

        String relativePath = resourcesDirectory.toURI().relativize(xmlFile.toURI()).getPath();
        if (relativePath.isEmpty()) {
            relativePath = xmlFile.getName();
        }

        File destination = new File(new File(parent, QUARANTINE_DIR_NAME), relativePath);
        if (destination.exists()) {
            destination = new File(destination.getParentFile(), System.currentTimeMillis() + "-" + destination.getName());
        }
        File destinationParent = destination.getParentFile();
        if (destinationParent != null && !destinationParent.exists()) {
            FileUtil.makeDir(destinationParent.getAbsolutePath());
        }
        if (destinationParent == null || !destinationParent.exists()) {
            return null;
        }

        FileUtil.moveFile(xmlFile.getAbsolutePath(), destination.getAbsolutePath());
        return destination.exists() ? destination : null;
    }
}
