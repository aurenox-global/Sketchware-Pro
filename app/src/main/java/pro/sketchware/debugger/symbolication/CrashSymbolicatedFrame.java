package pro.sketchware.debugger.symbolication;

public final class CrashSymbolicatedFrame {

    public final String originalFrame;
    public final String symbolicatedFrame;
    public final String className;
    public final String methodName;
    public final String fileName;
    public final int lineNumber;
    public final float confidence;

    public CrashSymbolicatedFrame(String originalFrame,
                                  String symbolicatedFrame,
                                  String className,
                                  String methodName,
                                  String fileName,
                                  int lineNumber,
                                  float confidence) {
        this.originalFrame = originalFrame == null ? "" : originalFrame;
        this.symbolicatedFrame = symbolicatedFrame == null ? "" : symbolicatedFrame;
        this.className = className == null ? "" : className;
        this.methodName = methodName == null ? "" : methodName;
        this.fileName = fileName == null ? "" : fileName;
        this.lineNumber = Math.max(lineNumber, 0);
        this.confidence = clampConfidence(confidence);
    }

    private static float clampConfidence(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, 1f);
    }
}
