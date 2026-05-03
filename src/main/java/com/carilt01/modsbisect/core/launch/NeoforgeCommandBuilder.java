package com.carilt01.modsbisect.core.launch;

import java.nio.file.Path;
import java.util.List;

public class NeoforgeCommandBuilder {

    private NeoforgeConfigExtractor configExtractor;

    public NeoforgeCommandBuilder() {
        this.configExtractor = new NeoforgeConfigExtractor();
    }

    public List<String> buildCommand(List<String> commandArguments, String modsPath) throws Exception {
        Path modsPathObj = Path.of(modsPath);
        NeoforgeConfig instanceConfig = this.configExtractor.extractConfig(commandArguments);

        return List.of(
                instanceConfig.javaPath(),
                String.format("-Dforgewrapper.librariesDir=%s", instanceConfig.forgeLibrariesDirectory()),
                String.format("-Dforgewrapper.minecraft=%s", instanceConfig.forgeMinecraftPath()),
                String.format("-Dforgewrapper.installer=%s", instanceConfig.forgeInstallerPath()),
                "-cp",
                instanceConfig.classPath(),
                "io.github.zekerzhayard.forgewrapper.installer.Main",
                "--accessToken",
                "0",
                "--version",
                instanceConfig.mcVersion(),
                "--gameDir",
                modsPathObj.getParent().toString(),
                "--launchTarget",
                "forgeclient",
                "--fml.neoForgeVersion",
                instanceConfig.neoforgeVersion(),
                "--fml.fmlVersion",
                instanceConfig.fmlVersion(),
                "--fml.mcVersion",
                instanceConfig.mcVersion(),
                "--fml.neoFormVersion",
                instanceConfig.neoformVersion()
        );
    }
}
