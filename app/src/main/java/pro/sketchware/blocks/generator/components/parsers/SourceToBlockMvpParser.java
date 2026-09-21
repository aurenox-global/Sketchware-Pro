package pro.sketchware.blocks.generator.components.parsers;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SourceToBlockMvpParser {

    private static final int START_BLOCK_ID = 100000;
    private static final String BRANCH_ROOT = "root";
    private static final String BRANCH_SUBSTACK1 = "subStack1";
    private static final String BRANCH_SUBSTACK2 = "subStack2";

    public SourceToBlockParseResult parse(String sourceCode) {
        String source = sourceCode == null ? "" : sourceCode.trim();
        if (source.isEmpty()) {
            return SourceToBlockParseResult.success(new ArrayList<>(), new ArrayList<>());
        }

        ArrayList<SourceToBlockParseIssue> issues = new ArrayList<>();
        ArrayList<ParsedNode> nodes = new ArrayList<>();
        AtomicInteger idGenerator = new AtomicInteger(START_BLOCK_ID);

        try {
            BlockStmt root = StaticJavaParser.parseBlock("{\n" + source + "\n}");
            parseStatements(root.getStatements(), -1, BRANCH_ROOT, nodes, issues, idGenerator);
            return SourceToBlockParseResult.success(convertToBlockBeans(nodes), issues);
        } catch (Exception e) {
            issues.add(new SourceToBlockParseIssue("PARSE_ERROR", e.getMessage(), -1, -1));
            return SourceToBlockParseResult.failed("Unable to parse source code", issues);
        }
    }

    private void parseStatements(List<Statement> statements,
                                 int parentId,
                                 String branch,
                                 List<ParsedNode> output,
                                 List<SourceToBlockParseIssue> issues,
                                 AtomicInteger idGenerator) {
        for (Statement statement : statements) {
            if (statement instanceof BlockStmt blockStmt) {
                parseStatements(blockStmt.getStatements(), parentId, branch, output, issues, idGenerator);
                continue;
            }

            ParsedNodeDescriptor descriptor = describe(statement);
            int nodeId = idGenerator.getAndIncrement();
            output.add(new ParsedNode(nodeId, parentId, branch, descriptor));

            if (statement instanceof IfStmt ifStmt) {
                parseNestedStatement(ifStmt.getThenStmt(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                ifStmt.getElseStmt().ifPresent(elseStmt ->
                        parseNestedStatement(elseStmt, nodeId, BRANCH_SUBSTACK2, output, issues, idGenerator));
                continue;
            }

            if (statement instanceof WhileStmt whileStmt) {
                parseNestedStatement(whileStmt.getBody(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                continue;
            }

            if (statement instanceof DoStmt doStmt) {
                parseNestedStatement(doStmt.getBody(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                continue;
            }

            if (statement instanceof ForStmt forStmt) {
                parseNestedStatement(forStmt.getBody(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                continue;
            }

            if (statement instanceof ForEachStmt forEachStmt) {
                parseNestedStatement(forEachStmt.getBody(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                continue;
            }

            if (statement instanceof SwitchStmt switchStmt) {
                switchStmt.getEntries().forEach(entry ->
                        parseStatements(entry.getStatements(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator));
                continue;
            }

            if (statement instanceof TryStmt tryStmt) {
                parseStatements(tryStmt.getTryBlock().getStatements(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                tryStmt.getCatchClauses().forEach(catchClause ->
                        parseStatements(catchClause.getBody().getStatements(), nodeId, BRANCH_SUBSTACK2, output, issues, idGenerator));
                tryStmt.getFinallyBlock().ifPresent(finallyBlock ->
                        parseStatements(finallyBlock.getStatements(), nodeId, BRANCH_SUBSTACK2, output, issues, idGenerator));
                continue;
            }

            if (statement instanceof SynchronizedStmt synchronizedStmt) {
                parseStatements(synchronizedStmt.getBody().getStatements(), nodeId, BRANCH_SUBSTACK1, output, issues, idGenerator);
                continue;
            }

            if (descriptor.opCode.equals("mvp_unsupported")) {
                issues.add(new SourceToBlockParseIssue(
                        "UNSUPPORTED_STATEMENT",
                        "MVP parser used fallback for: " + statement.getClass().getSimpleName(),
                        line(statement),
                        column(statement)
                ));
            }
        }
    }

    private void parseNestedStatement(Statement statement,
                                      int parentId,
                                      String branch,
                                      List<ParsedNode> output,
                                      List<SourceToBlockParseIssue> issues,
                                      AtomicInteger idGenerator) {
        if (statement == null) {
            return;
        }
        if (statement instanceof BlockStmt blockStmt) {
            parseStatements(blockStmt.getStatements(), parentId, branch, output, issues, idGenerator);
        } else {
            ArrayList<Statement> singleton = new ArrayList<>();
            singleton.add(statement);
            parseStatements(singleton, parentId, branch, output, issues, idGenerator);
        }
    }

    private ArrayList<BlockBean> convertToBlockBeans(List<ParsedNode> nodes) {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        Map<Integer, BlockBean> blocksById = new HashMap<>();
        Map<String, ArrayList<Integer>> siblingsByBranch = new HashMap<>();

        for (ParsedNode node : nodes) {
            BlockBean block = new BlockBean(
                    String.valueOf(node.id),
                    node.descriptor.spec,
                    node.descriptor.typeToken,
                    node.descriptor.typeName,
                    node.descriptor.opCode
            );
            blocks.add(block);
            blocksById.put(node.id, block);

            String branchKey = buildBranchKey(node.parentId, node.branch);
            siblingsByBranch.computeIfAbsent(branchKey, unused -> new ArrayList<>()).add(node.id);
        }

        for (ArrayList<Integer> siblings : siblingsByBranch.values()) {
            for (int i = 0; i < siblings.size() - 1; i++) {
                BlockBean block = blocksById.get(siblings.get(i));
                if (block != null) {
                    block.nextBlock = siblings.get(i + 1);
                }
            }
        }

        for (ParsedNode node : nodes) {
            BlockBean block = blocksById.get(node.id);
            if (block == null) {
                continue;
            }

            ArrayList<Integer> subStack1Children = siblingsByBranch.get(buildBranchKey(node.id, BRANCH_SUBSTACK1));
            if (subStack1Children != null && !subStack1Children.isEmpty()) {
                block.subStack1 = subStack1Children.get(0);
            }

            ArrayList<Integer> subStack2Children = siblingsByBranch.get(buildBranchKey(node.id, BRANCH_SUBSTACK2));
            if (subStack2Children != null && !subStack2Children.isEmpty()) {
                block.subStack2 = subStack2Children.get(0);
            }
        }

        return blocks;
    }

    private ParsedNodeDescriptor describe(Statement statement) {
        String snippet = compact(statement.toString(), 120);

        if (statement instanceof IfStmt) {
            return new ParsedNodeDescriptor("mvp_if", "c", "", "if " + snippet);
        }
        if (statement instanceof WhileStmt) {
            return new ParsedNodeDescriptor("mvp_while", "c", "", "while " + snippet);
        }
        if (statement instanceof DoStmt) {
            return new ParsedNodeDescriptor("mvp_do_while", "c", "", "do while " + snippet);
        }
        if (statement instanceof ForStmt) {
            return new ParsedNodeDescriptor("mvp_for", "c", "", "for " + snippet);
        }
        if (statement instanceof ForEachStmt) {
            return new ParsedNodeDescriptor("mvp_for_each", "c", "", "for each " + snippet);
        }
        if (statement instanceof SwitchStmt) {
            return new ParsedNodeDescriptor("mvp_switch", "c", "", "switch " + snippet);
        }
        if (statement instanceof TryStmt) {
            return new ParsedNodeDescriptor("mvp_try", "c", "", "try " + snippet);
        }
        if (statement instanceof ReturnStmt) {
            return new ParsedNodeDescriptor("mvp_return", "f", "", "return " + snippet);
        }
        if (statement instanceof BreakStmt) {
            return new ParsedNodeDescriptor("mvp_break", "f", "", "break");
        }
        if (statement instanceof ContinueStmt) {
            return new ParsedNodeDescriptor("mvp_continue", "f", "", "continue");
        }
        if (statement instanceof ExpressionStmt expressionStmt) {
            return describeExpression(expressionStmt);
        }

        return new ParsedNodeDescriptor("mvp_unsupported", " ", "", snippet);
    }

    private ParsedNodeDescriptor describeExpression(ExpressionStmt expressionStmt) {
        String text = compact(expressionStmt.getExpression().toString(), 120);
        String expressionName = expressionStmt.getExpression().getClass().getSimpleName();

        if (expressionName.equals("AssignExpr")) {
            return new ParsedNodeDescriptor("mvp_assign", " ", "", text);
        }
        if (expressionName.equals("VariableDeclarationExpr")) {
            return new ParsedNodeDescriptor("mvp_declare", " ", "", text);
        }
        if (expressionName.equals("MethodCallExpr")) {
            return new ParsedNodeDescriptor("mvp_call", " ", "", text);
        }
        return new ParsedNodeDescriptor("mvp_expression", " ", "", text);
    }

    private String buildBranchKey(int parentId, String branch) {
        return parentId + "::" + branch;
    }

    private static String compact(String text, int maxLength) {
        String compact = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength - 3) + "...";
    }

    private static int line(Node node) {
        return node.getRange().map(range -> range.begin.line).orElse(-1);
    }

    private static int column(Node node) {
        return node.getRange().map(range -> range.begin.column).orElse(-1);
    }

    private static final class ParsedNode {

        private final int id;
        private final int parentId;
        private final String branch;
        private final ParsedNodeDescriptor descriptor;

        private ParsedNode(int id, int parentId, String branch, ParsedNodeDescriptor descriptor) {
            this.id = id;
            this.parentId = parentId;
            this.branch = branch;
            this.descriptor = descriptor;
        }
    }

    private static final class ParsedNodeDescriptor {

        private final String opCode;
        private final String typeToken;
        private final String typeName;
        private final String spec;

        private ParsedNodeDescriptor(String opCode, String typeToken, String typeName, String spec) {
            this.opCode = opCode;
            this.typeToken = typeToken;
            this.typeName = typeName;
            this.spec = spec;
        }
    }
}
