package com.carilt01.modsbisect.core.launch;

import com.carilt01.modsbisect.core.launch.json.FileData;
import com.carilt01.modsbisect.core.launch.json.Library;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NeoforgeConfigExtractor {

    private static final String MINECRAFT_CLIENT_REGEX = "^minecraft-.*-client\\.jar$";
    private static final Pattern MinecraftClientRegexPattern = Pattern.compile(MINECRAFT_CLIENT_REGEX);

    private static final String MINECRAFT_CLIENT_VERSION_REGEX = "minecraft-(.+)-client\\.jar";
    private static final Pattern MinecraftClientVersionRegexPattern = Pattern.compile(MINECRAFT_CLIENT_VERSION_REGEX);

    private static final String NEOFORGE_INSTALLER_REGEX = "^neoforge-(.+)-installer\\.jar$";
    private static final Pattern NeoforgeInstallerRegexPattern = Pattern.compile(NEOFORGE_INSTALLER_REGEX);

    private static final ObjectMapper mapper = new ObjectMapper();

    public NeoforgeConfigExtractor() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static int getIndex(List<String> haystack, String needle) {
        return haystack.indexOf(needle);
    }

    private @Nullable Path findLibrariesParent(Path path) {
        Path current = path;

        while (current != null) {
            Path fileName = current.getFileName();

            if (fileName != null && fileName.toString().equals("libraries")) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    private String findMostCommonLibrariesDirectory(String classPath) {
        List<String> libraries = List.of(classPath.split(";"));
        Map<String, Integer> allLibrariesParent = new HashMap<>();

        for (String library : libraries) {
            Path libraryPath = Path.of(library);

            Path librariesParent = this.findLibrariesParent(libraryPath);
            if (librariesParent == null) {
                System.out.println("fallback to 5-level hardcoded parent for: " + library);
                librariesParent = libraryPath.getParent().getParent().getParent().getParent().getParent();
            }
            String librariesStrParent = librariesParent.toString();


            if (!allLibrariesParent.containsKey(librariesStrParent)) {
                System.out.println("Detected new possible libraries directory: " + librariesStrParent);
                allLibrariesParent.put(librariesStrParent, 1);
            } else {
                allLibrariesParent.compute(librariesStrParent, (k, currentCount) -> currentCount + 1);
            }
        }

        return allLibrariesParent.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private @Nullable String searchForInstaller(String modLoader, String modLoaderVersion, String librariesPath) {
        String installerName = String.format("%s-%s-installer.jar", modLoader, modLoaderVersion);

        System.out.println("Searching for installer: " + installerName);

        Path libPath = Path.of(librariesPath);

        try (Stream<Path> paths = Files.walk(libPath)) {

            return paths
                    .filter(Files::isRegularFile) // path.is_file()
                    .filter(p -> p.getFileName().toString().equals(installerName))
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private @Nullable String getClientJar(String classPath) {
        List<String> libraries = List.of(classPath.split(";"));

        for (String library : libraries) {
            String baseName = Path.of(library).getFileName().toString();
            Matcher matcher = MinecraftClientRegexPattern.matcher(baseName);

            if (matcher.matches()) {
                return library;
            }
        }
        return null;
    }

    private @Nullable String getClientVersionFromName(String name) {
        Matcher matcher = MinecraftClientVersionRegexPattern.matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String mavenToExpandedPath(String coord) {
        String[] parts = coord.split(":");

        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];

        @Nullable String classifier = parts.length > 3 ? parts[3] : null;
        String base = String.format("%s/%s/%s", group, artifact, version);

        String filename = "";

        if (classifier != null) {
            filename = String.format("%s-%s-%s.jar", artifact, version, classifier);
        } else {
            filename = String.format("%s-%s.jar", artifact, version);
        }

        return String.format("%s/%s", base, filename);
    }


    private NeoforgeVersions processNeoforgeVersionData(byte[] fileData, String installerPath) throws Exception {
        FileData versionsData = mapper.readValue(fileData, FileData.class);

        List<String> gameArguments = versionsData.arguments.game;
        int neoformIndex = gameArguments.indexOf("--fml.neoFormVersion");
        int fmlVersionIndex = gameArguments.indexOf("--fml.fmlVersion");
        int neoforgeVersionIndex = gameArguments.indexOf("--fml.neoForgeVersion");

        if (neoformIndex == -1 || fmlVersionIndex == -1 || neoforgeVersionIndex == -1) {
            throw new RuntimeException("Missing arguments in version data");
        }

        String neoformVersion = gameArguments.get(neoformIndex + 1);
        String fmlVersion = gameArguments.get(fmlVersionIndex + 1);
        String gameVersion = versionsData.inheritsFrom;
        String neoforgeVersion = gameArguments.get(neoforgeVersionIndex + 1);

        List<Library> librariesToDownload = versionsData.libraries;
        List<String> libs = new ArrayList<>();
        for (Library lib : librariesToDownload) {
            String name = lib.name;
            String transformedName = this.mavenToExpandedPath(name);
            libs.add(transformedName);
        }

        return new NeoforgeVersions(
                neoformVersion,
                fmlVersion,
                gameVersion,
                libs,
                installerPath,
                neoforgeVersion
        );
    }

    public @Nullable NeoforgeVersions getForgeVersions(String installerPath) throws Exception {
        byte[] fileInput = Files.readAllBytes(Path.of(installerPath));

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileInput))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("version.json")) {
                    byte[] data = zis.readAllBytes();
                    return this.processNeoforgeVersionData(data, installerPath);
                }
                zis.closeEntry();
            }
        }

        return null;
    }

    private boolean testNeoforgeConfiguration(String libsPath, String classPath, NeoforgeVersions neoforgeConfig) {
        String[] classPathLibs = classPath.split(";");
        Set<String> classPathLibsNorm = new HashSet<>();

        for (String classPathLib : classPathLibs) {
            classPathLibsNorm.add(Path.of(classPathLib).normalize().toString());
        }

        for (String library : neoforgeConfig.libraries()) {
            String fullPath = Path.of(libsPath).resolve(library).normalize().toString();

            if (!classPathLibsNorm.contains(fullPath)) {
                System.out.printf("Path %s not found in classpath\n", fullPath);
                return false;
            }
        }

        System.out.println("All dependencies match classpath");
        return true;
    }

    private @Nullable NeoforgeVersions guessNeoforgeVersion(String librariesDirectory, String classPath, String mcVersion) throws Exception {
        Path libsPathObj = Path.of(librariesDirectory);
        Path neoforgeLibsPath = libsPathObj.resolve("net").resolve("neoforged").resolve("neoforge");

        try (Stream<Path> stream = Files.walk(neoforgeLibsPath)) {

            Iterable<Path> iterable = stream::iterator;

            for (Path path : iterable) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                String fileName = path.getFileName().toString();
                Matcher matcher = NeoforgeInstallerRegexPattern.matcher(fileName);
                if (matcher.find()) {
                    String neoforgeVersion = matcher.group(1);
                    System.out.printf("Attempting neoforge version: %s\n", neoforgeVersion);
                    @Nullable NeoforgeVersions neoforgeVersionsProperties = this.getForgeVersions(path.toString());

                    if (neoforgeVersionsProperties == null) {
                        System.out.printf("Could not find versions properties for: %s\n", path);
                        continue;
                    }

                    if (!Objects.equals(neoforgeVersionsProperties.mcVersion(), mcVersion)) {
                        System.out.printf("Minecraft versions do not match: %s and %s\n", neoforgeVersionsProperties.mcVersion(), mcVersion);
                        continue;
                    }

                    boolean testConfigValid = this.testNeoforgeConfiguration(
                            librariesDirectory, classPath, neoforgeVersionsProperties
                    );

                    if (!testConfigValid) {
                        System.out.printf("Dependencies do not match for: %s", path);
                        continue;
                    }

                    return neoforgeVersionsProperties;
                }
            }
        }

        return null;
    }

    public NeoforgeConfig extractConfig(List<String> commandArguments) throws Exception {
        int cpLabelIndex = commandArguments.indexOf("-cp");

        if (cpLabelIndex == -1) {
            System.out.println(commandArguments);
            throw new IllegalArgumentException("Command arguments does not contain label -cp");
        }

        int cpIndex = cpLabelIndex + 1;
        String classPath = commandArguments.get(cpIndex);
        String librariesPath = this.findMostCommonLibrariesDirectory(classPath);
        System.out.printf("Libraries directory: %s\n", librariesPath);

        @Nullable String clientJar = this.getClientJar(classPath);
        if (clientJar == null) {
            throw new RuntimeException("Unable to find Minecraft client JAR");
        }

        System.out.printf("Minecraft client jar located at: %s\n", clientJar);

        String clientJarName = Path.of(clientJar).getFileName().toString();
        @Nullable String clientVersion = this.getClientVersionFromName(clientJarName);

        if (clientVersion == null) {
            throw new RuntimeException("Unable to determine Minecraft game version from jar name");
        }

        System.out.printf("Minecraft game version: %s\n", clientVersion);

        @Nullable  NeoforgeVersions neoforgeVersionProperties = this.guessNeoforgeVersion(librariesPath, classPath, clientVersion);

        if (neoforgeVersionProperties == null) {
            throw new RuntimeException("No suitable candidate for current runtime arguments found");
        }

        System.out.printf("Neoforge version: %s\n", neoforgeVersionProperties.neoforgeVersion());
        System.out.printf("Installer path: %s\n", neoforgeVersionProperties.installerPath());
        System.out.printf("FML version: %s\n", neoforgeVersionProperties.fmlVersion());
        System.out.printf("Neoform version: %s\n", neoforgeVersionProperties.neoformVersion());

        return new NeoforgeConfig(
                librariesPath,
                clientJar,
                neoforgeVersionProperties.installerPath(),
                neoforgeVersionProperties.neoforgeVersion(),
                neoforgeVersionProperties.fmlVersion(),
                clientVersion,
                neoforgeVersionProperties.neoformVersion(),
                commandArguments.get(0),
                classPath
        );
    }




}
