package com.carilt01.modsbisect.core.dependencyResolver;

import com.carilt01.modsbisect.core.Unit;
import com.carilt01.modsbisect.core.toml.Dependency;
import com.carilt01.modsbisect.core.toml.Mod;
import com.carilt01.modsbisect.core.toml.ModFile;
import com.carilt01.modsbisect.core.toml.ModParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.Nullable;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DependencyResolver {
    private ObjectMapper mapper = new ObjectMapper();
    private ObjectMapper prettyprintMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Map<String, List<String>> manualOverrides = new HashMap<>();

    public DependencyResolver(String manualOverridesPath) {
        try {
            this.manualOverrides = this.loadManualOverrides(manualOverridesPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, List<String>> loadManualOverrides(String manualOverridesPath) throws IOException {
        ManualOverrides data = mapper.readValue(new File(manualOverridesPath), ManualOverrides.class);
        return data.overrides;
    }

    private Set<String> getDependencies(String modId, Set<String> dependencies) {
        if (!manualOverrides.containsKey(modId)) return dependencies;

        // for safety, clone the set
        Set<String> newDependencies = new HashSet<>(dependencies);
        newDependencies.addAll(manualOverrides.get(modId));

        return newDependencies;
    }

    private DependencyResolverNodeResult recursiveParseJar(
            ByteBuffer jarFile, @Nullable Set<String> dependencyList, @Nullable Set<String> modList
    ) throws IOException {

        if (dependencyList == null) {
            dependencyList = new HashSet<>();
        }

        if (modList == null) {
            modList = new HashSet<>();
        }

        byte[] bytes = new byte[jarFile.remaining()];
        jarFile.get(bytes);

        // open once, for jar in jar
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (
                        name.startsWith("META-INF/jarjar") && !name.endsWith("/") && name.endsWith("jar")
                ) {
                    byte[] data = zis.readAllBytes();

                    ByteBuffer byteBuffer = ByteBuffer.wrap(data);

                    this.recursiveParseJar(byteBuffer, dependencyList, modList);
                }

                zis.closeEntry();
            }
        }

        // open second time, for toml
        boolean foundConfigFile = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("META-INF/neoforge.mods.toml")) {
                    byte[] data = zis.readAllBytes();

                    TomlParseResult result = Toml.parse(new String(data, StandardCharsets.UTF_8));
                    ModFile tomlData = ModParser.parse(result);



                    if (tomlData.dependencies != null) {
                        for (String modId : tomlData.dependencies.keySet()) {
                            for (Dependency dep : tomlData.dependencies.get(modId)) {
                                String dependency_id = dep.modId;
                                String required = dep.type;

                                if (!(required == null) && !Objects.equals(required, "") &&  !required.equalsIgnoreCase("required")) {
                                    continue;
                                }

                                if (Objects.equals(dependency_id, "minecraft")) continue;
                                if (Objects.equals(dependency_id, "neoforge")) continue;

                                dependencyList.add(dependency_id);
                            }
                        }
                    } else {
                        System.out.println("dependencies is null");
                    }


                    for (Mod mod : tomlData.mods) {
                        modList.add(mod.modId);
                    }

                    // stuff cannot require itself, make sure to remove
                    for (String modId : modList) {
                        dependencyList.remove(modId);
                    }

                    return new DependencyResolverNodeResult(dependencyList, modList);
                }

                zis.closeEntry();
            }
        }

        //System.out.println("error: MODS.toml not found!");

        return new DependencyResolverNodeResult(dependencyList, modList);
    }

    private ModJarInfo parseRawJar(Path jarFile) throws IOException {
        byte[] data = Files.readAllBytes(jarFile);
        ByteBuffer wrappedBuffer = ByteBuffer.wrap(data);

        DependencyResolverNodeResult results = this.recursiveParseJar(wrappedBuffer, null, null);

        ModJarInfo jarInfo = new ModJarInfo(
                results.dependencyList(),
                results.modList(),
                jarFile.getFileName().toString()
        );
        System.out.printf("Dependency resolver found %d dependencies for mod %s: %s\n", results.dependencyList().size(), results.modList(), results.dependencyList());

        return jarInfo;
    }

    private Set<String> recursivelyResolveDependencies(String toResolve, Map<String, ModJarInfo> modDataMap, Map<String, String> modIdToJar, @Nullable Set<String> visited) {
        if (visited == null) {
            visited = new HashSet<>();
        }

        visited.add(toResolve);

        String jarFile = modIdToJar.getOrDefault(toResolve, "");
        if (Objects.equals(jarFile, "")) {
            System.out.printf("Not found: %s", toResolve);
            return new HashSet<>();
        }

        ModJarInfo modInfo = modDataMap.getOrDefault(jarFile, null);
        if (modInfo == null) {
            System.out.printf("Mod info not found: %s", jarFile);
            return new HashSet<>();
        }

        Set<String> rawDependencies = modInfo.dependencyIDs();
        Set<String> dependencies = this.getDependencies(toResolve, rawDependencies);

        Set<String> allDependenciesSet = new HashSet<>();

        for (String dependencyId : dependencies) {
            allDependenciesSet.add(dependencyId);
            if (!visited.contains(dependencyId)) {
                Set<String> innerDependencies = this.recursivelyResolveDependencies(
                        dependencyId, modDataMap, modIdToJar, visited
                );
                allDependenciesSet.addAll(innerDependencies);
            }
        }

        return allDependenciesSet;
    }

    public List<Unit> resolveDependencies(String modFolder) throws IOException {
        List<Unit> units = new ArrayList<>();

        Map<String, Set<String>> jarToModIdMap = new HashMap<>();
        Map<String, String> modItToJarMap = new HashMap<>();
        Map<String, ModJarInfo> jarToJarDataMap = new HashMap<>();


        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(modFolder))) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    System.out.printf("Resolving dependencies for: %s", entry.getFileName());

                    ModJarInfo jarData = this.parseRawJar(entry);
                    jarToModIdMap.put(jarData.jarFile(), jarData.modIDs());
                    for (String modId : jarData.modIDs()) {
                        modItToJarMap.put(modId, entry.toString());
                    }
                    jarToJarDataMap.put(entry.toString(), jarData);
                }
            }
        }

        for (Map.Entry<String, ModJarInfo> entry : jarToJarDataMap.entrySet()) {
            Set<String> modIDs = entry.getValue().modIDs();
            Set<String> allDependencies = new HashSet<>();
            for (String modId : modIDs) {
                Set<String> dependencyChildren = this.recursivelyResolveDependencies(
                        modId, jarToJarDataMap, modItToJarMap, null
                );
                allDependencies.addAll(dependencyChildren);
            }
            // convert all jars to files
            Set<String> dependencyJars = new HashSet<>();
            for (String dep : allDependencies) {
                String jarFile = modItToJarMap.get(dep);
                if (jarFile == null) {
                    System.out.println("warn: cannot find file: " + dep);
                    continue;
                }
                dependencyJars.add(Path.of(jarFile).getFileName().toString());
            }
            dependencyJars.add(entry.getKey());

            Unit unit = new Unit(dependencyJars.toArray(String[]::new), Path.of(entry.getKey()).getFileName().toString(), modIDs.toArray(String[]::new));

            units.add(unit);
        }
        try {
            System.out.println(prettyprintMapper.writeValueAsString(units));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return units;

    }
}
