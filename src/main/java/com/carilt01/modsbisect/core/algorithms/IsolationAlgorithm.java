package com.carilt01.modsbisect.core.algorithms;

import com.carilt01.modsbisect.core.ThrowingFunction;
import com.carilt01.modsbisect.core.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public interface IsolationAlgorithm<T> {
    List<Unit> run(@NotNull List<Unit> units, @NotNull ThrowingFunction<List<Unit>, Boolean> testFunction, @Nullable T loadState) throws Exception;
}
