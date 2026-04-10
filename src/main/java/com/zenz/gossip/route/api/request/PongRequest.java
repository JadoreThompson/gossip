package com.zenz.gossip.route.api.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PongRequest {

    private final RequestType type =  RequestType.PONG;

    private final String nodeId;
}
