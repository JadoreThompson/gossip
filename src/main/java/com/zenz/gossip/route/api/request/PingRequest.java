package com.zenz.gossip.route.api.request;

import com.zenz.gossip.message.Message;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class PingRequest {

    private final RequestType type = RequestType.PING;

    private final String nodeId;

    private final String target;

    private List<Message> payload = new ArrayList<>();

    @Override
    public String toString() {
        return "PingRequest{" +
                "type=" + type +
                ", nodeId='" + nodeId + '\'' +
                ", target='" + target + '\'' +
                ", payload=" + payload +
                '}';
    }
}