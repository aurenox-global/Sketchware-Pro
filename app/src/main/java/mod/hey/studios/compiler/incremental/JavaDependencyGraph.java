package mod.hey.studios.compiler.incremental;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reverse dependency graph for Java source units.
 */
public final class JavaDependencyGraph {

    private final Map<String, Set<String>> forwardEdges = new HashMap<>();
    private final Map<String, Set<String>> reverseEdges = new HashMap<>();
    private int edgeCount = 0;

    public void addNode(String node) {
        if (node == null || node.isEmpty()) {
            return;
        }
        forwardEdges.computeIfAbsent(node, ignored -> new HashSet<>());
        reverseEdges.computeIfAbsent(node, ignored -> new HashSet<>());
    }

    public void addDependency(String source, String dependency) {
        if (source == null || dependency == null || source.isEmpty() || dependency.isEmpty()) {
            return;
        }
        addNode(source);
        addNode(dependency);

        Set<String> dependencies = forwardEdges.get(source);
        if (dependencies.add(dependency)) {
            reverseEdges.get(dependency).add(source);
            edgeCount++;
        }
    }

    public Set<String> collectDependents(Set<String> changedNodes) {
        if (changedNodes == null || changedNodes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String changed : changedNodes) {
            if (changed != null && !changed.isEmpty() && visited.add(changed)) {
                queue.add(changed);
            }
        }

        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            Set<String> dependents = reverseEdges.get(node);
            if (dependents == null || dependents.isEmpty()) {
                continue;
            }

            for (String dependent : dependents) {
                if (visited.add(dependent)) {
                    queue.addLast(dependent);
                }
            }
        }

        return visited;
    }

    public int getNodeCount() {
        return forwardEdges.size();
    }

    public int getEdgeCount() {
        return edgeCount;
    }
}
