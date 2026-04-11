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
import org.springframework.beans.factory.annotation.Value;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws InterruptedException, IOException {
        System.out.println("Cluster members: " + clusterConfig.getMembers());
        if (clusterConfig.getFailureTimeout() == 0) {
            log.warn("Failure timeout is 0. Stopping loop");
            return;
        }

        while (true) {
            Thread.sleep(clusterConfig.getFailureTimeout());

            final Member member = memberList.getRandom();
            if (member == null) {
                continue;
            }

            final PingRequest pingRequest = new PingRequest(member.getNodeId());
            for (Message message : pendingMessages) {
                pingRequest.getPayload().add(message);
            }

            sendPingRequest(pingRequest, member);
        }
    }

    private void sendPingRequest(
            final PingRequest pingRequest, final Member member) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(member.getAddress() + "/ping"))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pingRequest)))
                        .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @PreDestroy
    public void destroy() {
        httpClient.close();
    }
}
