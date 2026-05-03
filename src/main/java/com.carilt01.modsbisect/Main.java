package com.carilt01.modsbisect;

import com.carilt01.modsbisect.core.BisectApplication;
import com.carilt01.modsbisect.core.dependencyResolver.DependencyResolver;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {

        BisectApplication app = new BisectApplication(Path.of("C:\\Users\\carip\\AppData\\Roaming\\PrismLauncher\\instances\\BISECT TEST\\minecraft\\mods"));
        app.run();

        // DependencyResolver resolver = new DependencyResolver("dependencies_overrides.json");
        // resolver.resolveDependencies("C:\\Users\\carip\\AppData\\Roaming\\PrismLauncher\\instances\\1.21.1 modpack neoforge\\minecraft\\mods");
    }
}
