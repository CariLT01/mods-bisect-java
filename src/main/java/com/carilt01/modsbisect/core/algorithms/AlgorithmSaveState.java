package com.carilt01.modsbisect.core.algorithms;

import com.carilt01.modsbisect.core.algorithms.quickxplain.QXPSaveState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type" // This field will appear in your JSON
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QXPSaveState.class, name = "qxp"),
})
public interface AlgorithmSaveState {
}
