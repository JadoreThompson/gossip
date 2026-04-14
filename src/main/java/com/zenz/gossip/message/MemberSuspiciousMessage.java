package com.zenz.gossip.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberSuspiciousMessage implements Message {

    private final MessageType type = MessageType.MEMBER_SUSPICIOUS;

    private final String nodeId;

    private final String target;

    private final long incarnation;
}
