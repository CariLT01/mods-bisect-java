package com.carilt01.modsbisect.core.dependencyResolver;

import java.util.Set;

public record DependencyResolverNodeResult(
        Set<String> dependencyList,
        Set<String> modList
) {
}
