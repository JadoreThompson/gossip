package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.RequestType;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.PendingMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

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
    void setUp() {
        failureDetectionService = new FailureDetectionService(memberList, pendingMessages, clusterConfig);
        failureDetectionService.setHttpClient(httpClient);
        failureDetectionService.setObjectMapper(objectMapper);
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
        when(clusterConfig.getNodeId()).thenReturn("node-1");

        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        final Message message1 = new MemberAliveMessage("node-2", 1L);
        final Message message2 = new MemberAliveMessage("node-3", 2L);

        when(pendingMessages.toList()).thenReturn(List.of(message1, message2));
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");

        failureDetectionService.sendPingRequest(member);

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
        when(clusterConfig.getNodeId()).thenReturn("node-1");

        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        final Message message = new MemberAliveMessage("node-2", 1L);

        when(pendingMessages.toList()).thenReturn(List.of(message));
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");

        failureDetectionService.sendPingRequest(member);

        verify(pendingMessages).clear();
    }

    @Test
    void init_skipsWhenFailureTimeoutIsZero() throws Exception {
        when(clusterConfig.getFailureTimeout()).thenReturn(0);
        lenient().when(clusterConfig.getNodeId()).thenReturn("node-1");
        lenient().when(clusterConfig.getAddress()).thenReturn(new InetSocketAddress("localhost", 8080));
        lenient().when(clusterConfig.getIncarnation()).thenReturn(1L);
        lenient().when(clusterConfig.getMembers()).thenReturn(List.of());

        failureDetectionService.detectFailures();
        verify(clusterConfig, times(1)).getFailureTimeout();
        verifyNoInteractions(memberList);
    }

    @Test
    void sendPingRequest_usesCorrectEndpoint() throws Exception {
        when(clusterConfig.getNodeId()).thenReturn("node-1");

        final Member member = new Member("member-1", new InetSocketAddress("localhost", 8080));
        when(pendingMessages.toList()).thenReturn(List.of());
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
        when(objectMapper.writeValueAsString(any(PingRequest.class))).thenReturn("{}");

        failureDetectionService.sendPingRequest(member);

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final String uri = requestCaptor.getValue().uri().toString();
        assert uri.contains("/ping");
        assert uri.contains("localhost:8080");
    }
}