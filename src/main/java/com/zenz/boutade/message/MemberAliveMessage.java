package com.zenz.boutade.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class MemberAliveMessage implements Message {

    private final MessageType type = MessageType.MEMBER_ALIVE;

    private UUID id = UUID.randomUUID();

    private final String nodeId;

    private final long incarnation;

    private final String target;

    @Setter
    @JsonIgnore
    private long round;

    @Override
    public String toString() {
        return "MemberAliveMessage{" +
                "type=" + type +
                ", id=" + id +
                ", nodeId='" + nodeId + '\'' +
                ", incarnation=" + incarnation +
                ", target='" + target + '\'' +
                ", round=" + round +
                '}';
    }
}
