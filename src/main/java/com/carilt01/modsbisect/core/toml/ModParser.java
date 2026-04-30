package com.carilt01.modsbisect.core.toml;

import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.tomlj.TomlArray;

import java.util.*;

public class ModParser {

    public static ModFile parse(TomlParseResult result) {
        ModFile modFile = new ModFile();

        // ---- 1. PARSE MODS ----
        List<Mod> mods = new ArrayList<>();
        Set<String> modIdsInFile = new HashSet<>();

        Object modsObj = result.get("mods");
        if (modsObj instanceof TomlArray modsArray) {
            for (int i = 0; i < modsArray.size(); i++) {
                Object entry = modsArray.get(i);
                if (entry instanceof TomlTable modTable) {
                    String modId = modTable.getString("modId");
                    if (modId != null) {
                        Mod mod = new Mod();
                        mod.modId = modId;
                        mods.add(mod);
                        modIdsInFile.add(modId);
                    }
                }
            }
        }
        modFile.mods = mods;

        // ---- 2. PARSE DEPENDENCIES ----
        Map<String, List<Dependency>> finalDependencies = new HashMap<>();
        Object depsObj = result.get("dependencies");

        // Equivalent to: if dependencies is not None:
        if (depsObj instanceof TomlTable depsRoot) {

            // Equivalent to: for dependency_mod in dependencies.values():
            for (String key : depsRoot.keySet()) {
                Object value = depsRoot.get(key);

                // Equivalent to: if not isinstance(dependency_mod, list): continue
                if (!(value instanceof TomlArray depArray)) {
                    continue;
                }

                List<Dependency> validDepsForMod = new ArrayList<>();

                // Equivalent to: for dependency in dependency_mod:
                for (int i = 0; i < depArray.size(); i++) {
                    Object item = depArray.get(i);
                    if (!(item instanceof TomlTable depTable)) continue;

                    String depId = depTable.getString("modId");
                    String type = depTable.getString("type");
                    Boolean mandatory = depTable.getBoolean("mandatory");

                    // Filter: if required.lower() != "required": continue
                    if (type != null && !type.equalsIgnoreCase("required")) {
                        continue;
                    }

                    // Filter: if dependency_id == "minecraft" or "neoforge": continue
                    if ("minecraft".equals(depId) || "neoforge".equals(depId)) {
                        continue;
                    }

                    // Filter: remove deps that are actually mods in the same JAR
                    // (Equivalent to the Python: for mod_id in mod_list: remove from deps)
                    if (depId != null && modIdsInFile.contains(depId)) {
                        continue;
                    }

                    if (depId != null) {
                        Dependency dep = new Dependency();
                        dep.modId = depId;
                        dep.type = type;
                        dep.mandatory = (mandatory != null) ? mandatory : false;
                        validDepsForMod.add(dep);
                    }
                }

                if (!validDepsForMod.isEmpty()) {
                    finalDependencies.put(key, validDepsForMod);
                }
            }
        }

        modFile.dependencies = finalDependencies;
        return modFile;
    }
}