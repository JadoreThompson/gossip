package com.zenz.gossip.route.api.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;

@Getter
@RequiredArgsConstructor
public class JoinRequest {

    private final RequestType type;

    private final String nodeId;

    private final InetSocketAddress address;

    private final long incarnation;

    @Override
    public String toString() {
        return "JoinRequest{" +
                "type=" + type +
                ", nodeId='" + nodeId + '\'' +
                ", address=" + address +
                ", incarnation=" + incarnation +
                '}';
    }
}
