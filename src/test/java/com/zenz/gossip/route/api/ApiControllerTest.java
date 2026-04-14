package com.zenz.gossip.route.api;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.exception.NotFoundException;
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
}