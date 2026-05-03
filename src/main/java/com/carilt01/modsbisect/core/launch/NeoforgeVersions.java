package com.carilt01.modsbisect.core.launch;

import java.util.List;

public record NeoforgeVersions(
        String neoformVersion,
        String fmlVersion,
        String mcVersion,
        List<String> libraries,
        String installerPath,
        String neoforgeVersion
) {
}
