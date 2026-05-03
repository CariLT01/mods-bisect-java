package com.carilt01.modsbisect.core;

import com.carilt01.modsbisect.core.algorithms.quickxplain.QXPSaveState;
import com.carilt01.modsbisect.core.algorithms.quickxplain.QuickXPlainAlgorithm;
import com.carilt01.modsbisect.core.dependencyResolver.DependencyResolver;
import com.carilt01.modsbisect.core.launch.LaunchTester;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModsConflictDetector {

    private final String modsPath;
    private final StateIO preloadedState;
    private final DependencyResolver dependencyResolver;
    private final List<String> launchArgs;
    private List<Unit> units = new ArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Isolators
    private final QuickXPlainAlgorithm qxpIsolator;
    private final LaunchTester launchTester;


    public ModsConflictDetector(@NotNull  String modsPath, @NotNull StateIO preloadedState, @NotNull  List<String> launchArgs) {
        this.modsPath = modsPath;
        this.launchArgs = launchArgs;
        this.preloadedState = preloadedState;
        this.dependencyResolver = new DependencyResolver("dependencies_overrides.json");
        this.launchTester = new LaunchTester(modsPath, launchArgs);

        this.qxpIsolator = new QuickXPlainAlgorithm(this::saveState);
    }

    private void saveState(QXPSaveState saveState) {
        try {
            this.preloadedState.addAndSaveState(new ResolverState(saveState));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private StatesFileData loadState() {
        return this.preloadedState.getFileData();
    }

    private boolean testFunc(List<Unit> units) throws Exception {

        return this.launchTester.test(units);
    }
    private List<Unit> resumeSearch(@Nullable QXPSaveState state) throws Exception {
        return this.qxpIsolator.run(this.units, this::testFunc, state);
    }

    public List<Unit> isolate() throws Exception {
        this.units = this.dependencyResolver.resolveDependencies(this.modsPath);

        List<Unit> candidates = this.resumeSearch(null);

        System.out.println("Minimal failing set:");
        System.out.println(mapper.writeValueAsString(candidates));

        return candidates;
    }







}
