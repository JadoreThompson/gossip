package com.zenz.boutade.util;

import com.zenz.boutade.message.MemberAliveMessage;
import com.zenz.boutade.message.Message;
import com.zenz.boutade.route.boutade.request.PingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UtilsTest {

    @Mock
    private MemberList memberList;

    @Test
    void utils_prunesOldMessages_basedOnLogN() {
        // log2(8) = 3, so messages older than 3 rounds are pruned
        when(memberList.size()).thenReturn(8);

        MemberAliveMessage oldMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        oldMsg.setRound(4L);

        MemberAliveMessage recentMsg = new MemberAliveMessage("node-3", 2L, "node-4");
        recentMsg.setRound(7L);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(List.<Message>of(oldMsg, recentMsg).iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");

        // curRound=8, oldMsg round=4, diff=4 > ceil(log2(8))=3 -> pruned
        // curRound=8, recentMsg round=7, diff=1 < 3 -> kept
        Utils.addPendingMessages(pingRequest, buffer, memberList, 8L);

        assertEquals(1, pingRequest.getPayload().size(), "Only the recent message should be in the payload");
        assertTrue(pingRequest.getPayload().contains(recentMsg));
        verify(buffer).remove(oldMsg);
    }

    @Test
    void utils_keepsNewMessages_whenWithinLogNThreshold() {
        // log2(4) = 2
        when(memberList.size()).thenReturn(4);

        MemberAliveMessage recentMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        recentMsg.setRound(99L);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(List.<Message>of(recentMsg).iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");
        // curRound=100, recentMsg round=99, diff=1 < ceil(log2(4))=2 -> kept
        Utils.addPendingMessages(pingRequest, buffer, memberList, 100L);

        assertEquals(1, pingRequest.getPayload().size());
        verify(buffer, never()).remove(any());
    }

    @Test
    void utils_removesAllMessages_whenVeryOld() {
        // log2(2) = 1
        when(memberList.size()).thenReturn(2);

        MemberAliveMessage oldMsg = new MemberAliveMessage("node-1", 1L, "node-2");
        oldMsg.setRound(1L);

        PendingMessages buffer = mock(PendingMessages.class);
        when(buffer.iterator()).thenReturn(List.<Message>of(oldMsg).iterator());

        PingRequest pingRequest = new PingRequest("node-1", "node-2");
        // curRound=8, oldMsg round=1, diff=7 >= ceil(log2(2))=1 -> pruned
        Utils.addPendingMessages(pingRequest, buffer, memberList, 8L);

        assertEquals(0, pingRequest.getPayload().size(), "Stale message should not be in the payload");
        verify(buffer).remove(oldMsg);
    }
}
