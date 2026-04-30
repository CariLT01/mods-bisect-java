package com.carilt01.modsbisect.core.algorithms.quickxplain;

import com.carilt01.modsbisect.core.algorithms.AlgorithmSaveState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QXPSaveState implements AlgorithmSaveState {

    public Map<String, Boolean> cache = new HashMap<>();
    public List<QXPStackFrame> stack = new ArrayList<>();
    public List<List<String>> results = new ArrayList<>();

    public QXPSaveState() {

    }
}
