package com.carilt01.modsbisect;

import com.carilt01.modsbisect.core.dependencyResolver.DependencyResolver;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        DependencyResolver resolver = new DependencyResolver("dependencies_overrides.json");
        resolver.resolveDependencies("C:\\Users\\carip\\AppData\\Roaming\\PrismLauncher\\instances\\1.21.1 modpack neoforge\\minecraft\\mods");
    }
}
