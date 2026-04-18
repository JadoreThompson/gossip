package com.zenz.boutade.service;

import com.zenz.boutade.config.ClusterConfig;
import com.zenz.boutade.message.MemberDeadMessage;
import com.zenz.boutade.route.boutade.request.JoinRequest;
import com.zenz.boutade.route.boutade.request.PingRequest;
import com.zenz.boutade.util.Member;
import com.zenz.boutade.util.MemberList;
import com.zenz.boutade.util.MemberStatus;
import com.zenz.boutade.util.PendingMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.ArrayIterator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailureDetectionServiceTest {

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

    private FailureDetectionService failureDetectionService;

    @BeforeEach
    void setUp() {
        failureDetectionService = new FailureDetectionService(
                memberList, pendingMessages, clusterConfig, httpClient, objectMapper);
    }

    @Test
    void joinCluster_sendsJoinRequestToAllMembers() throws Exception {
        final String nodeId = "node-1";

        final InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        final long incarnation = 1L;

        final Member member1 = new Member("member-1", new InetSocketAddress("localhost", 8081));
        final Member member2 = new Member("member-2", new InetSocketAddress("localhost", 8082));

        when(clusterConfig.getNodeId()).thenReturn(nodeId);
        when(clusterConfig.getIncarnation()).thenReturn(incarnation);
        when(clusterConfig.getMembers()).thenReturn(List.of(member1, member2));

        when(objectMapper.writeValueAsString(any(JoinRequest.class))).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        failureDetectionService.joinCluster();

        verify(httpClient, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void joinCluster_sendsCorrectJoinRequestBody() throws Exception {
        final String nodeId = "node-1";
        final InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        final long incarnation = 1L;

        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8081));

        when(clusterConfig.getNodeId()).thenReturn(nodeId);
        when(clusterConfig.getIncarnation()).thenReturn(incarnation);
        when(clusterConfig.getMembers()).thenReturn(List.of(member));

        final String requestBodyJson = "{\"type\":\"JOIN\",\"nodeId\":\"node-1\",\"address\":\"localhost:8080\",\"incarnation\":1}";
        lenient().when(objectMapper.writeValueAsString(any(JoinRequest.class))).thenReturn(requestBodyJson);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        failureDetectionService.joinCluster();

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final HttpRequest capturedRequest = requestCaptor.getValue();
        assertTrue(capturedRequest.uri().toString().contains("/boutade/join"));
    }

    @Test
    void joinCluster_joinsMemberListOnSuccess() throws Exception {
        final String nodeId = "node-1";
        final InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        final long incarnation = 1L;

        final Member member1 = new Member("member-1", new InetSocketAddress("localhost", 8081));
        final Member member2 = new Member("member-2", new InetSocketAddress("localhost", 8082));

        when(clusterConfig.getNodeId()).thenReturn(nodeId);
        when(clusterConfig.getIncarnation()).thenReturn(incarnation);
        when(clusterConfig.getMembers()).thenReturn(List.of(member1, member2));

        when(objectMapper.writeValueAsString(any(JoinRequest.class))).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        failureDetectionService.joinCluster();

        verify(memberList).addAll(List.of(member1, member2));
    }

    @Test
    void init_skipsWhenFailureTimeoutIsZero() throws IOException, InterruptedException {
        when(clusterConfig.getFailureTimeout()).thenReturn(0);
        failureDetectionService.run();
        verify(memberList, never()).getRandom();
    }

    @Test
    void sendPingRequest_usesCorrectEndpoint() throws Exception {
        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));

        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        failureDetectionService.sendPingRequest(new PingRequest("node-1", member.getNodeId()), member);

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final String uri = requestCaptor.getValue().uri().toString();
        assertTrue(uri.contains("/boutade/ping"));
        assertTrue(uri.contains("localhost:8080"));
    }

    @Test
    void detectFailures_sendsPingToEdgeNodesWhenPrimaryFails() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getNodeId()).thenReturn(nodeId);

        final Member primaryMember = new Member("primary", new InetSocketAddress("localhost", 8080));
        final Member edgeMember1 = new Member("edge-1", new InetSocketAddress("localhost", 8081));
        final Member edgeMember2 = new Member("edge-2", new InetSocketAddress("localhost", 8082));

        when(memberList.getRandom())
                .thenReturn(primaryMember)
                .thenReturn(edgeMember1)
                .thenReturn(edgeMember2);
        when(memberList.size()).thenReturn(3);
        when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());
        when(memberList.iterator()).thenReturn(List.of(primaryMember, edgeMember1, edgeMember2).iterator());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailure();

        verify(httpClient, atLeast(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void detectFailures_marksMemberSuspiciousWhenAllPingsFail() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getNodeId()).thenReturn(nodeId);

        final Member primaryMember = new Member("primary", new InetSocketAddress("localhost", 8080));
        primaryMember.setStatus(MemberStatus.ALIVE);

        final Member edgeMember1 = new Member("edge-1", new InetSocketAddress("localhost", 8081));
        final Member edgeMember2 = new Member("edge-2", new InetSocketAddress("localhost", 8082));

        when(memberList.getRandom())
                .thenReturn(primaryMember)
                .thenReturn(edgeMember1)
                .thenReturn(edgeMember2);
        when(memberList.size()).thenReturn(3);
        when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());
        when(memberList.iterator()).thenReturn(List.of(primaryMember, edgeMember1, edgeMember2).iterator());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailure();

        verify(pendingMessages).add(any(com.zenz.boutade.message.MemberSuspiciousMessage.class));
        assertEquals(MemberStatus.SUSPICIOUS, primaryMember.getStatus());
    }

    @Test
    void detectFailures_marksMemberDeadWhenAlreadySuspicious() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getNodeId()).thenReturn(nodeId);

        final Member primaryMember = new Member("primary", new InetSocketAddress("localhost", 8080));
        primaryMember.setStatus(MemberStatus.SUSPICIOUS);
        primaryMember.setRound(0);

        final Member edgeMember1 = new Member("edge-1", new InetSocketAddress("localhost", 8081));
        final Member edgeMember2 = new Member("edge-2", new InetSocketAddress("localhost", 8082));

        when(clusterConfig.getRound()).thenReturn(3L);
        when(memberList.getRandom())
                .thenReturn(primaryMember)
                .thenReturn(edgeMember1)
                .thenReturn(edgeMember2);
        when(memberList.size()).thenReturn(3);
        when(pendingMessages.iterator()).thenReturn(Collections.emptyListIterator());
        when(memberList.iterator()).thenReturn(List.of(primaryMember, edgeMember1, edgeMember2).iterator());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailure();

        verify(pendingMessages).add(any(MemberDeadMessage.class));
        verify(memberList).remove(primaryMember);
    }

    @Test
    void detectFailures_marksMemberAliveWhenAtLeastOneEdgePingSucceeds() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getNodeId()).thenReturn(nodeId);

        final Member primaryMember = new Member("primary", new InetSocketAddress("localhost", 8080));
        primaryMember.setStatus(MemberStatus.SUSPICIOUS);

        final Member edgeMember1 = new Member("edge-1", new InetSocketAddress("localhost", 8081));
        final Member edgeMember2 = new Member("edge-2", new InetSocketAddress("localhost", 8082));

        when(memberList.getRandom())
                .thenReturn(primaryMember)
                .thenReturn(edgeMember1)
                .thenReturn(edgeMember2);
        when(memberList.size()).thenReturn(3);
        when(pendingMessages.iterator()).thenReturn(Collections.emptyListIterator());
        when(memberList.iterator()).thenReturn(new ArrayIterator<>(new Member[]{primaryMember, edgeMember1, edgeMember2}));
        when(httpResponse.statusCode()).thenReturn(200);
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"))
                .thenReturn(httpResponse);

        failureDetectionService.detectFailure();

        assertEquals(MemberStatus.ALIVE, primaryMember.getStatus());
    }
}