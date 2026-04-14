package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.RequestType;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
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
class JoinClusterServiceTest {

    @Mock
    private ClusterConfig clusterConfig;

    @Mock
    private MemberList memberList;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private JoinClusterService joinClusterService;

    @BeforeEach
    void setUp() {
        joinClusterService = new JoinClusterService(clusterConfig, memberList, objectMapper);
    }

    @Test
    void run_sendsJoinRequestToAllMembers() throws Exception {
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

        joinClusterService.setClient(httpClient);

        joinClusterService.run();

        verify(httpClient, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void run_sendsCorrectJoinRequestBody() throws Exception {
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

        joinClusterService.setClient(httpClient);

        joinClusterService.run();

        final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        final HttpRequest capturedRequest = requestCaptor.getValue();
        assert capturedRequest.uri().toString().contains("/join");
    }

    @Test
    void run_joinsMemberListOnSuccess() throws Exception {
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

        joinClusterService.setClient(httpClient);

        joinClusterService.run();

        verify(memberList).addAll(List.of(member1, member2));
    }
}