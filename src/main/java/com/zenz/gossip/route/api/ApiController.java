package com.zenz.gossip.route.api;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.*;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.MessageRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.PongRequest;
import com.zenz.gossip.route.exception.ConflictException;
import com.zenz.gossip.route.exception.NotFoundException;
import com.zenz.gossip.util.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class ApiController {

    private final MemberList memberList;

    private final PendingMessages pendingMessages;

    private final ClusterConfig clusterConfig;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    @PostMapping("/ping")
    public void ping(
            HttpServletRequest request,
            @RequestBody PingRequest body) throws IOException, InterruptedException {
        log.info("Received ping request: {}", body.toString());
        Member member = memberList.get(body.getNodeId());
        if (member == null) {
            member = new Member(
                    body.getNodeId(), new InetSocketAddress(request.getRemoteAddr(), clusterConfig.getPort()));
            memberList.add(member);
        }

        for (Message message : body.getPayload()) {
            handleMessage(message);
        }

        if (!body.getTarget().equals(clusterConfig.getNodeId())) {
            final Member targetMember = memberList.get(body.getTarget());
            if (targetMember == null) {
                throw new NotFoundException("Target member not found");
            }

            final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), body.getTarget());
            pingRequest.getPayload().addAll(body.getPayload());
            sendPingRequest(pingRequest, targetMember.getAddress());
            sendPongRequest(new PongRequest(body.getTarget()), member.getAddress());
            return;
        }

        sendPongRequest(new PongRequest(clusterConfig.getNodeId()), member.getAddress());
    }

    @PostMapping("/pong")
    public void pong(@RequestBody PongRequest body) {
        final Member member = memberList.get(body.getNodeId());
        if (member == null) {
            throw new NotFoundException("Member not found");
        }

        if (member.getStatus() == MemberStatus.SUSPICIOUS) {
            final MemberAliveMessage msg = new MemberAliveMessage(
                    clusterConfig.getNodeId(), member.getIncarnation(), member.getNodeId());
            msg.setRound(clusterConfig.getRound());
            pendingMessages.add(msg);
            member.setStatus(MemberStatus.ALIVE);
        }
    }

    @PostMapping("/join")
    public void join(HttpServletRequest request, @RequestBody JoinRequest body) {
        if (memberList.contains(body.getNodeId())) {
            throw new ConflictException("Member list contains member");
        }

        final Member member =
                new Member(body.getNodeId(), new InetSocketAddress(request.getRemoteAddr(), clusterConfig.getPort()));
        member.setIncarnation(body.getIncarnation());
        memberList.add(member);

        final JoinMessage joinMessage = new JoinMessage(
                body.getNodeId(),
                new InetSocketAddress(request.getRemoteAddr(), clusterConfig.getPort()),
                body.getIncarnation());
        joinMessage.setRound(clusterConfig.getRound());
        pendingMessages.add(joinMessage);
    }

    @PostMapping("/message")
    public void message(@RequestBody MessageRequest body) {
        final RandomMessage msg = new RandomMessage(body.getData());
        msg.setRound(clusterConfig.getRound());
        pendingMessages.add(msg);
    }

    private void sendPongRequest(
            final PongRequest pongRequest, final InetSocketAddress address) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://%s:%s/api/pong", address.getHostString(), address.getPort())))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pongRequest)))
                .header("Content-Type", "application/json")
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPingRequest(
            final PingRequest pingRequest, final InetSocketAddress address) throws IOException, InterruptedException {
        Utils.addPendingMessages(pingRequest, pendingMessages, memberList, clusterConfig.getRound());
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://%s:%s/api/ping", address.getHostString(), address.getPort())))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void handleMessage(final Message message) {
        switch (message.getType()) {
            case MEMBER_SUSPICIOUS -> {
                final MemberSuspiciousMessage memberSuspiciousMessage = (MemberSuspiciousMessage) message;

                if (memberSuspiciousMessage.getTarget().equals(clusterConfig.getNodeId())) {
                    if (memberSuspiciousMessage.getIncarnation() >= clusterConfig.getIncarnation()) {
                        clusterConfig.setIncarnation(memberSuspiciousMessage.getIncarnation() + 1);
                    }
                    final MemberAliveMessage aliveMsg = new MemberAliveMessage(
                            clusterConfig.getNodeId(), clusterConfig.getIncarnation(), clusterConfig.getNodeId());
                    aliveMsg.setRound(clusterConfig.getRound());
                    pendingMessages.add(aliveMsg);
                    return;
                }

                final Member member = memberList.get(memberSuspiciousMessage.getTarget());
                if (member != null && memberSuspiciousMessage.getIncarnation() >= member.getIncarnation()) {
                    member.setStatus(MemberStatus.SUSPICIOUS);
                    member.setRound(clusterConfig.getRound());
                }

                memberSuspiciousMessage.setRound(clusterConfig.getRound());
                pendingMessages.add(memberSuspiciousMessage);
            }
            case MEMBER_ALIVE -> {
                final MemberAliveMessage memberAliveMessage = (MemberAliveMessage) message;
                memberAliveMessage.setRound(clusterConfig.getRound());

                if (memberAliveMessage.getNodeId().equals(clusterConfig.getNodeId())) {
                    clusterConfig.setIncarnation(memberAliveMessage.getIncarnation());
                } else {
                    final Member member = memberList.get(memberAliveMessage.getTarget());
                    if (member != null && memberAliveMessage.getIncarnation() >= member.getIncarnation()) {
                        member.setIncarnation(memberAliveMessage.getIncarnation());
                        member.setStatus(MemberStatus.ALIVE);
                        member.setRound(-1);
                    }
                }

                pendingMessages.add(memberAliveMessage);
            }
            case MEMBER_DEAD -> {
                final MemberDeadMessage memberDeadMessage = (MemberDeadMessage) message;

                if (memberDeadMessage.getTarget().equals(clusterConfig.getNodeId())) {
                    if (memberDeadMessage.getIncarnation() >= clusterConfig.getIncarnation()) {
                        clusterConfig.setIncarnation(memberDeadMessage.getIncarnation() + 1);
                    }

                    final MemberAliveMessage aliveMsg = new MemberAliveMessage(
                            clusterConfig.getNodeId(), clusterConfig.getIncarnation(), clusterConfig.getNodeId());
                    aliveMsg.setRound(clusterConfig.getRound());
                    pendingMessages.add(aliveMsg);
                    return;
                }

                final Member member = memberList.get(memberDeadMessage.getTarget());
                if (member != null && memberDeadMessage.getIncarnation() >= member.getIncarnation()) {
                    memberList.remove(member);
                    memberDeadMessage.setRound(clusterConfig.getRound());
                }

                memberDeadMessage.setRound(clusterConfig.getRound());
                pendingMessages.add(memberDeadMessage);
            }
            case RANDOM_MESSAGE -> log.info("Received message: {}", ((RandomMessage) message).getData());
            case MEMBER_JOIN -> {
                final JoinMessage joinMessage = (JoinMessage) message;

                if (
                        !joinMessage.getNodeId().equals(clusterConfig.getNodeId()) &&
                                memberList.get(joinMessage.getNodeId()) != null) {
                    final Member newMember = new Member(joinMessage.getNodeId(), joinMessage.getAddress());
                    newMember.setIncarnation(joinMessage.getIncarnation());
                    memberList.add(newMember);
                }

                joinMessage.setRound(clusterConfig.getRound());
                pendingMessages.add(joinMessage);
            }
        }
    }
}
