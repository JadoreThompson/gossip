package com.zenz.gossip.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.net.InetSocketAddress;

@Getter
@Setter
@RequiredArgsConstructor
public class JoinMessage implements Message {

    private final MessageType type = MessageType.MEMBER_JOIN;

    private final String nodeId;

    private final InetSocketAddress address;

    private final long incarnation;

    private long round;

    @Override
    public String toString() {
        return "JoinMessage{" +
                "type=" + type +
                ", nodeId='" + nodeId + '\'' +
                ", address=" + address +
                ", incarnation=" + incarnation +
                ", round=" + round +
                '}';
    }
}
