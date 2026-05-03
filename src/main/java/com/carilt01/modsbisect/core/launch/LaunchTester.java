package com.carilt01.modsbisect.core.launch;

import com.carilt01.modsbisect.core.Unit;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class LaunchTester {

    private final String modsPath;
    private final List<String> launchCommand;

    static {
        // Register the native hook once for the entire JVM lifetime
        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }
        } catch (NativeHookException e) {
            throw new ExceptionInInitializerError("Cannot register global native hook: " + e.getMessage());
        }

        // Cleanly unregister the hook when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (GlobalScreen.isNativeHookRegistered()) {
                    GlobalScreen.unregisterNativeHook();
                }
            } catch (NativeHookException e) {
                System.out.println("Failed to unregister native hook on shutdown" + e);
            }
        }));
    }

    public LaunchTester(@NotNull  String modsPath, @NotNull List<String> launchCommand) {
        this.modsPath = modsPath;
        this.launchCommand = launchCommand;
    }

    private File getCurrentWorkingDirectory() {
        String cwd = System.getProperty("user.dir");
        return new File(cwd);
    }

    private Process launchProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(this.launchCommand);

        pb.directory(this.getCurrentWorkingDirectory());
        pb.redirectErrorStream(true);

        return pb.start();
    }

    private boolean launchAndTest() throws Exception {
        Process process = launchProcess();
        AtomicReference<Boolean> result = new AtomicReference<>(null);

        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        outputThread.setDaemon(true);
        outputThread.start();

        NativeKeyListener listener = new NativeKeyListener() {
            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                if (result.get() != null) return;

                if (e.getKeyCode() == NativeKeyEvent.VC_Y) {
                    result.set(true);
                    process.destroy();
                } else if (e.getKeyCode() == NativeKeyEvent.VC_N) {
                    result.set(false);
                    process.destroy();
                }
            }
        };

        GlobalScreen.addNativeKeyListener(listener);
        try {
            process.waitFor();
        } finally {
            GlobalScreen.removeNativeKeyListener(listener);
        }

        if (result.get() == null) {
            System.out.println("Process detected crashed");
            return false;
        }

        System.out.println("User inputted: " + result.get());
        return result.get();
    }

    private List<String> unitToFiles(List<Unit> units) {
        List<String> files = new ArrayList<>();
        for (Unit u : units) {
            for (String jar : u.jars()) {
                System.out.println("DEBUG: Unit has jar: " + jar);
                files.add(Path.of(jar).getFileName().toString());
            }
        }
        return files;
    }

    private boolean testInternal(List<Unit> units) throws Exception {



        Path cwd = Paths.get("").toAbsolutePath();

        Path modsPathObj = Path.of(modsPath);
        Path tempPathObj = cwd.resolve("temp");
        Files.createDirectories(tempPathObj);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPathObj)) {
            for (Path entry : stream) {
                try {
                    Path temporaryPath = tempPathObj.resolve(entry.getFileName());
                    Files.move(entry, temporaryPath);
                } catch (IOException e) {
                    System.out.printf("error: failed to move file: %s\n", e.getMessage());
                }

            }
        }

        Set<String> modsSet = new HashSet<>(this.unitToFiles(units));

        for (String jarFile : modsSet) {
            String jarBaseName = Path.of(jarFile).getFileName().toString();
            Path src = tempPathObj.resolve(jarBaseName);
            Path dst = modsPathObj.resolve(jarBaseName);

            try {
                Files.move(src, dst);

            } catch (IOException e) {
                System.out.printf("Failed to move: %s\n", e.getMessage());
            }
        }

        return this.launchAndTest();
    }

    public boolean test(List<Unit> units) throws Exception {

        System.out.printf("Testing with %d units\n", units.size());

        return this.testInternal(units);
    }
}
