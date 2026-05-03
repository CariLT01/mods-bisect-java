package com.carilt01.modsbisect.core;

import com.carilt01.modsbisect.core.launch.LaunchCommandListener;

import java.nio.file.Path;
import java.util.List;

public class BisectApplication {

    private LaunchCommandListener listener;
    private ModsConflictDetector detector;
    private Path modsPath;

    public BisectApplication(Path modsPath) {
        this.listener = new LaunchCommandListener();


        this.modsPath = modsPath;
    }

    public void run() {

        try {
            List<String> args = this.listener.listenForGame(this.modsPath);
            System.out.printf("Game arguments: %s\n", args);

            this.detector = new ModsConflictDetector(this.modsPath.toString(), new StateIO("save_states.json"), args);

            List<Unit> failingUnits = this.detector.isolate();
            System.out.println("Found "+ failingUnits.size() + " failing units");

        } catch (Exception e) {
            System.out.println("Application crashed");
            e.printStackTrace();
        }


    }

}
