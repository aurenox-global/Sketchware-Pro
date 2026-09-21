package pro.sketchware.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.utility.FileUtil;

public final class LocalAiModelCatalog {

    private static final String HF_RESOLVE = "https://huggingface.co/%s/resolve/main/%s";

    private LocalAiModelCatalog() {
    }

    public static List<ModelFamily> getFamilies() {
        List<ModelFamily> families = new ArrayList<>();

        families.add(new ModelFamily(
                "qwen35_08b",
                "Qwen3.5-0.8B",
                "Qwen3.5 0.8B de Alibaba (Unsloth): muy rapido y ligero, contexto 256K, razonamiento desactivado por defecto. Q8_0 recomendado para maxima fidelidad de instrucciones.",
                "unsloth/Qwen3.5-0.8B-GGUF",
                262144,
                new QuantEntry("Qwen3.5-0.8B-Q8_0.gguf", 811_843_840L, true),
                new QuantEntry("Qwen3.5-0.8B-Q4_K_M.gguf", 558_891_008L, false),
                new QuantEntry("Qwen3.5-0.8B-UD-Q4_K_XL.gguf", 586_153_984L, false)
        ));

        return families;
    }

    public static ModelFamily getFamilyById(String familyId) {
        for (ModelFamily family : getFamilies()) {
            if (family.id.equals(familyId)) {
                return family;
            }
        }
        return null;
    }

    public static ModelFamily findFamilyForModelFile(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (ModelFamily family : getFamilies()) {
            for (QuantEntry quant : family.quants) {
                if (quant.fileName.equals(fileName)) {
                    return family;
                }
            }
        }
        return null;
    }

    public static final class QuantEntry {
        public final String fileName;
        public final long sizeBytes;
        public final boolean recommended;

        QuantEntry(String fileName, long sizeBytes, boolean recommended) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.recommended = recommended;
        }

        public String getLabel() {
            String quant = fileName.replaceFirst("^.*-(Q\\d+_\\d+|UD-Q\\d+_\\d+|Q\\d+_\\d+)\\.gguf$", "$1");
            if (quant.equals(fileName)) {
                quant = fileName.replaceFirst("\\.gguf$", "");
                int dash = quant.lastIndexOf('-');
                if (dash >= 0) {
                    quant = quant.substring(dash + 1);
                }
            }
            return quant + (recommended ? "  (Recomendado)" : "");
        }

        public String getSizeText() {
            return FileUtil.formatFileSize(sizeBytes);
        }
    }

    public static final class ModelFamily {
        public final String id;
        public final String name;
        public final String description;
        public final String repository;
        public final int contextLength;
        public final List<QuantEntry> quants;

        ModelFamily(String id,
                    String name,
                    String description,
                    String repository,
                    int contextLength,
                    QuantEntry... quants) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.repository = repository;
            this.contextLength = contextLength;
            this.quants = new ArrayList<>();
            for (QuantEntry quant : quants) {
                this.quants.add(quant);
            }
        }

        public String getDownloadUrl(String fileName) {
            return String.format(Locale.US, HF_RESOLVE, repository, fileName);
        }
    }
}
