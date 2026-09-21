package pro.sketchware.blocks.generator.components.parsers;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceToBlockParseResult {

    public final SourceToBlockParseStatus status;
    public final long parsedAtMs;
    public final List<BlockBean> blocks;
    public final List<SourceToBlockParseIssue> issues;
    public final String errorMessage;

    public SourceToBlockParseResult(SourceToBlockParseStatus status,
                                    long parsedAtMs,
                                    List<BlockBean> blocks,
                                    List<SourceToBlockParseIssue> issues,
                                    String errorMessage) {
        this.status = status == null ? SourceToBlockParseStatus.FAILED : status;
        this.parsedAtMs = Math.max(parsedAtMs, 0L);
        this.blocks = Collections.unmodifiableList(new ArrayList<>(
                blocks == null ? Collections.emptyList() : blocks
        ));
        this.issues = Collections.unmodifiableList(new ArrayList<>(
                issues == null ? Collections.emptyList() : issues
        ));
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public static SourceToBlockParseResult success(List<BlockBean> blocks,
                                                   List<SourceToBlockParseIssue> issues) {
        SourceToBlockParseStatus status = issues == null || issues.isEmpty()
                ? SourceToBlockParseStatus.SUCCESS
                : SourceToBlockParseStatus.PARTIAL;
        return new SourceToBlockParseResult(status, System.currentTimeMillis(), blocks, issues, "");
    }

    public static SourceToBlockParseResult failed(String errorMessage,
                                                  List<SourceToBlockParseIssue> issues) {
        return new SourceToBlockParseResult(
                SourceToBlockParseStatus.FAILED,
                System.currentTimeMillis(),
                Collections.emptyList(),
                issues,
                errorMessage
        );
    }
}
