package com.carilt01.modsbisect.core.launch;

import com.carilt01.modsbisect.core.Unit;
import com.github.kwhat.jnativehook.GlobalScreen;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class LaunchTester {

    private final String modsPath;
    private final String launchCommand;

    public LaunchTester(@NotNull  String modsPath, @NotNull  String launchCommand) {
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

        GlobalScreen.registerNativeHook();

        NativeKeyListener listener = new NativeKeyListener() {
            @Override
            public void nativeKeyTyped(NativeKeyEvent e) {
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
        process.waitFor();

        GlobalScreen.removeNativeKeyListener(listener);
        GlobalScreen.unregisterNativeHook();

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
            files.add(Path.of(u.rootJar()).getFileName().toString());
        }
        return files;
    }

    private boolean testInternal(List<Unit> units) throws Exception {
        Path modsPathObj = Path.of(modsPath);
        Path tempPathObj = Path.of("temp");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPathObj)) {
            for (Path entry : stream) {
                Path temporaryPath = tempPathObj.resolve(entry.getFileName());
                Files.move(entry, temporaryPath);
            }
        }

        Set<String> modsSet = new HashSet<>(this.unitToFiles(units));

        for (String jarFile : modsSet) {
            String jarBaseName = Path.of(jarFile).getFileName().toString();
            Path src = tempPathObj.resolve(jarBaseName);
            Path dst = modsPathObj.resolve(jarBaseName);

            Files.move(src, dst);
        }

        return this.launchAndTest();
    }

    public boolean test(List<Unit> units) throws Exception {
        return this.testInternal(units);
    }
}
