package com.zenz.gossip.util;


import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.PingRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;

@Slf4j
public class Utils {

    public static void addPendingMessages(
            final PingRequest pingRequest,
            final PendingMessages pendingMessages,
            final MemberList memberList,
            final long curRound) {
        synchronized (pendingMessages) {
            final Iterator<Message> iterator = pendingMessages.iterator();

            final long maxRounds = (long) Math.ceil(
                    Math.log(memberList.size()) / Math.log(2)
            );

            log.info("size {}", memberList.size());

            while (iterator.hasNext()) {
                final Message message = iterator.next();

                log.info("Message {} maxRounds {} curRound {}",
                        message, maxRounds, curRound);

                if (curRound - message.getRound() >= maxRounds) {
                    iterator.remove();
                } else {
                    pingRequest.getPayload().add(message);
                }
            }
        }
    }
}