package com.carilt01.modsbisect.core;

import com.carilt01.modsbisect.core.algorithms.AlgorithmSaveState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record StateData(
        String id,
        @Nullable String parent,
        ResolverState data,
        Map<String, String> metadata // extra field, just for future backward-compatible features
) {
}
