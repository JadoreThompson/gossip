package com.zenz.gossip.route.api.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MessageRequest {

    private final String data;
}