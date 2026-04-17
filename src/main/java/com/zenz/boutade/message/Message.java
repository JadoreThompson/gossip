package com.zenz.boutade.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MemberAliveMessage.class, name = "MEMBER_ALIVE"),
        @JsonSubTypes.Type(value = RandomMessage.class, name = "RANDOM_MESSAGE"),
        @JsonSubTypes.Type(value = MemberDeadMessage.class, name = "MEMBER_DEAD"),
        @JsonSubTypes.Type(value = MemberSuspiciousMessage.class, name = "MEMBER_SUSPICIOUS"),
        @JsonSubTypes.Type(value = JoinMessage.class, name = "MEMBER_JOIN")
})
public interface Message {

    /**
     * The type of the message
     */
    MessageType getType();

    /**
     * The round the message was conceived in
     */
    long getRound();

    void setRound(long round);

    UUID getId();
}