package com.zenz.gossip.util;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.PingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UtilsTest {

    @Mock
    private ClusterConfig clusterConfig;

    @Mock
    private MemberList memberList;


    @BeforeEach
    public void init() {
    }

    @Test
    void utils_prunesOldMessages_basedOnLogN() throws Exception {
        when(memberList.size()).thenReturn(8);

        List<Message> messages = new ArrayList<>();

        MemberAliveMessage oldMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        oldMsg.setRound(5L);
        messages.add(oldMsg);

        MemberAliveMessage recentMsg = new MemberAliveMessage("node-3", 2L, "node-4");
        recentMsg.setRound(7L);
        messages.add(recentMsg);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(messages.iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");

        Utils.addPendingMessages(pingRequest, buffer, memberList, 8L);

        assertEquals(2, pingRequest.getPayload().size());
        assertEquals(1, messages.size(), "Old message created in round 5 should be pruned. log(8) = 3");
    }

    @Test
    void utils_keepsNewMessages_whenWithinLogNThreshold() throws Exception {
        when(memberList.size()).thenReturn(4);

        List<Message> messages = new ArrayList<>();

        MemberAliveMessage recentMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        recentMsg.setRound(99L);
        messages.add(recentMsg);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(messages.iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");
        Utils.addPendingMessages(pingRequest, buffer, memberList, 100L);

        assertEquals(1, pingRequest.getPayload().size());
        assertEquals(
                1,
                messages.size(),
                "Message should have been retained within buffer max rounds haven't passed");
    }

    @Test
    void utils_removesAllMessages_whenVeryOld() throws Exception {
        when(memberList.size()).thenReturn(2);
        List<Message> messages = new ArrayList<>();

        MemberAliveMessage oldMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        oldMsg.setRound(1L);
        messages.add(oldMsg);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(messages.iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");

        Utils.addPendingMessages(pingRequest, buffer, memberList, 8L);

        assertEquals(1, pingRequest.getPayload().size());
        assertEquals(0, messages.size(), "Stale message should have been pruned");
    }
}
