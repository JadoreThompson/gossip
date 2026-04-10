package com.zenz.gossip.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MemberAliveMessage.class, name = "ALIVE"),
        @JsonSubTypes.Type(value = RandomMessage.class, name = "RANDOM_MESSAGE"),
        @JsonSubTypes.Type(value = MemberDeadMessage.class, name = "MEMBER_DEAD"),
})
public interface Message {

    MessageType getType();
}
