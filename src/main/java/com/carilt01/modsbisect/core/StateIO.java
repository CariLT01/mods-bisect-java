package com.carilt01.modsbisect.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StateIO {

    private boolean hasLoaded = false;
    private boolean fileLoadSuccess = false;

    private final Set<String> stateIDs = new HashSet<>();
    private final Map<String, Integer> idToIndex = new HashMap<>();
    private @Nullable String cursorPosition = null;

    private @Nullable StatesFileData fileData = null;

    private final ObjectMapper mapper = new ObjectMapper();

    private final String stateFile;

    public StateIO(String stateFile) {

        this.stateFile = stateFile;

        try {
            this.loadFile(this.stateFile);

            if (this.fileData.states == null) {
                throw new IOException("Corrupt file");
            }

            this.fileLoadSuccess = true;
            this.hasLoaded = true;
        } catch (IOException e) {
            System.out.println("error: failed to load save states: " + e.getMessage());
            this.fileLoadSuccess = false;
            this.fileData = new StatesFileData();
            this.hasLoaded = true;
            e.printStackTrace();
        }
    }

    private void loadFile(String stateFile) throws IOException {
        String statesContent = Files.readString(Path.of(stateFile));
        StatesFileData fileData = this.mapper.readValue(statesContent, StatesFileData.class);

        int i = 0;
        for (StateData state : fileData.states) {
            this.stateIDs.add(state.id());
            this.idToIndex.put(state.id(), i);
            i++;
        }
        this.fileData = fileData;
    }

    private void saveFile(String stateFile) throws IOException {
        this.mapper.writeValue(new File(stateFile), this.fileData);
    }

    public boolean isFileLoadSuccess() {
        return fileLoadSuccess;
    }

    public void selectHead(String id) throws RuntimeException {
        if (!fileLoadSuccess) {
            throw new RuntimeException("Cannot select HEAD when the file was not successfully loaded");
        }

        if (!this.stateIDs.contains(id)) {
            throw new RuntimeException("ID is not in set");
        }

        this.cursorPosition = id;
        this.hasLoaded = true;
    }

    public @Nullable StatesFileData getFileData() {
        return fileData;
    }

    public void addAndSaveState(ResolverState state) throws IOException {
        if (!this.hasLoaded) {
            throw new RuntimeException("StateIO has not been loaded yet");
        }

        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();

        StateData newState = new StateData(
                uuidString, // id
                this.cursorPosition, // parent
                state, // data
                Map.of() // metadata
        );

        this.cursorPosition = uuidString; // move head
        assert this.fileData != null;
        this.fileData.states.add(newState);

        this.saveFile(this.stateFile);

    }

    public @Nullable String getCursorPosition() {
        return cursorPosition;
    }

    public StateData getStateAt(String id) {
        int index = this.idToIndex.get(id);
        assert this.fileData != null;
        return this.fileData.states.get(index);
    }
}
