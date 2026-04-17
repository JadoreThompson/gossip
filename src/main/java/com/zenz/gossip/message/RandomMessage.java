package com.zenz.gossip.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class RandomMessage implements Message {

    private final MessageType type = MessageType.RANDOM_MESSAGE;

    private final String data;

    @Setter
    @JsonIgnore
    private long round;

    @Override
    public String toString() {
        return "RandomMessage{" +
                "type=" + type +
                ", data='" + data + '\'' +
                ", round=" + round +
                '}';
    }
}
