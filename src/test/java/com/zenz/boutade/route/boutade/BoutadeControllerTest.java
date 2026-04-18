package com.zenz.boutade.route.boutade;

import com.zenz.boutade.config.ClusterConfig;
import com.zenz.boutade.message.JoinMessage;
import com.zenz.boutade.message.MemberAliveMessage;
import com.zenz.boutade.message.MemberDeadMessage;
import com.zenz.boutade.message.MemberSuspiciousMessage;
import com.zenz.boutade.route.boutade.request.JoinRequest;
import com.zenz.boutade.route.boutade.request.PingRequest;
import com.zenz.boutade.route.boutade.request.PongRequest;
import com.zenz.boutade.route.boutade.request.RequestType;
import com.zenz.boutade.route.exception.ConflictException;
import com.zenz.boutade.route.exception.NotFoundException;
import com.zenz.boutade.util.Member;
import com.zenz.boutade.util.MemberList;
import com.zenz.boutade.util.MemberStatus;
import com.zenz.boutade.util.PendingMessages;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoutadeControllerTest {

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

    @Mock
    private HttpServletRequest httpServletRequest;

    private BoutadeController boutadeController;

    @BeforeEach
    void setUp() {
        boutadeController = new BoutadeController(memberList, pendingMessages, clusterConfig, httpClient, objectMapper);
    }

    @Nested
    @DisplayName("/ping endpoint tests")
    class PingEndpointTests {

        @Nested
        @DisplayName("Direct ping handling (target is self)")
        class DirectPingTests {

            @Test
            @DisplayName("Returns pong when target is self")
            void returnsPongWhenTargetIsSelf() throws Exception {
                String requesterId = "node-1";
                String selfId = "self-node";

                PingRequest request = new PingRequest(requesterId, selfId);
                Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));

                when(clusterConfig.getNodeId()).thenReturn(selfId);
                when(memberList.get(requesterId)).thenReturn(requester);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient, times(1)).send(captor.capture(), any());

                HttpRequest requestSent = captor.getValue();
                assertTrue(requestSent.uri().toString().contains("/boutade/pong"));
            }

            @Test
            @DisplayName("Adds unknown requester to member list")
            void addsUnknownRequesterToMemberList() throws Exception {
                String requesterId = "unknown-node";
                String selfId = "self-node";

                PingRequest request = new PingRequest(requesterId, selfId);

                when(clusterConfig.getNodeId()).thenReturn(selfId);
                when(clusterConfig.getPort()).thenReturn(8080);
                when(memberList.get(requesterId)).thenReturn(null);
                when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.100");
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                Member addedMember = memberCaptor.getValue();
                assertEquals(requesterId, addedMember.getNodeId());
                assertEquals("192.168.1.100", addedMember.getAddress().getHostString());
            }
        }

        @Nested
        @DisplayName("Indirect ping relay (target is different node)")
        class IndirectPingRelayTests {

            @Test
            @DisplayName("Throws NotFoundException when target member not found")
            void throwsWhenTargetNotFound() throws IOException, InterruptedException {
                String requesterId = "node-1";
                String targetId = "node-2";

                PingRequest request = new PingRequest(requesterId, targetId);
                Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));

                when(memberList.get(requesterId)).thenReturn(requester);
                when(memberList.get(targetId)).thenReturn(null);
                when(clusterConfig.getNodeId()).thenReturn("edge-node");

                assertThrows(NotFoundException.class, () -> boutadeController.ping(httpServletRequest, request));
                verify(httpClient, never()).send(any(), any());
            }

            @Test
            @DisplayName("Forwards ping to TARGET address, not requester address")
            void forwardsPingToTargetNotRequester() throws Exception {
                String requesterId = "node-1";
                String targetId = "node-2";
                String edgeNodeId = "edge-node";

                PingRequest request = new PingRequest(requesterId, targetId);

                Member requester = new Member(requesterId, new InetSocketAddress("requester-host", 8080));
                Member target = new Member(targetId, new InetSocketAddress("target-host", 9090));

                when(clusterConfig.getNodeId()).thenReturn(edgeNodeId);
                when(memberList.get(requesterId)).thenReturn(requester);
                when(memberList.get(targetId)).thenReturn(target);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);
                when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient, times(2)).send(captor.capture(), any());

                List<HttpRequest> requests = captor.getAllValues();
                HttpRequest pingRequest = requests.get(0);
                HttpRequest pongRequest = requests.get(1);

                // Ping should go to TARGET (target-host:9090), not requester
                URI pingUri = pingRequest.uri();
                assertTrue(pingUri.toString().contains("target-host"));
                assertTrue(pingUri.toString().contains("9090"));
                assertFalse(pingUri.toString().contains("requester-host"));

                // Pong should go back to REQUESTER
                URI pongUri = pongRequest.uri();
                assertTrue(pongUri.toString().contains("requester-host"));
                assertTrue(pongUri.toString().contains("8080"));
            }

            @Test
            @DisplayName("Does not create ping loop by sending back to requester")
            void doesNotCreatePingLoop() throws Exception {
                String requesterId = "node-1";
                String targetId = "node-2";
                String edgeNodeId = "edge-node";

                PingRequest request = new PingRequest(requesterId, targetId);

                Member requester = new Member(requesterId, new InetSocketAddress("10.0.0.1", 8080));
                Member target = new Member(targetId, new InetSocketAddress("10.0.0.2", 8080));

                when(clusterConfig.getNodeId()).thenReturn(edgeNodeId);
                when(memberList.get(requesterId)).thenReturn(requester);
                when(memberList.get(targetId)).thenReturn(target);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);
                when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient, times(2)).send(captor.capture(), any());

                HttpRequest forwardedPing = captor.getAllValues().get(0);

                // The forwarded ping must NOT go to the requester's address
                assertFalse(
                        forwardedPing.uri().toString().contains("10.0.0.1"),
                        "Forwarded ping should not be sent back to requester (would cause loop)"
                );
                assertTrue(
                        forwardedPing.uri().toString().contains("10.0.0.2"),
                        "Forwarded ping should be sent to target"
                );
            }

            @Test
            @DisplayName("Forwards payload messages to target")
            void forwardsPayloadToTarget() throws Exception {
                String requesterId = "node-1";
                String targetId = "node-2";
                String edgeNodeId = "edge-node";

                PingRequest request = new PingRequest(requesterId, targetId);
                MemberAliveMessage aliveMsg = new MemberAliveMessage("node-3", 5L, "node-3");
                request.getPayload().add(aliveMsg);

                Member requester = new Member(requesterId, new InetSocketAddress("localhost", 8080));
                Member target = new Member(targetId, new InetSocketAddress("localhost", 9090));

                when(clusterConfig.getNodeId()).thenReturn(edgeNodeId);
                when(memberList.get(requesterId)).thenReturn(requester);
                when(memberList.get(targetId)).thenReturn(target);
                when(memberList.get("node-3")).thenReturn(null);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);
                when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<PingRequest> pingCaptor = ArgumentCaptor.forClass(PingRequest.class);
                verify(objectMapper, atLeastOnce()).writeValueAsString(pingCaptor.capture());

                // Find the forwarded ping request
                PingRequest forwardedPing = pingCaptor.getAllValues().stream()
                        .filter(p -> p instanceof PingRequest)
                        .map(p -> (PingRequest) p)
                        .filter(p -> edgeNodeId.equals(p.getNodeId()))
                        .findFirst()
                        .orElseThrow();

                assertEquals(1, forwardedPing.getPayload().size());
            }

            @Test
            @DisplayName("Sets edge node as originator of forwarded ping")
            void setsEdgeNodeAsOriginator() throws Exception {
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
                when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<PingRequest> pingCaptor = ArgumentCaptor.forClass(PingRequest.class);
                verify(objectMapper, atLeastOnce()).writeValueAsString(pingCaptor.capture());

                PingRequest forwardedPing = pingCaptor.getAllValues().stream()
                        .filter(p -> p instanceof PingRequest)
                        .map(p -> (PingRequest) p)
                        .filter(p -> edgeNodeId.equals(p.getNodeId()))
                        .findFirst()
                        .orElseThrow();

                assertEquals(edgeNodeId, forwardedPing.getNodeId());
                assertEquals(targetId, forwardedPing.getTarget());
            }

            @Test
            @DisplayName("Sends pong back to requester after forwarding")
            void sendsPongBackToRequester() throws Exception {
                String requesterId = "node-1";
                String targetId = "node-2";
                String edgeNodeId = "edge-node";

                PingRequest request = new PingRequest(requesterId, targetId);

                Member requester = new Member(requesterId, new InetSocketAddress("requester-host", 8080));
                Member target = new Member(targetId, new InetSocketAddress("target-host", 9090));

                when(clusterConfig.getNodeId()).thenReturn(edgeNodeId);
                when(memberList.get(requesterId)).thenReturn(requester);
                when(memberList.get(targetId)).thenReturn(target);
                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                        .thenReturn(httpResponse);
                when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                boutadeController.ping(httpServletRequest, request);

                ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient, times(2)).send(captor.capture(), any());

                HttpRequest pongRequest = captor.getAllValues().get(1);
                assertTrue(pongRequest.uri().toString().contains("/boutade/pong"));
                assertTrue(pongRequest.uri().toString().contains("requester-host"));
                assertTrue(pongRequest.uri().toString().contains("8080"));
            }
        }

        @Nested
        @DisplayName("Message payload handling")
        class MessagePayloadTests {

            @Nested
            @DisplayName("MemberSuspicious message handling")
            class MemberSuspiciousTests {

                @Test
                @DisplayName("Updates member status when incarnation is higher")
                void updatesStatusWhenIncarnationHigher() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-2");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
                    Member suspiciousMember = new Member("node-3", new InetSocketAddress("localhost", 8082));
                    suspiciousMember.setIncarnation(5L);

                    MemberSuspiciousMessage msg = new MemberSuspiciousMessage("node-1", "node-3", 10L);
                    request.getPayload().add(msg);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-2")).thenReturn(target);
                    when(memberList.get("node-3")).thenReturn(suspiciousMember);
                    when(clusterConfig.getNodeId()).thenReturn("edge-node");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);
                    when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                    boutadeController.ping(httpServletRequest, request);

                    assertEquals(MemberStatus.SUSPICIOUS, suspiciousMember.getStatus());
                    verify(pendingMessages).add(any(MemberSuspiciousMessage.class));
                }

                @Test
                @DisplayName("Ignores message when incarnation is lower")
                void ignoresWhenIncarnationLower() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-2");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
                    Member staleSuspect = new Member("node-3", new InetSocketAddress("localhost", 8082));
                    staleSuspect.setIncarnation(10L);
                    staleSuspect.setStatus(MemberStatus.ALIVE);

                    MemberSuspiciousMessage msg = new MemberSuspiciousMessage("node-1", "node-3", 5L);
                    request.getPayload().add(msg);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-2")).thenReturn(target);
                    when(memberList.get("node-3")).thenReturn(staleSuspect);
                    when(clusterConfig.getNodeId()).thenReturn("edge-node");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);
                    when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                    boutadeController.ping(httpServletRequest, request);

                    assertEquals(MemberStatus.ALIVE, staleSuspect.getStatus());
                    verify(pendingMessages, times(1)).add(msg);
                }

                @Test
                @DisplayName("Ignores message when incarnation is equal")
                void processesWhenIncarnationEqual() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-2");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));
                    Member staleSuspect = new Member("node-3", new InetSocketAddress("localhost", 8082));
                    staleSuspect.setIncarnation(5L);
                    staleSuspect.setStatus(MemberStatus.ALIVE);

                    MemberSuspiciousMessage msg = new MemberSuspiciousMessage("node-1", "node-3", 5L);
                    request.getPayload().add(msg);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-2")).thenReturn(target);
                    when(memberList.get("node-3")).thenReturn(staleSuspect);
                    when(clusterConfig.getNodeId()).thenReturn("edge-node");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);
                    when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                    boutadeController.ping(httpServletRequest, request);

                    assertEquals(MemberStatus.SUSPICIOUS, staleSuspect.getStatus());
                    verify(pendingMessages, times(1)).add(msg);
                }

                @Test
                @DisplayName("Increments incarnation and sends alive when target is self")
                void incrementsIncarnationWhenTargetIsSelf() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-2");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(clusterConfig.getNodeId()).thenReturn("node-2");
                    when(clusterConfig.getIncarnation()).thenReturn(5L);
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberSuspiciousMessage msg = new MemberSuspiciousMessage("node-3", "node-2", 5L);
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    verify(clusterConfig).setIncarnation(6L);
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }

                @Test
                @DisplayName("Does nothing when target member not in list")
                void doesNothingWhenTargetNotInList() throws Exception {
                    PingRequest request = new PingRequest("node-1", "self");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

                    MemberSuspiciousMessage msg = new MemberSuspiciousMessage("node-1", "unknown-node", 5L);
                    request.getPayload().add(msg);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("unknown-node")).thenReturn(null);
                    when(clusterConfig.getNodeId()).thenReturn("self");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    boutadeController.ping(httpServletRequest, request);

                    verify(pendingMessages, times(1)).add(msg);
                }
            }

            @Nested
            @DisplayName("MemberAlive message handling")
            class MemberAliveTests {

                @Test
                @DisplayName("Updates incarnation and status when incarnation is higher")
                void updatesWhenIncarnationHigher() throws Exception {
                    PingRequest request = new PingRequest("node-1", "edge");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member member = new Member("node-3", new InetSocketAddress("localhost", 8081));
                    member.setIncarnation(3L);
                    member.setStatus(MemberStatus.SUSPICIOUS);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-3")).thenReturn(member);
                    when(clusterConfig.getNodeId()).thenReturn("edge");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberAliveMessage msg = new MemberAliveMessage("node-1", 5L, "node-3");
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    assertEquals(MemberStatus.ALIVE, member.getStatus());
                    assertEquals(5L, member.getIncarnation());
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }

                @Test
                @DisplayName("Ignores message when incarnation is lower")
                void ignoresWhenIncarnationLower() throws Exception {
                    PingRequest request = new PingRequest("node-1", "edge");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member member = new Member("node-3", new InetSocketAddress("localhost", 8081));
                    member.setIncarnation(10L);
                    member.setStatus(MemberStatus.SUSPICIOUS);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-3")).thenReturn(member);
                    when(clusterConfig.getNodeId()).thenReturn("edge");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberAliveMessage msg = new MemberAliveMessage("node-1", 5L, "node-3");
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    assertEquals(MemberStatus.SUSPICIOUS, member.getStatus());
                    assertEquals(10L, member.getIncarnation());
                    verify(pendingMessages, times(1)).add(msg);
                }

                @Test
                @DisplayName("Updates own incarnation when message is for self")
                void updatesOwnIncarnationWhenForSelf() throws Exception {
                    PingRequest request = new PingRequest("node-1", "self-node");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(clusterConfig.getNodeId()).thenReturn("self-node");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberAliveMessage msg = new MemberAliveMessage("self-node", 10L, "self-node");
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    verify(clusterConfig).setIncarnation(10L);
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }
            }

            @Nested
            @DisplayName("MemberDead message handling")
            class MemberDeadTests {

                @Test
                @DisplayName("Removes member when incarnation is higher or equal")
                void removesMemberWhenIncarnationHigherOrEqual() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-3");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member victim = new Member("node-3", new InetSocketAddress("localhost", 8081));
                    victim.setIncarnation(2L);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-3")).thenReturn(victim);
                    when(clusterConfig.getNodeId()).thenReturn("edge");
                    when(objectMapper.writeValueAsString(any())).thenReturn("");
                    when(pendingMessages.iterator()).thenReturn(Collections.emptyIterator());

                    MemberDeadMessage msg = new MemberDeadMessage(requester.getNodeId(), victim.getNodeId(), 2L);
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    verify(memberList).remove(victim);
                    verify(pendingMessages).add(any(MemberDeadMessage.class));
                }

                @Test
                @DisplayName("Does not remove member when incarnation is lower")
                void doesNotRemoveWhenIncarnationLower() throws Exception {
                    PingRequest request = new PingRequest("node-1", "edge");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member victim = new Member("node-3", new InetSocketAddress("localhost", 8081));
                    victim.setIncarnation(10L);

                    when(memberList.get("node-1")).thenReturn(requester);
                    when(memberList.get("node-3")).thenReturn(victim);
                    when(clusterConfig.getNodeId()).thenReturn("edge");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-3", 5L);
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    verify(memberList, never()).remove(any(Member.class));
                    verify(pendingMessages, times(1)).add(msg);
                }

                @Test
                @DisplayName("Does not remove when member not in list")
                void doesNotRemoveWhenMemberNotInList() throws Exception {
                    PingRequest request = new PingRequest("node-1", "node-2");

                    Member requester = new Member("node-1", new InetSocketAddress("localhost", 8080));
                    Member target = new Member("node-2", new InetSocketAddress("localhost", 8081));

                    lenient().when(memberList.get("node-1")).thenReturn(requester);
                    lenient().when(memberList.get("node-2")).thenReturn(target);
                    lenient().when(memberList.get("node-3")).thenReturn(null);
                    lenient().when(clusterConfig.getNodeId()).thenReturn("node-2");
                    when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                            .thenReturn(httpResponse);

                    MemberDeadMessage msg = new MemberDeadMessage("node-1", "node-3", 5L);
                    request.getPayload().add(msg);

                    boutadeController.ping(httpServletRequest, request);

                    verify(memberList, never()).remove(any(Member.class));
                    verify(pendingMessages, times(1)).add(msg);
                }

                @Test
                @DisplayName("Increments incarnation when target is self with matching incarnation")
                void incrementsIncarnationWhenTargetIsSelfMatching() throws Exception {
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

                    boutadeController.ping(httpServletRequest, request);

                    verify(clusterConfig).setIncarnation(6L);
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }

                @Test
                @DisplayName("Increments incarnation when target is self with higher incarnation")
                void incrementsIncarnationWhenTargetIsSelfHigher() throws Exception {
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

                    boutadeController.ping(httpServletRequest, request);

                    verify(clusterConfig).setIncarnation(11L);
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }

                @Test
                @DisplayName("Does not increment incarnation when target is self with lower incarnation")
                void doesNotIncrementWhenTargetIsSelfLower() throws Exception {
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

                    boutadeController.ping(httpServletRequest, request);

                    verify(clusterConfig, never()).setIncarnation(anyLong());
                    verify(pendingMessages).add(any(MemberAliveMessage.class));
                }
            }
        }
    }

    @Nested
    @DisplayName("/pong endpoint tests")
    class PongEndpointTests {

        @Test
        @DisplayName("Throws NotFoundException when member not found")
        void throwsWhenMemberNotFound() {
            PongRequest request = new PongRequest("unknown-node");

            when(memberList.get("unknown-node")).thenReturn(null);

            assertThrows(NotFoundException.class, () -> boutadeController.pong(request));
            verify(pendingMessages, never()).add(any());
        }

        @Test
        @DisplayName("Adds alive message when member is suspicious")
        void addsAliveMessageWhenSuspicious() {
            String nodeId = "node-1";
            PongRequest request = new PongRequest(nodeId);

            Member member = new Member(nodeId, new InetSocketAddress("localhost", 8080));
            member.setStatus(MemberStatus.SUSPICIOUS);
            member.setIncarnation(5L);

            when(memberList.get(nodeId)).thenReturn(member);
            when(clusterConfig.getNodeId()).thenReturn("self-node");
            when(clusterConfig.getRound()).thenReturn(10L);

            boutadeController.pong(request);

            assertEquals(MemberStatus.ALIVE, member.getStatus());

            ArgumentCaptor<MemberAliveMessage> captor = ArgumentCaptor.forClass(MemberAliveMessage.class);
            verify(pendingMessages).add(captor.capture());

            MemberAliveMessage aliveMsg = captor.getValue();
            assertEquals("self-node", aliveMsg.getNodeId());
            assertEquals(5L, aliveMsg.getIncarnation());
            assertEquals(nodeId, aliveMsg.getTarget());
        }

        @Test
        @DisplayName("Does not add alive message when member is already alive")
        void doesNotAddAliveMessageWhenAlive() {
            String nodeId = "node-1";
            PongRequest request = new PongRequest(nodeId);

            Member member = new Member(nodeId, new InetSocketAddress("localhost", 8080));
            member.setStatus(MemberStatus.ALIVE);

            when(memberList.get(nodeId)).thenReturn(member);

            boutadeController.pong(request);

            assertEquals(MemberStatus.ALIVE, member.getStatus());
            verify(pendingMessages, never()).add(any());
        }

        @Test
        @DisplayName("Sets correct round on alive message")
        void setsCorrectRoundOnAliveMessage() {
            String nodeId = "node-1";
            PongRequest request = new PongRequest(nodeId);

            Member member = new Member(nodeId, new InetSocketAddress("localhost", 8080));
            member.setStatus(MemberStatus.SUSPICIOUS);
            member.setIncarnation(3L);

            when(memberList.get(nodeId)).thenReturn(member);
            when(clusterConfig.getNodeId()).thenReturn("self-node");
            when(clusterConfig.getRound()).thenReturn(42L);

            boutadeController.pong(request);

            ArgumentCaptor<MemberAliveMessage> captor = ArgumentCaptor.forClass(MemberAliveMessage.class);
            verify(pendingMessages).add(captor.capture());

            assertEquals(42L, captor.getValue().getRound());
        }

        @Test
        @DisplayName("Preserves member incarnation when transitioning to alive")
        void preservesMemberIncarnation() {
            String nodeId = "node-1";
            PongRequest request = new PongRequest(nodeId);

            Member member = new Member(nodeId, new InetSocketAddress("localhost", 8080));
            member.setStatus(MemberStatus.SUSPICIOUS);
            member.setIncarnation(99L);

            when(memberList.get(nodeId)).thenReturn(member);
            when(clusterConfig.getNodeId()).thenReturn("self-node");
            when(clusterConfig.getRound()).thenReturn(1L);

            boutadeController.pong(request);

            assertEquals(99L, member.getIncarnation());

            ArgumentCaptor<MemberAliveMessage> captor = ArgumentCaptor.forClass(MemberAliveMessage.class);
            verify(pendingMessages).add(captor.capture());
            assertEquals(99L, captor.getValue().getIncarnation());
        }
    }

    @Nested
    @DisplayName("/join endpoint tests")
    class JoinEndpointTests {

        @Nested
        @DisplayName("Successful join scenarios")
        class SuccessfulJoinTests {

            @Test
            @DisplayName("Adds new member to list and buffers join message")
            void addsNewMemberAndBuffersMessage() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(5L);
                when(clusterConfig.getPort()).thenReturn(9090);
                when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.100");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 1L);

                boutadeController.join(httpServletRequest, request);

                verify(memberList).add(any(Member.class));
                verify(pendingMessages).add(any(JoinMessage.class));
            }

            @Test
            @DisplayName("Creates member with correct nodeId")
            void createsMemberWithCorrectNodeId() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(5L);
                when(clusterConfig.getPort()).thenReturn(9090);
                when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.100");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 1L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                assertEquals("new-node", memberCaptor.getValue().getNodeId());
            }

            @Test
            @DisplayName("Creates member with address from request remote addr and config port")
            void createsMemberWithCorrectAddress() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(5L);
                when(clusterConfig.getPort()).thenReturn(8080);
                when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.50");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 1L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                Member addedMember = memberCaptor.getValue();
                assertEquals("10.0.0.50", addedMember.getAddress().getHostString());
                assertEquals(8080, addedMember.getAddress().getPort());
            }

            @Test
            @DisplayName("Sets incarnation from request")
            void setsIncarnationFromRequest() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(5L);
                when(clusterConfig.getPort()).thenReturn(9090);
                when(httpServletRequest.getRemoteAddr()).thenReturn("localhost");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 42L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                assertEquals(42L, memberCaptor.getValue().getIncarnation());
            }

            @Test
            @DisplayName("Creates join message with correct properties")
            void createsJoinMessageWithCorrectProperties() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(7L);
                when(clusterConfig.getPort()).thenReturn(9090);
                when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.100");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 5L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<JoinMessage> msgCaptor = ArgumentCaptor.forClass(JoinMessage.class);
                verify(pendingMessages).add(msgCaptor.capture());

                JoinMessage joinMsg = msgCaptor.getValue();
                assertEquals("new-node", joinMsg.getNodeId());
                assertEquals(5L, joinMsg.getIncarnation());
                assertEquals(7L, joinMsg.getRound());
                assertEquals("192.168.1.100", joinMsg.getAddress().getHostString());
                assertEquals(9090, joinMsg.getAddress().getPort());
            }
        }

        @Nested
        @DisplayName("Conflict scenarios")
        class ConflictTests {

            @Test
            @DisplayName("Throws ConflictException when member already exists")
            void throwsWhenMemberExists() {
                when(memberList.contains("existing-node")).thenReturn(true);

                JoinRequest request = new JoinRequest(RequestType.JOIN, "existing-node", 1L);

                assertThrows(ConflictException.class, () -> boutadeController.join(httpServletRequest, request));

                verify(memberList, never()).add(any(Member.class));
                verify(pendingMessages, never()).add(any(JoinMessage.class));
            }

            @Test
            @DisplayName("Does not modify member list when conflict occurs")
            void doesNotModifyMemberListOnConflict() {
                when(memberList.contains("existing-node")).thenReturn(true);

                JoinRequest request = new JoinRequest(RequestType.JOIN, "existing-node", 1L);

                assertThrows(ConflictException.class, () -> boutadeController.join(httpServletRequest, request));

                verify(memberList, never()).add(any());
                verify(memberList, never()).remove(any());
            }
        }

        @Nested
        @DisplayName("Edge cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("Handles zero incarnation")
            void handlesZeroIncarnation() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(1L);
                when(clusterConfig.getPort()).thenReturn(8080);
                when(httpServletRequest.getRemoteAddr()).thenReturn("localhost");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 0L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                assertEquals(0L, memberCaptor.getValue().getIncarnation());
            }

            @Test
            @DisplayName("Uses config port, not a port from request")
            void usesConfigPortNotRequestPort() {
                when(memberList.contains("new-node")).thenReturn(false);
                when(clusterConfig.getRound()).thenReturn(1L);
                when(clusterConfig.getPort()).thenReturn(9999);
                when(httpServletRequest.getRemoteAddr()).thenReturn("localhost");

                JoinRequest request = new JoinRequest(RequestType.JOIN, "new-node", 1L);

                boutadeController.join(httpServletRequest, request);

                ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
                verify(memberList).add(memberCaptor.capture());

                assertEquals(9999, memberCaptor.getValue().getAddress().getPort());
            }
        }
    }
}
