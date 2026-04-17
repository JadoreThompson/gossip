package com.zenz.boutade.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class RandomMessage implements Message {

    private final MessageType type = MessageType.RANDOM_MESSAGE;

    private UUID id = UUID.randomUUID();

    private final JsonNode data;

    @Setter
    @JsonIgnore
    private long round;

    @Override
    public String toString() {
        return "RandomMessage{" +
                "type=" + type +
                ", id=" + id +
                ", data='" + data + '\'' +
                ", round=" + round +
                '}';
    }
}
