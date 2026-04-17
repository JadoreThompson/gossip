package com.zenz.boutade.route.boutade.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@RequiredArgsConstructor
public class MessageRequest {

    private final JsonNode data;
}