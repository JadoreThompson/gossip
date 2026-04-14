package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.JoinRequest;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.route.api.request.RequestType;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailureDetectionService {

    private final MemberList memberList;

    private final PendingMessages pendingMessages;

    private final ClusterConfig clusterConfig;

    @Getter
    @Setter
    private HttpClient httpClient = HttpClient.newHttpClient();

    @Setter
    private ObjectMapper objectMapper = new ObjectMapper();

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

    void joinCluster() throws InterruptedException {
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

    void sendPingRequest(final Member member) throws IOException, InterruptedException {
        final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), member.getNodeId());
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
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    void detectFailures() throws InterruptedException {
        if (clusterConfig.getFailureTimeout() == 0) {
            log.warn("Failure timeout is 0. Stopping loop");
            return;
        }

        while (true) {
            log.info("Sleeping for " + clusterConfig.getFailureTimeout() + " milliseconds");
            Thread.sleep(clusterConfig.getFailureTimeout());

            final Member member = memberList.getRandom();
            if (member == null) {
                log.info("No member found");
                continue;
            }

            try {
                sendPingRequest(member);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
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