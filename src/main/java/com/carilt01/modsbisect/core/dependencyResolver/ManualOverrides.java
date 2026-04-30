package com.carilt01.modsbisect.core.dependencyResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManualOverrides {
    public Map<String, List<String>> overrides;
    public Map<String, String> metadata;

    public ManualOverrides() {
        this.overrides = new HashMap<>();
        this.metadata = new HashMap<>();
    }
}
