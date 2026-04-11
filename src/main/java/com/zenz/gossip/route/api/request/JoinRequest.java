package com.zenz.gossip.route.api.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;

@Getter
@RequiredArgsConstructor
public class JoinRequest {

    private final RequestType type = RequestType.JOIN;

    private final String nodeId;

    private final InetSocketAddress address;

    private final long incarnation;
}
