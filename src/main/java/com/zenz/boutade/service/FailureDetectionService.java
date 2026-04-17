package com.zenz.boutade.service;

import com.zenz.boutade.config.ClusterConfig;
import com.zenz.boutade.message.MemberDeadMessage;
import com.zenz.boutade.message.MemberSuspiciousMessage;
import com.zenz.boutade.route.boutade.request.JoinRequest;
import com.zenz.boutade.route.boutade.request.PingRequest;
import com.zenz.boutade.route.boutade.request.RequestType;
import com.zenz.boutade.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class FailureDetectionService {

    private final MemberList memberList;

    private final PendingMessages pendingMessages;

    private final ClusterConfig clusterConfig;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private volatile boolean running = true;

    private Thread protocolThread;

    public FailureDetectionService(
            final MemberList memberList,
            final PendingMessages pendingMessages,
            final ClusterConfig clusterConfig,
            final HttpClient httpClient,
            final ObjectMapper objectMapper) {
        this.memberList = memberList;
        this.pendingMessages = pendingMessages;
        this.clusterConfig = clusterConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        protocolThread = new Thread(() -> {
            try {
                run();
            } catch (IOException | InterruptedException e) {
                if (running) {
                    throw new RuntimeException(e);
                }
            }
        });
        protocolThread.start();
    }

    public void run() throws IOException, InterruptedException {
        log.info("Starting gossip service");
        if (clusterConfig.getFailureTimeout() == 0) {
            log.error("Failure timeout set to 0. Stopping");
            return;
        }
        if (clusterConfig.getMembers() != null && !clusterConfig.getMembers().isEmpty()) {
            joinCluster();
        }
        while (running) {
            Thread.sleep(clusterConfig.getFailureTimeout());
            if (!running) break;
            detectFailure();
        }
    }

    public void joinCluster() throws InterruptedException {
        boolean success = false;
        final JoinRequest joinRequest = new JoinRequest(
                RequestType.JOIN,
                clusterConfig.getNodeId(),
                clusterConfig.getIncarnation());

        while (!success && running) {
            for (Member member : clusterConfig.getMembers()) {
                final HttpRequest request = HttpRequest
                        .newBuilder()
                        .uri(URI.create(String.format(
                                "http://%s:%s/boutade/join",
                                member.getAddress().getHostString(),
                                member.getAddress().getPort())))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(joinRequest)))
                        .header("Content-Type", "application/json")
                        .build();
                log.info("Sending join request to member {}, URI {}", member.getNodeId(), request.uri());
                try {
                    final HttpResponse<?> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 || response.statusCode() == 409) {
                        success = true;
                        log.info("Adding all config members to member list");
                        memberList.addAll(clusterConfig.getMembers());
                        break;
                    }
                } catch (IOException | InterruptedException e) {
                    log.warn("Failed to join via member {}: {}", member.getNodeId(), e.getMessage());
                }
            }

            if (!success) {
                Thread.sleep(5000L);
            }
        }

        log.info("Successfully joined cluster");
    }

    HttpResponse<String> sendPingRequest(
            final PingRequest pingRequest,
            final Member member) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(
                        "http://%s:%s/boutade/ping",
                        member.getAddress().getHostString(),
                        member.getAddress().getPort())))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> sendPingRequestIndirect(
            final PingRequest pingRequest,
            final Member member) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(
                        "http://%s:%s/boutade/ping",
                        member.getAddress().getHostString(),
                        member.getAddress().getPort())))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void detectFailure() {
        final Member randomMember = memberList.getRandom();
        if (randomMember == null) {
            log.info("Member list empty. No member found");
            return;
        }

        // Dead member list
        synchronized (memberList) {
            final List<Member> deadMembers = new ArrayList<>();
            for (Member member : memberList) {
                if (member.getStatus() == MemberStatus.SUSPICIOUS && clusterConfig.getRound() - member.getRound() > 3) {
                    deadMembers.add(member);
                }
            }
            for (Member member : deadMembers) {
                memberList.remove(member);
                pendingMessages.add(new MemberDeadMessage(
                        clusterConfig.getNodeId(),
                        member.getNodeId(),
                        member.getIncarnation()));
            }
        }

        final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), randomMember.getNodeId());
        Utils.addPendingMessages(pingRequest, pendingMessages, memberList, clusterConfig.getRound());

        try {
            log.info("Attempting to detect failure of member {}", randomMember);
            clusterConfig.incrementRound();
            sendPingRequest(pingRequest, randomMember);
            log.info("Member {} is alive", randomMember.getNodeId());
            if (randomMember.getStatus() == MemberStatus.SUSPICIOUS) {
                randomMember.setStatus(MemberStatus.ALIVE);
                randomMember.setRound(-1);
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Member {} potentially suspicious", randomMember.getNodeId());
            final int size = Math.min(3, memberList.size() - 1);
            final Set<Member> edgeMembers = new HashSet<>();
            while (edgeMembers.size() < size) {
                final Member edgeMember = memberList.getRandom();
                if (edgeMember == randomMember) {
                    continue;
                }
                edgeMembers.add(edgeMember);
            }

            Utils.addPendingMessages(pingRequest, pendingMessages, memberList, clusterConfig.getRound());

            boolean success = false;
            for (Member edgeMember : edgeMembers) {
                try {
                    final HttpResponse<String> response = sendPingRequestIndirect(pingRequest, edgeMember);
                    if (response.statusCode() == 200) {
                        success = true;
                        log.info("Member {} detected {} as alive", edgeMember.getNodeId(), randomMember.getNodeId());
                        break;
                    }

                } catch (IOException | InterruptedException e2) {
                    log.info("Member {} detected {} as dead", edgeMember.getNodeId(), randomMember.getNodeId());
                }
            }

            if (success) {
                log.info("Member {} is alive", randomMember.getNodeId());
                randomMember.setStatus(MemberStatus.ALIVE);
                randomMember.setRound(-1);
            } else {
                if (randomMember.getStatus() == MemberStatus.ALIVE) {
                    log.info("Member {} is suspicious", randomMember.getNodeId());
                    randomMember.setStatus(MemberStatus.SUSPICIOUS);
                    randomMember.setRound(clusterConfig.getRound());
                    final MemberSuspiciousMessage msg = new MemberSuspiciousMessage(
                            clusterConfig.getNodeId(), randomMember.getNodeId(), randomMember.getIncarnation());
                    msg.setRound(clusterConfig.getRound());
                    clusterConfig.incrementRound();
                    pendingMessages.add(msg);
                } else if (clusterConfig.getRound() - randomMember.getRound() >= 3) {
                    log.info("Member {} is dead", randomMember.getNodeId());
                    memberList.remove(randomMember);
                    final MemberDeadMessage msg = new MemberDeadMessage(
                            clusterConfig.getNodeId(), randomMember.getNodeId(), randomMember.getIncarnation());
                    msg.setRound(clusterConfig.getRound());
                    clusterConfig.incrementRound();
                    pendingMessages.add(msg);
                }
            }
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (protocolThread != null) {
            protocolThread.interrupt();
        }
        httpClient.close();
    }

    private void broadcastLeave() {
        final MemberDeadMessage leaveMsg = new MemberDeadMessage(
                clusterConfig.getNodeId(), clusterConfig.getNodeId(), clusterConfig.getIncarnation());
        leaveMsg.setRound(clusterConfig.getRound());
        pendingMessages.add(leaveMsg);

        log.info("Broadcasting leave message to cluster");
        try {
            for (Member member : clusterConfig.getMembers()) {
                try {
                    final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), member.getNodeId());
                    Utils.addPendingMessages(pingRequest, pendingMessages, memberList, clusterConfig.getRound());
                    log.info("Broadcasting leaving ping {}", pingRequest);
                    final HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(String.format(
                                    "http://%s:%s/boutade/ping",
                                    member.getAddress().getHostString(),
                                    member.getAddress().getPort())))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                            .header("Content-Type", "application/json")
                            .build();
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    log.warn("Failed to send leave message to {}: {}", member.getNodeId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error during leave broadcast: {}", e.getMessage());
        }
    }
}
