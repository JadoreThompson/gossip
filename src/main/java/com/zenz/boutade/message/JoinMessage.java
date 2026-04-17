package com.zenz.boutade.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.net.InetSocketAddress;
import java.util.UUID;

@Getter
@Setter
@RequiredArgsConstructor
public class JoinMessage implements Message {

    private final MessageType type = MessageType.MEMBER_JOIN;

    private UUID id = UUID.randomUUID();

    private final String nodeId;

    private final InetSocketAddress address;

    private final long incarnation;

    @JsonIgnore
    private long round;

    @Override
    public String toString() {
        return "JoinMessage{" +
                "type=" + type +
                ", id=" + id +
                ", nodeId='" + nodeId + '\'' +
                ", address=" + address +
                ", incarnation=" + incarnation +
                ", round=" + round +
                '}';
    }
}
