package com.zenz.gossip.route.api;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.message.MemberDeadMessage;
import com.zenz.gossip.message.MemberSuspiciousMessage;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.exception.NotFoundException;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.MemberStatus;
import com.zenz.gossip.util.PendingMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    @Mock
    private MemberList memberList;

    @Mock
    private PendingMessages pendingMessages;

    @Mock
    private ClusterConfig clusterConfig;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private ObjectMapper objectMapper;

    private ApiController apiController;

    @BeforeEach
    void setUp() throws Exception {
        apiController = new ApiController(memberList, pendingMessages, clusterConfig);

        apiController.setHttpClient(httpClient);

        Field objectMapperField = ApiController.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(apiController, objectMapper);
    }

    @Test
    void ping_throwsWhenTargetNotFound() throws IOException, InterruptedException {
        String requesterId = "node-1";
        String targetId = "node-2";

        PingRequest request = new PingRequest(requesterId, targetId);

        Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));

        when(memberList.get(requesterId)).thenReturn(requester);
        when(memberList.get(targetId)).thenReturn(null);
        when(clusterConfig.getNodeId()).thenReturn("edge-node");

        assertThrows(NotFoundException.class, () -> apiController.ping(request));

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void ping_forwardsPingAndReturnsPongWhenTargetFound() throws Exception {
        String requesterId = "node-1";
        String targetId = "node-2";
        String edgeNodeId = "edge-node";

        PingRequest request = new PingRequest(requesterId, targetId);

        Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));
        Member target = new Member(targetId, new InetSocketAddress("localhost", 9090));

        when(clusterConfig.getNodeId()).thenReturn(edgeNodeId);
        when(memberList.get(requesterId)).thenReturn(requester);
        when(memberList.get(targetId)).thenReturn(target);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        apiController.ping(request);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        verify(httpClient, times(2)).send(captor.capture(), any());

        HttpRequest pingCall = captor.getAllValues().get(0);
        assertTrue(
                pingCall.uri().toString().contains("/ping"),
                "Expected first call to be a /ping request"
        );

        HttpRequest pongCall = captor.getAllValues().get(1);
        assertTrue(
                pongCall.uri().toString().contains("/pong"),
                "Expected second call to be a /pong request"
        );

        ArgumentCaptor<PingRequest> pingCaptor = ArgumentCaptor.forClass(PingRequest.class);
        verify(objectMapper, atLeastOnce()).writeValueAsString(pingCaptor.capture());

        PingRequest forwardedPing = pingCaptor.getValue();
        assertEquals(targetId, forwardedPing.getTarget());
        assertEquals(
                edgeNodeId,
                forwardedPing.getNodeId(),
                "Forwarded ping should originate from edge node"
        );
        assertEquals(
                targetId,
                forwardedPing.getTarget(),
                "Forwarded ping should target the correct node"
        );
    }

    @Test
    void ping_returnsPongWhenTargetIsSelf() throws Exception {
        String requesterId = "node-1";
        String selfId = "node-1";

        PingRequest request = new PingRequest(requesterId, selfId);

        Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));

        when(clusterConfig.getNodeId()).thenReturn(selfId);
        when(memberList.get(requesterId)).thenReturn(requester);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        apiController.ping(request);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        verify(httpClient, times(1)).send(captor.capture(), any());

        HttpRequest requestSent = captor.getValue();

        assertTrue(
                requestSent.uri().toString().contains("/pong"),
                "Expected only a /pong request when target is self"
        );
    }

    @Test
    void ping_handlesMemberSuspicious_updatesStatusAndForwardsMessage() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
        Member suspiciousMember = new Member("node-3", new InetSocketAddress("localhost", 8082));

        MemberSuspiciousMessage msg =
                new MemberSuspiciousMessage("node-1", "node-3", 1L);

        request.getPayload().add(msg);

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-2")).thenReturn(target);
        when(memberList.get("node-3")).thenReturn(suspiciousMember);
        when(clusterConfig.getNodeId()).thenReturn("edge-node");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        apiController.ping(request);

        assertEquals(MemberStatus.SUSPICIOUS, suspiciousMember.getStatus());
        assertEquals(MemberStatus.ALIVE, target.getStatus());
    }

    @Test
    void ping_handlesMemberSuspiciousForSelf_incrementsIncarnationAndAddsAliveMessage() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

        when(memberList.get("node-1")).thenReturn(requester);
        when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(clusterConfig.getIncarnation()).thenReturn(5L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        MemberSuspiciousMessage msg =
                new MemberSuspiciousMessage("node-3", "node-2", 1L);

        request.getPayload().add(msg);

        apiController.ping(request);

        verify(clusterConfig).setIncarnation(6L);
        verify(pendingMessages).add(any(MemberAliveMessage.class));
    }

    @Test
    void ping_handlesMemberAlive_updatesIncarnationWhenHigher() throws Exception {
        PingRequest request = new PingRequest("node-1", "edge");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member member = new Member("node-3", new InetSocketAddress("localhost", 8081));
        member.setIncarnation(3L);
        member.setStatus(MemberStatus.SUSPICIOUS);

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-3")).thenReturn(member);
        when(clusterConfig.getNodeId()).thenReturn("edge");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        MemberAliveMessage msg = new MemberAliveMessage("node-1", 5L, "node-3");
        request.getPayload().add(msg);

        apiController.ping(request);

        assertEquals(MemberStatus.ALIVE, member.getStatus());
        assertEquals(5L, member.getIncarnation());
    }

    @Test
    void ping_handlesMemberDead_removesMemberAndBuffersMessage() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-3");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member victim = new Member("node-3", new InetSocketAddress("localhost", 8081));

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-3")).thenReturn(victim);
        when(clusterConfig.getNodeId()).thenReturn("edge");
        when(objectMapper.writeValueAsString(any())).thenReturn("");

        MemberDeadMessage msg =
                new MemberDeadMessage(requester.getNodeId(), victim.getNodeId(), 2L);

        request.getPayload().add(msg);

        apiController.ping(request);

        verify(memberList).remove(victim);
        verify(pendingMessages).add(any(MemberDeadMessage.class));
    }

    @Test
    void ping_ignoresStaleMemberSuspiciousWhenIncarnationIsLower() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
        Member staleSuspect = new Member("node-3", new InetSocketAddress("localhost", 8082));
        staleSuspect.setIncarnation(10L);
        staleSuspect.setStatus(MemberStatus.ALIVE);

        MemberSuspiciousMessage msg =
                new MemberSuspiciousMessage("node-1", "node-3", 5L);

        request.getPayload().add(msg);

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-2")).thenReturn(target);
        when(memberList.get("node-3")).thenReturn(staleSuspect);
        when(clusterConfig.getNodeId()).thenReturn("edge-node");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        apiController.ping(request);

        assertEquals(MemberStatus.ALIVE, staleSuspect.getStatus());
        assertEquals(10L, staleSuspect.getIncarnation());
        verify(pendingMessages, never()).add(any(MemberSuspiciousMessage.class));
    }

    @Test
    void ping_ignoresStaleMemberSuspiciousWhenIncarnationEqual() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
        Member staleSuspect = new Member("node-3", new InetSocketAddress("localhost", 8082));
        staleSuspect.setIncarnation(5L);
        staleSuspect.setStatus(MemberStatus.ALIVE);

        MemberSuspiciousMessage msg =
                new MemberSuspiciousMessage("node-1", "node-3", 5L);

        request.getPayload().add(msg);

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-2")).thenReturn(target);
        when(memberList.get("node-3")).thenReturn(staleSuspect);
        when(clusterConfig.getNodeId()).thenReturn("edge-node");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        apiController.ping(request);

        assertEquals(MemberStatus.ALIVE, staleSuspect.getStatus());
        verify(pendingMessages, never()).add(any(MemberSuspiciousMessage.class));
    }

    @Test
    void ping_processesFreshMemberSuspiciousWhenIncarnationIsHigher() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
        Member freshSuspect = new Member("node-3", new InetSocketAddress("localhost", 8082));
        freshSuspect.setIncarnation(5L);
        freshSuspect.setStatus(MemberStatus.ALIVE);

        MemberSuspiciousMessage msg =
                new MemberSuspiciousMessage("node-1", "node-3", 10L);

        request.getPayload().add(msg);

        when(memberList.get("node-1")).thenReturn(requester);
        when(memberList.get("node-2")).thenReturn(target);
        when(memberList.get("node-3")).thenReturn(freshSuspect);
        when(clusterConfig.getNodeId()).thenReturn("edge-node");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        apiController.ping(request);

        assertEquals(MemberStatus.SUSPICIOUS, freshSuspect.getStatus());
        verify(pendingMessages).add(any(MemberSuspiciousMessage.class));
    }

    @Test
    void ping_handlesMemberDeadForSelf_withMatchingIncarnation_incrementsIncarnationAndAddsAlive() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

        when(memberList.get("node-1")).thenReturn(requester);
        when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(clusterConfig.getIncarnation()).thenReturn(5L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-2", 5L);
        request.getPayload().add(msg);

        apiController.ping(request);

        verify(clusterConfig).setIncarnation(6L);
        verify(pendingMessages).add(any(MemberAliveMessage.class));
    }

    @Test
    void ping_handlesMemberDeadForSelf_withHigherIncarnation_incrementsIncarnationAndAddsAlive() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

        when(memberList.get("node-1")).thenReturn(requester);
        when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(clusterConfig.getIncarnation()).thenReturn(5L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-2", 10L);
        request.getPayload().add(msg);

        apiController.ping(request);

        verify(clusterConfig).setIncarnation(11L);
        verify(pendingMessages).add(any(MemberAliveMessage.class));
    }

    @Test
    void ping_handlesMemberDeadForSelf_withLowerIncarnation_incrementsIncarnationAndAddsAlive() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

        when(memberList.get("node-1")).thenReturn(requester);
        when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(clusterConfig.getIncarnation()).thenReturn(10L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-2", 5L);
        request.getPayload().add(msg);

        apiController.ping(request);

        assertEquals(
                10L,
                clusterConfig.getIncarnation(),
                "Incarnation should have remained the same after receiving stale incarnation");
        verify(pendingMessages).add(any(MemberAliveMessage.class));
    }

    @Test
    void ping_handlesMemberDeadForOther_removesMemberWhenExistsAndHigherIncarnation() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
        Member deadMember = new Member("node-3", new InetSocketAddress("localhost", 8082));
        deadMember.setIncarnation(3L);

        lenient().when(memberList.get("node-1")).thenReturn(requester);
        lenient().when(memberList.get("node-2")).thenReturn(target);
        lenient().when(memberList.get("node-3")).thenReturn(deadMember);
        lenient().when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-3", 5L);
        request.getPayload().add(msg);

        apiController.ping(request);

        verify(memberList).remove(deadMember);
        verify(pendingMessages).add(any(MemberDeadMessage.class));
    }

    @Test
    void ping_handlesMemberDeadForOther_doesNotRemoveWhenMemberNotExists() throws Exception {
        PingRequest request = new PingRequest("node-1", "node-2");

        Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
        Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));

        lenient().when(memberList.get("node-1")).thenReturn(requester);
        lenient().when(memberList.get("node-2")).thenReturn(target);
        lenient().when(clusterConfig.getNodeId()).thenReturn("node-2");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-3", 5L);
        request.getPayload().add(msg);

        apiController.ping(request);

        verify(memberList, never()).remove(any(Member.class));
        verify(pendingMessages, never()).add(any(MemberDeadMessage.class));
    }
}