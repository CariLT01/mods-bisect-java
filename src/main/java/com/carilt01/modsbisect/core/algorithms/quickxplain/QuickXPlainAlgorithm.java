package com.carilt01.modsbisect.core.algorithms.quickxplain;

import com.carilt01.modsbisect.core.Unit;
import com.carilt01.modsbisect.core.algorithms.IsolationAlgorithm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class QuickXPlainAlgorithm implements IsolationAlgorithm<QXPSaveState> {

    private final Map<String, Unit> allUnits = new HashMap<>();
    private @Nullable  Function<List<Unit>, Boolean> testFunc = null;
    private Map<String, Boolean> testCache = new HashMap<>();
    private List<QXPStackFrame> stack = new ArrayList<>();
    private List<List<String>> results = new ArrayList<>();
    private final Consumer<QXPSaveState> saveFunc;

    public QuickXPlainAlgorithm(Consumer<QXPSaveState> saveFunc) {
        this.saveFunc = saveFunc;
    }

    private Map<String, Integer> computeDependencyDepth(List<Unit> units) {
        Map<String, List<String>> earlyDependencyGraph = getEarlyDependencyGraph(units);

        // now build the graph
        Map<String, Integer> depth = new HashMap<>();

        for (String start : earlyDependencyGraph.keySet()) {
            if (depth.containsKey(start)) continue;

            Deque<String> stack = new ArrayDeque<>();
            Deque<Boolean> processed = new ArrayDeque<>();

            stack.push(start);
            processed.push(false);

            while (!stack.isEmpty()) {
                String node = stack.pop();
                boolean isProcessed = processed.pop();

                if (depth.containsKey(node)) continue;

                if (isProcessed) {
                    List<String> deps = earlyDependencyGraph.getOrDefault(node, List.of());
                    int maxDep = -1;
                    for (String dep : deps) {
                        maxDep = Math.max(maxDep, depth.get(dep));
                    }
                    depth.put(node, maxDep + 1);
                } else {
                    // Post-order: process after children
                    stack.push(node);
                    processed.push(true);

                    for (String dep : earlyDependencyGraph.getOrDefault(node, List.of())) {
                        if (!depth.containsKey(dep)) {
                            stack.push(dep);
                            processed.push(false);
                        }
                    }
                }
            }
        }

        return depth;
    }

    private static @NotNull Map<String, List<String>> getEarlyDependencyGraph(List<Unit> units) {
        Map<String, Unit> jarToUnit = new HashMap<>();
        for (Unit unit : units) {
            jarToUnit.put(unit.rootJar(), unit);
        }

        // build early graph of
        // root_jar -> dependencies
        Map<String, List<String>> earlyDependencyGraph = new HashMap<>();
        for (Unit unit : units) {
            List<String> dependencyRoots = new ArrayList<>();
            for (String jar : unit.jars()) {
                if (jarToUnit.containsKey(jar) && !Objects.equals(jar, unit.rootJar())) {
                    dependencyRoots.add(jar);
                }
            }
            earlyDependencyGraph.put(unit.rootJar(), dependencyRoots);
        }
        return earlyDependencyGraph;
    }

    public List<Unit> run(@NotNull List<Unit> units, @NotNull Function<List<Unit>, Boolean> testFunc, @Nullable QXPSaveState loadState) {
        Map<String, Integer> depthMap = this.computeDependencyDepth(units);

        List<Unit> sortedUnits = new ArrayList<>(units);
        sortedUnits.sort(
                Comparator.comparingInt(
                        (Unit u) -> depthMap.getOrDefault(u.rootJar(), 0)
                ).thenComparing(Unit::rootJar)
        );

        for (Unit u : sortedUnits) {
            this.allUnits.put(u.rootJar(), u);
        }
        this.testFunc = testFunc;
        this.testCache.clear();
        this.stack.clear();
        this.results.clear();

        System.out.printf("QuickXPlain received input size of: %s", units.size());

        return this.qxpRun(loadState);
    }

    private boolean loadState(@Nullable QXPSaveState loadedState) {
        if (loadedState == null) return false;

        this.testCache = loadedState.cache;
        this.stack = loadedState.stack;
        this.results = loadedState.results;

        return true;
    }

    private void saveState() {
        QXPSaveState state = new QXPSaveState();

        state.cache = this.testCache;
        state.stack = this.stack;
        state.results = this.results;

        this.saveFunc.accept(state);
    }

    private boolean executeTest(List<String> unitIDs) {

        String cacheKey = String.join("|", unitIDs);
        if (this.testCache.containsKey(cacheKey)) {
            return this.testCache.get(cacheKey);
        }

        List<Unit> unitsToTest = new ArrayList<>();
        for (String id : unitIDs) {
            unitsToTest.add(this.allUnits.get(id));
        }
        assert this.testFunc != null;
        boolean result = this.testFunc.apply(unitsToTest);

        this.testCache.put(cacheKey, result);
        this.saveState();

        return result;
    }

    private List<Unit> qxpRun(@Nullable QXPSaveState loadedState) {
        if (!this.loadState(loadedState)) {
            List<String> allIDs = new ArrayList<>(this.allUnits.keySet());

            QXPStackFrame initialFrame = new QXPStackFrame();
            initialFrame.delta = allIDs;
            initialFrame.suspect = allIDs;
            initialFrame.stage = QXPIsolationStage.SPLIT;

            this.stack.add(initialFrame);
        }

        while (!this.stack.isEmpty()) {
            QXPStackFrame current = this.stack.get(this.stack.size() - 1);
            QXPIsolationStage stage = current.stage;

            if (current.delta.isEmpty() && !this.executeTest(current.bg)) {
                this.stack.remove(this.stack.size() - 1);
                this.results.add(new ArrayList<>());
                continue;
            }

            List<String> combinedList = new ArrayList<>(current.bg);
            combinedList.addAll(current.delta);

            if (this.executeTest(combinedList)) {
                this.stack.remove(this.stack.size() - 1);
                this.results.add(new ArrayList<>());
                continue;
            }

            if (current.suspect.size() == 1) {
                this.stack.remove(this.stack.size() - 1);
                this.results.add(current.suspect);
                continue;
            }

            if (stage == QXPIsolationStage.SPLIT) {
                int k = current.suspect.size() / 2;

                List<String> s1 = current.suspect.subList(0, k);
                List<String> s2 = current.suspect.subList(k, current.suspect.size());

                current.s1 = s1;
                current.s2 = s2;
                current.stage = QXPIsolationStage.AFTER_S2;

                List<String> combinedList2 = new ArrayList<>(current.bg);
                combinedList2.addAll(s1);

                QXPStackFrame newFrame = new QXPStackFrame();

                newFrame.delta = s2;
                newFrame.bg = combinedList2;
                newFrame.suspect = s2;
                newFrame.stage = QXPIsolationStage.SPLIT;

                this.stack.add(newFrame);
                continue;
            }

            if (stage == QXPIsolationStage.AFTER_S2) {
                List<String> delta2 = results.get(results.size() - 1);
                results.remove(results.size() - 1);

                current.delta2 = delta2;
                current.stage = QXPIsolationStage.AFTER_S1;

                List<String> combinedList3 = new ArrayList<>(current.bg);
                combinedList3.addAll(current.delta2);

                QXPStackFrame newStackFrame = new QXPStackFrame();
                newStackFrame.delta = current.s1;
                newStackFrame.bg = combinedList3;
                newStackFrame.suspect = current.s1;
                newStackFrame.stage = QXPIsolationStage.SPLIT;

                this.stack.add(newStackFrame);
            }

            if (stage == QXPIsolationStage.AFTER_S1) {
                List<String> delta1 = results.get(results.size() - 1);
                results.remove(results.size() - 1);

                stack.remove(stack.size() - 1);

                List<String> combinedList4 = new ArrayList<>(delta1);
                combinedList4.addAll(current.delta2);

                results.add(combinedList4);
            }
        }

        List<Unit> minimalFailingSet = new ArrayList<>();

        for (String nodeName : results.get(0)) {
            minimalFailingSet.add(this.allUnits.get(nodeName));
        }

        return minimalFailingSet;
    }
}
