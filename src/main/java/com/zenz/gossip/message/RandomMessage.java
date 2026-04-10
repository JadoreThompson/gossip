package com.zenz.gossip.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RandomMessage implements Message {

    private final MessageType type = MessageType.RANDOM_MESSAGE;

    private final String data;
}
