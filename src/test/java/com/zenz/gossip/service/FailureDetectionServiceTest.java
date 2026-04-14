package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberAliveMessage;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.PendingMessages;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        failureDetectionService.setClient(httpClient);
        failureDetectionService.setObjectMapper(objectMapper);
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

        failureDetectionService.init();

        verify(clusterConfig).getFailureTimeout();
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