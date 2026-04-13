package com.zenz.gossip.route.api;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.*;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.MessageRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.PongRequest;
import com.zenz.gossip.route.exception.BadRequestException;
import com.zenz.gossip.route.exception.NotFoundException;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.MemberStatus;
import com.zenz.gossip.util.PendingMessages;
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
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class ApiController {

    private final MemberList memberList;

    private final PendingMessages pendingMessages;

    private final ClusterConfig clusterConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/ping")
    public void ping(@RequestBody PingRequest body) throws IOException, InterruptedException {
        final Member member = memberList.get(body.getNodeId());
        if (member == null) {
            throw new NotFoundException("Member not found");
        }

        for (Message message : body.getPayload()) {
            handleMessage(message);
        }

        sendPongRequest(member.getAddress());
    }

    @PostMapping("/pong")
    public void pong(@RequestBody PongRequest body) {
        log.info("Pong Request: {}", body);
        final Member member = memberList.get(body.getNodeId());
        if (member == null) {
            throw new NotFoundException("Member not found");
        }

        if (member.getStatus() == MemberStatus.SUSPICIOUS) {
            pendingMessages.add(new MemberAliveMessage(member.getNodeId(), member.getIncarnation()));
            member.setStatus(MemberStatus.ALIVE);
        }
    }

    @PostMapping("/join")
    public void join(@RequestBody JoinRequest body) {
        log.info("Join request {}", body);
        final Member member = new Member(body.getNodeId(), body.getAddress());
        if (memberList.contains(member)) {
            throw new BadRequestException("Member list contains member");
        }
    }

    @PostMapping("/message")
    public void message(@RequestBody MessageRequest body) {
        pendingMessages.add(new RandomMessage(body.getData()));
    }

    private void sendPongRequest(final InetSocketAddress address) throws IOException, InterruptedException {
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final PongRequest pongRequest = new PongRequest(clusterConfig.getNodeId());

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("http://%s:%s/pong", address.getHostString(), address.getPort())))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pongRequest)))
                    .header("Content-Type", "application/json")
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void handleMessage(final Message message) {
        switch (message.getType()) {
            case MEMBER_SUSPICIOUS -> {
                final MemberSuspiciousMessage memberSuspiciousMessage = (MemberSuspiciousMessage) message;
                final Member member = memberList.get(memberSuspiciousMessage.getNodeId());
                if (member != null && member.getStatus() != MemberStatus.SUSPICIOUS) {
                    member.setStatus(MemberStatus.SUSPICIOUS);
                    pendingMessages.add(memberSuspiciousMessage);
                }
            }
            case MEMBER_ALIVE -> {
                final MemberAliveMessage memberAliveMessage = (MemberAliveMessage) message;
                final Member member = memberList.get(memberAliveMessage.getNodeId());
                if (member != null && member.getStatus() != MemberStatus.ALIVE) {
                    member.setStatus(MemberStatus.ALIVE);
                    pendingMessages.add(memberAliveMessage);
                }
            }
            case MEMBER_DEAD -> {
                final MemberDeadMessage memberDeadMessage = (MemberDeadMessage) message;
                final Member member = memberList.get(memberDeadMessage.getNodeId());
                if (member != null) {
                    memberList.remove(member);
                }
            }
            case RANDOM_MESSAGE -> log.info("Received message: {}", ((RandomMessage) message).getData());
        }
    }
}
