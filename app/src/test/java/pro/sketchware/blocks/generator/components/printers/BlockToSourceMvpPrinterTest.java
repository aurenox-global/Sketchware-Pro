package pro.sketchware.blocks.generator.components.printers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import pro.sketchware.blocks.generator.components.parsers.SourceToBlockMvpParser;
import pro.sketchware.blocks.generator.components.parsers.SourceToBlockParseResult;
import pro.sketchware.blocks.generator.components.parsers.SourceToBlockParseStatus;

public class BlockToSourceMvpPrinterTest {

    @Test
    public void printer_isDeterministic_forSameInput() {
        SourceToBlockParseResult parseResult = parseSampleSource();
        BlockToSourceMvpPrinter printer = new BlockToSourceMvpPrinter();

        String first = printer.print(parseResult.blocks);
        String second = printer.print(parseResult.blocks);

        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    @Test
    public void printer_isDeterministic_forShuffledBlocks() {
        SourceToBlockParseResult parseResult = parseSampleSource();
        BlockToSourceMvpPrinter printer = new BlockToSourceMvpPrinter();

        List<BlockBean> shuffled = new ArrayList<>(parseResult.blocks);
        Collections.shuffle(shuffled, new Random(7));

        String orderedOutput = printer.print(parseResult.blocks);
        String shuffledOutput = printer.print(shuffled);

        assertEquals(orderedOutput, shuffledOutput);
    }

    @Test
    public void printer_emitsStableControlFlowSkeleton() {
        String source = """
                if (ready) {
                    total = total + 1;
                } else {
                    total = 0;
                }
                while (total < 10) {
                    total = total + 1;
                }
                return;
                """;

        SourceToBlockParseResult parseResult = new SourceToBlockMvpParser().parse(source);
        assertTrue(parseResult.status != SourceToBlockParseStatus.FAILED);

        String printed = new BlockToSourceMvpPrinter().print(parseResult.blocks);
        assertTrue(printed.contains("if (/*"));
        assertTrue(printed.contains("while (/*"));
        assertTrue(printed.contains("return;"));
    }

    private SourceToBlockParseResult parseSampleSource() {
        String source = """
                int total = 0;
                if (input > 0) {
                    total = input;
                } else {
                    total = 1;
                }
                for (int i = 0; i < 3; i++) {
                    total = total + i;
                }
                return;
                """;

        SourceToBlockParseResult parseResult = new SourceToBlockMvpParser().parse(source);
        assertTrue(parseResult.status != SourceToBlockParseStatus.FAILED);
        return parseResult;
    }
}
