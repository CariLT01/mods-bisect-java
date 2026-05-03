package com.carilt01.modsbisect.core.launch;

public record NeoforgeConfig(
        String forgeLibrariesDirectory,
        String forgeMinecraftPath,
        String forgeInstallerPath,

        String neoforgeVersion,
        String fmlVersion,
        String mcVersion,
        String neoformVersion,

        String javaPath,
        String classPath
) {
}
