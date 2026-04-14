package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.MemberDeadMessage;
import com.zenz.gossip.message.MemberSuspiciousMessage;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.RequestType;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.MemberStatus;
import com.zenz.gossip.util.PendingMessages;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FailureDetectionService {

    private MemberList memberList;

    private PendingMessages pendingMessages;

    private ClusterConfig clusterConfig;

    @Getter
    @Setter
    private HttpClient httpClient = HttpClient.newHttpClient();

    @Setter
    private ObjectMapper objectMapper = new ObjectMapper();

    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FailureDetectionService.class);

    public FailureDetectionService() {
    }

    public FailureDetectionService(final MemberList memberList, final PendingMessages pendingMessages, final ClusterConfig clusterConfig) {
        this.memberList = memberList;
        this.pendingMessages = pendingMessages;
        this.clusterConfig = clusterConfig;
    }

    @PostConstruct
    public void init() {
        final Thread th = new Thread(() -> {
            try {
                run();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        th.start();
    }

    public void run() throws IOException, InterruptedException {
        log.info("Starting gossip service");
        joinCluster();
        detectFailures();
    }

    public void joinCluster() throws InterruptedException {
        log.info("Starting cluster join");
        boolean success = false;
        final JoinRequest joinRequest = new JoinRequest(
                RequestType.JOIN,
                clusterConfig.getNodeId(),
                clusterConfig.getAddress(),
                clusterConfig.getIncarnation());

        log.info("Formed request {}", joinRequest);

        while (!success) {
            for (Member member : clusterConfig.getMembers()) {
                final HttpRequest request = HttpRequest
                        .newBuilder()
                        .uri(URI.create(String.format("http://%s:%s/join", member.getAddress().getHostString(), member.getAddress().getPort())))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(joinRequest)))
                        .header("Content-Type", "application/json")
                        .build();
                log.info("Sending join request to member " + member.getNodeId() + ", URI " + request.uri());
                try {
                    final HttpResponse<?> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        success = true;
                        log.info("Adding all config members to member list");
                        memberList.addAll(clusterConfig.getMembers());
                        break;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Thread.sleep(5000L);
        }

        log.info("Successfully joined cluster");
    }

    HttpResponse<String> sendPingRequest(final PingRequest pingRequest, final Member member) throws IOException, InterruptedException {
//        final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), member.getNodeId());
        final List<Message> messages = pendingMessages.toList();
        for (Message message : messages) {
            pingRequest.getPayload().add(message);
        }
        pendingMessages.clear();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(
                        "http://%s:%s/ping",
                        member.getAddress().getHostString(),
                        member.getAddress().getPort())))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void detectFailures() throws InterruptedException {
        log.info("Sleeping for " + clusterConfig.getFailureTimeout() + " milliseconds");
//        try {
//            Thread.sleep(clusterConfig.getFailureTimeout());
//        } catch (InterruptedException e) {
//            return;
//        }
        if (clusterConfig.getFailureTimeout() == 0) {
            return;
        }

        final Member member = memberList.getRandom();
        if (member == null) {
            log.info("Member list empty. No member found");
            return;
        }

        try {
            sendPingRequest(new PingRequest(clusterConfig.getNodeId(), member.getNodeId()), member);
        } catch (IOException | InterruptedException e) {
            final int maxFailures = memberList.size() - 1;
            final List<Member> edgeMembers = new ArrayList<>();
            for (int i = 0; i < memberList.size(); i++) {
                if (edgeMembers.size() == maxFailures) {
                    break;
                }
                final Member edgeMember = memberList.getRandom();
                if (edgeMember == member) {
                    continue;
                }
                edgeMembers.add(edgeMember);
            }

            int failureCount = 0;
            for (Member edgeMember : edgeMembers) {
                try {
                    sendPingRequest(new PingRequest(clusterConfig.getNodeId(), edgeMember.getNodeId()), edgeMember);
                } catch (IOException | InterruptedException e2) {
                    ++failureCount;
                }
            }

            if (failureCount == maxFailures) {
                if (member.getStatus() == MemberStatus.SUSPICIOUS) {
                    memberList.remove(member);
                    pendingMessages.add(new MemberDeadMessage(
                            clusterConfig.getNodeId(), member.getNodeId(), member.getIncarnation()));
                } else {
                    member.setStatus(MemberStatus.SUSPICIOUS);
                    pendingMessages.add(new MemberSuspiciousMessage(
                            clusterConfig.getNodeId(), member.getNodeId(), member.getIncarnation()));
                }
            } else {
                member.setStatus(MemberStatus.ALIVE);
            }
        }
    }


    @PreDestroy
    public void destroy() {
        httpClient.close();
    }

    private interface CheckedRunnable {

        void run() throws IOException, InterruptedException;
    }
}