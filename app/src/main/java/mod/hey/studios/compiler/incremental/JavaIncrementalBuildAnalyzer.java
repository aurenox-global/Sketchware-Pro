package mod.hey.studios.compiler.incremental;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaIncrementalBuildAnalyzer {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final String HASH_ALGORITHM = "SHA-1";
    private static final String HASH_FILE = "java-source-hashes.properties";

    private JavaIncrementalBuildAnalyzer() {
    }

    public static AnalysisResult analyze(File javaSourceDirectory, File stateDirectory) {
        if (javaSourceDirectory == null || !javaSourceDirectory.exists()) {
            return AnalysisResult.skipped("Java source directory does not exist");
        }

        try {
            if (stateDirectory != null && !stateDirectory.exists()) {
                stateDirectory.mkdirs();
            }

            List<File> javaFiles = new ArrayList<>();
            collectJavaFiles(javaSourceDirectory, javaFiles);
            if (javaFiles.isEmpty()) {
                return AnalysisResult.skipped("No Java sources found");
            }

            Map<String, SourceNode> sourceNodes = new HashMap<>();
            Map<String, String> qualifiedNameToPath = new HashMap<>();
            Map<String, String> currentHashes = new HashMap<>();

            for (File javaFile : javaFiles) {
                String relativePath = toRelativePath(javaSourceDirectory, javaFile);
                String contents = readFile(javaFile);
                currentHashes.put(relativePath, hash(contents));

                SourceNode node = parseSource(contents);
                sourceNodes.put(relativePath, node);
                if (!node.qualifiedName.isEmpty()) {
                    qualifiedNameToPath.put(node.qualifiedName, relativePath);
                }
            }

            JavaDependencyGraph graph = new JavaDependencyGraph();
            for (String relativePath : sourceNodes.keySet()) {
                graph.addNode(relativePath);
            }

            for (Map.Entry<String, SourceNode> entry : sourceNodes.entrySet()) {
                String sourcePath = entry.getKey();
                SourceNode node = entry.getValue();

                for (String importedName : node.imports) {
                    if (importedName.endsWith(".*")) {
                        String importedPackage = importedName.substring(0, importedName.length() - 2);
                        for (Map.Entry<String, String> classEntry : qualifiedNameToPath.entrySet()) {
                            if (classEntry.getKey().startsWith(importedPackage + ".")) {
                                graph.addDependency(sourcePath, classEntry.getValue());
                            }
                        }
                        continue;
                    }

                    String dependencyPath = qualifiedNameToPath.get(importedName);
                    if (dependencyPath != null && !dependencyPath.equals(sourcePath)) {
                        graph.addDependency(sourcePath, dependencyPath);
                    }
                }
            }

            File hashFile = new File(stateDirectory, HASH_FILE);
            Map<String, String> previousHashes = loadHashes(hashFile);

            Set<String> changedSources = new HashSet<>();
            for (Map.Entry<String, String> currentEntry : currentHashes.entrySet()) {
                String previousHash = previousHashes.get(currentEntry.getKey());
                if (previousHash == null || !previousHash.equals(currentEntry.getValue())) {
                    changedSources.add(currentEntry.getKey());
                }
            }

            for (String previousPath : previousHashes.keySet()) {
                if (!currentHashes.containsKey(previousPath)) {
                    changedSources.add(previousPath);
                }
            }

            Set<String> impactedSources = graph.collectDependents(changedSources);
            saveHashes(hashFile, currentHashes);

            return AnalysisResult.success(
                    javaFiles.size(),
                    graph.getEdgeCount(),
                    changedSources,
                    impactedSources
            );
        } catch (Exception e) {
            return AnalysisResult.failed(e.getMessage());
        }
    }

    private static SourceNode parseSource(String contents) {
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(contents);
        String packageName = packageMatcher.find() ? packageMatcher.group(1).trim() : "";

        Matcher typeMatcher = TYPE_PATTERN.matcher(contents);
        String simpleTypeName = typeMatcher.find() ? typeMatcher.group(2).trim() : "";

        String qualifiedName = simpleTypeName.isEmpty()
                ? ""
                : (packageName.isEmpty() ? simpleTypeName : packageName + "." + simpleTypeName);

        Set<String> imports = new HashSet<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(contents);
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1).trim());
        }

        return new SourceNode(qualifiedName, imports);
    }

    private static void collectJavaFiles(File directory, List<File> result) {
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectJavaFiles(child, result);
            } else if (child.getName().endsWith(".java")) {
                result.add(child);
            }
        }
    }

    private static Map<String, String> loadHashes(File hashFile) throws IOException {
        if (!hashFile.exists()) {
            return Collections.emptyMap();
        }

        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(hashFile)) {
            properties.load(inputStream);
        }

        Map<String, String> hashes = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            hashes.put(key, properties.getProperty(key, ""));
        }
        return hashes;
    }

    private static void saveHashes(File hashFile, Map<String, String> hashes) throws IOException {
        List<String> keys = new ArrayList<>(hashes.keySet());
        Collections.sort(keys);

        Properties properties = new Properties();
        for (String key : keys) {
            properties.setProperty(key, hashes.get(key));
        }

        try (FileOutputStream outputStream = new FileOutputStream(hashFile, false)) {
            properties.store(outputStream, "Sketchware incremental Java source hashes");
        }
    }

    private static String toRelativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) {
            return file.getName();
        }

        String relative = filePath.substring(rootPath.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative.replace(File.separatorChar, '/');
    }

    private static String readFile(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[8192];
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        }
        return builder.toString();
    }

    private static String hash(String text) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] result = digest.digest(text.getBytes(StandardCharsets.UTF_8));

        StringBuilder builder = new StringBuilder(result.length * 2);
        for (byte value : result) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }

    private static final class SourceNode {
        private final String qualifiedName;
        private final Set<String> imports;

        private SourceNode(String qualifiedName, Set<String> imports) {
            this.qualifiedName = qualifiedName;
            this.imports = imports;
        }
    }

    public static final class AnalysisResult {
        public final boolean executed;
        public final boolean successful;
        public final String message;
        public final int totalSources;
        public final int dependencyEdges;
        public final Set<String> changedSources;
        public final Set<String> impactedSources;

        private AnalysisResult(boolean executed,
                               boolean successful,
                               String message,
                               int totalSources,
                               int dependencyEdges,
                               Set<String> changedSources,
                               Set<String> impactedSources) {
            this.executed = executed;
            this.successful = successful;
            this.message = message;
            this.totalSources = totalSources;
            this.dependencyEdges = dependencyEdges;
            this.changedSources = changedSources;
            this.impactedSources = impactedSources;
        }

        private static AnalysisResult skipped(String reason) {
            return new AnalysisResult(false, true, reason, 0, 0, Collections.emptySet(), Collections.emptySet());
        }

        private static AnalysisResult failed(String reason) {
            return new AnalysisResult(true, false, reason, 0, 0, Collections.emptySet(), Collections.emptySet());
        }

        private static AnalysisResult success(int totalSources,
                                              int dependencyEdges,
                                              Set<String> changedSources,
                                              Set<String> impactedSources) {
            Set<String> changedCopy = Collections.unmodifiableSet(new HashSet<>(changedSources));
            Set<String> impactedCopy = Collections.unmodifiableSet(new HashSet<>(impactedSources));
            return new AnalysisResult(true, true, "ok", totalSources, dependencyEdges, changedCopy, impactedCopy);
        }

        public String toLogLine() {
            if (!executed) {
                return "Incremental Java graph skipped: " + message;
            }
            if (!successful) {
                return "Incremental Java graph failed: " + message;
            }

            int changedCount = changedSources.size();
            int impactedCount = impactedSources.size();
            String profile = (changedCount > 0 && impactedCount < totalSources) ? "incremental-candidate" : "cold-candidate";
            return "Incremental Java graph -> sources=" + totalSources
                    + ", edges=" + dependencyEdges
                    + ", changed=" + changedCount
                    + ", impacted=" + impactedCount
                    + ", profile=" + profile;
        }
    }
}
