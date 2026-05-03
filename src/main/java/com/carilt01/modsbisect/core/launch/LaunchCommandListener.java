package com.carilt01.modsbisect.core.launch;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.nio.file.Path;
import java.util.*;

public class LaunchCommandListener {

    private final SystemInfo si = new SystemInfo();
    private final OperatingSystem os = si.getOperatingSystem();

    private static final Set<Long> seen = new HashSet<>();
    private NeoforgeCommandBuilder commandBuilder = new NeoforgeCommandBuilder();

    public LaunchCommandListener() {

    }

    public List<String> listenForGame(Path modsPath) throws Exception {
        while (true) {
            List<OSProcess> processes = os.getProcesses();


            for (OSProcess p : processes) {
                try {
                    String name = p.getName().toLowerCase();

                    if (name.contains("java")) {
                        long pid = p.getProcessID();

                        if (!seen.contains(pid)) {
                            System.out.println("New JAVA process detected");

                            String fullCmdLine = p.getCommandLine();


                            List<String> cmdLine = CommandSplitter.splitCommand(fullCmdLine);
                            seen.add(pid);

                            List<String> builtCommand;
                            try {
                                builtCommand = this.buildCommand(cmdLine, modsPath);
                            } catch (Exception e) {
                                System.out.println("Failed to build command. Please use a \"better\" launcher like Prism.");
                                e.printStackTrace();
                                continue;
                            }

                            System.out.println("Attempting to terminate process");
                            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
                            return builtCommand;


                        }
                    }
                } catch (Exception ignored) {

                }
            }
        }
    }

    private List<String> buildCommand(List<String> args, Path modsPath) throws Exception {
        return this.commandBuilder.buildCommand(args, modsPath.toString());
    }
}
