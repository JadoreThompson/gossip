package com.zenz.gossip.service;

import com.zenz.gossip.config.ClusterConfig;
import com.zenz.gossip.message.Message;
import com.zenz.gossip.route.api.request.PingRequest;
import com.zenz.gossip.util.Member;
import com.zenz.gossip.util.MemberList;
import com.zenz.gossip.util.PendingMessages;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class FailureDetectionService {

    private final MemberList memberList;

    private final PendingMessages pendingMessages;

    private final ClusterConfig clusterConfig;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws InterruptedException, IOException {
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

    private void sendPingRequest(final Member member) throws IOException, InterruptedException {
        final PingRequest pingRequest = new PingRequest(clusterConfig.getNodeId(), member.getNodeId());
        for (Message message : pendingMessages) {
            pingRequest.getPayload().add(message);
        }

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

    @PreDestroy
    public void destroy() {
        httpClient.close();
    }

    private interface CheckedRunnable {

        void run() throws IOException, InterruptedException;
    }
}
