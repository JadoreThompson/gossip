package com.zenz.boutade.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class MemberDeadMessage implements Message {

    private final MessageType type = MessageType.MEMBER_DEAD;

    private UUID id = UUID.randomUUID();

    private final String nodeId;

    private final String target;

    private final long incarnation;

    @Setter
    @JsonIgnore
    private long round;

    @Override
    public String toString() {
        return "MemberDeadMessage{" +
                "type=" + type +
                ", id=" + id +
                ", nodeId='" + nodeId + '\'' +
                ", target='" + target + '\'' +
                ", incarnation=" + incarnation +
                ", round=" + round +
                '}';
    }
}
