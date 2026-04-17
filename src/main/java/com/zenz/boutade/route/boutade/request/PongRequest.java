package com.zenz.boutade.route.boutade.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PongRequest {

    private final RequestType type = RequestType.PONG;

    private final String nodeId;
}
