package com.zenz.gossip.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class MemberAliveMessage implements Message {

    private final MessageType type = MessageType.MEMBER_ALIVE;

    private final String nodeId;

    private final long incarnation;

    private final String target;

    @Setter
    @JsonIgnore
    private long round;
}
