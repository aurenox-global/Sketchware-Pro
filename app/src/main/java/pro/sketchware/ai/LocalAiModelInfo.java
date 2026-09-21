package pro.sketchware.ai;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.utility.FileUtil;

public class LocalAiModelInfo {
    private static final long MAX_CONTEXT_LIMIT = 262144L;
    private static final long FALLBACK_CONTEXT = 32768L;

    private final File file;
    private final int ggufVersion;
    private final Map<String, Long> metadata = new HashMap<>();
    private final String architecture;

    private LocalAiModelInfo(File file, int ggufVersion, Map<String, Long> metadata, String architecture) {
        this.file = file;
        this.ggufVersion = ggufVersion;
        this.metadata.putAll(metadata);
        this.architecture = architecture == null ? "" : architecture;
    }

    public static LocalAiModelInfo fromPath(String path) throws LocalAiException {
        if (path == null || path.trim().isEmpty()) {
            throw new LocalAiException("Select a .gguf model first.");
        }

        File file = new File(path);
        if (!file.isFile()) {
            throw new LocalAiException("Model file doesn't exist: " + path);
        }

        if (!file.getName().toLowerCase(Locale.US).endsWith(".gguf")) {
            throw new LocalAiException("Only .gguf models are supported.");
        }

        byte[] header = new byte[8];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read = inputStream.read(header);
            if (read < header.length) {
                throw new LocalAiException("The selected file is too small to be a GGUF model.");
            }
        } catch (IOException e) {
            throw new LocalAiException("Couldn't read model header.", e);
        }

        if (header[0] != 'G' || header[1] != 'G' || header[2] != 'U' || header[3] != 'F') {
            throw new LocalAiException("The selected file doesn't look like a GGUF model.");
        }

        int version = (header[4] & 0xff)
                | ((header[5] & 0xff) << 8)
                | ((header[6] & 0xff) << 16)
                | ((header[7] & 0xff) << 24);
        String[] archHolder = new String[1];
        Map<String, Long> metadata = readMetadata(file, archHolder);
        return new LocalAiModelInfo(file, version, metadata, archHolder[0]);
    }

    private static Map<String, Long> readMetadata(File file, String[] archHolder) {
        Map<String, Long> result = new HashMap<>();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file), 256 * 1024);
             DataInputStream data = new DataInputStream(inputStream)) {
            data.readFully(new byte[4]);
            data.readInt();
            data.readLong();
            long kvCount = data.readLong();
            kvCount = Math.min(kvCount, 500L);
            for (long i = 0; i < kvCount; i++) {
                String key = readString(data);
                int type = data.readUnsignedByte();
                if ("general.architecture".equals(key) && type == 8) {
                    archHolder[0] = readString(data);
                    continue;
                }
                Long value = readSkippedValue(data, type);
                if (value != null && result.size() < 16) {
                    result.put(key, value);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            android.util.Log.d("SketchwarePro", "LocalAiModelInfo: IOException | RuntimeException ignored", ignored);
        }
        return result;
    }

    private static String readString(DataInputStream data) throws IOException {
        long length = data.readLong();
        if (length < 0 || length > 1_000_000L) {
            return "";
        }
        byte[] bytes = new byte[(int) length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Long readSkippedValue(DataInputStream data, int type) throws IOException {
        switch (type) {
            case 0: // UINT8
            case 1: // INT8
            case 7: // BOOL
                return Long.valueOf(data.readUnsignedByte());
            case 2: // UINT16
                return Long.valueOf(data.readUnsignedShort());
            case 3: // INT16
                return Long.valueOf(data.readShort());
            case 4: // UINT32
                return Long.valueOf(Integer.toUnsignedLong(data.readInt()));
            case 5: // INT32
                return Long.valueOf(data.readInt());
            case 10: // UINT64
                return data.readLong();
            case 11: // INT64
                return data.readLong();
            case 6: // FLOAT32
                data.readFloat();
                return null;
            case 12: // FLOAT64
                data.readDouble();
                return null;
            case 8: // STRING
                readString(data);
                return null;
            case 9: // ARRAY
                int elementType = data.readUnsignedByte();
                long elementCount = data.readLong();
                elementCount = Math.min(elementCount, 100_000L);
                for (long j = 0; j < elementCount; j++) {
                    readSkippedValue(data, elementType);
                }
                return null;
            default:
                throw new IOException("Unsupported GGUF value type " + type);
        }
    }

    public String getArchitecture() {
        return architecture;
    }

    private long getMetadataValue(String key, long fallback) {
        Long value = metadata.get(key);
        return value == null ? fallback : value;
    }

    private long getModelBlockCount() {
        return getMetadataValue("llama.block_count", 0L);
    }

    private long getModelEmbeddingLength() {
        return getMetadataValue("llama.embedding_length", 0L);
    }

    private long getModelHeadCount() {
        return getMetadataValue("llama.attention.head_count", 0L);
    }

    private long getModelHeadCountKv() {
        return getMetadataValue("llama.attention.head_count_kv", 0L);
    }

    public long getModelContextLength() {
        long context = getMetadataValue("llama.context_length", 0L);
        if (context <= 0L) {
            context = getMetadataValue("model.context_length", 0L);
        }
        if (context <= 0L) {
            return 0L;
        }
        return Math.min(context, MAX_CONTEXT_LIMIT);
    }

    public long getKvCacheBytesPerToken() {
        long blockCount = getModelBlockCount();
        long headCount = getModelHeadCount();
        long headCountKv = getModelHeadCountKv();
        long embeddingLength = getModelEmbeddingLength();
        if (blockCount <= 0L || headCount <= 0L || headCountKv <= 0L || embeddingLength <= 0L) {
            return 0L;
        }
        long headDim = embeddingLength / headCount;
        if (headDim <= 0L) {
            return 0L;
        }
        return 2L * blockCount * headCountKv * headDim * 2L;
    }

    public long getMaxContextForDevice(long deviceBudgetBytes) {
        long kvBytesPerToken = getKvCacheBytesPerToken();
        if (kvBytesPerToken <= 0L) {
            long byModel = getModelContextLength();
            if (byModel > 0L) {
                return Math.max(512L, Math.min(MAX_CONTEXT_LIMIT, byModel));
            }
            return FALLBACK_CONTEXT;
        }
        long byBudget = deviceBudgetBytes > 0L ? deviceBudgetBytes / kvBytesPerToken : FALLBACK_CONTEXT;
        long byModel = getModelContextLength();
        long max = byModel > 0L ? Math.min(byBudget, byModel) : byBudget;
        return Math.max(512L, Math.min(MAX_CONTEXT_LIMIT, max));
    }

    public String getDisplaySummary() {
        StringBuilder builder = new StringBuilder();
        builder.append(file.getName()).append('\n');
        builder.append(FileUtil.formatFileSize(file.length())).append(" | GGUF v").append(ggufVersion);
        long context = getModelContextLength();
        if (context > 0L) {
            builder.append(" | Contexto máx: ").append(context).append(" tokens");
        }
        long kvBytes = getKvCacheBytesPerToken();
        if (kvBytes > 0L) {
            builder.append("\nKV cache: ~").append(FileUtil.formatFileSize(kvBytes)).append("/token");
        }
        builder.append('\n').append("Estimated RAM: ").append(getEstimatedRamText());
        return builder.toString();
    }

    public String getEstimatedRamText() {
        long estimatedBytes = (long) (file.length() * 1.35f) + (512L * 1024L * 1024L);
        return FileUtil.formatFileSize(estimatedBytes);
    }
}
