package com.carilt01.modsbisect.core.dependencyResolver;

import java.util.Set;

public record ModJarInfo(
        Set<String> dependencyIDs,
        Set<String> modIDs,
        String jarFile
) {
}
