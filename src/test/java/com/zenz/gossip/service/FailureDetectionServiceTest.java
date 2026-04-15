package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.RequestType;
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
import java.util.List;

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
    void setUp() throws Exception {
        failureDetectionService = new FailureDetectionService();

        final Field memberListField = FailureDetectionService.class.getDeclaredField("memberList");
        memberListField.setAccessible(true);
        memberListField.set(failureDetectionService, memberList);

        final Field pendingMessagesField = FailureDetectionService.class.getDeclaredField("pendingMessages");
        pendingMessagesField.setAccessible(true);
        pendingMessagesField.set(failureDetectionService, pendingMessages);

        final Field clusterConfigField = FailureDetectionService.class.getDeclaredField("clusterConfig");
        clusterConfigField.setAccessible(true);
        clusterConfigField.set(failureDetectionService, clusterConfig);

        final Field httpClientField = FailureDetectionService.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(failureDetectionService, httpClient);

        final Field objectMapperField = FailureDetectionService.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(failureDetectionService, objectMapper);
    }

    @Test
    void joinCluster_sendsJoinRequestToAllMembers() throws Exception {
        final String nodeId = "node-1";

        final InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        final long incarnation = 1L;

        final Member member1 = new Member("member-1", new InetSocketAddress("localhost", 8081));
        final Member member2 = new Member("member-2", new InetSocketAddress("localhost", 8082));

        when(clusterConfig.getNodeId()).thenReturn(nodeId);
        when(clusterConfig.getAddress()).thenReturn(address);
        when(clusterConfig.getIncarnation()).thenReturn(incarnation);
        when(clusterConfig.getMembers()).thenReturn(List.of(member1, member2));

        final JoinRequest expectedRequest = new JoinRequest(
                RequestType.JOIN,
                nodeId,
                address,
                incarnation);

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
        when(clusterConfig.getAddress()).thenReturn(address);
        when(clusterConfig.getIncarnation()).thenReturn(incarnation);
        when(clusterConfig.getMembers()).thenReturn(List.of(member));

        final JoinRequest expectedRequest = new JoinRequest(
                RequestType.JOIN,
                nodeId,
                address,
                incarnation);

        final String requestBodyJson = "{\"type\":\"JOIN\",\"nodeId\":\"node-1\",\"address\":\"localhost:8080\",\"incarnation\":1}";
        lenient().when(objectMapper.writeValueAsString(any(JoinRequest.class))).thenReturn(requestBodyJson);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        failureDetectionService.joinCluster();

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final HttpRequest capturedRequest = requestCaptor.getValue();
        assert capturedRequest.uri().toString().contains("/join");
    }

    @Test
    void joinCluster_joinsMemberListOnSuccess() throws Exception {
        final String nodeId = "node-1";
        final InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        final long incarnation = 1L;

        final Member member1 = new Member("member-1", new InetSocketAddress("localhost", 8081));
        final Member member2 = new Member("member-2", new InetSocketAddress("localhost", 8082));

        when(clusterConfig.getNodeId()).thenReturn(nodeId);
        when(clusterConfig.getAddress()).thenReturn(address);
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
    void sendPingRequest_addsPendingMessagesToPayload() throws Exception {
        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        final Message message1 = new MemberAliveMessage("node-1", 1L, "node-2");
        final Message message2 = new MemberAliveMessage("node-1", 2L, "node-3");

        when(pendingMessages.toList()).thenReturn(List.of(message1, message2));
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        failureDetectionService.sendPingRequest(new PingRequest("node-1", member.getNodeId()), member);

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final var pingRequestCaptor = ArgumentCaptor.forClass(PingRequest.class);
        verify(objectMapper).writeValueAsString(pingRequestCaptor.capture());

        final PingRequest capturedPingRequest = pingRequestCaptor.getValue();
        assert capturedPingRequest.getPayload().size() == 2;
        assert capturedPingRequest.getPayload().contains(message1);
        assert capturedPingRequest.getPayload().contains(message2);
    }

    @Test
    void sendPingRequest_clearsPendingMessagesAfterSending() throws Exception {
        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        final Message message = new MemberAliveMessage("node-1", 1L, "node-2");

        when(pendingMessages.toList()).thenReturn(List.of(message));
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        failureDetectionService.sendPingRequest(new PingRequest("node-1", member.getNodeId()), member);

        verify(pendingMessages).clear();
    }

    @Test
    void init_skipsWhenFailureTimeoutIsZero() throws Exception {
        when(clusterConfig.getFailureTimeout()).thenReturn(0);
        failureDetectionService.detectFailures();
        verify(memberList, never()).getRandom();
    }

    @Test
    void sendPingRequest_usesCorrectEndpoint() throws Exception {
        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        when(pendingMessages.toList()).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        failureDetectionService.sendPingRequest(new PingRequest("node-1", member.getNodeId()), member);

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final String uri = requestCaptor.getValue().uri().toString();
        assert uri.contains("/ping");
        assert uri.contains("localhost:8080");
    }

    @Test
    void detectFailures_sendsPingToEdgeNodesWhenPrimaryFails() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getFailureTimeout()).thenReturn(100);
        when(clusterConfig.getNodeId()).thenReturn(nodeId);

        final Member primaryMember = new Member("primary", new InetSocketAddress("localhost", 8080));
        final Member edgeMember1 = new Member("edge-1", new InetSocketAddress("localhost", 8081));
        final Member edgeMember2 = new Member("edge-2", new InetSocketAddress("localhost", 8082));

        when(memberList.getRandom())
                .thenReturn(primaryMember)
                .thenReturn(edgeMember1)
                .thenReturn(edgeMember2);
        when(memberList.size()).thenReturn(3);
        when(pendingMessages.toList()).thenReturn(List.of());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailures();

        verify(httpClient, atLeast(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void detectFailures_marksMemberSuspiciousWhenAllPingsFail() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getFailureTimeout()).thenReturn(100);
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
        when(pendingMessages.toList()).thenReturn(List.of());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailures();

        verify(pendingMessages).add(any(com.zenz.gossip.message.MemberSuspiciousMessage.class));
        assert primaryMember.getStatus() == MemberStatus.SUSPICIOUS;
    }

    @Test
    void detectFailures_marksMemberDeadWhenAlreadySuspicious() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getFailureTimeout()).thenReturn(100);
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
        when(pendingMessages.toList()).thenReturn(List.of());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"));

        failureDetectionService.detectFailures();

        verify(pendingMessages).add(any(com.zenz.gossip.message.MemberDeadMessage.class));
        verify(memberList).remove(primaryMember);
    }

    @Test
    void detectFailures_marksMemberAliveWhenAtLeastOneEdgePingSucceeds() throws Exception {
        final String nodeId = "node-1";

        when(clusterConfig.getFailureTimeout()).thenReturn(100);
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
        when(pendingMessages.toList()).thenReturn(List.of());
        lenient().when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection failed"))
                .thenReturn(httpResponse);

        failureDetectionService.detectFailures();

        assert primaryMember.getStatus() == MemberStatus.ALIVE;
    }
}