package com.carilt01.modsbisect.core;

import com.carilt01.modsbisect.core.algorithms.quickxplain.QXPSaveState;
import com.carilt01.modsbisect.core.algorithms.quickxplain.QuickXPlainAlgorithm;
import com.carilt01.modsbisect.core.dependencyResolver.DependencyResolver;

import java.io.IOException;

public class ModsConflictDetector {

    private final String modsPath;
    private final StateIO preloadedState;
    private final DependencyResolver dependencyResolver;

    // Isolators
    private final QuickXPlainAlgorithm qxpIsolator;

    public ModsConflictDetector(String modsPath, StateIO preloadedState) {
        this.modsPath = modsPath;
        this.preloadedState = preloadedState;
        this.dependencyResolver = new DependencyResolver("dependencies_overrides.json");

        this.qxpIsolator = new QuickXPlainAlgorithm(this::saveState);
    }

    private void saveState(QXPSaveState saveState) {
        try {
            this.preloadedState.addAndSaveState(new ResolverState(saveState));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }







}
