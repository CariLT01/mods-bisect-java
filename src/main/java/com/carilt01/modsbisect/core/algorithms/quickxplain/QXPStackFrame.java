package com.carilt01.modsbisect.core.algorithms.quickxplain;

import java.util.ArrayList;
import java.util.List;

public class QXPStackFrame {
    public List<String> delta;
    public List<String> bg;
    public List<String> suspect;
    public QXPIsolationStage stage;
    public List<String> s1;
    public List<String> s2;
    public List<String> delta2;

    public QXPStackFrame() {
        this.delta = new ArrayList<>();
        this.bg = new ArrayList<>();
        this.suspect = new ArrayList<>();
        this.s1 = new ArrayList<>();
        this.s2 = new ArrayList<>();
        this.delta2 = new ArrayList<>();
    }
}
