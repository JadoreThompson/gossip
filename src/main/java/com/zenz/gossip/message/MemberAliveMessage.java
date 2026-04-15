package com.zenz.gossip.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberAliveMessage implements Message {

    private final MessageType type = MessageType.MEMBER_ALIVE;

    private final String nodeId;

    private final long incarnation;

    private final String target;
}
