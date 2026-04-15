package com.zenz.gossip.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class MemberDeadMessage implements Message {

    private final MessageType type = MessageType.MEMBER_DEAD;

    private final String nodeId;

    private final String target;

    private final long incarnation;

    @Setter
    @JsonIgnore
    private long round;
}
