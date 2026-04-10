package com.zenz.gossip.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberDeadMessage implements Message {

    private final MessageType type = MessageType.DEAD;

    private final String nodeId;
}
