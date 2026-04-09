package com.zenz.gossip.route.api.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PingRequest<T> {

    private final RequestType type =  RequestType.PING;

    private final String nodeId;

    private final T payload;
}
