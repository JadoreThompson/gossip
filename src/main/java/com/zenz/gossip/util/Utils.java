package com.zenz.gossip.util;


import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.PingRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Utils {

    public static void addPendingMessages(
            final PingRequest pingRequest,
            final PendingMessages pendingMessages,
            final MemberList memberList,
            final long curRound) {
        synchronized (pendingMessages) {
            final long maxRounds = memberList.size() < 2 ? 1 : (long) Math.ceil(
                    Math.log(memberList.size())
            );

            final List<Message> toRemove = new ArrayList<>();
            for (Message message : pendingMessages) {
                if (curRound - message.getRound() > maxRounds) {
                    toRemove.add(message);
                } else {
                    pingRequest.getPayload().add(message);
                }
            }
            for (Message message : toRemove) {
                pendingMessages.remove(message);
            }
        }
    }
}