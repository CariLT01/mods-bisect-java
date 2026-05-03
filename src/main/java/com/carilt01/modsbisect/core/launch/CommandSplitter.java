package com.carilt01.modsbisect.core.launch;

import java.util.ArrayList;
import java.util.List;

public class CommandSplitter {
    public static List<String> splitCommand(String commandLine) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (escaped) {
                currentArg.append(c);
                escaped = false;
            } else if (c == '\\') {
                // Check if next char is a quote to handle escaped quotes \"
                if (i + 1 < commandLine.length() && commandLine.charAt(i + 1) == '"') {
                    escaped = true;
                } else {
                    currentArg.append(c);
                }
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }
}
