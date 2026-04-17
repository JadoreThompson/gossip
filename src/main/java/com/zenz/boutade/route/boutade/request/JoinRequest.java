package com.zenz.boutade.route.boutade.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JoinRequest {

    private final RequestType type;

    private final String nodeId;

    private final long incarnation;

    @Override
    public String toString() {
        return "JoinRequest{" +
                "type=" + type +
                ", nodeId='" + nodeId + '\'' +
                ", incarnation=" + incarnation +
                '}';
    }
}
